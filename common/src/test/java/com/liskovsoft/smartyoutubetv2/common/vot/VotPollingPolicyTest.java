package com.liskovsoft.smartyoutubetv2.common.vot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VotPollingPolicyTest {
    @Test
    public void firstPollUsesServerEtaUpToThreeMinutes() {
        assertEquals(1, VotPollingPolicy.getDelaySec(0, 1));
        assertEquals(180, VotPollingPolicy.getDelaySec(0, 180));
    }

    @Test
    public void firstPollCapsLongServerEtaAtTwoMinutes() {
        assertEquals(120, VotPollingPolicy.getDelaySec(0, 181));
        assertEquals(120, VotPollingPolicy.getDelaySec(0, 900));
    }

    @Test
    public void retriesUseThirtySecondCadence() {
        assertEquals(30, VotPollingPolicy.getDelaySec(0, 0));
        assertEquals(30, VotPollingPolicy.getDelaySec(1, 5));
        assertEquals(30, VotPollingPolicy.getDelaySec(20, 900));
    }

    @Test
    public void serverFailureRetryIsHonoredOnlyOnce() {
        VotTranslationResponse response = new VotTranslationResponse();
        response.parseValid = true;
        response.status = VotTranslationResponse.STATUS_FAILED;
        response.shouldRetry = 1;

        org.junit.Assert.assertTrue(VotPollingPolicy.shouldRetryServerFailure(response, false));
        org.junit.Assert.assertFalse(VotPollingPolicy.shouldRetryServerFailure(response, true));
    }

    @Test
    public void livelyPollHttp400FallsBackOnlyOnce() {
        org.junit.Assert.assertTrue(VotPollingPolicy.shouldFallbackFromLivelyPollHttpError(
                400, true, true, false));
        org.junit.Assert.assertFalse(VotPollingPolicy.shouldFallbackFromLivelyPollHttpError(
                400, false, true, false));
        org.junit.Assert.assertFalse(VotPollingPolicy.shouldFallbackFromLivelyPollHttpError(
                400, true, false, false));
        org.junit.Assert.assertFalse(VotPollingPolicy.shouldFallbackFromLivelyPollHttpError(
                400, true, true, true));
        org.junit.Assert.assertFalse(VotPollingPolicy.shouldFallbackFromLivelyPollHttpError(
                500, true, true, false));
    }
}