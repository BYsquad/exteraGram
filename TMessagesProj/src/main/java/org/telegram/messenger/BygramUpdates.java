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
    public static final String DOWNLOAD_URL = SITE + "download.html#beta";
    public static final int RELEASE_NUMBER = BuildConfig.BYGRAM_RELEASE_NUMBER;
    private static final String LAST_RELEASE = "last_release";

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_updates", Context.MODE_PRIVATE);
    }

    public static boolean isAvailable() {
        return preferences().getInt(LAST_RELEASE, 0) > RELEASE_NUMBER;
    }

    public static void check(Consumer<Boolean> onChecked) {
        Utilities.externalNetworkQueue.postRunnable(() -> {
            final int release;
            try {
                release = fetchRelease();
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> onChecked.accept(false));
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                preferences().edit().putInt(LAST_RELEASE, release).apply();
                onChecked.accept(true);
            });
        });
    }

    private static int fetchRelease() throws Exception {
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
                return readRelease(new JSONObject(body.toString()));
            }
        } finally {
            connection.disconnect();
        }
    }

    static int readRelease(JSONObject metadata) throws IOException {
        if (!BuildConfig.APPLICATION_ID.equals(metadata.optString("package"))
                || !metadata.optString("apk_name").matches("BYgram-beta-[0-9A-Za-z._-]{1,55}\\.apk")) {
            throw new IOException("Invalid BYgram APK metadata");
        }
        if (!metadata.has("bygram_release")) return 0;
        String release = metadata.optString("bygram_release");
        if (!release.matches("[1-9][0-9]{0,9}")) throw new IOException("Invalid BYgram release number");
        return Integer.parseInt(release);
    }
}
