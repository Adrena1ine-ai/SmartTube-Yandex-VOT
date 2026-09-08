package com.liskovsoft.smartyoutubetv2.common.vot;

public class VotTranslationResponse {
    public static final int STATUS_FAILED = 0;
    public static final int STATUS_FINISHED = 1;
    public static final int STATUS_WAITING = 2;
    public static final int STATUS_LONG_WAITING = 3;
    public static final int STATUS_PART_CONTENT = 5;
    public static final int STATUS_AUDIO_REQUESTED = 6;
    public static final int STATUS_SESSION_REQUIRED = 7;

    public String url;
    public int status;
    public int remainingTimeSec;
    public String translationId;
    public String message;
    public boolean parseValid;
    public boolean statusPresent;
    public boolean isLivelyVoice;
    public boolean allowToTranslateVideo;
    public int shouldRetry;

    public boolean isReady() {
        return status == STATUS_FINISHED || status == STATUS_PART_CONTENT;
    }

    public boolean isComplete() {
        return status == STATUS_FINISHED;
    }

    public boolean isWaiting() {
        return status == STATUS_WAITING || status == STATUS_LONG_WAITING;
    }

    public String getStatusName() {
        switch (status) {
            case STATUS_FAILED:
                return "FAILED";
            case STATUS_FINISHED:
                return "FINISHED";
            case STATUS_WAITING:
                return "WAITING";
            case STATUS_LONG_WAITING:
                return "LONG_WAITING";
            case STATUS_PART_CONTENT:
                return "PART_CONTENT";
            case STATUS_AUDIO_REQUESTED:
                return "AUDIO_REQUESTED";
            case STATUS_SESSION_REQUIRED:
                return "SESSION_REQUIRED";
            default:
                return "UNKNOWN_" + status;
        }
    }
}
