package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.LongSparseArray;
import android.util.SparseArray;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;

public final class BygramFlame {
    private static final ZoneId DAY_ZONE = ZoneId.of("Europe/Moscow");
    private static final long POLL_MS = 60_000;
    private static final LongSparseArray<DialogFlames> dialogFlames = new LongSparseArray<>();

    public static final class Badge {
        public final int days;
        public final boolean active;

        public Badge(int days, boolean active) {
            this.days = days;
            this.active = active;
        }

        public boolean matches(Badge other) {
            return other != null && days == other.days && active == other.active;
        }
    }

    private static final class DialogFlames {
        final JSONObject peers;
        final int day;
        final long midnight;

        DialogFlames(JSONObject peers, JSONObject response) {
            this.peers = peers;
            day = response.optInt("day");
            midnight = nextMidnight(response);
        }
    }

    public static Badge getDialogBadge(int account, long peerId) {
        DialogFlames snapshot = dialogFlames.get(UserConfig.getInstance(account).getClientUserId());
        if (snapshot == null) return null;
        JSONArray flame = snapshot.peers.optJSONArray(Long.toString(peerId));
        if (flame == null) return null;
        int today = currentDay(snapshot.day, snapshot.midnight);
        int last = flame.optInt(1);
        return last >= today - 1 ? new Badge(flame.optInt(0), last == today) : null;
    }

    public static void loadDialogBadges(int account, Runnable onChanged) {
        long userId = UserConfig.getInstance(account).getClientUserId();
        if (userId <= 0) return;
        BygramAccountService.flameRequest(userId, BygramAccountService.json("action", "flame_list", "user_id", userId), response -> {
            if (response == null || UserConfig.getInstance(account).getClientUserId() != userId) return;
            JSONObject peers = response.optJSONObject("flames");
            if (response.optBoolean("ok") && peers != null) {
                dialogFlames.put(userId, new DialogFlames(peers, response));
            } else if (response.optBoolean("needs_registration")) {
                dialogFlames.remove(userId);
            } else {
                return;
            }
            onChanged.run();
        });
    }

    public static boolean supportsPeer(TLRPC.User user) {
        return user != null && user.id > 0 && !user.self && !user.bot && !UserObject.isDeleted(user)
                && !UserObject.isAnonymous(user) && !UserObject.isReplyUser(user) && !MessagesController.isSupportUser(user);
    }

    public static boolean supportsGroup(TLRPC.Chat chat) {
        return chat != null && !ChatObject.isNotInChat(chat) && (!ChatObject.isChannel(chat) || chat.megagroup);
    }

    public JSONObject restoreEvent() {
        return state == null ? null : state.optJSONObject("restore_event");
    }

    public interface Listener {
        void onFlameChanged(int days, int activeDay, int restorableDays, int remaining, boolean needsConfirmation);
    }

    private final int account;
    private final long userId;
    private final long peerId;
    private final Listener listener;
    private final Runnable poll = this::refresh;
    private JSONObject state;
    private long midnight;
    private boolean active;
    private boolean busy;
    private boolean needsHistory;
    private boolean needsConfirmation;
    private boolean attemptedLogin;

    public BygramFlame(int account, long peerId, Listener listener) {
        this.account = account;
        this.userId = UserConfig.getInstance(account).getClientUserId();
        this.peerId = peerId;
        this.listener = listener;
    }

    public void resume() {
        active = true;
        needsHistory = true;
        attemptedLogin = false;
        refresh();
    }

    public void pause() {
        active = false;
        AndroidUtilities.cancelRunOnUIThread(poll);
    }

    public void messagesChanged() {
        if (!active || (state != null && state.optInt("last_day") == day())) return;
        needsHistory = true;
        if (!busy) schedule(1500);
    }

    private int day() {
        return currentDay(state.optInt("day"), midnight);
    }

    private static int currentDay(int day, long midnight) {
        long elapsed = SystemClock.elapsedRealtime() - midnight;
        return elapsed < 0 ? day : day + 1 + (int) (elapsed / 86_400_000);
    }

    private static long nextMidnight(JSONObject response) {
        return SystemClock.elapsedRealtime() + response.optLong("seconds_to_midnight") * 1000;
    }

    public void render() {
        if (needsConfirmation) {
            listener.onFlameChanged(0, 0, 0, 0, true);
            return;
        }
        if (state == null) return;
        int today = day();
        int last = state.optInt("last_day");
        int days = state.optInt("days");
        int lost = days > 0 && last < today - 1 ? days : state.optInt("lost_days");
        listener.onFlameChanged(last >= today - 1 ? days : 0, days > 0 && last == today ? today : 0, lost, state.optInt("remaining"), false);
    }

