package org.telegram.messenger;

import org.json.JSONObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.ConnectionsManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import android.content.Context;
import android.content.SharedPreferences;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.BitmapFactory;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.os.Build;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.LaunchActivity;

public final class BygramAccountService {
    public static final String OPEN_PROFILE_VIEWS = "org.telegram.messenger.OPEN_BYGRAM_PROFILE_VIEWS";
    public static final String PROFILE_VIEWS_OWNER = "bygram_profile_views_owner";

    private static final String ENDPOINT = "https://byxarek.site/TEST-PROJECT/BYgram/api/";
    private static final java.util.HashSet<Integer> pollingProfileViews = new java.util.HashSet<>();

    public interface Callback { void run(boolean ok, String authUrl); }
    public interface ProfileViewsCallback { void run(JSONObject response, String authUrl); }
    private interface Result { void run(JSONObject value); }
    private BygramAccountService() {}

    public static boolean profileViewsEnabled(int account) {
        long id = UserConfig.getInstance(account).getClientUserId();
        return id > 0 && ApplicationLoader.applicationContext.getSharedPreferences("bygram_profile_views", Context.MODE_PRIVATE).getBoolean("enabled_" + id, false);
    }

    public static void profileViewsRequest(int account, JSONObject data, ProfileViewsCallback callback) {
        long id = UserConfig.getInstance(account).getClientUserId();
        if (id <= 0) { callback.run(null, ""); return; }
        profileViewsRequest(account, id, data, true, callback);
    }

    public static boolean canRefreshProfileViews(int account) {
        long id = UserConfig.getInstance(account).getClientUserId();
        return id > 0 && (!accountToken(id).isEmpty() || accountSessions().contains("exchange_" + id));
    }

    private static void profileViewsRequest(int account, long id, JSONObject data, boolean canRegister, ProfileViewsCallback callback) {
        try { data.put("user_id", id); } catch (Exception e) { FileLog.e(e); callback.run(null, ""); return; }
        request(id, data, true, response -> {
            if (UserConfig.getInstance(account).getClientUserId() != id) {
                callback.run(null, "");
                return;
            }
            if (canRegister && response != null && response.optBoolean("needs_registration") && "profile_views_set".equals(data.optString("action"))) {
                register(id, (registered, authUrl) -> {
                    if (registered && UserConfig.getInstance(account).getClientUserId() == id) {
                        profileViewsRequest(account, id, data, false, callback);
                    }
                    else callback.run(response, authUrl);
                });
                return;
            }
            if (response != null && response.optBoolean("ok") && response.has("enabled")) {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences("bygram_profile_views", Context.MODE_PRIVATE);
                boolean enabled = response.optBoolean("enabled");
                if (prefs.getBoolean("enabled_" + id, false) != enabled) {
                    prefs.edit().putBoolean("enabled_" + id, enabled).apply();
                    scheduleProfileViewsJob();
                }
            }
            callback.run(response, "");
        });
    }

    public static void scheduleProfileViewsJob() {
        Context context = ApplicationLoader.applicationContext;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        boolean enabled = false;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) enabled |= profileViewsEnabled(account);
        if (!enabled) { scheduler.cancel(280928); return; }
        scheduler.schedule(new JobInfo.Builder(280928, new ComponentName(context, BygramProfileViewsJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60 * 1000L).setPersisted(true).build());
    }

    public static void clearProfileViews(int account) {
        if (profileViewsEnabled(account)) profileViewsRequest(account, json("action", "profile_views_set", "enabled", false), (response, authUrl) -> {});
    }

    public static void pollProfileViews(int account) {
        pollProfileViews(account, null);
    }

