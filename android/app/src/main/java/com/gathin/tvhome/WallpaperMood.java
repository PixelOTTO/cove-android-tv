package com.gathin.tvhome;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Groups wallpaper collections by time of day, using the configured sunrise and sunset when available. */
final class WallpaperMood {
    static final String MORNING = "morning", AFTERNOON = "afternoon", EVENING = "evening", NIGHT = "night";
    static final String[] ALL = {MORNING, AFTERNOON, EVENING, NIGHT};
    static final int DEFAULT_SUNRISE = 7 * 60, DEFAULT_SUNSET = 18 * 60 + 30;
    private static final int NOON = 12 * 60, EVENING_START = 17 * 60, DUSK_MINUTES = 30;

    private WallpaperMood() { }

    static String timeOfDay(int minute, int sunrise, int sunset) {
        if (minute >= sunrise && minute < NOON) return MORNING;
        if (minute >= NOON && minute < EVENING_START) return AFTERNOON;
        if (minute >= EVENING_START && minute < sunset + DUSK_MINUTES) return EVENING;
        return NIGHT;
    }

    static int[] sun(LocalDate day, String latitude, String longitude, ZoneId zone) {
        int[] times = null;
        try {
            if (!latitude.trim().isEmpty() && !longitude.trim().isEmpty()) {
                times = Sun.riseAndSet(day, Double.parseDouble(latitude.trim()), Double.parseDouble(longitude.trim()), zone);
            }
        } catch (NumberFormatException ignored) { }
        return times != null ? times : new int[]{DEFAULT_SUNRISE, DEFAULT_SUNSET};
    }

    static List<File> select(List<File> all, Map<String, Set<String>> groups, String mood) {
        Set<String> names = groups.get(mood);
        // Older themes grouped noon through dusk under "evening"; retain that look in the afternoon.
        if (names == null && AFTERNOON.equals(mood)) names = groups.get(EVENING);
        if (names == null) return all;
        List<File> group = new ArrayList<>();
        for (File file : all) if (names.contains(file.getName())) group.add(file);
        return group.isEmpty() ? all : group;
    }
}