    public boolean takeMilestone(int days, int activeDay) {
        if (activeDay == 0 || !(days == 1 || days == 5 || days >= 10 && days % 10 == 0)) return false;
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("bygram_flame", Context.MODE_PRIVATE);
        String key = userId + "_" + peerId;
        long milestone = ((long) activeDay << 32) | days;
        if (preferences.getLong(key, 0) >= milestone) return false;
        preferences.edit().putLong(key, milestone).apply();
        return true;
    }

    private void refresh() {
        if (!active || busy) return;
        render();
        busy = true;
        request("flame_get", response -> {
            if (response != null && response.optBoolean("needs_registration") && active && !attemptedLogin
                    && UserConfig.getInstance(account).getClientUserId() == userId) {
                attemptedLogin = true;
                BygramAccountService.registerAccount(userId, (registered, url) -> {
                    busy = false;
                    if (registered) refresh();
                    else accept(response);
                });
                return;
            }
            if (accept(response) && active && needsHistory
                    && state.optInt("last_day") < state.optInt("day")) {
                needsHistory = false;
                new History(state.optInt("day"), state.optInt("last_day"), state.optInt("revision")).load(0);
            } else {
                finish();
            }
        });
    }

    private boolean accept(JSONObject response) {
        if (response == null || UserConfig.getInstance(account).getClientUserId() != userId) return false;
        if (response.optBoolean("needs_registration")) {
            state = null;
            needsConfirmation = true;
            if (active) render();
            return false;
        }
        if (!response.optBoolean("ok")) return false;
        needsConfirmation = false;
        if (state == null || state.optInt("day") != response.optInt("day")) needsHistory = true;
        state = response;
        midnight = nextMidnight(response);
        if (active) render();
        return true;
    }

    private void finish() {
        busy = false;
        if (!active || needsConfirmation) return;
        long untilMidnight = midnight - SystemClock.elapsedRealtime();
        schedule(untilMidnight > 0 ? Math.min(POLL_MS, untilMidnight) : POLL_MS);
    }

    private void schedule(long delay) {
        AndroidUtilities.cancelRunOnUIThread(poll);
        AndroidUtilities.runOnUIThread(poll, delay);
    }

    public void restore(Utilities.Callback<Boolean> callback) {
        if (!active || busy || state == null) {
            callback.run(false);
            return;
        }
        int today = day();
        int last = state.optInt("last_day");
        int lost = state.optInt("days") > 0 && last < today - 1 ? last : state.optInt("lost_day");
        if (lost <= 0) {
            callback.run(false);
            return;
        }
        busy = true;
        request("flame_restore", response -> {
            boolean restored = accept(response) && response.optBoolean("restored");
            finish();
            if (active) callback.run(restored);
        }, "day", today, "lost_day", lost);
    }

    public void requestAccountLogin(Utilities.Callback<String> callback) {
        BygramAccountService.registerAccount(userId, (registered, url) -> {
            if (active && UserConfig.getInstance(account).getClientUserId() == userId) {
                if (registered) refresh();
                else callback.run(url);
            }
        });
    }

    private void request(String action, Utilities.Callback<JSONObject> callback, Object... fields) {
        JSONObject data = new JSONObject();
        try {
            data.put("action", action);
            data.put("user_id", userId);
            data.put("peer_id", peerId);
            if (peerId < 0) {
                data.put("supergroup", ChatObject.isChannel(MessagesController.getInstance(account).getChat(-peerId)));
            }
            for (int i = 0; i < fields.length; i += 2) data.put((String) fields[i], fields[i + 1]);
        } catch (JSONException error) {
            FileLog.e(error);
            callback.run(null);
            return;
        }
        BygramAccountService.flameRequest(userId, data, callback);
    }

    private static int dayOf(int timestamp) {
        return (int) Instant.ofEpochSecond(timestamp).atZone(DAY_ZONE).toLocalDate().toEpochDay();
    }

    static boolean hasSameDayReaction(TLRPC.Message message, long recipientId, int messageDay) {
        if (message.reactions == null || message.reactions.recent_reactions == null) return false;
        for (TLRPC.MessagePeerReaction reaction : message.reactions.recent_reactions) {
            if (reaction.peer_id != null && reaction.peer_id.user_id == recipientId
                    && reaction.date >= message.date && dayOf(reaction.date) == messageDay) {
                return true;
            }
        }
        return false;
    }

    private final class History {
        private final int today;
        private final int checkpoint;
        private final int revision;
        private final ArrayList<int[]> completed = new ArrayList<>(2);
        private int cursorDay;
        private int directions;
        private long firstGroupSender;

        private History(int today, int checkpoint, int revision) {
            this.today = this.cursorDay = today;
            this.checkpoint = checkpoint;
            this.revision = revision;
        }

