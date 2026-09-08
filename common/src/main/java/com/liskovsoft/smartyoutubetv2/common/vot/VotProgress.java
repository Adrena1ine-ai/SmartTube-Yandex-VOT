package com.liskovsoft.smartyoutubetv2.common.vot;

public final class VotProgress {
    public static final int TYPE_WAITING = 0;
    public static final int TYPE_READY = 1;
    public static final int TYPE_FAILED = 2;

    public static final int STAGE_REQUESTING = 0;
    public static final int STAGE_PROCESSING = 1;
    public static final int STAGE_LONG_WAITING = 2;
    public static final int STAGE_AUDIO_REQUESTED = 3;
    public static final int STAGE_PART_CONTENT = 4;
    public static final int STAGE_LOADING_AUDIO = 5;
    public static final int STAGE_PLAYING = 6;
    public static final int STAGE_RETRYING = 7;

    public final int type;
    public final String audioUrl;
    public final int remainingTimeSec;
    public final int status;
    public final int stage;
    public final boolean complete;
    public final String message;

    private VotProgress(int type, String audioUrl, int remainingTimeSec, int status, int stage,
                        boolean complete, String message) {
        this.type = type;
        this.audioUrl = audioUrl;
        this.remainingTimeSec = remainingTimeSec;
        this.status = status;
        this.stage = stage;
        this.complete = complete;
        this.message = message;
    }

    public static VotProgress waiting(int remainingTimeSec, int status, int stage) {
        return new VotProgress(TYPE_WAITING, null, remainingTimeSec, status, stage, false, null);
    }

    public static VotProgress ready(String audioUrl, int remainingTimeSec, boolean complete) {
        int status = complete ? VotTranslationResponse.STATUS_FINISHED : VotTranslationResponse.STATUS_PART_CONTENT;
        int stage = complete ? STAGE_LOADING_AUDIO : STAGE_PART_CONTENT;
        return new VotProgress(TYPE_READY, audioUrl, remainingTimeSec, status, stage, complete, null);
    }

    public static VotProgress failed(String message) {
        return new VotProgress(TYPE_FAILED, null, 0, VotTranslationResponse.STATUS_FAILED,
                STAGE_REQUESTING, false, message);
    }
}
