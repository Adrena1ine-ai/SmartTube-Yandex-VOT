package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import com.liskovsoft.sharedutils.helpers.MessageHelpers;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.BasePlayerController;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.VotData;
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.smartyoutubetv2.common.vot.TranslationAudioPlayer;
import com.liskovsoft.smartyoutubetv2.common.vot.VotAudioTrackHelper;
import com.liskovsoft.smartyoutubetv2.common.vot.VotAudioTrackHelper.TrackInfo;
import com.liskovsoft.smartyoutubetv2.common.vot.VotClient;
import com.liskovsoft.smartyoutubetv2.common.vot.VotProgress;

import java.io.IOException;
import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

/**
 * Yandex voice-over translation (EN→RU) alongside the main player.
 */
public class VoiceTranslateController extends BasePlayerController {
    private static final String TAG = VoiceTranslateController.class.getSimpleName();
    private static final int ACTION_VOICE_TRANSLATE = R.id.action_voice_translate;

    public static final int BTN_OFF = 0;
    public static final int BTN_PENDING = 1;
    public static final int BTN_ON = 2;
    public static final int BTN_ERROR = 3;

    private static final int STATE_OFF = 0;
    private static final int STATE_PENDING = 1;
    private static final int STATE_ACTIVE = 2;
    private static final int STATE_ERROR = 3;

    private static final long SYNC_INTERVAL_MS = 1000;
    private static final long SYNC_THRESHOLD_MS = 800;
    private static final long AUTO_TRANSLATE_RETRY_MS = 1000;
    private static final int AUTO_TRANSLATE_MAX_RETRIES = 20;
    private static final long ERROR_DISPLAY_MS = 3_000;

    private VotData mVotData;
    private VotClient mVotClient;
    private TranslationAudioPlayer mTranslationPlayer;
    private Disposable mTranslationDisposable;
    private float mSavedMainVolume = 1f;
    private boolean mMainAudioDucked;
    private FormatItem mSavedAudioFormat;
    private boolean mUserArmed;
    private boolean mArmed;
    private int mState = STATE_OFF;
    private int mPendingEtaSec;
    private int mPendingStage = VotProgress.STAGE_REQUESTING;
    private boolean mPendingToastShown;
    private boolean mPartialTranslation;
    private String mTranslationAudioUrl;
    private String mPendingVideoUrl;
    private String mCurrentVideoId;
    private int mAutoTranslateRetryCount;

