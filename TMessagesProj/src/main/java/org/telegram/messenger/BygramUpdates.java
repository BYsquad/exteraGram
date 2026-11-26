package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class BygramUpdates {
    private static final String SITE = "https://byxarek.site/TEST-PROJECT/BYgram/";
    public static final int RELEASE_NUMBER = BuildConfig.BYGRAM_RELEASE_NUMBER;
    private static final String LAST_RELEASE = "last_release";
    private static final String LAST_APK_NAME = "last_apk_name";
    private static final String APK_NAME_PATTERN = "BYgram-beta-[0-9A-Za-z._-]{1,55}\\.apk";

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_updates", Context.MODE_PRIVATE);
    }

    public static String getDownloadUrl() {
        SharedPreferences prefs = preferences();
        String apkName = prefs.getString(LAST_APK_NAME, "");
        return prefs.getInt(LAST_RELEASE, 0) > RELEASE_NUMBER && apkName.matches(APK_NAME_PATTERN)
                ? SITE + apkName : null;
    }

    public static void check(Consumer<Boolean> onChecked) {
        Utilities.externalNetworkQueue.postRunnable(() -> {
            final int release;
            final String apkName;
            try {
                JSONObject metadata = fetchRelease();
                release = readRelease(metadata);
                apkName = metadata.getString("apk_name");
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> onChecked.accept(false));
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                preferences().edit().putInt(LAST_RELEASE, release).putString(LAST_APK_NAME, apkName).apply();
                onChecked.accept(true);
            });
        });
    }

    private static JSONObject fetchRelease() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(SITE + "api/beta.json").openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("BYgram update metadata is unavailable");
            }
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                StringBuilder body = new StringBuilder();
                char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    body.append(buffer, 0, read);
                    if (body.length() > 8192) throw new IOException("BYgram update metadata is too large");
                }
                return new JSONObject(body.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    static int readRelease(JSONObject metadata) throws IOException {
        if (!BuildConfig.APPLICATION_ID.equals(metadata.optString("package"))
                || !metadata.optString("apk_name").matches(APK_NAME_PATTERN)) {
            throw new IOException("Invalid BYgram APK metadata");
        }
        if (!metadata.has("bygram_release")) return 0;
        String release = metadata.optString("bygram_release");
        if (!release.matches("[1-9][0-9]{0,9}")) throw new IOException("Invalid BYgram release number");
        return Integer.parseInt(release);
    }
}