    public static void pollProfileViews(int account, Runnable done) {
        if (!profileViewsEnabled(account)) { if (done != null) done.run(); return; }
        if (!androidx.core.app.NotificationManagerCompat.from(ApplicationLoader.applicationContext).areNotificationsEnabled()) {
            if (done != null) done.run();
            return;
        }
        if (!pollingProfileViews.add(account)) { if (done != null) done.run(); return; }
        long ownerId = UserConfig.getInstance(account).getClientUserId();
        JSONObject data = json("action", "profile_views_poll");
        profileViewsRequest(account, data, (response, authUrl) -> {
            org.json.JSONArray views = response == null || !response.optBoolean("ok") ? null : response.optJSONArray("views");
            if (views == null || views.length() == 0) {
                pollingProfileViews.remove(account);
                if (done != null) done.run();
                return;
            }
            java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(views.length());
            Runnable finished = () -> {
                if (remaining.decrementAndGet() == 0) {
                    pollingProfileViews.remove(account);
                    if (done != null) done.run();
                }
            };
            for (int i = 0; i < views.length(); i++) {
                JSONObject value = views.optJSONObject(i);
                if (value == null) { finished.run(); continue; }
                long viewerId = value.optLong("viewer_id");
                String name = value.optString("viewer_name", "");
                String avatarUrl = value.optString("avatar_url", "");
                TLRPC.User user = MessagesController.getInstance(account).getUser(viewerId);
                AvatarDrawable avatar = new AvatarDrawable();
                if (user == null) avatar.setInfo(viewerId, name, ""); else avatar.setInfo(user);
                Bitmap bitmap = null;
                if (user != null && user.photo != null && user.photo.photo_small != null) {
                    bitmap = BitmapFactory.decodeFile(FileLoader.getInstance(account).getPathToAttach(user.photo.photo_small, true).toString());
                }
                if (bitmap != null || !avatarUrl.startsWith("https://byxarek.site/TEST-PROJECT/BYgram/api/avatar.php?")) {
                    if (bitmap == null) bitmap = avatarBitmap(avatar);
                    try { notifyProfileView(account, ownerId, viewerId, name, bitmap); }
                    finally { finished.run(); }
                    continue;
                }
                Bitmap fallback = avatarBitmap(avatar);
                // ponytail: avatar downloads share the global queue; use a bounded pool if large visitor lists become common.
                Utilities.globalQueue.postRunnable(() -> {
                    Bitmap downloaded = null;
                    HttpURLConnection connection = null;
                    try {
                        connection = (HttpURLConnection) new URL(avatarUrl).openConnection();
                        connection.setConnectTimeout(4000); connection.setReadTimeout(4000);
                        if (connection.getResponseCode() == 200) {
                            try (InputStream stream = connection.getInputStream()) { downloaded = BitmapFactory.decodeStream(stream); }
                        }
                    } catch (Exception error) { FileLog.e(error); }
                    finally { if (connection != null) connection.disconnect(); }
                    Bitmap image = downloaded == null ? fallback : downloaded;
                    AndroidUtilities.runOnUIThread(() -> {
                        try { notifyProfileView(account, ownerId, viewerId, name, image); }
                        finally { finished.run(); }
                    });
                });
            }
        });
    }

