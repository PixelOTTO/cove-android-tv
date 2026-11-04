package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Installs the official Scenes theme from the APK when no wallpaper is already present. */
final class BundledScenes {
    private static final String ASSET_ROOT = "themes/scenes";
    private static final String WALLPAPER_ASSETS = ASSET_ROOT + "/wallpapers";
    private static final String DEFAULT_WALLPAPER = "06-above-the-clouds.jpg";
    private static final String INSTALLED_MARKER = ".bundled-scenes-v1";

    private BundledScenes() {}

    /** Copies only into an empty wallpaper setup, preserving an existing theme and its settings. */
    static boolean installIfMissing(Context context, File directory) {
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) return false;

        File marker = new File(directory, INSTALLED_MARKER);
        File single = new File(directory, "wallpaper.jpg");
        File collection = new File(directory, "wallpapers");
        // An explicit theme config belongs to the owner; don't seed Scenes over it.
        if (!marker.isFile() && new File(directory, "config.json").isFile()) return false;
        boolean enabled = wallpaperEnabled(context, new File(directory, "config.json"));
        if (!enabled) return marker.isFile();

        boolean hasWallpaper = single.isFile() && single.length() > 0;
        boolean hasCollection = !WallpaperRotation.images(collection).isEmpty();
        if (!marker.isFile() && (hasWallpaper || hasCollection)) return false;

        // Mark before copying so a later launch can fill any assets if the first copy was interrupted.
        if (!marker.isFile()) {
            try {
                if (!marker.createNewFile()) return false;
            } catch (IOException e) {
                return false;
            }
        }

        if (!collection.isDirectory() && !collection.mkdirs()) return false;
        copyIfMissing(context, WALLPAPER_ASSETS + "/" + DEFAULT_WALLPAPER, single);
        try {
            AssetManager assets = context.getAssets();
            for (String name : assets.list(WALLPAPER_ASSETS)) {
                if (isImage(name)) copyIfMissing(context, WALLPAPER_ASSETS + "/" + name, new File(collection, name));
            }
            copyIfMissing(context, WALLPAPER_ASSETS + "/captions.txt", new File(collection, "captions.txt"));
        } catch (IOException ignored) {
            // Keep Home usable; a later launch retries any missing bundled files.
        }
        return marker.isFile();
    }

    static boolean wallpaperEnabled(Context context) {
        return config(context).optBoolean("wallpaper", true);
    }

    static int wallpaperRotationMinutes(Context context) {
        return Math.max(0, Math.min(1440, config(context).optInt("wallpaper_rotation_minutes", 0)));
    }

    private static boolean wallpaperEnabled(Context context, File externalConfig) {
        if (!externalConfig.isFile()) return wallpaperEnabled(context);
        try (InputStream input = new java.io.FileInputStream(externalConfig)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            copy(input, bytes);
            Object value = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).opt("wallpaper");
            return !(value instanceof Boolean) || (Boolean) value;
        } catch (IOException | JSONException e) {
            return true;
        }
    }

    private static JSONObject config(Context context) {
        try (InputStream input = context.getAssets().open(ASSET_ROOT + "/config.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            copy(input, bytes);
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException | JSONException e) {
            return new JSONObject();
        }
    }

    private static void copyIfMissing(Context context, String asset, File destination) {
        if (destination.isFile() && destination.length() > 0) return;
        File parent = destination.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return;
        File temporary = new File(parent, destination.getName() + ".bundled.tmp");
        try (InputStream input = context.getAssets().open(asset);
             OutputStream output = new FileOutputStream(temporary, false)) {
            copy(input, output);
        } catch (IOException e) {
            temporary.delete();
            return;
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            return;
        }
        if (!temporary.renameTo(destination)) temporary.delete();
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }
}
