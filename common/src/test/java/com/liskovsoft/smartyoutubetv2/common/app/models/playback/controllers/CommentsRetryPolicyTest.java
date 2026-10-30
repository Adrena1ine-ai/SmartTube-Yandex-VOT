package com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers;

import org.junit.Test;
import static org.junit.Assert.*;

public class CommentsRetryPolicyTest {
    @Test public void firstAndSecondFailuresRetryWithBackoff() {
        assertTrue(CommentsRetryPolicy.shouldRetry(1));
        assertEquals(1L, CommentsRetryPolicy.retryDelaySeconds(1));
        assertTrue(CommentsRetryPolicy.shouldRetry(2));
        assertEquals(3L, CommentsRetryPolicy.retryDelaySeconds(2));
    }

    @Test public void thirdAndLaterFailuresAreTerminal() {
        assertFalse(CommentsRetryPolicy.shouldRetry(0));
        assertFalse(CommentsRetryPolicy.shouldRetry(3));
        assertFalse(CommentsRetryPolicy.shouldRetry(99));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidRetryDelayFailsFast() {
        CommentsRetryPolicy.retryDelaySeconds(3);
    }
}
