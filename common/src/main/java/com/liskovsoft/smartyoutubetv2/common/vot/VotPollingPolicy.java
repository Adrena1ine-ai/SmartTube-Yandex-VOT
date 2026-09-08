package com.liskovsoft.smartyoutubetv2.common.vot;

/** Poll cadence based only on the latest Yandex ETA and community client behavior. */
public final class VotPollingPolicy {
    static final int MAX_INITIAL_WAIT_SEC = 180;
    static final int LONG_WAIT_SEC = 120;
    static final int RETRY_INTERVAL_SEC = 30;

    private VotPollingPolicy() {
    }

    public static int getDelaySec(int pollAttempt, int remainingTimeSec) {
        if (pollAttempt > 0 || remainingTimeSec <= 0) {
            return RETRY_INTERVAL_SEC;
        }
        if (remainingTimeSec <= MAX_INITIAL_WAIT_SEC) {
            return remainingTimeSec;
        }
        return LONG_WAIT_SEC;
    }

    public static boolean shouldRetryServerFailure(VotTranslationResponse response, boolean retryUsed) {
        return !retryUsed && response != null && response.parseValid &&
                response.status == VotTranslationResponse.STATUS_FAILED && response.shouldRetry > 0;
    }

    public static boolean shouldFallbackFromLivelyPollHttpError(int responseCode, boolean subsequent,
                                                                 boolean useLively, boolean retryUsed) {
        return responseCode == 400 && subsequent && useLively && !retryUsed;
    }
}