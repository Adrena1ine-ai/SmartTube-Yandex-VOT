package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.leanback.widget.PlaybackControlsRow.MultiAction;

import com.liskovsoft.smartyoutubetv2.tv.R;
/**
 * OFF = idle, PENDING = waiting for Yandex, ON = translation playing, ERROR = failed.
 */
public class VoiceTranslateAction extends MultiAction {
    public static final int INDEX_OFF = 0;
    public static final int INDEX_PENDING = 1;
    public static final int INDEX_ON = 2;
    public static final int INDEX_ERROR = 3;

    private final String[] mLabels;
    private String mTimerLabel;
    private int mRemainingTimeSec;

    public VoiceTranslateAction(Context context) {
        super(R.id.action_voice_translate);
        Drawable[] drawables = new Drawable[4];
        drawables[INDEX_OFF] = ActionHelpers.getBitmapDrawable(
                context, R.drawable.action_voice_translate_off);
        drawables[INDEX_PENDING] = ActionHelpers.getBitmapDrawable(
                context, R.drawable.action_voice_translate_pending);
        drawables[INDEX_ON] = ActionHelpers.getBitmapDrawable(
                context, R.drawable.action_voice_translate_on);
        drawables[INDEX_ERROR] = ActionHelpers.getBitmapDrawable(
                context, R.drawable.action_voice_translate_error);
        setDrawables(drawables);

        mLabels = new String[4];
        mLabels[INDEX_OFF] = context.getString(
                com.liskovsoft.smartyoutubetv2.common.R.string.action_voice_translate_off);
        mLabels[INDEX_PENDING] = context.getString(
                com.liskovsoft.smartyoutubetv2.common.R.string.action_voice_translate_pending);
        mLabels[INDEX_ON] = context.getString(
                com.liskovsoft.smartyoutubetv2.common.R.string.action_voice_translate_on);
        mLabels[INDEX_ERROR] = context.getString(
                com.liskovsoft.smartyoutubetv2.common.R.string.action_voice_translate_error);
        setLabels(mLabels);
        setIndex(INDEX_OFF);
    }

    public void updateStatusLabel(int stage, int remainingTimeSec) {
        mRemainingTimeSec = Math.max(0, remainingTimeSec);
        mTimerLabel = formatTimer(mRemainingTimeSec);
    }

    public void resetLabels() {
        mTimerLabel = null;
        mRemainingTimeSec = 0;
    }

    public boolean isStatusVisible() {
        return getIndex() == INDEX_PENDING ||
                (getIndex() == INDEX_ON && mRemainingTimeSec > 0);
    }

    public String getStatusLabel() {
        return mTimerLabel;
    }

    private String formatTimer(int remainingTimeSec) {
        int minutes = remainingTimeSec / 60;
        int seconds = remainingTimeSec % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds);
    }
}