    private final Runnable mSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (mState == STATE_ACTIVE) {
                syncTranslationPositionIfNeeded();
                Utils.postDelayed(mSyncRunnable, SYNC_INTERVAL_MS);
            }
        }
    };

    private final Runnable mAutoTranslateRetryRunnable = new Runnable() {
        @Override
        public void run() {
            tryApplyAutoTranslate(false);
        }
    };

    private final Runnable mErrorResetRunnable = new Runnable() {
        @Override
        public void run() {
            if (mState == STATE_ERROR) {
                setState(STATE_OFF);
            }
        }
    };

    private final Runnable mEtaTickRunnable = new Runnable() {
        @Override
        public void run() {
            boolean showEta = mState == STATE_PENDING || (mState == STATE_ACTIVE && mPartialTranslation);
            if (!showEta || mPendingEtaSec <= 0) {
                return;
            }
            mPendingEtaSec--;
            updateVoiceButton(mState == STATE_ACTIVE ? BTN_ON : BTN_PENDING);
            if (mPendingEtaSec > 0) {
                Utils.postDelayed(mEtaTickRunnable, 1_000);
            }
        }
    };

    public VoiceTranslateController() {
    }

    private VotData votData() {
        if (mVotData == null) {
            mVotData = VotData.instance(getContext());
        }
        return mVotData;
    }

    private VotClient votClient() {
        if (mVotClient == null) {
            mVotClient = new VotClient(getContext());
        }
        return mVotClient;
    }

    @Override
    public void onNewVideo(Video item) {
        Utils.removeCallbacks(mAutoTranslateRetryRunnable, mErrorResetRunnable, mEtaTickRunnable);
        mAutoTranslateRetryCount = 0;
        cancelTranslationJob();
        releaseTranslationPlayer();
        restoreMainVolume();
        restoreSavedAudioFormat();
        mPendingToastShown = false;
        mPendingEtaSec = 0;
        mPendingStage = VotProgress.STAGE_REQUESTING;
        mPartialTranslation = false;
        mTranslationAudioUrl = null;
        mPendingVideoUrl = null;
        mCurrentVideoId = item != null ? item.videoId : null;

        if (mUserArmed) {
            mArmed = true;
            setState(STATE_PENDING);
        } else {
            mArmed = false;
            setState(STATE_OFF);
        }
    }

    @Override
    public void onVideoLoaded(Video item) {
        tryApplyAutoTranslate(false);
    }

    @Override
    public void onMetadata(MediaItemMetadata metadata) {
        tryApplyAutoTranslate(false);
    }

    @Override
    public void onTrackChanged(FormatItem track) {
        if (track != null && track.getType() == FormatItem.TYPE_AUDIO) {
            tryApplyAutoTranslate(true);
        }
    }

    @Override
    public void onPlay() {
        if (mState == STATE_ACTIVE && mTranslationPlayer != null) {
            mTranslationPlayer.resume();
            syncTranslationPositionIfNeeded();
        }
    }

    @Override
    public void onPause() {
        if (mState == STATE_ACTIVE && mTranslationPlayer != null) {
            mTranslationPlayer.pause();
        }
    }

    @Override
    public void onSeekEnd() {
        if (mState == STATE_ACTIVE && mTranslationPlayer != null && mTranslationPlayer.isReady()) {
            mTranslationPlayer.seekTo(getPlayer().getPositionMs());
        }
    }

    @Override
    public void onSpeedChanged(float speed) {
        if (mState == STATE_ACTIVE && mTranslationPlayer != null) {
            mTranslationPlayer.setPlaybackSpeed(speed);
        }
    }

    @Override
    public void onEngineReleased() {
        disarm();
    }

    @Override
    public void onButtonClicked(int buttonId, int buttonState) {
        if (buttonId != ACTION_VOICE_TRANSLATE) {
            return;
        }
        if (buttonState == BTN_OFF || buttonState == BTN_ERROR) {
            armAndStart();
        } else {
            disarm();
        }
    }

    @Override
    public void onButtonLongClicked(int buttonId, int buttonState) {
        if (buttonId == ACTION_VOICE_TRANSLATE) {
            AppDialogUtil.showVotMixDialog(getContext(), () -> tryApplyAutoTranslate(false));
        }
    }

    private void armAndStart() {
        if (votData().isPreferYoutubeAutoDub()) {
            MessageHelpers.showMessage(getContext(), R.string.vot_disable_google_for_yandex);
            return;
        }
        TrackInfo info = resolveAudioInfo();
        if (VotAudioTrackHelper.isRussianOriginal(info)) {
            MessageHelpers.showMessage(getContext(), R.string.vot_already_russian);
            return;
        }
        mUserArmed = true;
        mArmed = true;
        startYandexTranslation();
    }

    private void tryApplyAutoTranslate(boolean fromTrackChange) {
        if (getPlayer() == null || getPlayer().getVideo() == null) {
            return;
        }
        String videoId = getPlayer().getVideo().videoId;
        if (videoId != null && !videoId.equals(mCurrentVideoId)) {
            mCurrentVideoId = videoId;
            mAutoTranslateRetryCount = 0;
        }

        boolean autoEnabled = votData().isAutoTranslateEnabled();
        if (!autoEnabled && !mUserArmed) {
            return;
        }

        if (mState == STATE_ACTIVE) {
            return;
        }

        if (votData().isPreferYoutubeAutoDub()) {
            if (tryApplyYoutubeAutoDub(autoEnabled)) {
                return;
            }
        }

        TrackInfo info = resolveAudioInfo();

        if (info.langCode == null && info.acont == null) {
            List<FormatItem> formats = getAudioFormats();
            if (autoEnabled && VotAudioTrackHelper.shouldAutoStartLikeManual(info, formats)) {
                Utils.removeCallbacks(mAutoTranslateRetryRunnable);
                mAutoTranslateRetryCount = 0;
                Log.d(TAG, "auto-start (legacy/metadata-poor) video=%s tracks=%s",
                        videoId, VotAudioTrackHelper.formatTracksForLog(formats));
                if (!mArmed || mState == STATE_OFF) {
                    mArmed = true;
                    startYandexTranslation();
                }
                return;
            }
            if ((autoEnabled || mUserArmed) && mAutoTranslateRetryCount < AUTO_TRANSLATE_MAX_RETRIES) {
                mAutoTranslateRetryCount++;
                Utils.removeCallbacks(mAutoTranslateRetryRunnable);
                Utils.postDelayed(mAutoTranslateRetryRunnable, AUTO_TRANSLATE_RETRY_MS);
                return;
            }
            if (autoEnabled && !VotAudioTrackHelper.isLikelyRussianContent(info, formats)
                    && (VotAudioTrackHelper.canStartLikeManualButton(info)
                    || VotAudioTrackHelper.isMetadataPoorNonRussianAudio(formats))) {
                Log.d(TAG, "auto-start (late metadata) video=%s tracks=%s",
                        videoId, VotAudioTrackHelper.formatTracksForLog(formats));
                if (!mArmed || mState == STATE_OFF) {
                    mArmed = true;
                    startYandexTranslation();
                }
                return;
            }
            if (autoEnabled) {
                Log.d(TAG, "auto skipped (no lang) video=%s tracks=%s",
                        videoId, VotAudioTrackHelper.formatTracksForLog(formats));
            }
            return;
        }

        Utils.removeCallbacks(mAutoTranslateRetryRunnable);
        mAutoTranslateRetryCount = 0;

        if (VotAudioTrackHelper.isRussianOriginal(info)) {
            if (mUserArmed) {
                disarmWithMessage(R.string.vot_skip_russian);
                mUserArmed = false;
            } else if (autoEnabled) {
                if (mArmed || mState != STATE_OFF) {
                    disarmQuiet();
                }
                MessageHelpers.showMessage(getContext(), R.string.vot_skip_russian);
            }
            return;
        }

        if (!autoEnabled && !mUserArmed) {
            return;
        }

        List<FormatItem> formats = getAudioFormats();
        if (VotAudioTrackHelper.shouldAutoStartLikeManual(info, formats)) {
            if (!mArmed || (mState == STATE_OFF && mTranslationDisposable == null)) {
                mArmed = true;
                startYandexTranslation();
            }
        } else if (autoEnabled) {
            Log.d(TAG, "auto skipped video=%s track=%s legacy=%s all=%s",
                    videoId, info.rawLabel,
                    VotAudioTrackHelper.isLegacyEnglishStream(info, formats),
                    VotAudioTrackHelper.formatTracksForLog(formats));
        }
    }

    /** @return true if YouTube dub was applied and Yandex should not run */
    private boolean tryApplyYoutubeAutoDub(boolean showToast) {
        FormatItem dub = VotAudioTrackHelper.findYoutubeRussianAutoDub(getAudioFormats());
        if (dub == null) {
            return false;
        }
        saveCurrentAudioFormatBeforeSwitch(dub);
        getPlayer().setFormat(dub);
        mArmed = false;
        mUserArmed = false;
        cancelTranslationJob();
        releaseTranslationPlayer();
        restoreMainVolume();
        setState(STATE_OFF);
        if (showToast) {
            MessageHelpers.showMessage(getContext(), R.string.vot_using_youtube_dub);
        }
        return true;
    }

    private void startYandexTranslation() {
        if (getPlayer() == null || getPlayer().getVideo() == null) {
            MessageHelpers.showMessage(getContext(), R.string.vot_error_no_video);
            return;
        }

        ensureOriginalAudioForYandex();

        TrackInfo info = resolveAudioInfo();
        if (VotAudioTrackHelper.isRussianOriginal(info)) {
            disarmWithMessage(R.string.vot_skip_russian);
            return;
        }

        String videoUrl = getPlayer().getVideo().videoId != null
                ? "https://www.youtube.com/watch?v=" + getPlayer().getVideo().videoId
                : null;
        if (videoUrl == null) {
            MessageHelpers.showMessage(getContext(), R.string.vot_error_no_video);
            return;
        }

        if (mTranslationDisposable != null && !mTranslationDisposable.isDisposed()
                && videoUrl.equals(mPendingVideoUrl)) {
            return;
        }

        cancelTranslationJob();
        mPendingToastShown = false;
        mPendingEtaSec = 0;
        mPendingStage = VotProgress.STAGE_REQUESTING;
        Utils.removeCallbacks(mErrorResetRunnable);
        mPendingVideoUrl = videoUrl;
        setState(STATE_PENDING);

        long durationSec = Math.max(1, getPlayer().getDurationMs() / 1000);
        mTranslationDisposable = votClient().observeTranslation(videoUrl, durationSec)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::onVotProgress,
                        this::onVotError
                );
    }

    private void ensureOriginalAudioForYandex() {
        if (getPlayer() == null) {
            return;
        }
        TrackInfo current = resolveAudioInfo();
        if (VotAudioTrackHelper.isOriginalTrack(current) && !VotAudioTrackHelper.isYoutubeAutoDub(current)) {
            return;
        }
        FormatItem original = VotAudioTrackHelper.findBestOriginalForYandex(getAudioFormats());
        if (original == null) {
            return;
        }
        FormatItem active = getPlayer().getAudioFormat();
        if (!VotAudioTrackHelper.isSameFormat(active, original)) {
            saveCurrentAudioFormatBeforeSwitch(original);
            getPlayer().setFormat(original);
        }
    }

    private void saveCurrentAudioFormatBeforeSwitch(FormatItem target) {
        if (getPlayer() == null || target == null || mSavedAudioFormat != null) {
            return;
        }
        FormatItem current = getPlayer().getAudioFormat();
        if (current != null && !VotAudioTrackHelper.isSameFormat(current, target)) {
            mSavedAudioFormat = current;
        }
    }

    private void restoreSavedAudioFormat() {
        if (mSavedAudioFormat != null && getPlayer() != null) {
            getPlayer().setFormat(mSavedAudioFormat);
            mSavedAudioFormat = null;
        }
    }

    private TrackInfo resolveAudioInfo() {
        if (getPlayer() == null) {
            return VotAudioTrackHelper.from(null);
        }
        return VotAudioTrackHelper.resolveCurrent(getPlayer().getAudioFormat(), getAudioFormats());
    }

    private List<FormatItem> getAudioFormats() {
        return getPlayer() != null ? getPlayer().getAudioFormats() : null;
    }

    private void onVotProgress(VotProgress progress) {
        if (getPlayer() == null || getPlayer().getVideo() == null) {
            return;
        }
        String currentUrl = "https://www.youtube.com/watch?v=" + getPlayer().getVideo().videoId;
        if (mPendingVideoUrl != null && !mPendingVideoUrl.equals(currentUrl)) {
            return;
        }
        if (!mArmed) {
            return;
        }

        Log.d(TAG, "VOT progress: type=%s stage=%s status=%s etaSec=%s complete=%s hasAudio=%s",
                progress.type, progress.stage, progress.status, progress.remainingTimeSec,
                progress.complete, progress.audioUrl != null && !progress.audioUrl.isEmpty());

        switch (progress.type) {
            case VotProgress.TYPE_WAITING:
                mPendingEtaSec = progress.remainingTimeSec;
                mPendingStage = progress.stage;
                restartEtaCountdown();
                if (mTranslationPlayer != null && mTranslationPlayer.isReady()) {
                    setState(STATE_ACTIVE);
                } else {
                    setState(STATE_PENDING);
                }
                if (!mPendingToastShown) {
                    mPendingToastShown = true;
                    if (progress.remainingTimeSec > 0) {
                        int min = Math.max(1, (progress.remainingTimeSec + 59) / 60);
                        MessageHelpers.showMessage(getContext(),
                                getContext().getString(R.string.vot_pending_eta, min));
                    } else {
                        MessageHelpers.showMessage(getContext(), R.string.vot_pending_long);
                    }
                }
                break;
            case VotProgress.TYPE_READY:
                if (progress.audioUrl != null) {
                    mPendingEtaSec = progress.remainingTimeSec;
                    mPendingStage = progress.stage;
                    mPartialTranslation = !progress.complete;
                    restartEtaCountdown();
                    if (!progress.audioUrl.equals(mTranslationAudioUrl) || mTranslationPlayer == null) {
                        if (mTranslationPlayer != null) {
                            restoreMainVolume();
                        }
                        mTranslationAudioUrl = progress.audioUrl;
                        playTranslation(progress.audioUrl);
                    } else if (mTranslationPlayer.isReady()) {
                        if (progress.complete) {
                            mPendingStage = VotProgress.STAGE_PLAYING;
                        }
                        setState(STATE_ACTIVE);
                    }
                }
                break;
            case VotProgress.TYPE_FAILED:
                handleTranslationError(progress.message);
                break;
        }
    }

    private void onVotError(Throwable e) {
        String msg = e.getMessage();
        if (e instanceof IOException) {
            handleTranslationError(msg);
        } else {
            handleTranslationError(msg != null ? msg : getContext().getString(R.string.vot_error_generic));
        }
    }

    private void playTranslation(String audioUrl) {
        if (getPlayer() == null) {
            return;
        }
        releaseTranslationPlayer();
        mTranslationPlayer = new TranslationAudioPlayer(getContext());
        mTranslationPlayer.setOnReadyListener(() -> {
            if (getPlayer() != null && mTranslationPlayer != null) {
                Log.d(TAG, "VOT translated audio ready: partial=%s", mPartialTranslation);
                mTranslationPlayer.seekTo(getPlayer().getPositionMs());
                if (!getPlayer().isPlaying()) {
                    mTranslationPlayer.pause();
                }
                duckMainAudio();
                mPendingStage = mPartialTranslation
                        ? VotProgress.STAGE_PART_CONTENT : VotProgress.STAGE_PLAYING;
                setState(STATE_ACTIVE);
                MessageHelpers.showMessage(getContext(), R.string.vot_enabled);
                Utils.removeCallbacks(mSyncRunnable);
                Utils.postDelayed(mSyncRunnable, SYNC_INTERVAL_MS);
            }
        });
        mTranslationPlayer.setOnErrorListener(() -> handleTranslationError("Translation audio playback failed"));
        float speed = getPlayer().getSpeed();
        mTranslationPlayer.play(
                audioUrl,
                getPlayer().getPositionMs(),
                votData().getTranslationVolumeMultiplier(),
                speed > 0 ? speed : 1f
        );
        setState(STATE_PENDING);
    }

    private void syncTranslationPositionIfNeeded() {
        if (mTranslationPlayer == null || getPlayer() == null || !mTranslationPlayer.isReady()) {
            return;
        }
        long mainPos = getPlayer().getPositionMs();
        long transPos = mTranslationPlayer.getPositionMs();
        if (Math.abs(mainPos - transPos) > SYNC_THRESHOLD_MS) {
            mTranslationPlayer.seekTo(mainPos);
        }
    }

    private void duckMainAudio() {
        if (getPlayer() == null || mMainAudioDucked) {
            return;
        }
        mSavedMainVolume = getPlayer().getVolume();
        getPlayer().setVolume(votData().getOriginalVolumeMultiplier());
        mMainAudioDucked = true;
    }

    private void restoreMainVolume() {
        if (getPlayer() != null && mMainAudioDucked) {
            getPlayer().setVolume(mSavedMainVolume);
        }
        mMainAudioDucked = false;
    }

    private void cancelTranslationJob() {
        if (mTranslationDisposable != null && !mTranslationDisposable.isDisposed()) {
            mTranslationDisposable.dispose();
        }
        mTranslationDisposable = null;
    }

    private void releaseTranslationPlayer() {
        Utils.removeCallbacks(mSyncRunnable);
        if (mTranslationPlayer != null) {
            mTranslationPlayer.release();
            mTranslationPlayer = null;
        }
    }

    private void disarm() {
        mUserArmed = false;
        mArmed = false;
        Utils.removeCallbacks(mAutoTranslateRetryRunnable, mErrorResetRunnable, mEtaTickRunnable);
        cancelTranslationJob();
        releaseTranslationPlayer();
        restoreMainVolume();
        restoreSavedAudioFormat();
        setState(STATE_OFF);
        mPendingVideoUrl = null;
        mPartialTranslation = false;
        mTranslationAudioUrl = null;
        mPendingEtaSec = 0;
    }

    private void disarmQuiet() {
        mArmed = false;
        cancelTranslationJob();
        releaseTranslationPlayer();
        restoreMainVolume();
        setState(STATE_OFF);
        mPendingVideoUrl = null;
        mPartialTranslation = false;
        mTranslationAudioUrl = null;
        mPendingEtaSec = 0;
        Utils.removeCallbacks(mEtaTickRunnable);
    }

    private void disarmWithMessage(int msgResId) {
        mUserArmed = false;
        mArmed = false;
        Utils.removeCallbacks(mAutoTranslateRetryRunnable, mErrorResetRunnable, mEtaTickRunnable);
        cancelTranslationJob();
        releaseTranslationPlayer();
        restoreMainVolume();
        restoreSavedAudioFormat();
        setState(STATE_OFF);
        mPendingVideoUrl = null;
        mPartialTranslation = false;
        mTranslationAudioUrl = null;
        mPendingEtaSec = 0;
        MessageHelpers.showMessage(getContext(), msgResId);
    }

    private void handleTranslationError(String message) {
        String safeMessage = sanitizeDiagnostic(message);
        Log.e(TAG, "Translation error: %s", safeMessage);
        if (message != null && message.contains("auth required")) {
            MessageHelpers.showMessage(getContext(), R.string.vot_error_auth_required);
        } else {
            MessageHelpers.showMessage(getContext(), R.string.vot_error_generic);
        }
        mUserArmed = false;
        mArmed = false;
        cancelTranslationJob();
        releaseTranslationPlayer();
        restoreMainVolume();
        restoreSavedAudioFormat();
        mPendingVideoUrl = null;
        mPartialTranslation = false;
        mTranslationAudioUrl = null;
        mPendingEtaSec = 0;
        Utils.removeCallbacks(mEtaTickRunnable);
        setState(STATE_ERROR);
        Utils.postDelayed(mErrorResetRunnable, ERROR_DISPLAY_MS);
    }

    private String sanitizeDiagnostic(String message) {
        if (message == null || message.isEmpty()) {
            return "unknown";
        }
        String safe = message.replaceAll("https?://\\S+", "<url>")
                .replaceAll("[A-Za-z0-9._~-]{24,}", "<redacted>")
                .replaceAll("[\\r\\n\\t]+", " ");
        return safe.length() > 160 ? safe.substring(0, 160) : safe;
    }

    private void restartEtaCountdown() {
        Utils.removeCallbacks(mEtaTickRunnable);
        if (mPendingEtaSec > 0) {
            Utils.postDelayed(mEtaTickRunnable, 1_000);
        }
    }

    private void setState(int state) {
        mState = state;
        int btnIndex;
        switch (state) {
            case STATE_PENDING:
                btnIndex = BTN_PENDING;
                break;
            case STATE_ACTIVE:
                btnIndex = BTN_ON;
                break;
            case STATE_ERROR:
                btnIndex = BTN_ERROR;
                break;
            default:
                btnIndex = BTN_OFF;
                break;
        }
        Log.d(TAG, "VOT UI state: state=%s button=%s stage=%s etaSec=%s",
                state, btnIndex, mPendingStage, mPendingEtaSec);
        updateVoiceButton(btnIndex);
    }

    private void updateVoiceButton(int index) {
        if (getPlayer() == null) {
            return;
        }
        getPlayer().setButtonState(ACTION_VOICE_TRANSLATE, index);
        int stage = index == BTN_ON && mPartialTranslation
                ? VotProgress.STAGE_PART_CONTENT : mPendingStage;
        getPlayer().updateVoiceTranslateStatus(stage, mPendingEtaSec);
    }
}
