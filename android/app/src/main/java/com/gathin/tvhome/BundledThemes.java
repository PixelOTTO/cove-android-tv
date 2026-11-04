package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Applies one of the repository themes bundled with this launcher build. */
final class BundledThemes {
    static final String CURRENT_THEME_KEY = "bundled_theme";
    private static final String[] NAMES = {
            "aurora", "glass", "ruska", "scenes", "sweden"
    };
    private static final Set<String> CLEANYOURTV_THEMES = new HashSet<>(Arrays.asList(
            "aurora", "glass", "ruska", "scenes", "sweden"
    ));

    static final class WallpaperOption {
        final String choiceName;
        final String label;
        final String assetPath;

        WallpaperOption(String choiceName, String label, String assetPath) {
            this.choiceName = choiceName;
            this.label = label;
            this.assetPath = assetPath;
        }

        boolean bundled() { return assetPath != null; }
    }

    private BundledThemes() {}

    static String[] names() {
        return NAMES.clone();
    }

    static String label(String name) {
        if (name == null) return "Scenes";
        String[] words = name.replace('-', ' ').split(" ");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (label.length() > 0) label.append(' ');
            label.append(word.substring(0, 1).toUpperCase(java.util.Locale.ROOT))
                    .append(word.substring(1));
        }
        return label.toString();
    }

    /** Source and artwork credits shown from the theme confirmation dialog. */
    static String credits(Context context, String name) {
        StringBuilder result = new StringBuilder();
        if (CLEANYOURTV_THEMES.contains(name)) {
            result.append("Theme settings adapted from CleanYourTV: https://github.com/Gathin23/cleanyourtv/tree/main/themes/")
                    .append(name)
                    .append("\n\nCleanYourTV theme settings are MIT-licensed. The MIT notice is included in the app at licenses/CLEANYOURTV-MIT.txt.\n\n");
        } else if ("happy-vibe".equals(name) || "red-test".equals(name)) {
            result.append("CoveTV theme created for this launcher.\n\n");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("themes/" + name + "/CREDITS.md"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        } catch (IOException ignored) {
            // Config-only themes have no separate artwork credits.
        }
        if (result.length() == 0) result.append("No separate artwork credits are required for this theme.");
        return result.toString().trim();
    }

    /** All bundled theme wallpapers, followed by any extra images already on this device. */
    static List<WallpaperOption> wallpaperOptions(Context context, File directory) {
        List<WallpaperOption> result = new ArrayList<>();
        Set<String> bundledFilenames = new HashSet<>();
        AssetManager assets = context.getAssets();
        for (String theme : NAMES) {
            String root = "themes/" + theme;
            try {
                if (hasAsset(assets, root + "/wallpaper.jpg")) {
                    addWallpaper(result, bundledFilenames, theme, "wallpaper.jpg", root + "/wallpaper.jpg", null);
                }
            } catch (IOException ignored) {
                // Themes without a single wallpaper can still contribute their collection below.
            }
            String folder = root + "/wallpapers";
            try {
                String[] entries = assets.list(folder);
                if (entries == null) continue;
                Arrays.sort(entries, String.CASE_INSENSITIVE_ORDER);
                Map<String, String> captions = readCaptions(assets, folder + "/captions.txt");
                for (String filename : entries) {
                    if (isImage(filename)) {
                        addWallpaper(result, bundledFilenames, theme, filename, folder + "/" + filename,
                                captions.get(filename));
                    }
                }
            } catch (IOException ignored) {
                // A theme with no collection still contributes its single wallpaper, if present.
            }
        }

        if (directory != null) {
            List<File> local = new ArrayList<>(WallpaperRotation.images(new File(directory, "wallpapers")));
            File single = new File(directory, Config.WALLPAPER_NAME);
            if (single.isFile() && single.length() > 0) local.add(single);
            for (File image : local) {
                String filename = image.getName();
                if (bundledFilenames.contains(filename) || filename.startsWith("bundle-")) continue;
                result.add(new WallpaperOption(filename, "On device — " + friendlyName(filename), null));
            }
        }
        return result;
    }

    /** Copies a bundled selection into the app's theme folder so Config can resolve it after restart. */
    static boolean selectWallpaper(Context context, File directory, WallpaperOption option) {
        if (option == null || directory == null) return false;
        if (!option.bundled()) return true;
        File folder = new File(directory, "bundled-wallpapers");
        if (!folder.isDirectory() && !folder.mkdirs()) return false;
        File destination = new File(folder, option.choiceName);
        if (destination.isFile() && destination.length() > 0) return true;
        File temporary = new File(folder, option.choiceName + ".bundled.tmp");
        try {
            copyAsset(context.getAssets(), option.assetPath, temporary);
            if (destination.exists() && !destination.delete()) return false;
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                return false;
            }
            return true;
        } catch (IOException e) {
            temporary.delete();
            return false;
        }
    }

    static boolean isBundledWallpaperChoice(String choice) {
        return choice != null && choice.startsWith("bundle-");
    }

    static File chosenWallpaper(File directory, String choice) {
        if (!isBundledWallpaperChoice(choice) || directory == null) return null;
        File file = new File(new File(directory, "bundled-wallpapers"), choice);
        return file.isFile() && file.length() > 0 ? file : null;
    }

    private static void addWallpaper(List<WallpaperOption> result, Set<String> filenames, String theme,
                                    String filename, String assetPath, String caption) {
        if (!"wallpaper.jpg".equals(filename) && !isImage(filename)) return;
        filenames.add(filename);
        String title = caption == null || caption.trim().isEmpty() ? friendlyName(filename) : caption.trim();
        String choice = "bundle-" + theme + "-" + filename;
        result.add(new WallpaperOption(choice, label(theme) + " — " + title, assetPath));
    }

    private static Map<String, String> readCaptions(AssetManager assets, String path) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(assets.open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        } catch (IOException ignored) {
            return new HashMap<>();
        }
        return WallpaperCaptions.parse(lines);
    }

    private static String friendlyName(String filename) {
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        return stem.replace('-', ' ').replace('_', ' ');
    }

    /** Stages the bundled files beside the active theme, then swaps them in with rollback on failure. */
    static boolean apply(Context context, File directory, String name) {
        if (!contains(name) || directory == null) return false;
        if (!directory.isDirectory() && !directory.mkdirs()) return false;

        File stage = new File(directory, ".theme-apply-" + UUID.randomUUID());
        File wallpapers = new File(stage, "wallpapers");
        String assetRoot = "themes/" + name;
        boolean hadConfig = false;
        boolean hadWallpaper = false;
        boolean hadCollection = false;
        boolean hadMarker = false;
        boolean hadRevision = false;
        boolean installedConfig = false;
        boolean installedWallpaper = false;
        boolean installedCollection = false;
        boolean installedMarker = false;
        boolean installedRevision = false;
        File config = new File(directory, Config.FILE_NAME);
        File wallpaper = new File(directory, Config.WALLPAPER_NAME);
        File collection = new File(directory, "wallpapers");
        File marker = new File(directory, ".bundled-scenes-v1");
        File revision = new File(directory, "theme_revision");
        try {
            if (!stage.mkdirs()) return false;
            AssetManager assets = context.getAssets();
            File stagedConfig = new File(stage, Config.FILE_NAME);
            copyAsset(assets, assetRoot + "/config.json", stagedConfig);
            JSONObject configJson;
            try (InputStream input = new FileInputStream(stagedConfig);
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                if (bytes.size() == 0) throw new IOException("empty theme config");
                configJson = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            } catch (JSONException e) {
                throw new IOException("invalid theme config", e);
            }
            writeText(stagedConfig, configJson.toString());

            if (hasAsset(assets, assetRoot + "/wallpaper.jpg")) {
                copyAsset(assets, assetRoot + "/wallpaper.jpg", new File(stage, Config.WALLPAPER_NAME));
            }
            String wallpaperAssetDir = assetRoot + "/wallpapers";
            String[] wallpaperAssets = assets.list(wallpaperAssetDir);
            if (wallpaperAssets != null) {
                for (String filename : wallpaperAssets) {
                    if (isImage(filename) || "captions.txt".equals(filename)) {
                        copyAsset(assets, wallpaperAssetDir + "/" + filename, new File(wallpapers, filename));
                    }
                }
            }
            if (wallpapers.isDirectory() && wallpapers.list().length == 0) wallpapers.delete();

            writeText(new File(stage, "theme_revision"), UUID.randomUUID().toString());
            if ("scenes".equals(name)) new File(stage, ".bundled-scenes-v1").createNewFile();

            hadConfig = moveAside(config, stage, "previous-config.json");
            hadWallpaper = moveAside(wallpaper, stage, "previous-wallpaper.jpg");
            hadCollection = moveAside(collection, stage, "previous-wallpapers");
            hadMarker = moveAside(marker, stage, "previous-marker");
            hadRevision = moveAside(revision, stage, "previous-revision");

            moveRequired(new File(stage, Config.FILE_NAME), config);
            installedConfig = true;
            File stagedWallpaper = new File(stage, Config.WALLPAPER_NAME);
            if (stagedWallpaper.isFile()) {
                moveRequired(stagedWallpaper, wallpaper);
                installedWallpaper = true;
            }
            if (wallpapers.isDirectory()) {
                moveRequired(wallpapers, collection);
                installedCollection = true;
            }
            File stagedMarker = new File(stage, ".bundled-scenes-v1");
            if (stagedMarker.isFile()) {
                moveRequired(stagedMarker, marker);
                installedMarker = true;
            }
            moveRequired(new File(stage, "theme_revision"), revision);
            installedRevision = true;
            deleteTree(stage);
            return true;
        } catch (IOException e) {
            rollback(directory, stage, hadConfig, hadWallpaper, hadCollection, hadMarker, hadRevision,
                    installedConfig, installedWallpaper, installedCollection, installedMarker, installedRevision);
            return false;
        }
    }

    private static boolean contains(String name) {
        for (String candidate : NAMES) if (candidate.equals(name)) return true;
        return false;
    }

    private static boolean hasAsset(AssetManager assets, String asset) throws IOException {
        try (InputStream input = assets.open(asset)) {
            return input.read() >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyAsset(AssetManager assets, String source, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("mkdir failed");
        try (InputStream input = assets.open(source); OutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private static void writeText(File destination, String value) throws IOException {
        try (OutputStream output = new FileOutputStream(destination)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean moveAside(File source, File stage, String backupName) throws IOException {
        if (!source.exists()) return false;
        File backup = new File(stage, backupName);
        if (!source.renameTo(backup)) throw new IOException("could not stage existing theme file");
        return true;
    }

    private static void moveRequired(File source, File destination) throws IOException {
        if (!source.renameTo(destination)) throw new IOException("could not install theme file");
    }

    private static void rollback(File directory, File stage, boolean hadConfig, boolean hadWallpaper,
                                boolean hadCollection, boolean hadMarker, boolean hadRevision,
                                boolean installedConfig, boolean installedWallpaper, boolean installedCollection,
                                boolean installedMarker, boolean installedRevision) {
        if (installedConfig) deleteTree(new File(directory, Config.FILE_NAME));
        if (installedWallpaper) deleteTree(new File(directory, Config.WALLPAPER_NAME));
        if (installedCollection) deleteTree(new File(directory, "wallpapers"));
        if (installedMarker) deleteTree(new File(directory, ".bundled-scenes-v1"));
        if (installedRevision) deleteTree(new File(directory, "theme_revision"));
        restore(new File(stage, "previous-config.json"), new File(directory, Config.FILE_NAME), hadConfig);
        restore(new File(stage, "previous-wallpaper.jpg"), new File(directory, Config.WALLPAPER_NAME), hadWallpaper);
        restore(new File(stage, "previous-wallpapers"), new File(directory, "wallpapers"), hadCollection);
        restore(new File(stage, "previous-marker"), new File(directory, ".bundled-scenes-v1"), hadMarker);
        restore(new File(stage, "previous-revision"), new File(directory, "theme_revision"), hadRevision);
        deleteTree(stage);
    }

    private static void restore(File backup, File destination, boolean existed) {
        if (existed && backup.exists()) backup.renameTo(destination);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }
}
