package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppUpdatePolicyTest {
    @Test
    public void publicVotUpdaterIsDisabled() {
        assertFalse(AppUpdatePolicy.isUpdateEnabled("org.smarttube.vot"));
    }

    @Test
    public void existingUpdaterBehaviorIsPreserved() {
        assertTrue(AppUpdatePolicy.isUpdateEnabled("org.smarttube.stable"));
        assertTrue(AppUpdatePolicy.isUpdateEnabled("org.smarttube.beta"));
        assertTrue(AppUpdatePolicy.isUpdateEnabled("app.smarttube.fdroid"));
    }
}