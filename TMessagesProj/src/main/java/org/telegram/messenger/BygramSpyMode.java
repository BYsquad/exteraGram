package org.telegram.messenger;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;


import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class BygramSpyMode {
    private static final int DATA_RETENTION_SECONDS = 90 * 24 * 60 * 60;
    public static final String SPY_SAVE_DELETED = "bygram_spy_save_deleted";
    public static final String SPY_SAVE_OWN_DELETED = "bygram_spy_save_own_deleted";
    public static final String SPY_NOTIFY_DELETED = "bygram_spy_notify_deleted";
    public static final String SPY_SAVE_EDITS = "bygram_spy_save_edits";
    public static final String SPY_SAVE_READ_DATE = "bygram_spy_save_read_date";
    public static final String SPY_SAVE_LAST_ONLINE = "bygram_spy_save_last_online";
    public static final String SPY_EXCLUDED_CHATS = "bygram_spy_excluded_";
    private static final String EDIT_HISTORY_PREFIX = "bygram_edit_";
    private static SQLiteOpenHelper historyOpenHelper;

    private BygramSpyMode() {}

    static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean getBoolean(String key) {
        return preferences().getBoolean(key, false);
    }

    public static void setBoolean(String key, boolean enabled) {
        preferences().edit().putBoolean(key, enabled).apply();
    }

    public static boolean isHistoryAllowed(int account, long dialogId) {
        return !excludedChats(account).contains(Long.toString(dialogId));
    }

    public static Set<String> excludedChats(int account) {
        return preferences().getStringSet(exclusionsKey(account), Collections.emptySet());
    }

    public static void setExcludedChats(int account, Collection<Long> dialogIds) {
        Set<String> ids = new HashSet<>();
        for (long dialogId : dialogIds) ids.add(Long.toString(dialogId));
        preferences().edit().putStringSet(exclusionsKey(account), ids).apply();
    }

    private static String exclusionsKey(int account) {
        return SPY_EXCLUDED_CHATS + UserConfig.getInstance(account).getClientUserId();
    }

    private static String historyKey(int account, long dialogId, int messageId) {
        return EDIT_HISTORY_PREFIX + account + '_' + dialogId + '_' + messageId;
    }

    private static SQLiteDatabase editDatabase() {
        if (historyOpenHelper == null) {
            historyOpenHelper = new SQLiteOpenHelper(ApplicationLoader.applicationContext, "bygram_edit_history.db", null, 1) {
                @Override
                public void onCreate(SQLiteDatabase db) {
                    db.execSQL("CREATE TABLE revisions (id INTEGER PRIMARY KEY, history_key TEXT NOT NULL, date INTEGER NOT NULL, payload TEXT NOT NULL)");
                    db.execSQL("CREATE INDEX revisions_key ON revisions(history_key, id)");
                    db.execSQL("CREATE INDEX revisions_date ON revisions(date)");
                    for (var item : preferences().getAll().entrySet()) {
                        if (!item.getKey().startsWith(EDIT_HISTORY_PREFIX) || !(item.getValue() instanceof String raw)) continue;
                        try {
                            JSONArray entries = new JSONArray(raw);
                            for (int i = 0; i < entries.length(); i++) {
                                JSONObject entry = entries.optJSONObject(i);
                                if (entry != null) insertRevision(db, item.getKey(), entry);
                            }
                        } catch (org.json.JSONException invalidHistory) {
                            FileLog.e(invalidHistory);
                        }
                    }
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int from, int to) {
                }
            };
        }
        SQLiteDatabase db = historyOpenHelper.getWritableDatabase();
        if (!preferences().getBoolean("bygram_edit_sqlite_migrated", false)) {
            SharedPreferences.Editor editor = preferences().edit();
            for (String key : preferences().getAll().keySet()) {
                if (key.startsWith(EDIT_HISTORY_PREFIX)) editor.remove(key);
            }
            editor.putBoolean("bygram_edit_sqlite_migrated", true).apply();
        }
        return db;
    }

    private static void insertRevision(SQLiteDatabase db, String key, JSONObject entry) {
        ContentValues values = new ContentValues();
        values.put("history_key", key);
        values.put("date", entry.optInt("date"));
        values.put("payload", entry.toString());
        db.insertOrThrow("revisions", null, values);
    }

    public static synchronized void saveEditHistory(int account, long dialogId, int messageId, int date, TLRPC.Message message) {
        if (!getBoolean(SPY_SAVE_EDITS) || !isHistoryAllowed(account, dialogId) || message == null) {
            return;
        }
        String key = historyKey(account, dialogId, messageId);
        try {
            SQLiteDatabase db = editDatabase();
            String text = message.message == null ? "" : message.message;
            String serializedEntities = serializeEntities(message.entities);
            String serializedMedia = serializeMedia(message.media);
            String serializedRichMessage = serializeObject(message.rich_message);
            try (Cursor cursor = db.rawQuery("SELECT payload FROM revisions WHERE history_key = ? ORDER BY id DESC LIMIT 1", new String[]{key})) {
                if (cursor.moveToFirst()) {
                    JSONObject previous = new JSONObject(cursor.getString(0));
                    if (text.equals(previous.optString("text")) && serializedEntities.equals(previous.optString("entities"))
                            && serializedMedia.equals(previous.optString("media")) && serializedRichMessage.equals(previous.optString("richMessage"))) return;
                }
            }
            JSONObject entry = new JSONObject();
            entry.put("date", date);
            entry.put("text", text);
            if (!serializedEntities.isEmpty()) entry.put("entities", serializedEntities);
            if (!serializedMedia.isEmpty()) entry.put("media", serializedMedia);
            if (!serializedRichMessage.isEmpty()) entry.put("richMessage", serializedRichMessage);
            entry.put("attachPath", message.attachPath);
            insertRevision(db, key, entry);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static synchronized boolean hasEditHistory(int account, long dialogId, int messageId) {
        try (Cursor cursor = editDatabase().rawQuery("SELECT 1 FROM revisions WHERE history_key = ? AND date > ? LIMIT 1",
                new String[]{historyKey(account, dialogId, messageId), Integer.toString(retentionCutoff())})) {
            return cursor.moveToFirst();
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static synchronized ArrayList<MessageObject> getEditHistory(int account, TLRPC.Message source) {
        ArrayList<MessageObject> result = new ArrayList<>();
        try (Cursor cursor = editDatabase().rawQuery("SELECT payload FROM revisions WHERE history_key = ? AND date > ? ORDER BY id DESC",
                new String[]{historyKey(account, MessageObject.getDialogId(source), source.id), Integer.toString(retentionCutoff())})) {
            while (cursor.moveToNext()) {
                try {
                    JSONObject entry = new JSONObject(cursor.getString(0));
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    // Local preview IDs keep playback of different revisions separate from the live message.
                    message.id = Integer.MIN_VALUE + cursor.getPosition();
                    message.dialog_id = source.dialog_id;
                    message.peer_id = source.peer_id;
                    message.from_id = source.from_id;
                    message.out = source.out;
                    message.noforwards = source.noforwards;
                    message.date = entry.optInt("date");
                    message.message = entry.optString("text");
                    message.attachPath = entry.optString("attachPath");
                    String entities = entry.optString("entities");
                    if (!entities.isEmpty()) {
                        SerializedData data = new SerializedData(Base64.decode(entities, Base64.NO_WRAP));
                        try {
                            int count = data.readInt32(true);
                            if (count < 0 || count > data.remaining() / 4) throw new IllegalArgumentException("Invalid history entities");
                            for (int j = 0; j < count; j++) {
                                message.entities.add(TLRPC.MessageEntity.TLdeserialize(data, data.readInt32(true), true));
                            }
                        } finally {
                            data.cleanup();
                        }
                    }
                    String media = entry.optString("media");
                    if (!media.isEmpty()) {
                        SerializedData data = new SerializedData(Base64.decode(media, Base64.NO_WRAP));
                        try {
                            message.media = TLRPC.MessageMedia.TLdeserialize(data, data.readInt32(true), true);
                        } finally {
                            data.cleanup();
                        }
                    }
                    String richMessage = entry.optString("richMessage");
                    if (!richMessage.isEmpty()) {
                        SerializedData data = new SerializedData(Base64.decode(richMessage, Base64.NO_WRAP));
                        try {
                            message.rich_message = TL_iv.RichMessage.TLdeserialize(data, data.readInt32(true), true);
                        } finally {
                            data.cleanup();
                        }
                    }
                    message.flags = (message.from_id != null ? 256 : 0)
                            | (!message.entities.isEmpty() ? 128 : 0) | (message.media != null ? 512 : 0);
                    result.add(new MessageObject(account, message, true, true));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static void rememberReadDate(int account, long dialogId, int previousMaxMessageId, int maxMessageId, int date) {
        if (!getBoolean(SPY_SAVE_READ_DATE) || maxMessageId <= 0 || date <= 0) return;
        String key = "bygram_read_ranges_" + account + '_' + dialogId;
        try {
            JSONArray ranges = new JSONArray(preferences().getString(key, "[]"));
            String baselineKey = "bygram_read_baseline_" + account + '_' + dialogId;
            int previousMax = ranges.length() == 0 ? Math.max(0, previousMaxMessageId) : ranges.getJSONObject(ranges.length() - 1).optInt("end");
            if (maxMessageId <= previousMax) return;
            ranges.put(new JSONObject().put("start", previousMax + 1).put("end", maxMessageId).put("date", date));
            int cutoff = date - DATA_RETENTION_SECONDS;
            while (ranges.length() > 0 && (ranges.getJSONObject(0).optInt("date") < cutoff || ranges.length() > 256)) ranges.remove(0);
            preferences().edit().putString(key, ranges.toString()).putInt(baselineKey, maxMessageId).putInt(baselineKey + "_time", date).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String serializeEntities(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) return "";
        int size = 4;
        for (TLRPC.MessageEntity entity : entities) size += entity.getObjectSize();
        SerializedData data = new SerializedData(size);
        data.writeInt32(entities.size());
        for (TLRPC.MessageEntity entity : entities) entity.serializeToStream(data);
        String result = Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        data.cleanup();
        return result;
    }

    public static boolean sameEntities(ArrayList<TLRPC.MessageEntity> left, ArrayList<TLRPC.MessageEntity> right) {
        return serializeEntities(left).equals(serializeEntities(right));
    }

    private static String serializeMedia(TLRPC.MessageMedia media) {
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty) return "";
        return serializeObject(media);
    }

    private static String serializeObject(TLObject object) {
        if (object == null) return "";
        SerializedData data = new SerializedData(object.getObjectSize());
        try {
            object.serializeToStream(data);
            return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        } finally {
            data.cleanup();
        }
    }

    public static boolean sameMedia(TLRPC.MessageMedia left, TLRPC.MessageMedia right) {
        return serializeMedia(left).equals(serializeMedia(right));
    }

    public static boolean sameRichMessage(TL_iv.RichMessage left, TL_iv.RichMessage right) {
        return serializeObject(left).equals(serializeObject(right));
    }

    public static int getReadDate(int account, long dialogId, int messageId) {
        if (!getBoolean(SPY_SAVE_READ_DATE)) return 0;
        try {
            JSONArray ranges = new JSONArray(preferences().getString("bygram_read_ranges_" + account + '_' + dialogId, "[]"));
            for (int i = ranges.length() - 1; i >= 0; i--) {
                JSONObject range = ranges.getJSONObject(i);
                if (messageId >= range.optInt("start") && messageId <= range.optInt("end")) {
                    int date = range.optInt("date");
                    return date > ConnectionsManager.getInstance(account).getCurrentTime() - DATA_RETENTION_SECONDS ? date : 0;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public static synchronized void rememberLastOnline(int account, long userId, int date) {
        if (!getBoolean(SPY_SAVE_LAST_ONLINE) || userId <= 0 || userId == UserConfig.getInstance(account).getClientUserId() || date <= 0) return;
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        if (date > now || date <= now - DATA_RETENTION_SECONDS) return;
        String key = "bygram_online_" + account + '_' + userId;
        int previous = preferences().getInt(key, 0);
        if (previous > now || date > previous) {
            preferences().edit().putInt(key, date).apply();
        }
    }

    public static int getLastOnline(int account, long userId) {
        int date = getBoolean(SPY_SAVE_LAST_ONLINE) ? preferences().getInt("bygram_online_" + account + '_' + userId, 0) : 0;
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        return date <= now && date > now - DATA_RETENTION_SECONDS ? date : 0;
    }

    public static synchronized void clearSpyData() {
        try {
            editDatabase().delete("revisions", null, null);
        } catch (Exception e) {
            FileLog.e(e);
        }
        SharedPreferences.Editor editor = preferences().edit();
        for (String key : preferences().getAll().keySet()) {
            if (key.startsWith("bygram_read_") || key.startsWith("bygram_online_")) editor.remove(key);
        }
        editor.apply();
    }

    private static int retentionCutoff() {
        return (int) (System.currentTimeMillis() / 1000) - DATA_RETENTION_SECONDS;
    }

    public static synchronized void pruneSpyData() {
        int cutoff = retentionCutoff();
        try {
            editDatabase().delete("revisions", "date <= ?", new String[]{Integer.toString(cutoff)});
        } catch (Exception e) {
            FileLog.e(e);
        }
        SharedPreferences.Editor editor = preferences().edit();
        for (java.util.Map.Entry<String, ?> item : preferences().getAll().entrySet()) {
            String key = item.getKey();
            try {
                if (key.startsWith("bygram_online_") && item.getValue() instanceof Integer && (Integer) item.getValue() < cutoff) {
                    editor.remove(key);
                } else if (key.startsWith("bygram_read_ranges_") && item.getValue() instanceof String) {
                    JSONArray ranges = new JSONArray((String) item.getValue());
                    while (ranges.length() > 0 && ranges.getJSONObject(0).optInt("date") < cutoff) ranges.remove(0);
                    if (ranges.length() == 0) editor.remove(key); else editor.putString(key, ranges.toString());
                } else if (key.startsWith("bygram_read_baseline_") && key.endsWith("_time") && item.getValue() instanceof Integer && (Integer) item.getValue() < cutoff) {
                    editor.remove(key).remove(key.substring(0, key.length() - 5));
                }
            } catch (Exception e) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

}
