package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import org.junit.Test;
import static org.junit.Assert.*;

public class VotPlaybackContinuityPolicyTest {
    @Test public void preservesSameVideoWhileTranslationActive() {
        assertTrue(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "video01", "video01", true, true, true));
    }
    @Test public void preservesSameVideoWhileTranslationIsPending() {
        assertTrue(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "video01", "video01", true, true, true));
    }
    @Test public void neverPreservesTranslationForAnotherVideo() {
        assertFalse(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "video01", "video02", true, true, true));
    }
    @Test public void doesNotKeepDisarmedOrFailedTranslation() {
        assertFalse(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "video01", "video01", false, true, true));
        assertFalse(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "video01", "video01", true, false, true));
        assertFalse(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "video01", "video01", true, true, false));
    }
    @Test public void missingVideoIdentityNeverReusesAudio() {
        assertFalse(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                null, "video01", true, true, true));
        assertFalse(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "video01", null, true, true, true));
        assertFalse(VotPlaybackContinuityPolicy.shouldKeepTranslation(
                "", "", true, true, true));
    }
}
