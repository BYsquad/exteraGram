package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

final class BygramDeviceReporter {
    private static final long REPORT_INTERVAL_MS = 24 * 60 * 60 * 1000L;
    private static boolean pending;

    private BygramDeviceReporter() {}

    static void report() {
        Context context = ApplicationLoader.applicationContext;
        SharedPreferences preferences = context.getSharedPreferences("bygram_device_metrics", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = preferences.getLong("last_report", 0);
        if (pending || (now >= last && now - last < REPORT_INTERVAL_MS)) return;
        pending = true;
        try {
            String identity = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (identity == null || identity.isEmpty()) {
                identity = preferences.getString("fallback_id", "");
                if (identity.isEmpty()) {
                    identity = UUID.randomUUID().toString();
                    preferences.edit().putString("fallback_id", identity).apply();
                }
            }
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(("BYgram device\n" + identity).getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder(64);
            for (byte value : hash) {
                id.append(Character.forDigit((value & 0xff) >> 4, 16));
                id.append(Character.forDigit(value & 0xf, 16));
            }
            String model = (Build.MANUFACTURER + " " + Build.MODEL).trim();
            BygramAccountService.reportDevice(id.toString(), model.isEmpty() ? "Android" : model,
                    BuildVars.BUILD_VERSION_STRING, Build.VERSION.SDK_INT, ok -> {
                        pending = false;
                        if (ok) preferences.edit().putLong("last_report", System.currentTimeMillis()).apply();
                    });
        } catch (Exception error) {
            pending = false;
            FileLog.e(error);
        }
    }
}
