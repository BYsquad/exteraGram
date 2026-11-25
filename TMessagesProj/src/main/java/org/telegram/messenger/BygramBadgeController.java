package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BygramBadgeController {
    private static final long CACHE_MS = 5 * 60 * 1000L;
    private static final long NEGATIVE_CACHE_MS = 60 * 1000L;
    private static final Map<String, Entry> cache = new HashMap<>();
    private static final Set<Long> pendingUsers = new HashSet<>();
    private static final Set<Long> pendingChats = new HashSet<>();
    private static boolean scheduled;
    private static boolean restored;

    private static final class Entry {
        boolean badge;
        boolean loading;
        long loadedAt;
    }

    private BygramBadgeController() {}

    public static boolean hasBadge(TLObject peer) {
        if (peer instanceof TLRPC.User) return hasBadge("user", ((TLRPC.User) peer).id);
        if (peer instanceof TLRPC.Chat) return hasBadge("chat", ((TLRPC.Chat) peer).id);
        return false;
    }

    private static boolean hasBadge(String type, long id) {
        if (id <= 0) return false;
        if (!restored) {
            restored = true;
            for (String key : badgesPreferences().getStringSet("assigned", java.util.Collections.emptySet())) {
                Entry saved = new Entry();
                saved.badge = true;
                cache.put(key, saved);
            }
        }
        String key = type + ':' + id;
        Entry entry = cache.get(key);
        if (entry == null) cache.put(key, entry = new Entry());
        if (!entry.loading && System.currentTimeMillis() - entry.loadedAt > (entry.badge ? CACHE_MS : NEGATIVE_CACHE_MS)) {
            entry.loading = true;
            ("user".equals(type) ? pendingUsers : pendingChats).add(id);
            if (!scheduled) {
                scheduled = true;
                AndroidUtilities.runOnUIThread(BygramBadgeController::flush, 150);
            }
        }
        return entry.badge;
    }

    private static void flush() {
        scheduled = false;
        ArrayList<Long> users = new ArrayList<>(pendingUsers);
        ArrayList<Long> chats = new ArrayList<>(pendingChats);
        pendingUsers.clear(); pendingChats.clear();
        if (users.isEmpty() && chats.isEmpty()) return;
        Utilities.externalNetworkQueue.postRunnable(() -> {
            HttpURLConnection connection = null;
            JSONObject response = null;
            try {
                JSONObject data = new JSONObject();
                data.put("action", "badges"); data.put("user_ids", new JSONArray(users)); data.put("chat_ids", new JSONArray(chats));
                connection = (HttpURLConnection) new URL("https://byxarek.site/TEST-PROJECT/BYgram/api/").openConnection();
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setConnectTimeout(7000); connection.setReadTimeout(7000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) { output.write(data.toString().getBytes(StandardCharsets.UTF_8)); }
                if (connection.getResponseCode() == 200) try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder body = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    response = new JSONObject(body.toString());
                }
            } catch (Exception e) { FileLog.e(e); }
            finally { if (connection != null) connection.disconnect(); }
            JSONObject result = response;
            AndroidUtilities.runOnUIThread(() -> {
                if (result == null || !result.optBoolean("ok")) {
                    long now = System.currentTimeMillis();
                    for (long id : users) retryLater("user", id, now);
                    for (long id : chats) retryLater("chat", id, now);
                    return;
                }
                long now = System.currentTimeMillis();
                for (long id : users) update("user", id, false, now);
                for (long id : chats) update("chat", id, false, now);
                JSONArray badges = result.optJSONArray("badges");
                if (badges != null) for (int i = 0; i < badges.length(); i++) {
                    JSONObject badge = badges.optJSONObject(i);
                    if (badge == null) continue;
                    String type = badge.optString("peer_type"); long id = badge.optLong("peer_id");
                    if (("user".equals(type) && users.contains(id)) || ("chat".equals(type) && chats.contains(id))) update(type, id, true, now);
                }
                Set<String> assigned = new HashSet<>();
                for (Map.Entry<String, Entry> item : cache.entrySet()) {
                    if (item.getValue().badge) assigned.add(item.getKey());
                }
                badgesPreferences().edit().putStringSet("assigned", assigned).apply();
                for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++)
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces,
                            MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_CHAT_NAME);
            });
        });
    }

    private static void update(String type, long id, boolean enabled, long time) {
        Entry entry = new Entry(); entry.badge = enabled; entry.loadedAt = time;
        cache.put(type + ':' + id, entry);
    }

    private static void retryLater(String type, long id, long time) {
        Entry entry = cache.get(type + ':' + id);
        if (entry != null) {
            entry.loading = false;
            entry.loadedAt = time;
        }
    }

    private static SharedPreferences badgesPreferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_badges", Context.MODE_PRIVATE);
    }
}
