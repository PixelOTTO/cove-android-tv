package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Keeps the last Home wallpaper as a small private preview so a cold start has an image behind its first frame. */
final class StartupWallpaper {
    private static final String FILE_NAME = "home-wallpaper-preview.jpg";
    private static final int MAX_EDGE = 1920;

    private StartupWallpaper() { }

    /** Applies only when this configuration currently uses a wallpaper. */
    static boolean apply(Activity activity, Config config) {
        File source = currentSource(activity, config);
        if (source == null) return false;
        File preview = new File(activity.getFilesDir(), FILE_NAME);
        String previewSource = activity.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                .getString("preview_source", "");
        File image = preview.isFile() && preview.length() > 0 && identity(source).equals(previewSource)
                ? preview : source;
        int targetW = activity.getResources().getDisplayMetrics().widthPixels;
        int targetH = activity.getResources().getDisplayMetrics().heightPixels;
        Bitmap decoded = decodePreview(image, targetW, targetH);
        if (decoded == null) return false;
        Bitmap bitmap = centerCrop(decoded, targetW, targetH);
        if (bitmap != decoded) decoded.recycle();
        BitmapDrawable background = new BitmapDrawable(activity.getResources(), bitmap);
        background.setGravity(Gravity.FILL);
        activity.getWindow().setBackgroundDrawable(background);
        return true;
    }

    private static File currentSource(Context context, Config config) {
        LocalDateTime now = LocalDateTime.now();
        int[] sun = WallpaperMood.sun(now.toLocalDate(), context.getString(R.string.latitude),
                context.getString(R.string.longitude), ZoneId.systemDefault());
        String mood = WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]);
        android.content.SharedPreferences choices = context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        String selected = choices.getString("wallpaper_mood_" + mood, null);
        if (selected != null) {
            File override = resolveChoice(context, selected);
            if (override != null) return override;
        }
        List<File> images = config.wallpapers;
        if (images.isEmpty()) return null;
        List<File> inMood = WallpaperMood.select(images, config.wallpaperMoods, mood);
        String last = context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                .getString("image", "");
        for (File file : inMood) if (file.getName().equals(last) && file.isFile()) return file;
        for (File file : inMood) if (file.isFile()) return file;
        return null;
    }

    private static File resolveChoice(Context context, String choice) {
        File dir = Config.dir(context);
        if (BundledThemes.isBundledWallpaperChoice(choice)) return BundledThemes.chosenWallpaper(dir, choice);
        if (dir == null) return null;
        File collection = new File(new File(dir, "wallpapers"), choice);
        if (collection.isFile() && collection.length() > 0) return collection;
        File single = new File(dir, choice);
        return single.isFile() && single.length() > 0 ? single : null;
    }

    private static Bitmap decodePreview(File file, int targetW, int targetH) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth / (sample * 2), bounds.outHeight / (sample * 2)) > MAX_EDGE) sample *= 2;
        sample = Math.max(sample, WallpaperSize.sampleSize(bounds.outWidth, bounds.outHeight, targetW, targetH));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    /** Called on WallpaperPlayer's decoder thread after a picture is successfully shown. */
    static void remember(Context context, File source) {
        if (source == null || !source.isFile()) return;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return;
        int sample = 1;
        while (Math.max(bounds.outWidth / (sample * 2), bounds.outHeight / (sample * 2)) > MAX_EDGE) {
            sample *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
        if (decoded == null) return;

        // Crop the saved preview to the TV screen's aspect ratio, as the real wallpaper ImageView does.
        int screenW = context.getResources().getDisplayMetrics().widthPixels;
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        Bitmap cropped = centerCrop(decoded, screenW, screenH);
        File destination = new File(context.getFilesDir(), FILE_NAME);
        File temporary = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        boolean saved = false;
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            saved = cropped.compress(Bitmap.CompressFormat.JPEG, 88, output);
            output.getFD().sync();
        } catch (IOException ignored) {
            // A missing preview never blocks Home; the regular wallpaper path still loads as before.
        } finally {
            if (cropped != decoded) cropped.recycle();
            decoded.recycle();
        }
        if (saved) {
            boolean moved = temporary.renameTo(destination);
            if (!moved) {
                // Windows-hosted builds don't run this path; Android uses atomic same-directory rename.
                destination.delete();
                moved = temporary.renameTo(destination);
            }
            if (moved) {
                context.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE).edit()
                        .putString("preview_source", identity(source)).apply();
            } else {
                temporary.delete();
            }
        } else {
            temporary.delete();
        }
    }

    private static String identity(File file) {
        return file.getAbsolutePath() + ':' + file.length() + ':' + file.lastModified();
    }

    private static Bitmap centerCrop(Bitmap source, int screenW, int screenH) {
        if (screenW <= 0 || screenH <= 0) return source;
        float targetRatio = screenW / (float) screenH;
        float sourceRatio = source.getWidth() / (float) source.getHeight();
        int width = source.getWidth();
        int height = source.getHeight();
        if (sourceRatio > targetRatio) width = Math.max(1, Math.round(height * targetRatio));
        else if (sourceRatio < targetRatio) height = Math.max(1, Math.round(width / targetRatio));
        int left = (source.getWidth() - width) / 2;
        int top = (source.getHeight() - height) / 2;
        if (left == 0 && top == 0 && width == source.getWidth() && height == source.getHeight()) return source;
        return Bitmap.createBitmap(source, left, top, width, height);
    }
}
