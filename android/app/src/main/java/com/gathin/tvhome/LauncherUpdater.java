package com.gathin.tvhome;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Checks CoveTV's public GitHub releases and hands verified updates to Android's installer. */
final class LauncherUpdater {
    static final String AUTO_CHECK_KEY = "auto_update_checks";
    static final String RELEASE_API =
            "https://api.github.com/repos/PixelOTTO/cove-android-tv/releases/latest";
    private static final String ASSET_PREFIX = "covetv-";
    private static final long CHECK_DEBOUNCE_MS = 30_000L;
    private static final long MAX_APK_BYTES = 160L * 1024L * 1024L;
    private static final int MAX_API_BYTES = 1024 * 1024;
    private static final Object CHECK_LOCK = new Object();
    private static long lastCheckElapsed;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "covetv-updater");
        thread.setDaemon(true);
        return thread;
    });

    interface UpdateListener {
        void onUpdateAvailable(Release release);
    }

    interface InstallListener {
        void onInstallRequestReady();
        void onFailure(String message);
    }

    static final class Release {
        final String version;
        final String assetName;
        final String downloadUrl;
        final String sha256;
        final long size;

        Release(String version, String assetName, String downloadUrl, String sha256, long size) {
            this.version = version;
            this.assetName = assetName;
            this.downloadUrl = downloadUrl;
            this.sha256 = sha256;
            this.size = size;
        }
    }

    private LauncherUpdater() { }

    static boolean isEnabled(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getBoolean(AUTO_CHECK_KEY, true);
    }

    static void checkForUpdate(Context context, UpdateListener listener) {
        Context app = context.getApplicationContext();
        if (!isEnabled(app)) return;
        long now = SystemClock.elapsedRealtime();
        synchronized (CHECK_LOCK) {
            if (lastCheckElapsed != 0 && now - lastCheckElapsed < CHECK_DEBOUNCE_MS) return;
            lastCheckElapsed = now;
        }
        IO.execute(() -> {
            try {
                Release release = fetchNewerRelease(app);
                if (release == null) return;
                MAIN.post(() -> {
                    if (isEnabled(app)) listener.onUpdateAvailable(release);
                });
            } catch (Exception ignored) {
                // No connection, no matching APK, or a malformed release means no home-screen popup.
            }
        });
    }

    static void downloadAndInstall(Context context, Release release, InstallListener listener) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                File apk = downloadAndVerify(app, release);
                installWithPackageInstaller(app, apk);
                MAIN.post(listener::onInstallRequestReady);
            } catch (Exception error) {
                String message = error.getMessage();
                if (message == null || message.trim().isEmpty()) message = "CoveTV could not prepare this update.";
                final String userMessage = message;
                MAIN.post(() -> listener.onFailure(userMessage));
            }
        });
    }

    private static Release fetchNewerRelease(Context context) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            connection.setRequestProperty("User-Agent", "CoveTV-Android-TV");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            JSONObject json;
            try (InputStream input = connection.getInputStream()) {
                json = new JSONObject(new String(readLimited(input, MAX_API_BYTES), "UTF-8"));
            }
            if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) return null;
            String version = normalizeVersion(json.optString("tag_name", ""));
            if (version == null) return null;
            String installedVersion = installedVersion(context);
            if (compareVersions(version, installedVersion) <= 0) return null;

            JSONArray assets = json.optJSONArray("assets");
            if (assets == null) return null;
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;
                String name = asset.optString("name", "");
                String lowerName = name.toLowerCase(java.util.Locale.ROOT);
                if (!lowerName.startsWith(ASSET_PREFIX) || !lowerName.endsWith(".apk")) continue;
                String url = asset.optString("browser_download_url", "");
                if (!url.startsWith("https://github.com/PixelOTTO/cove-android-tv/releases/download/")) continue;
                String digest = asset.optString("digest", "");
                if (digest.startsWith("sha256:")) digest = digest.substring("sha256:".length());
                return new Release(version, name, url, digest, asset.optLong("size", 0));
            }
            return null;
        } finally {
            connection.disconnect();
        }
    }

    private static File downloadAndVerify(Context context, Release release) throws Exception {
        if (release.size <= 0 || release.size > MAX_APK_BYTES) throw new IOException("The release APK has an invalid size.");
        HttpURLConnection connection = (HttpURLConnection) new URL(release.downloadUrl).openConnection();
        File apk = new File(context.getCacheDir(), "covetv-update.apk");
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        long copied = 0;
        try {
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "CoveTV-Android-TV");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("GitHub could not provide the update APK.");
            }
            String finalHost = connection.getURL().getHost().toLowerCase(java.util.Locale.ROOT);
            if (!finalHost.equals("github.com") && !finalHost.endsWith(".githubusercontent.com")) {
                throw new IOException("The update download left GitHub.");
            }
            long contentLength = connection.getContentLengthLong();
            if (contentLength > MAX_APK_BYTES || (contentLength > 0 && contentLength != release.size)) {
                throw new IOException("The update APK size does not match the release.");
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(apk, false)) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    copied += count;
                    if (copied > MAX_APK_BYTES || copied > release.size) {
                        throw new IOException("The downloaded update is larger than the release file.");
                    }
                    sha256.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            }
        } catch (Exception error) {
            // Never leave a partial file that could be mistaken for the next update.
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
        if (copied != release.size) {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            throw new IOException("The downloaded update is incomplete.");
        }
        String actualDigest = toHex(sha256.digest());
        if (!release.sha256.isEmpty() && !actualDigest.equalsIgnoreCase(release.sha256)) {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            throw new IOException("The update checksum did not match the GitHub release.");
        }
        verifyApk(context, apk, release.version);
        return apk;
    }

    @SuppressWarnings("deprecation")
    private static void verifyApk(Context context, File apk, String releaseVersion) throws Exception {
        PackageManager packages = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = packages.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)) {
            throw new IOException("The release asset is not a CoveTV APK.");
        }
        if (candidate.versionName == null || compareVersions(candidate.versionName, releaseVersion) != 0) {
            throw new IOException("The APK version does not match the GitHub release tag.");
        }
        PackageInfo installed = packages.getPackageInfo(context.getPackageName(), flags);
        long candidateCode = versionCode(candidate);
        if (candidateCode <= versionCode(installed)) {
            throw new IOException("This release is not newer than the installed CoveTV version.");
        }
        if (!Arrays.equals(signerDigests(installed), signerDigests(candidate))) {
            throw new IOException("This APK was signed with a different key and cannot update this installation.");
        }
    }

    private static void installWithPackageInstaller(Context context, File apk) throws Exception {
        PackageManager packages = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packages.canRequestPackageInstalls()) {
            throw new IOException("Allow CoveTV to install apps in Android settings, then try the update again.");
        }
        PackageInstaller installer = packages.getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(apk.length());
        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                session.fsync(output);
            }
            Intent status = new Intent(context, MainActivity.class)
                    .setAction(context.getPackageName() + ".UPDATE_INSTALL_STATUS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) pendingFlags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent callback = PendingIntent.getActivity(context, sessionId, status, pendingFlags);
            session.commit(callback.getIntentSender());
            committed = true;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            apk.delete();
            if (!committed) installer.abandonSession(sessionId);
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private static byte[] signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        if (signatures == null || signatures.length == 0) throw new IOException("The APK has no signing certificate.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[][] values = new byte[signatures.length][];
        for (int i = 0; i < signatures.length; i++) values[i] = digest.digest(signatures[i].toByteArray());
        Arrays.sort(values, (left, right) -> compareBytes(left, right));
        int length = 0;
        for (byte[] value : values) length += value.length;
        byte[] joined = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, joined, offset, value.length);
            offset += value.length;
        }
        return joined;
    }

    private static int compareBytes(byte[] left, byte[] right) {
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int difference = (left[i] & 0xff) - (right[i] & 0xff);
            if (difference != 0) return difference;
        }
        return left.length - right.length;
    }

    private static String installedVersion(Context context) throws PackageManager.NameNotFoundException {
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        return info.versionName == null ? "0" : info.versionName;
    }

    private static String normalizeVersion(String value) {
        Matcher matcher = Pattern.compile("(?i)^v?([0-9]+(?:\\.[0-9]+){1,3})").matcher(value.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int compareVersions(String left, String right) {
        String[] a = numericVersion(left).split("\\.");
        String[] b = numericVersion(right).split("\\.");
        int count = Math.max(a.length, b.length);
        for (int i = 0; i < count; i++) {
            long av = i < a.length ? parseNumber(a[i]) : 0;
            long bv = i < b.length ? parseNumber(b[i]) : 0;
            if (av < bv) return -1;
            if (av > bv) return 1;
        }
        return 0;
    }

    private static String numericVersion(String value) {
        String normalized = normalizeVersion(value);
        return normalized == null ? "0" : normalized;
    }

    private static long parseNumber(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > maxBytes) throw new IOException("The GitHub release response is too large.");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }
}
