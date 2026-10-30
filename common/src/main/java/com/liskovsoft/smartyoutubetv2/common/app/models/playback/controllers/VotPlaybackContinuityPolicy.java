package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

/** Decisions for reloading the current video without discarding a Yandex
 * translation already playing or being prepared. No Android dependencies. */
public final class VotPlaybackContinuityPolicy {
    private VotPlaybackContinuityPolicy() {
    }

    public static boolean shouldKeepTranslation(
            String currentId, String incomingId, boolean armed,
            boolean inProgressOrActive, boolean hasAudioOrRequest) {
        return currentId != null && !currentId.isEmpty()
                && currentId.equals(incomingId) && armed
                && inProgressOrActive && hasAudioOrRequest;
    }
}
