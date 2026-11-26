package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.UUID;

public final class BygramDeviceReporter {
    private static final long REPORT_INTERVAL_MS = 24 * 60 * 60 * 1000L;
    private static boolean pending;
    private static final HashSet<Long> pendingAccounts = new HashSet<>();

    private BygramDeviceReporter() {}

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_device_metrics", Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return preferences().getBoolean("enabled", true);
    }

    public static void setEnabled(boolean enabled) {
        SharedPreferences preferences = preferences();
        if (isEnabled() == enabled) return;
        preferences.edit().putBoolean("enabled", enabled)
                .putLong("revision", preferences.getLong("revision", 0) + 1).apply();
        report();
    }

    static void report() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            reportAccount(UserConfig.getInstance(account).getClientUserId());
        }
        reportDevice();
    }

    static void reportAccount(long userId) {
        if (userId <= 0 || BygramAccountService.accountToken(userId).isEmpty()) return;
        SharedPreferences preferences = preferences();
        long revision = preferences.getLong("revision", 0);
        String key = "account_revision_" + userId;
        if (revision == preferences.getLong(key, 0) || !pendingAccounts.add(userId)) return;
        BygramAccountService.reportStatisticsPreference(userId, isEnabled(), ok -> {
            pendingAccounts.remove(userId);
            if (ok) preferences.edit().putLong(key, revision).apply();
            if (revision != preferences.getLong("revision", 0)) reportAccount(userId);
        });
    }

    private static void reportDevice() {
        Context context = ApplicationLoader.applicationContext;
        SharedPreferences preferences = preferences();
        boolean enabled = isEnabled();
        long revision = preferences.getLong("revision", 0);
        long now = System.currentTimeMillis();
        long last = preferences.getLong("last_report", 0);
        boolean synced = revision == preferences.getLong("device_revision", 0);
        if (pending || (synced && (!enabled || (now >= last && now - last < REPORT_INTERVAL_MS)))) return;
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
            BygramAccountService.reportDevice(id.toString(), enabled, model.isEmpty() ? "Android" : model,
                    BuildVars.BUILD_VERSION_STRING, Build.VERSION.SDK_INT, ok -> {
                        pending = false;
                        if (ok) preferences.edit().putLong("last_report", System.currentTimeMillis())
                                .putLong("device_revision", revision).apply();
                        if (revision != preferences.getLong("revision", 0)) reportDevice();
                    });
        } catch (Exception error) {
            pending = false;
            FileLog.e(error);
        }
    }
}
