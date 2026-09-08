package com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs;

public final class AppUpdatePolicy {
    private static final String PUBLIC_VOT_PACKAGE = "org.smarttube.vot";

    private AppUpdatePolicy() {
    }

    public static boolean isUpdateEnabled(String packageName) {
        return !PUBLIC_VOT_PACKAGE.equals(packageName);
    }
}