    private static Bitmap avatarBitmap(AvatarDrawable avatar) {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); avatar.setBounds(0, 0, 96, 96); avatar.draw(canvas);
        return bitmap;
    }

    private static void notifyProfileView(int account, long ownerId, long viewerId, String name, Bitmap bitmap) {
        if (UserConfig.getInstance(account).getClientUserId() != ownerId || !profileViewsEnabled(account)) return;
        Context context = ApplicationLoader.applicationContext;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        String channel = "bygram_profile_views";
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel(channel,
                LocaleController.getString(R.string.BygramProfileViews), NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.setAction(OPEN_PROFILE_VIEWS);
        intent.setData(Uri.parse("bygram://profile-views/" + ownerId));
        intent.putExtra("currentAccount", account);
        intent.putExtra(PROFILE_VIEWS_OWNER, ownerId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, account, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification notification = new androidx.core.app.NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.notification).setLargeIcon(bitmap)
                .setContentTitle(LocaleController.getString(R.string.BygramProfileViews))
                .setContentText(LocaleController.formatString(R.string.BygramProfileViewsNotification, name))
                .setContentIntent(pending).setAutoCancel(true).build();
        try { manager.notify("bygram_profile_views_" + account, (int) (viewerId ^ (viewerId >>> 32)), notification); }
        catch (SecurityException error) { FileLog.e(error); }
    }

    private static SharedPreferences accountSessions() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_account_sessions", Context.MODE_PRIVATE);
    }

    static String accountToken(long userId) {
        return accountSessions().getString("token_" + userId, "");
    }

    private static void register(long userId, Callback callback) {
        request(userId, json("action", "account_login_start", "user_id", userId), false, response -> {
            String url = response == null ? "" : response.optString("auth_url", "");
            String exchange = response == null ? "" : response.optString("exchange_token", "");
            if (!url.startsWith(ENDPOINT + "account-login.php?") || exchange.isEmpty()) {
                callback.run(false, "");
                return;
            }
            accountSessions().edit().putString("exchange_" + userId, exchange).apply();
            int account = -1;
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                if (UserConfig.getInstance(i).getClientUserId() == userId) { account = i; break; }
            }
            if (account < 0 || response.optString("mini_app").isEmpty()) { callback.run(false, url); return; }
            authorizeMiniApp(account, userId, response, exchange, ok -> callback.run(ok, ok ? "" : url));
        });
    }

    private static void authorizeMiniApp(int account, long userId, JSONObject config, String exchange,
                                         Utilities.Callback<Boolean> callback) {
        ConnectionsManager connection = ConnectionsManager.getInstance(account);
        TLRPC.TL_contacts_resolveUsername resolve = new TLRPC.TL_contacts_resolveUsername();
        resolve.username = config.optString("bot");
        connection.sendRequest(resolve, (result, failure) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(result instanceof TLRPC.TL_contacts_resolvedPeer) || UserConfig.getInstance(account).getClientUserId() != userId) {
                callback.run(false);
                return;
            }
            TLRPC.TL_contacts_resolvedPeer peer = (TLRPC.TL_contacts_resolvedPeer) result;
            TLRPC.User bot = null;
            if (peer.peer != null) for (TLRPC.User user : peer.users) {
                if (user.bot && user.id == peer.peer.user_id) { bot = user; break; }
            }
            if (bot == null) { callback.run(false); return; }
            TLRPC.TL_inputBotAppShortName app = new TLRPC.TL_inputBotAppShortName();
            app.bot_id = MessagesController.getInstance(account).getInputUser(bot);
            app.short_name = config.optString("mini_app");
            TLRPC.TL_messages_requestAppWebView request = new TLRPC.TL_messages_requestAppWebView();
            request.app = app;
            request.peer = MessagesController.getInputPeer(bot);
            request.platform = "android";
            request.flags |= 2;
            request.start_param = config.optString("start_param");
            connection.sendRequest(request, (web, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (!(web instanceof TLRPC.TL_webViewResultUrl) || UserConfig.getInstance(account).getClientUserId() != userId) {
                    callback.run(false);
                    return;
                }
                String fragment = Uri.parse(((TLRPC.TL_webViewResultUrl) web).url).getEncodedFragment();
                String init = Uri.parse("https://localhost/?" + (fragment == null ? "" : fragment)).getQueryParameter("tgWebAppData");
                if (init == null) { callback.run(false); return; }
                sendRequest(userId, json("action", "account_login_webapp", "user_id", userId, "exchange_token", exchange,
                        "init_data", init), false, reply -> {
                    String credential = reply == null ? "" : reply.optString("account_token");
                    if (credential.isEmpty() || UserConfig.getInstance(account).getClientUserId() != userId) {
                        callback.run(false);
                        return;
                    }
                    accountSessions().edit().putString("token_" + userId, credential).remove("exchange_" + userId).apply();
                    BygramDeviceReporter.reportAccount(userId);
                    callback.run(true);
                });
            }));
        }));
    }

    static void registerAccount(long userId, Callback callback) {
        register(userId, callback);
    }

    static java.net.URL profileBackgroundUrl(long userId, String revision) throws java.net.MalformedURLException {
        return new java.net.URL(ENDPOINT + "profile-background.php?user_id=" + userId + "&v=" + revision);
    }

    public static void profileBackgroundRequest(long userId, JSONObject data, boolean write, Utilities.Callback<JSONObject> callback) {
        request(userId, data, write, callback::run);
    }

    static void flameRequest(long userId, JSONObject data, Utilities.Callback<JSONObject> callback) {
        BygramDeviceReporter.reportAccount(userId);
        request(userId, data, true, callback::run);
    }

    static void reportStatisticsPreference(long userId, boolean enabled, Utilities.Callback<Boolean> callback) {
        sendRequest(userId, json("action", "statistics_set", "user_id", userId, "enabled", enabled), true,
                response -> callback.run(response != null && response.optBoolean("ok")));
    }

    static void reportDevice(String deviceId, boolean enabled, String model, String version, int androidApi, Utilities.Callback<Boolean> callback) {
        JSONObject data = enabled
                ? json("action", "device_seen", "device_id", deviceId, "model", model, "version", version, "android_api", androidApi)
                : json("action", "device_statistics_disable", "device_id", deviceId);
        request(0, data, false, response -> callback.run(response != null && response.optBoolean("ok")));
    }

    public static void openAccountLogin(Context context, String url) {
        if (context == null || !url.startsWith(ENDPOINT + "account-login.php?")) return;
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));
        } catch (Exception error) {
            FileLog.e(error);
        }
    }

    static JSONObject json(Object... pairs) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]); }
        catch (Exception e) { FileLog.e(e); }
        return result;
    }

    private static void request(long userId, JSONObject data, boolean useAccount, Result callback) {
        String key = "exchange_" + userId;
        String pendingLogin = useAccount ? accountSessions().getString(key, "") : "";
        if (!pendingLogin.isEmpty()) {
            sendRequest(userId, json("action", "account_login_exchange", "user_id", userId, "exchange_token", pendingLogin),
                    false, response -> {
                        if (pendingLogin.equals(accountSessions().getString(key, "")) && response != null) {
                            String credential = response.optString("account_token", "");
                            if (!credential.isEmpty()) {
                                accountSessions().edit().remove(key).putString("token_" + userId, credential).apply();
                                BygramDeviceReporter.reportAccount(userId);
                            } else if (response.optBoolean("expired")) {
                                accountSessions().edit().remove(key).apply();
                            }
                        }
                        sendRequest(userId, data, true, callback);
                    });
            return;
        }
        sendRequest(userId, data, useAccount, callback);
    }

    private static void sendRequest(long userId, JSONObject data, boolean useAccount, Result callback) {
        Utilities.globalQueue.postRunnable(() -> {
            JSONObject result = null;
            HttpURLConnection connection = null;
            String action = data.optString("action");
            boolean settingsRequest = "typing_put".equals(action) || "profile_background_put".equals(action)
                    || "profile_views_set".equals(action) || "profile_views_list".equals(action);
            boolean backgroundRequest = action.startsWith("profile_background_");
            int status = 0;
            try {
                URL url = new URL(ENDPOINT);
                connection = (HttpURLConnection) url.openConnection();
                boolean store = action.startsWith("store_");
                if (store) connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(7000); connection.setReadTimeout(store || backgroundRequest ? 25000 : 7000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] body = data.toString().getBytes(StandardCharsets.UTF_8);
                if (useAccount) {
                    String credential = accountToken(userId);
                    if (!credential.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + credential);
                }
                connection.setDoOutput(true);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                status = connection.getResponseCode();
                InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
                if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    char[] buffer = new char[8192];
                    int count;
                    while ((count = reader.read(buffer)) != -1) {
                        if ((store || backgroundRequest) && response.length() + count > 14_000_000) throw new java.io.IOException("Store response exceeds limit");
                        response.append(buffer, 0, count);
                    }
                    if (response.length() > 0) result = new JSONObject(response.toString());
                }
                if (settingsRequest && (result == null || !result.optBoolean("ok") && !result.optBoolean("needs_registration"))) {
                    FileLog.w("BYgramSettings page=api feature=" + action + " result=failed http_status=" + status);
                }
            } catch (Exception e) {
                if (settingsRequest) {
                    FileLog.w("BYgramSettings page=api feature=" + action + " result=failed http_status=" + status
                            + " error=" + e.getClass().getSimpleName());
                } else {
                    FileLog.e(e);
                }
            } finally { if (connection != null) connection.disconnect(); }
            JSONObject response = result;
            AndroidUtilities.runOnUIThread(() -> callback.run(response));
        });
    }
}