        private void load(int offsetId) {
            if (!active || UserConfig.getInstance(account).getClientUserId() != userId) {
                finish();
                return;
            }
            TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
            request.peer = MessagesController.getInstance(account).getInputPeer(peerId);
            request.limit = 100;
            if (offsetId != 0) {
                request.offset_id = offsetId;
            } else if (cursorDay != today) {
                request.offset_date = (int) LocalDate.ofEpochDay(cursorDay + 1L).atStartOfDay(DAY_ZONE).toEpochSecond();
            }
            ConnectionsManager.getInstance(account).sendRequest(request, (result, error) ->
                    AndroidUtilities.runOnUIThread(() -> onPage(result instanceof TLRPC.messages_Messages
                            ? (TLRPC.messages_Messages) result : null, offsetId, false)));
        }

        private void onPage(TLRPC.messages_Messages page, int offsetId, boolean fetchedReactions) {
            if (!active || state == null || day() != today || UserConfig.getInstance(account).getClientUserId() != userId) {
                needsHistory = true;
                finish();
                return;
            }
            if (page == null) {
                needsHistory = true;
                finish();
                return;
            }
            if (peerId > 0 && !fetchedReactions && fetchPartialReactions(page, offsetId)) return;
            int oldestId = 0;
            for (TLRPC.Message message : page.messages) {
                if (message.id <= 0) continue;
                oldestId = oldestId == 0 ? message.id : Math.min(oldestId, message.id);
                if (message.date <= 0) continue;
                int messageDay = dayOf(message.date);
                if (messageDay > cursorDay) continue;
                while (cursorDay > messageDay) {
                    if (!completeDay()) {
                        publish();
                        return;
                    }
                }
                if (!(message instanceof TLRPC.TL_message)) continue;
                if (peerId > 0) {
                    if (hasSameDayReaction(message, message.out ? peerId : userId, messageDay)) {
                        directions = 3;
                    } else {
                        directions |= message.out ? 1 : 2;
                    }
                } else if (message.from_id != null && message.from_id.user_id > 0) {
                    long sender = message.from_id.user_id;
                    if (firstGroupSender == 0) {
                        firstGroupSender = sender;
                        directions = 1;
                    } else if (firstGroupSender != sender) {
                        directions = 3;
                    }
                }
            }
            if (oldestId == 0 || offsetId != 0 && oldestId >= offsetId) {
                completeDay();
                publish();
            } else if (directions == 3) {
                if (completeDay()) load(0);
                else publish();
            } else {
                load(oldestId);
            }
        }

        private boolean fetchPartialReactions(TLRPC.messages_Messages page, int offsetId) {
            TLRPC.TL_messages_getMessagesReactions request = new TLRPC.TL_messages_getMessagesReactions();
            SparseArray<TLRPC.Message> candidates = new SparseArray<>();
            for (TLRPC.Message message : page.messages) {
                if (!(message instanceof TLRPC.TL_message) || message.id <= 0 || message.date <= 0
                        || message.reactions == null || message.reactions.results.isEmpty()
                        || !message.reactions.min && !message.reactions.recent_reactions.isEmpty()
                        || dayOf(message.date) <= checkpoint) {
                    continue;
                }
                request.id.add(message.id);
                candidates.put(message.id, message);
            }
            if (request.id.isEmpty()) return false;
            request.peer = MessagesController.getInstance(account).getInputPeer(peerId);
            ConnectionsManager.getInstance(account).sendRequest(request, (result, error) ->
                    AndroidUtilities.runOnUIThread(() -> {
                        if (result instanceof TLRPC.Updates updates) {
                            for (TLRPC.Update update : updates.updates) {
                                if (update instanceof TL_update.TL_updateMessageReactions reactionUpdate) {
                                    TLRPC.Message message = candidates.get(reactionUpdate.msg_id);
                                    if (message != null && !reactionUpdate.reactions.recent_reactions.isEmpty()) {
                                        message.reactions = reactionUpdate.reactions;
                                    }
                                }
                            }
                        }
                        onPage(page, offsetId, true);
                    }));
            return true;
        }

        private boolean completeDay() {
            boolean mutual = directions == 3;
            if (mutual) {
                int[] last = completed.isEmpty() ? null : completed.get(completed.size() - 1);
                if (last != null && last[0] == cursorDay + 1) last[0] = cursorDay;
                else completed.add(new int[] {cursorDay, cursorDay});
            }
            directions = 0;
            firstGroupSender = 0;
            return (mutual || cursorDay >= today - 1) && --cursorDay > checkpoint;
        }

        private JSONArray ranges() {
            JSONArray ranges = new JSONArray();
            for (int i = completed.size() - 1; i >= 0; i--) {
                int[] range = completed.get(i);
                ranges.put(new JSONArray().put(range[0]).put(range[1]));
            }
            return ranges;
        }

        private void publish() {
            if (completed.isEmpty()) {
                finish();
                return;
            }
            request("flame_sync", response -> {
                if (!accept(response) || state.optInt("day") != today || state.optInt("last_day") < completed.get(0)[1]) needsHistory = true;
                finish();
            }, "day", today, "revision", revision, "ranges", ranges());
        }
    }
}
