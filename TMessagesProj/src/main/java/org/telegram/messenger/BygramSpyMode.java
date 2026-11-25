package org.telegram.messenger;

import android.content.SharedPreferences;
import android.util.Base64;


import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class BygramSpyMode {
    private static final int DATA_RETENTION_SECONDS = 90 * 24 * 60 * 60;
    public static final String SPY_SAVE_DELETED = "bygram_spy_save_deleted";
    public static final String SPY_SAVE_OWN_DELETED = "bygram_spy_save_own_deleted";
    public static final String SPY_NOTIFY_DELETED = "bygram_spy_notify_deleted";
    public static final String SPY_SAVE_EDITS = "bygram_spy_save_edits";
    public static final String SPY_SAVE_READ_DATE = "bygram_spy_save_read_date";
    public static final String SPY_SAVE_LAST_ONLINE = "bygram_spy_save_last_online";

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

    private static String historyKey(int account, long dialogId, int messageId) {
        return "bygram_edit_" + account + '_' + dialogId + '_' + messageId;
    }

    public static void saveEditHistory(int account, long dialogId, int messageId, int date, TLRPC.Message message) {
        if (!getBoolean(SPY_SAVE_EDITS) || message == null) {
            return;
        }
        String key = historyKey(account, dialogId, messageId);
        try {
            JSONArray history = new JSONArray(preferences().getString(key, "[]"));
            String text = message.message == null ? "" : message.message;
            String serializedEntities = serializeEntities(message.entities);
            String serializedMedia = serializeMedia(message.media);
            if (history.length() > 0 && text.equals(history.getJSONObject(history.length() - 1).optString("text"))
                    && serializedEntities.equals(history.getJSONObject(history.length() - 1).optString("entities"))
                    && serializedMedia.equals(history.getJSONObject(history.length() - 1).optString("media"))) {
                return;
            }
            JSONObject entry = new JSONObject();
            entry.put("date", date);
            entry.put("text", text);
            if (!serializedEntities.isEmpty()) entry.put("entities", serializedEntities);
            if (!serializedMedia.isEmpty()) entry.put("media", serializedMedia);
            entry.put("attachPath", message.attachPath);
            history.put(entry);
            // shortcut: SharedPreferences stores at most 20 snapshots; move to SQLite for larger histories.
            while (history.length() > 20) {
                history.remove(0);
            }
            preferences().edit().putString(key, history.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean hasEditHistory(int account, long dialogId, int messageId) {
        return preferences().contains(historyKey(account, dialogId, messageId));
    }

    public static ArrayList<MessageObject> getEditHistory(int account, TLRPC.Message source) {
        ArrayList<MessageObject> result = new ArrayList<>();
        try {
            JSONArray history = new JSONArray(preferences().getString(historyKey(account, MessageObject.getDialogId(source), source.id), "[]"));
            for (int i = history.length() - 1; i >= 0; i--) {
                try {
                    JSONObject entry = history.getJSONObject(i);
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    // Local preview IDs keep playback of different revisions separate from the live message.
                    message.id = Integer.MIN_VALUE + i;
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
        SerializedData data = new SerializedData(media.getObjectSize());
        try {
            media.serializeToStream(data);
            return Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP);
        } finally {
            data.cleanup();
        }
    }

    public static boolean sameMedia(TLRPC.MessageMedia left, TLRPC.MessageMedia right) {
        return serializeMedia(left).equals(serializeMedia(right));
    }

    public static int getReadDate(int account, long dialogId, int messageId) {
        if (!getBoolean(SPY_SAVE_READ_DATE)) return 0;
        try {
            JSONArray ranges = new JSONArray(preferences().getString("bygram_read_ranges_" + account + '_' + dialogId, "[]"));
            for (int i = ranges.length() - 1; i >= 0; i--) {
                JSONObject range = ranges.getJSONObject(i);
                if (messageId >= range.optInt("start") && messageId <= range.optInt("end")) return range.optInt("date");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public static void rememberLastOnline(int account, long userId, int date) {
        if (getBoolean(SPY_SAVE_LAST_ONLINE) && userId > 0 && date > 0) {
            preferences().edit().putInt("bygram_online_" + account + '_' + userId, date).apply();
        }
    }

    public static int getLastOnline(int account, long userId) {
        int date = getBoolean(SPY_SAVE_LAST_ONLINE) ? preferences().getInt("bygram_online_" + account + '_' + userId, 0) : 0;
        return date > ConnectionsManager.getInstance(account).getCurrentTime() - DATA_RETENTION_SECONDS ? date : 0;
    }

    public static void clearSpyData() {
        SharedPreferences.Editor editor = preferences().edit();
        for (String key : preferences().getAll().keySet()) {
            if (key.startsWith("bygram_edit_") || key.startsWith("bygram_read_") || key.startsWith("bygram_online_")) editor.remove(key);
        }
        editor.apply();
    }

    public static void pruneSpyData() {
        int cutoff = (int) (System.currentTimeMillis() / 1000) - DATA_RETENTION_SECONDS;
        SharedPreferences.Editor editor = preferences().edit();
        for (java.util.Map.Entry<String, ?> item : preferences().getAll().entrySet()) {
            String key = item.getKey();
            try {
                if (key.startsWith("bygram_online_") && item.getValue() instanceof Integer && (Integer) item.getValue() < cutoff) {
                    editor.remove(key);
                } else if (key.startsWith("bygram_edit_") && item.getValue() instanceof String) {
                    JSONArray history = new JSONArray((String) item.getValue());
                    for (int i = history.length() - 1; i >= 0; i--) {
                        if (history.getJSONObject(i).optInt("date") < cutoff) history.remove(i);
                    }
                    if (history.length() == 0) editor.remove(key); else editor.putString(key, history.toString());
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
