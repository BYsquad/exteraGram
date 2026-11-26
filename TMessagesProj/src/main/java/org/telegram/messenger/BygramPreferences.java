package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

public final class BygramPreferences {
    private BygramPreferences() {}

    public static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
    }

    public static String key(int account, String name) {
        return userKey(UserConfig.getInstance(account).getClientUserId(), name);
    }

    private static String userKey(long userId, String name) {
        return "bygram_account_" + userId + "_" + name;
    }

    public static boolean getBoolean(int account, String name, boolean fallback) {
        return getUserBoolean(UserConfig.getInstance(account).getClientUserId(), name, fallback);
    }

    static boolean hasUserSetting(long userId, String name) {
        return userId > 0 && preferences().contains(userKey(userId, name));
    }

    static boolean getUserBoolean(long userId, String name, boolean fallback) {
        return userId > 0 ? preferences().getBoolean(userKey(userId, name), fallback) : fallback;
    }

    public static void setBoolean(int account, String name, boolean value) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId > 0) preferences().edit().putBoolean(userKey(userId, name), value).apply();
    }

    static void migrate() {
        SharedPreferences prefs = preferences();
        String marker = "bygram_account_preferences_migrated";
        if (prefs.getBoolean(marker, false)) return;
        Map<String, ?> values = prefs.getAll();
        SharedPreferences metrics = ApplicationLoader.applicationContext.getSharedPreferences("bygram_device_metrics", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            long userId = UserConfig.getInstance(account).getClientUserId();
            if (userId <= 0) continue;
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                if (name.startsWith("bygram_ghost_") || name.startsWith("bygram_spy_")
                        || name.startsWith("bygram_scroll_") || name.startsWith("bygram_snow_")
                        || name.equals("bygram_background_others")) {
                    String key = userKey(userId, name);
                    if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                    if (value instanceof Integer) editor.putInt(key, (Integer) value);
                }
            }
            editor.putBoolean(userKey(userId, "bygram_activity_enabled"),
                    prefs.getBoolean("bygram_activity_enabled_" + account, prefs.getBoolean("bygram_activity_enabled", false)));
            editor.putString(userKey(userId, "bygram_activity_viewers"),
                    prefs.getString("bygram_activity_viewers_" + account, "{}"));
            if (metrics.contains("enabled")) {
                editor.putBoolean(userKey(userId, "usage_statistics"), metrics.getBoolean("enabled", true));
            }
        }
        editor.putBoolean(marker, true).apply();
    }
}
