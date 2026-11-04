package com.gathin.tvhome;

import android.content.Context;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

/** Keeps the home clock and both screensaver clocks on the same user-selected format. */
final class ClockFormat {
    static final String KEY = "clock_format";
    static final int FOLLOW_TV = 0;
    static final int TWELVE_HOUR = 1;
    static final int TWENTY_FOUR_HOUR = 2;

    private ClockFormat() {}

    static int selected(Context context) {
        try {
            int value = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY, FOLLOW_TV);
            return value >= FOLLOW_TV && value <= TWENTY_FOUR_HOUR ? value : FOLLOW_TV;
        } catch (ClassCastException ignored) {
            return FOLLOW_TV;
        }
    }

    static String label(int format) {
        switch (format) {
            case TWELVE_HOUR: return "12-hour";
            case TWENTY_FOUR_HOUR: return "24-hour";
            default: return "Follow TV setting";
        }
    }

    static void applyHome(Activity activity) {
        TextClock clock = activity.findViewById(R.id.clock);
        TextClock ampm = activity.findViewById(R.id.ampm);
        apply(activity, clock, ampm);
    }

    static void applyScreensaver(Context context, View root) {
        TextClock clock = null;
        TextClock ampm = null;
        View content = root.findViewById(R.id.dreamContent);
        if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 0) {
            View timeRow = ((ViewGroup) content).getChildAt(0);
            if (timeRow instanceof ViewGroup && ((ViewGroup) timeRow).getChildCount() >= 2) {
                View first = ((ViewGroup) timeRow).getChildAt(0);
                View second = ((ViewGroup) timeRow).getChildAt(1);
                if (first instanceof TextClock && second instanceof TextClock) {
                    if (content.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                        ampm = (TextClock) first;
                        clock = (TextClock) second;
                    } else {
                        clock = (TextClock) first;
                        ampm = (TextClock) second;
                    }
                }
            }
        }
        apply(context, clock, ampm);
    }

    private static void apply(Context context, TextClock clock, TextClock ampm) {
        if (clock == null) return;
        int format = selected(context);
        if (format == TWENTY_FOUR_HOUR) {
            clock.setFormat12Hour("HH:mm");
            clock.setFormat24Hour("HH:mm");
            if (ampm != null) ampm.setVisibility(View.GONE);
        } else if (format == TWELVE_HOUR) {
            clock.setFormat12Hour("h:mm");
            clock.setFormat24Hour("h:mm");
            if (ampm != null) {
                ampm.setFormat12Hour("a");
                ampm.setFormat24Hour("a");
                ampm.setVisibility(View.VISIBLE);
            }
        } else {
            // Match the layout's existing formats so this choice follows Android's TV clock preference.
            clock.setFormat12Hour("h:mm");
            clock.setFormat24Hour("HH:mm");
            if (ampm != null) {
                ampm.setFormat12Hour("a");
                ampm.setFormat24Hour("");
                ampm.setVisibility(View.VISIBLE);
            }
        }
    }
}
