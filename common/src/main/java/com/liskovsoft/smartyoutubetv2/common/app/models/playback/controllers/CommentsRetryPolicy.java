package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

/** Fixed, bounded recovery for transient comment fetch failures. */
public final class CommentsRetryPolicy {
    private CommentsRetryPolicy() {}

    public static boolean shouldRetry(int failureNumber) {
        return failureNumber == 1 || failureNumber == 2;
    }

    public static long retryDelaySeconds(int failureNumber) {
        if (failureNumber == 1) return 1;
        if (failureNumber == 2) return 3;
        throw new IllegalArgumentException("No retry for failure #" + failureNumber);
    }
}
