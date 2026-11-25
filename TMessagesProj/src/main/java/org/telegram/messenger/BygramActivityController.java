package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public final class BygramActivityController {
    private static final String ENDPOINT = "https://byxarek.site/TEST-PROJECT/BYgram/api/";
    private static final long ACTIVITY_CACHE_MS = 7_000L;
    private static final long ACTIVITY_HEARTBEAT_MS = 7_000L;

    public interface Callback { void run(String activity); }
    private interface JsonCallback { void run(JSONObject response); }
    public interface PluginCatalogCallback { void run(JSONObject response); }
    public static void pluginCatalogRequest(int account, String repository, PluginCatalogCallback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId == 0) { callback.run(null); return; }
        ensureSession(account, ready -> {
            if (!ready || UserConfig.getInstance(account).getClientUserId() != userId) { callback.run(null); return; }
            request(userId, repository == null ? json("action", "plugin_submissions") : json("action", "plugin_submit", "repository", repository),
                    true, response -> { if (UserConfig.getInstance(account).getClientUserId() == userId) callback.run(response); else callback.run(null); });
        });
    }
    private static final int PEER_USER = 1;
    private static final int PEER_BOT = 2;
    private static final int PEER_GROUP = 3;
    private static final int PEER_CHANNEL = 4;
    public static final class ActivityInfo {
        public final int type;
        public final long peerId;
        public final String peerName;
        public final int peerKind;

        ActivityInfo(int type, long peerId, String peerName, int peerKind) {
            this.type = type;
            this.peerId = peerId;
            this.peerName = peerName;
            this.peerKind = peerKind;
        }

        public String text() {
            if (type == BygramActivitySettings.ACTIVITY_DIALOGS) return LocaleController.getString(R.string.BygramActivityDialogs);
            if (type == BygramActivitySettings.ACTIVITY_CHAT && peerKind == PEER_BOT) return LocaleController.formatString(R.string.BygramActivityBot, peerName);
            if (type == BygramActivitySettings.ACTIVITY_CHAT && peerKind == PEER_GROUP) return LocaleController.formatString(R.string.BygramActivityGroup, peerName);
            if (peerKind == PEER_CHANNEL) return LocaleController.formatString(R.string.BygramActivityChannel, peerName);
            if (type == BygramActivitySettings.ACTIVITY_PROFILE) return LocaleController.formatString(R.string.BygramActivityProfile, peerName);
            return LocaleController.formatString(R.string.BygramViewingChat, peerName);
        }
    }
    private static final class OwnActivity {
        final int type;
        final long peerId;
        final String peerName;
        final int peerKind;
        OwnActivity(int type, long peerId, String peerName, int peerKind) { this.type = type; this.peerId = peerId; this.peerName = peerName; this.peerKind = peerKind; }
    }
    private static final class CachedActivity {
        final ActivityInfo info;
        final long loadedAt;
        CachedActivity(ActivityInfo info) { this.info = info; this.loadedAt = System.currentTimeMillis(); }
    }
    private static final OwnActivity[] ownActivities = new OwnActivity[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Map<String, CachedActivity> activityCache = new HashMap<>();
    private static final Map<Integer, Set<Long>> pendingActivities = new HashMap<>();
    private static final Map<String, ArrayList<Runnable>> activityCallbacks = new HashMap<>();
    private static final Map<Long, ArrayList<Utilities.Callback<Boolean>>> sessionCallbacks = new HashMap<>();
    private static final Set<Long> viewersSyncing = new HashSet<>();
    private static boolean activityFlushScheduled;
    private static boolean heartbeatScheduled;
    private static final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            heartbeatScheduled = false;
            boolean active = false;
            for (int account = 0; account < ownActivities.length; account++) {
                if (ownActivities[account] != null) { sendActivity(account); active = true; }
            }
            if (active) scheduleHeartbeat();
        }
    };
    private BygramActivityController() {}

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_activity_sessions", Context.MODE_PRIVATE);
    }

    private static String key(String name, long userId) { return name + '_' + userId; }

    private static boolean hasSession(long userId) {
        SharedPreferences preferences = preferences();
        return userId > 0 && !preferences.getString(key("access", userId), "").isEmpty()
                && preferences.getLong(key("access_expires", userId), 0) > System.currentTimeMillis() / 1000 + 30;
    }

    private static void ensureSession(int account, Utilities.Callback<Boolean> callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0 || hasSession(userId)) {
            callback.run(userId > 0);
            return;
        }
        ArrayList<Utilities.Callback<Boolean>> waiting = sessionCallbacks.computeIfAbsent(userId, id -> new ArrayList<>());
        waiting.add(callback);
        if (waiting.size() != 1) return;
        Utilities.Callback<Boolean> done = ready -> {
            sessionCallbacks.remove(userId);
            boolean sameAccount = UserConfig.getInstance(account).getClientUserId() == userId;
            for (Utilities.Callback<Boolean> waiter : waiting) waiter.run(ready && sameAccount);
        };
        refreshAccess(userId, done);
    }

    private static void authenticate(long userId, Utilities.Callback<Boolean> callback) {
        authenticate(userId, true, callback);
    }

    private static void authenticate(long userId, boolean tryAccount, Utilities.Callback<Boolean> callback) {
        request(userId, json("action", "activity_start", "user_id", userId), false, response -> {
            if (tryAccount && response != null && response.optBoolean("needs_registration")) {
                BygramAccountService.registerAccount(userId, (ok, url) -> {
                    if (ok) authenticate(userId, false, callback);
                    else callback.run(false);
                });
                return;
            }
            if (response == null || !response.optBoolean("ok")) { callback.run(false); return; }
            saveTokens(userId, response);
            callback.run(hasSession(userId));
        });
    }

    private static void refreshAccess(long userId, Utilities.Callback<Boolean> callback) {
        String refresh = preferences().getString(key("refresh", userId), "");
        if (refresh.isEmpty()) { preferences().edit().remove(key("access", userId)).apply(); authenticate(userId, callback); return; }
        request(userId, json("action", "refresh", "refresh_token", refresh), false, response -> {
            if (response == null || response.optInt("_http_status") >= 500 || response.optInt("_http_status") == 429) {
                callback.run(false);
            } else if (!response.optBoolean("ok")) {
                if (response.optInt("_http_status") != 401 && response.optInt("_http_status") != 403) {
                    callback.run(false);
                    return;
                }
                preferences().edit().remove(key("access", userId)).remove(key("refresh", userId)).apply();
                authenticate(userId, callback);
            } else {
                saveTokens(userId, response);
                callback.run(hasSession(userId));
            }
        });
    }

    private static void saveTokens(long userId, JSONObject response) {
        SharedPreferences.Editor editor = preferences().edit().putString(key("access", userId), response.optString("access_token", ""))
                .putLong(key("access_expires", userId), response.optLong("access_expires", 0));
        String refresh = response.optString("refresh_token", "");
        if (!refresh.isEmpty()) editor.putString(key("refresh", userId), refresh);
        editor.apply();
    }

    private static boolean hasAccess(int account, Runnable retry) {
        if (hasSession(UserConfig.getInstance(account).getClientUserId())) return true;
        ensureSession(account, ready -> { if (ready) retry.run(); });
        return false;
    }

    public static void setCurrentActivity(int account, int type, long dialogId, TLRPC.User user, TLRPC.Chat chat) {
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (selfId <= 0 || !BygramActivitySettings.isActivityEnabled(account) || BygramActivitySettings.getActivityViewers(account).isEmpty()) return;
        if (type != BygramActivitySettings.ACTIVITY_DIALOGS && type != BygramActivitySettings.ACTIVITY_PROFILE && type != BygramActivitySettings.ACTIVITY_CHAT) return;
        if (type != BygramActivitySettings.ACTIVITY_DIALOGS && (dialogId == 0 || dialogId == selfId)) return;
        String username = user != null ? UserObject.getPublicUsername(user) : chat != null ? ChatObject.getPublicUsername(chat) : null;
        String title = username != null && !username.isEmpty() ? "@" + username
                : user != null ? UserObject.getUserName(user) : chat != null ? chat.title : "";
        if (type != BygramActivitySettings.ACTIVITY_DIALOGS && title.isEmpty()) return;
        int peerKind = user != null ? (user.bot ? PEER_BOT : PEER_USER)
                : chat != null ? (ChatObject.isChannelAndNotMegaGroup(chat) ? PEER_CHANNEL : PEER_GROUP) : 0;
        ownActivities[account] = new OwnActivity(type, dialogId, title, peerKind);
        sendActivity(account);
        scheduleHeartbeat();
    }

    private static void scheduleHeartbeat() {
        if (heartbeatScheduled) return;
        heartbeatScheduled = true;
        AndroidUtilities.runOnUIThread(heartbeat, ACTIVITY_HEARTBEAT_MS);
    }

    private static void sendActivity(int account) {
        OwnActivity activity = ownActivities[account];
        long selfId = UserConfig.getInstance(account).getClientUserId();
        if (activity == null) return;
        if (selfId <= 0 || !BygramActivitySettings.isActivityEnabled(account)) { ownActivities[account] = null; return; }
        if (viewersDirty(selfId)) { syncViewers(account); return; }
        if (!hasAccess(account, () -> sendActivity(account))) return;
        request(selfId, json("action", "update", "activity_type", activity.type,
                "peer_id", activity.peerId, "peer_name", activity.peerName, "peer_kind", activity.peerKind), true, null);
    }

    public static void updateViewers(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0) return;
        preferences().edit().putBoolean(key("viewers_dirty", userId), true).apply();
        syncViewers(account);
    }

    private static boolean viewersDirty(long userId) {
        return preferences().getBoolean(key("viewers_dirty", userId), false);
    }

    private static JSONArray viewerPayload(int account) {
        JSONArray viewers = new JSONArray();
        for (Map.Entry<Long, Integer> viewer : BygramActivitySettings.getActivityPermissions(account).entrySet()) {
            viewers.put(json("user_id", viewer.getKey(), "permissions", viewer.getValue()));
        }
        return viewers;
    }

    private static void syncViewers(int account) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0) return;
        if (!hasAccess(account, () -> syncViewers(account))) return;
        if (!viewersSyncing.add(userId)) return;
        JSONArray viewers = viewerPayload(account);
        String sent = viewers.toString();
        request(userId, json("action", "viewers", "viewers", viewers), true, response -> {
            viewersSyncing.remove(userId);
            if (UserConfig.getInstance(account).getClientUserId() != userId) return;
            if (response == null || !response.optBoolean("ok")) {
                AndroidUtilities.runOnUIThread(() -> syncViewers(account), ACTIVITY_HEARTBEAT_MS);
                return;
            }
            if (!sent.equals(viewerPayload(account).toString())) {
                syncViewers(account);
                return;
            }
            preferences().edit().putBoolean(key("viewers_dirty", userId), false).apply();
            if (BygramActivitySettings.isActivityEnabled(account)) sendActivity(account);
        });
    }

    public static void clear(int account) {
        ownActivities[account] = null;
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0 || !hasAccess(account, () -> clear(account))) return;
        request(userId, json("action", "clear"), true, null);
    }

    public static void clearCurrentActivity(int account, int type, long peerId) {
        OwnActivity activity = ownActivities[account];
        if (activity != null && activity.type == type && activity.peerId == peerId) clear(account);
    }

    public static void clearSettings() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            ownActivities[account] = null;
            long userId = UserConfig.getInstance(account).getClientUserId();
            if (userId <= 0 || preferences().getString(key("access", userId), "").isEmpty()
                    || preferences().getLong(key("access_expires", userId), 0) <= System.currentTimeMillis() / 1000) continue;
            request(userId, json("action", "clear"), true, null);
            request(userId, json("action", "viewers", "viewers", new JSONArray()), true, null);
        }
    }

    public static void get(int account, long ownerId, Callback callback) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (ownerId <= 0) { callback.run(null); return; }
        if (!hasAccess(account, () -> get(account, ownerId, callback))) return;
        request(userId, json("action", "activity_get", "owner_id", ownerId), true, response -> {
            if (response == null || !response.optBoolean("ok")) return;
            ActivityInfo info = parseActivity(response);
            callback.run(info == null ? null : info.text());
        });
    }

    public static ActivityInfo getCachedActivity(int account, long ownerId, Runnable callback) {
        if (ownerId <= 0) return null;
        String key = account + ":" + ownerId;
        CachedActivity cached = activityCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.loadedAt < ACTIVITY_CACHE_MS) return cached.info;
        pendingActivities.computeIfAbsent(account, unused -> new HashSet<>()).add(ownerId);
        if (callback != null) activityCallbacks.computeIfAbsent(key, unused -> new ArrayList<>()).add(callback);
        if (!activityFlushScheduled) {
            activityFlushScheduled = true;
            AndroidUtilities.runOnUIThread(BygramActivityController::flushActivities, 150);
        }
        return cached == null ? null : cached.info;
    }

    private static void flushActivities() {
        activityFlushScheduled = false;
        HashMap<Integer, Set<Long>> batches = new HashMap<>(pendingActivities);
        pendingActivities.clear();
        for (Map.Entry<Integer, Set<Long>> batch : batches.entrySet()) {
            int account = batch.getKey();
            ArrayList<Long> ids = new ArrayList<>(batch.getValue());
            if (ids.isEmpty()) continue;
            if (!hasAccess(account, () -> {
                pendingActivities.computeIfAbsent(account, unused -> new HashSet<>()).addAll(ids);
                if (!activityFlushScheduled) {
                    activityFlushScheduled = true;
                    AndroidUtilities.runOnUIThread(BygramActivityController::flushActivities, 150);
                }
            })) continue;
            long userId = UserConfig.getInstance(account).getClientUserId();
            request(userId, json("action", "activities", "owner_ids", new JSONArray(ids)), true, response -> {
                if (response == null || !response.optBoolean("ok")) {
                    for (long id : ids) {
                        String key = account + ":" + id;
                        CachedActivity cached = activityCache.get(key);
                        activityCache.put(key, new CachedActivity(cached != null ? cached.info : null));
                        ArrayList<Runnable> callbacks = activityCallbacks.remove(key);
                        if (callbacks != null) for (Runnable callback : callbacks) callback.run();
                    }
                    return;
                }
                HashMap<Long, ActivityInfo> loaded = new HashMap<>();
                JSONArray values = response.optJSONArray("activities");
                if (values != null) for (int i = 0; i < values.length(); i++) {
                    JSONObject value = values.optJSONObject(i);
                    ActivityInfo info = parseActivity(value);
                    if (value != null && info != null) loaded.put(value.optLong("owner_id"), info);
                }
                for (long id : ids) {
                    String key = account + ":" + id;
                    activityCache.put(key, new CachedActivity(loaded.get(id)));
                    ArrayList<Runnable> callbacks = activityCallbacks.remove(key);
                    if (callbacks != null) for (Runnable callback : callbacks) callback.run();
                }
            });
        }
    }

    private static ActivityInfo parseActivity(JSONObject value) {
        if (value == null || !value.optBoolean("ok", true)) return null;
        int type = value.optInt("activity_type");
        String name = value.optString("peer_name", "");
        if (type != BygramActivitySettings.ACTIVITY_DIALOGS && type != BygramActivitySettings.ACTIVITY_PROFILE && type != BygramActivitySettings.ACTIVITY_CHAT) return null;
        if (type != BygramActivitySettings.ACTIVITY_DIALOGS && name.isEmpty()) return null;
        return new ActivityInfo(type, value.optLong("peer_id"), name, value.optInt("peer_kind"));
    }

    private static JSONObject json(Object... values) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < values.length; i += 2) result.put((String) values[i], values[i + 1]);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    private static void request(long userId, JSONObject data, boolean authenticated, JsonCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            JSONObject response = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(7000); connection.setReadTimeout(7000); connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
                if (authenticated) {
                    String token = preferences().getString(key("access", userId), "");
                    if (token.isEmpty()) throw new IllegalStateException("Missing BYgram access token");
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }
                if ("activity_start".equals(data.optString("action"))) {
                    String credential = BygramAccountService.accountToken(userId);
                    if (!credential.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + credential);
                }
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder text = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) text.append(line);
                    if (text.length() > 0) response = new JSONObject(text.toString());
                }
                if (response == null) response = new JSONObject();
                response.put("_http_status", status);
            } catch (Exception e) { FileLog.e(e); }
            finally { if (connection != null) connection.disconnect(); }
            if (callback != null) { JSONObject result = response; AndroidUtilities.runOnUIThread(() -> callback.run(result)); }
        });
    }

}
