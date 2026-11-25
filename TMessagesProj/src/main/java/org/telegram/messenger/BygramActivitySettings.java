package org.telegram.messenger;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BygramActivitySettings {
    private static final String LEGACY_ACTIVITY_ENABLED = "bygram_activity_enabled";
    public static final int ACTIVITY_DIALOGS = 1;
    public static final int ACTIVITY_PROFILE = 1 << 1;
    public static final int ACTIVITY_CHAT = 1 << 2;
    public static final int ACTIVITY_ALL = ACTIVITY_DIALOGS | ACTIVITY_PROFILE | ACTIVITY_CHAT;

    private static String activityEnabledKey(int account) {
        return "bygram_activity_enabled_" + account;
    }

    private static String activityViewersKey(int account) {
        return "bygram_activity_viewers_" + account;
    }

    public static Map<Long, Integer> getActivityPermissions(int account) {
        HashMap<Long, Integer> result = new HashMap<>();
        try {
            String stored = preferences().getString(activityViewersKey(account), "{}");
            if (stored.trim().startsWith("[")) {
                JSONArray values = new JSONArray(stored);
                for (int i = 0; i < values.length(); i++) {
                    long id = values.optLong(i);
                    if (id > 0) result.put(id, ACTIVITY_CHAT);
                }
            } else {
                JSONObject values = new JSONObject(stored);
                java.util.Iterator<String> keys = values.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    long id = Long.parseLong(key);
                    int permissions = values.optInt(key) & ACTIVITY_ALL;
                    if (id > 0 && permissions != 0) result.put(id, permissions);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static Set<Long> getActivityViewers(int account) {
        return new HashSet<>(getActivityPermissions(account).keySet());
    }

    public static void setActivityPermissions(int account, long userId, int permissions) {
        Map<Long, Integer> viewers = getActivityPermissions(account);
        permissions &= ACTIVITY_ALL;
        if (userId > 0 && permissions != 0) viewers.put(userId, permissions); else viewers.remove(userId);
        JSONObject stored = new JSONObject();
        try {
            for (Map.Entry<Long, Integer> viewer : viewers.entrySet()) {
                stored.put(Long.toString(viewer.getKey()), viewer.getValue());
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        preferences().edit().putString(activityViewersKey(account), stored.toString()).apply();
    }

    public static boolean isActivityEnabled(int account) {
        return preferences().getBoolean(activityEnabledKey(account), false);
    }

    public static void setActivityEnabled(int account, boolean enabled) {
        preferences().edit().putBoolean(activityEnabledKey(account), enabled).apply();
    }

    public static void clearActivitySettings() {
        SharedPreferences.Editor editor = preferences().edit().remove(LEGACY_ACTIVITY_ENABLED);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            editor.remove(activityEnabledKey(account)).remove(activityViewersKey(account));
        }
        editor.apply();
    }


    private BygramActivitySettings() {}

    private static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    public static void migrate() {
        SharedPreferences prefs = preferences();
        if (!prefs.contains(LEGACY_ACTIVITY_ENABLED)) return;
        SharedPreferences.Editor editor = prefs.edit();
        boolean enabled = prefs.getBoolean(LEGACY_ACTIVITY_ENABLED, false);
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!prefs.contains(activityEnabledKey(account))) editor.putBoolean(activityEnabledKey(account), enabled);
        }
        editor.remove(LEGACY_ACTIVITY_ENABLED).apply();
    }
}
