package com.gathin.tvhome;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Portable JSON archive for on-TV preferences. Theme state stays local; runtime caches are rebuilt. */
final class LauncherSettingsBackup {
    private static final String FORMAT = "com.gathin.tvhome.settings";
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 512;

    private LauncherSettingsBackup() { }

    static JSONObject create(SharedPreferences preferences) throws JSONException {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (isLocalOnly(key) || key.startsWith("tile_logo_")) continue;
            Object value = entry.getValue();
            if (value instanceof Set) value = new JSONArray(new ArrayList<>((Set<?>) value));
            if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof String || value instanceof JSONArray) {
                values.put(key, value);
            }
        }
        return new JSONObject().put("format", FORMAT).put("version", VERSION).put("preferences", values);
    }

    static JSONObject parse(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        if (!FORMAT.equals(root.optString("format")) || root.optInt("version", -1) != VERSION) {
            throw new JSONException("This settings file is not supported.");
        }
        JSONObject values = root.optJSONObject("preferences");
        if (values == null || values.length() > MAX_ENTRIES) {
            throw new JSONException("The settings file is incomplete.");
        }
        for (Iterator<String> keys = values.keys(); keys.hasNext();) {
            String key = keys.next();
            if (key.isEmpty() || key.length() > 200 || isLocalOnly(key) || key.startsWith("tile_logo_")) {
                throw new JSONException("The settings file contains an invalid setting.");
            }
            Object value = values.get(key);
            if (!(value instanceof String || value instanceof Boolean || value instanceof Integer
                    || value instanceof Long || value instanceof Double || value instanceof JSONArray)) {
                throw new JSONException("The settings file contains an unsupported value.");
            }
            if (value instanceof JSONArray) {
                JSONArray items = (JSONArray) value;
                for (int i = 0; i < items.length(); i++) {
                    if (!(items.get(i) instanceof String)) {
                        throw new JSONException("The settings file contains an invalid list.");
                    }
                }
            }
        }
        return values;
    }

    static boolean restore(SharedPreferences preferences, JSONObject values) {
        long revision = preferences.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1;
        String localThemeSignature = preferences.getString(Config.THEME_SIGNATURE_KEY, null);
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (isDeviceState(entry.getKey()) || entry.getKey().startsWith("tile_logo_")) {
                put(editor, entry.getKey(), entry.getValue());
            }
        }
        if (localThemeSignature != null) editor.putString(Config.THEME_SIGNATURE_KEY, localThemeSignature);
        for (Iterator<String> keys = values.keys(); keys.hasNext();) {
            String key = keys.next();
            try {
                Object value = values.get(key);
                putJsonValue(editor, key, value);
            } catch (JSONException e) {
                return false;
            }
        }
        editor.putLong(Config.SETTINGS_REVISION_KEY, revision);
        return editor.commit();
    }

    private static boolean isLocalOnly(String key) {
        return Config.SETTINGS_REVISION_KEY.equals(key)
                || Config.THEME_SIGNATURE_KEY.equals(key)
                || BundledThemes.CURRENT_THEME_KEY.equals(key)
                || "weather_cache".equals(key)
                || "weather_forecast_cache_v2".equals(key)
                || "weather_updated".equals(key);
    }

    private static boolean isDeviceState(String key) {
        return Config.SETTINGS_REVISION_KEY.equals(key)
                || Config.THEME_SIGNATURE_KEY.equals(key)
                || BundledThemes.CURRENT_THEME_KEY.equals(key);
    }

    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Set) {
            Set<?> source = (Set<?>) value;
            Set<String> items = new HashSet<>();
            for (Object item : source) if (item instanceof String) items.add((String) item);
            editor.putStringSet(key, items);
        }
    }

    private static void putJsonValue(SharedPreferences.Editor editor, String key, Object value)
            throws JSONException {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Double) editor.putFloat(key, ((Double) value).floatValue());
        else if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            Set<String> items = new HashSet<>();
            for (int i = 0; i < array.length(); i++) items.add(array.getString(i));
            editor.putStringSet(key, items);
        }
    }
}
