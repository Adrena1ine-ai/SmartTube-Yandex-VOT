package com.liskovsoft.smartyoutubetv2.common.vot;

import android.content.Context;

import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.prefs.VotData;

import org.json.JSONObject;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.schedulers.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class VotClient {
    private static final String TAG = VotClient.class.getSimpleName();
    private final VotHttp mHttp = new VotHttp();
    private final VotData mVotData;
    @Nullable
    private VotSession mSession;

    public VotClient(Context context) {
        mVotData = VotData.instance(context);
    }

    public String translateToRussian(String youtubeUrl, long durationSec) throws IOException, VotException {
        VotProgress last = observeTranslation(youtubeUrl, durationSec)
                .blockingLast();
        if (last == null) {
            throw new VotException("Translation cancelled");
        }
        if (last.type == VotProgress.TYPE_READY && last.audioUrl != null) {
            return last.audioUrl;
        }
        if (last.type == VotProgress.TYPE_FAILED) {
            throw new VotException(last.message != null ? last.message : "Translation failed");
        }
        throw new VotException("Translation timeout");
    }

    public Observable<VotProgress> observeTranslation(String youtubeUrl, long durationSec) {
        return Observable.<VotProgress>create(emitter -> pollTranslation(emitter, youtubeUrl, durationSec, true))
                .subscribeOn(Schedulers.io());
    }

    private void pollTranslation(ObservableEmitter<VotProgress> emitter, String youtubeUrl, long durationSec,
                                 boolean allowAudioFallback) {
        try {
            boolean[] serverRetryUsed = {false};
            emitter.onNext(VotProgress.waiting(0, -1, VotProgress.STAGE_REQUESTING));
            VotTranslationResponse response = requestTranslationWithServerRetry(
                    emitter, youtubeUrl, durationSec, false, 0, serverRetryUsed);
            if (!processResponse(emitter, youtubeUrl, durationSec, allowAudioFallback, response)) {
                return;
            }

            int pollAttempt = 0;
            while (!emitter.isDisposed()) {
                int waitSec = VotPollingPolicy.getDelaySec(pollAttempt, response.remainingTimeSec);
                Log.d(TAG, "VOT poll scheduled: attempt=%s delaySec=%s status=%s etaSec=%s",
                        pollAttempt + 1, waitSec, response.getStatusName(), response.remainingTimeSec);
                sleep(waitSec);
                if (emitter.isDisposed()) {
                    return;
                }
                pollAttempt++;
                response = requestTranslationWithServerRetry(
                        emitter, youtubeUrl, durationSec, true, pollAttempt, serverRetryUsed);
                if (!processResponse(emitter, youtubeUrl, durationSec, allowAudioFallback, response)) {
                    return;
                }
            }
        } catch (IOException e) {
            if (!emitter.isDisposed()) {
                emitter.onError(e);
            }
        } catch (VotException e) {
            if (!emitter.isDisposed()) {
                emitter.onNext(VotProgress.failed(e.getMessage()));
                emitter.onComplete();
            }
        }
    }

    /** @return false if polling should stop (ready, failed, or disposed) */
    private boolean processResponse(ObservableEmitter<VotProgress> emitter, String youtubeUrl, long durationSec,
                                    boolean allowAudioFallback, VotTranslationResponse response)
            throws IOException, VotException {
        if (emitter.isDisposed()) {
            return false;
        }

        if (!response.parseValid) {
            emitter.onNext(VotProgress.failed("Malformed translation response"));
            emitter.onComplete();
            return false;
        }

        if (response.status == VotTranslationResponse.STATUS_SESSION_REQUIRED) {
            emitter.onNext(VotProgress.failed("auth required"));
            emitter.onComplete();
            return false;
        }

        if (response.status == VotTranslationResponse.STATUS_AUDIO_REQUESTED && allowAudioFallback) {
            emitter.onNext(VotProgress.waiting(response.remainingTimeSec, response.status,
                    VotProgress.STAGE_AUDIO_REQUESTED));
            handleAudioRequested(youtubeUrl, durationSec, response.translationId);
            pollTranslation(emitter, youtubeUrl, durationSec, false);
            return false;
        }

        if (response.isReady() && response.url != null && !response.url.isEmpty()) {
            emitter.onNext(VotProgress.ready(response.url, response.remainingTimeSec, response.isComplete()));
            if (response.isComplete()) {
                emitter.onComplete();
                return false;
            }
            return true;
        }

        if (response.status == VotTranslationResponse.STATUS_FAILED) {
            String msg = response.message != null ? response.message : "Translation failed";
            emitter.onNext(VotProgress.failed(msg));
            emitter.onComplete();
            return false;
        }

        if (response.isWaiting() || response.status == VotTranslationResponse.STATUS_AUDIO_REQUESTED) {
            emitter.onNext(VotProgress.waiting(response.remainingTimeSec, response.status,
                    getProgressStage(response.status)));
            return true;
        }

        emitter.onNext(VotProgress.failed("Unexpected translation status: " + response.status));
        emitter.onComplete();
        return false;
    }

    private int getProgressStage(int status) {
        switch (status) {
            case VotTranslationResponse.STATUS_LONG_WAITING:
                return VotProgress.STAGE_LONG_WAITING;
            case VotTranslationResponse.STATUS_AUDIO_REQUESTED:
                return VotProgress.STAGE_AUDIO_REQUESTED;
            default:
                return VotProgress.STAGE_PROCESSING;
        }
    }

    private VotTranslationResponse requestTranslationWithServerRetry(
            ObservableEmitter<VotProgress> emitter, String youtubeUrl, long durationSec,
            boolean subsequent, int pollAttempt, boolean[] serverRetryUsed) throws IOException {
        boolean useLively = mVotData.isLivelyVoiceEnabled();
        VotTranslationResponse response;
        try {
            response = requestTranslation(youtubeUrl, durationSec, subsequent, useLively);
        } catch (VotHttpException e) {
            if (!VotPollingPolicy.shouldFallbackFromLivelyPollHttpError(
                    e.responseCode, subsequent, useLively, serverRetryUsed[0]) || emitter.isDisposed()) {
                throw e;
            }
            serverRetryUsed[0] = true;
            Log.d(TAG, "VOT lively poll HTTP 400; retrying once without lively: poll=%s", pollAttempt);
            emitter.onNext(VotProgress.waiting(0, VotTranslationResponse.STATUS_FAILED,
                    VotProgress.STAGE_RETRYING));
            response = requestTranslation(youtubeUrl, durationSec, false, false);
            logResponse(response, pollAttempt, false);
            return response;
        }
        logResponse(response, pollAttempt, subsequent);
        if (!VotPollingPolicy.shouldRetryServerFailure(response, serverRetryUsed[0]) || emitter.isDisposed()) {
            return response;
        }
        serverRetryUsed[0] = true;
        Log.d(TAG, "VOT server requested one retry: poll=%s status=%s",
                pollAttempt, response.getStatusName());
        emitter.onNext(VotProgress.waiting(0, response.status, VotProgress.STAGE_RETRYING));
        response = requestTranslation(youtubeUrl, durationSec, false, false);
        logResponse(response, pollAttempt, false);
        return response;
    }

    private void logResponse(VotTranslationResponse response, int pollAttempt, boolean subsequent) {
        Log.d(TAG,
                "VOT response: poll=%s subsequent=%s status=%s etaSec=%s hasAudioUrl=%s hasTranslationId=%s " +
                        "valid=%s lively=%s allow=%s retry=%s hasMessage=%s",
                pollAttempt, subsequent, response.getStatusName(), response.remainingTimeSec,
                response.url != null && !response.url.isEmpty(),
                response.translationId != null && !response.translationId.isEmpty(),
                response.parseValid, response.isLivelyVoice, response.allowToTranslateVideo, response.shouldRetry,
                response.message != null && !response.message.isEmpty());
    }

    private VotTranslationResponse requestTranslation(String youtubeUrl, double durationSec, boolean subsequent)
            throws IOException {
        return requestTranslation(youtubeUrl, durationSec, subsequent, mVotData.isLivelyVoiceEnabled());
    }

    private VotTranslationResponse requestTranslation(String youtubeUrl, double durationSec, boolean subsequent,
                                                       boolean useLively) throws IOException {
        Log.d(TAG, "VOT request: first=%s lively=%s oauth=%s",
                !subsequent, useLively, useLively && !mVotData.getOAuthToken().isEmpty());
        byte[] body = VotProtobuf.encodeTranslationRequest(
                youtubeUrl,
                durationSec,
                VotConfig.REQUEST_LANG,
                VotConfig.RESPONSE_LANG,
                !subsequent,
                useLively
        );

        Map<String, String> headers = buildTranslateHeaders(body, useLively);
        byte[] raw;
        try {
            raw = mHttp.postProtobuf("/video-translation/translate", body, headers);
        } catch (VotHttpException e) {
            VotTranslationResponse errorResponse = decodeHttpErrorResponse(e);
            if (errorResponse != null) {
                return errorResponse;
            }
            throw e;
        }

        if (raw == null || raw.length == 0) {
            throw new IOException("Empty translation response");
        }
        return VotProtobuf.decodeTranslationResponse(raw);
    }

    @Nullable
    private VotTranslationResponse decodeHttpErrorResponse(VotHttpException error) {
        byte[] body = error.responseBody;
        if (body == null || body.length == 0) {
            Log.e(TAG, "VOT HTTP error: code=%s bodyBytes=0 protocol=false", error.responseCode);
            return null;
        }
        VotTranslationResponse response = VotProtobuf.decodeTranslationResponse(body);
        boolean protocolResponse = response.parseValid &&
                (response.statusPresent || response.message != null || response.translationId != null);
        Log.e(TAG, "VOT HTTP error: code=%s bodyBytes=%s protocol=%s status=%s etaSec=%s hasMessage=%s",
                error.responseCode, body.length, protocolResponse,
                protocolResponse ? response.getStatusName() : "UNKNOWN", response.remainingTimeSec,
                response.message != null && !response.message.isEmpty());
        return protocolResponse ? response : null;
    }

    private Map<String, String> buildTranslateHeaders(byte[] body, boolean useLively) {
        Map<String, String> headers;
        if (mSession != null) {
            headers = VotHeaders.sessionTranslate(mSession, body, "/video-translation/translate");
        } else {
            headers = VotHeaders.simpleTranslate(body);
        }
        if (useLively) {
            headers = VotHeaders.merge(headers, VotHeaders.oauthHeader(mVotData.getOAuthToken()));
        }
        return headers;
    }

    private void handleAudioRequested(String youtubeUrl, long durationSec, @Nullable String translationId)
            throws IOException, VotException {
        if (translationId == null || translationId.isEmpty()) {
            VotTranslationResponse r = requestTranslation(youtubeUrl, durationSec, false);
            translationId = r.translationId;
        }
        if (translationId == null || translationId.isEmpty()) {
            throw new VotException("Missing translationId for audio upload");
        }

        ensureSession();
        requestFailAudio(youtubeUrl);
        uploadEmptyAudio(youtubeUrl, translationId);
    }

    private void requestFailAudio(String youtubeUrl) throws IOException, VotException {
        String json = "{\"video_url\":\"" + youtubeUrl.replace("\"", "\\\"") + "\"}";
        byte[] raw = mHttp.putJson("/video-translation/fail-audio-js", json,
                VotHeaders.simpleTranslate(json.getBytes(StandardCharsets.UTF_8)));
        if (raw == null) {
            throw new VotException("fail-audio-js: empty response");
        }
        try {
            String text = new String(raw, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(text);
            if (obj.optInt("status", 0) != 1) {
                throw new VotException("fail-audio-js failed");
            }
        } catch (VotException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "fail-audio-js parse error: %s", e.getMessage());
        }
    }

    private void uploadEmptyAudio(String youtubeUrl, String translationId) throws IOException {
        byte[] body = VotProtobuf.encodeTranslationAudioRequest(
                youtubeUrl, translationId, VotConfig.FAKE_AUDIO_FILE_ID);
        mHttp.putProtobuf("/video-translation/audio", body,
                VotHeaders.sessionTranslate(mSession, body, "/video-translation/audio"));
    }

    private void ensureSession() throws IOException {
        if (mSession != null && System.currentTimeMillis() - mSession.createdAtMs < mSession.expiresSec * 1000L) {
            return;
        }
        String uuid = VotSignature.randomToken();
        byte[] body = VotProtobuf.encodeSessionRequest(uuid, "video-translation");
        byte[] raw = mHttp.postProtobuf("/session/create", body, VotHeaders.simpleTranslate(body));
        if (raw == null) {
            throw new IOException("Empty session response");
        }
        VotSession decoded = VotProtobuf.decodeSessionResponse(raw);
        if (!decoded.parseValid || decoded.secretKey == null || decoded.secretKey.isEmpty()) {
            throw new IOException("Malformed VOT session response");
        }
        decoded.uuid = uuid;
        decoded.createdAtMs = System.currentTimeMillis();
        mSession = decoded;
    }

    private void sleep(int sec) throws VotException {
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VotException("Interrupted");
        }
    }
}
