package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import java.util.HashMap;
import java.util.Map;

/** One visible private chat; Telegram owns the incoming action lifetime. */
public final class BygramGroupActivity {
    private final int account;
    private final long userId;
    private final int guid;
    private final Runnable changed;
    private final Map<Long, String> groups = new HashMap<>();
    private final Map<Long, TLRPC.Chat> polling = new HashMap<>();
    private final long startedAt = android.os.SystemClock.elapsedRealtime();
    private boolean stopped;
    private boolean loaded;
    private String text = "";
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (stopped) return;
            Map<Long, String> currentGroups = new HashMap<>(groups);
            Utilities.stageQueue.postRunnable(() -> {
                String value = findActivity(currentGroups, MessagesController.getInstance(account).printingUsers, userId, System.currentTimeMillis());
                AndroidUtilities.runOnUIThread(() -> {
                    if (stopped) return;
                    if (!text.equals(value)) { text = value; changed.run(); }
                    AndroidUtilities.runOnUIThread(this, 500);
                });
            });
        }
    };

    public BygramGroupActivity(int account, long userId, int guid, Runnable changed) {
        this.account = account;
        this.userId = userId;
        this.guid = guid;
        this.changed = changed;
        loadGroups(0);
        poll.run();
    }

    public String text() { return text; }
    public boolean loaded() { return loaded || android.os.SystemClock.elapsedRealtime() - startedAt >= 10_000; }

    public void stop() {
        stopped = true;
        AndroidUtilities.cancelRunOnUIThread(poll);
        for (TLRPC.Chat chat : polling.values()) MessagesController.getInstance(account).startShortPoll(chat, guid, true);
        polling.clear();
    }

    private void loadGroups(long maxId) {
        TLRPC.TL_messages_getCommonChats request = new TLRPC.TL_messages_getCommonChats();
        MessagesController controller = MessagesController.getInstance(account);
        request.user_id = controller.getInputUser(userId);
        if (request.user_id instanceof TLRPC.TL_inputUserEmpty) { loaded = true; return; }
        request.max_id = maxId;
        request.limit = 100;
        int requestId = org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(request, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (stopped) return;
                    loaded = true;
                    if (!(response instanceof TLRPC.messages_Chats)) return;
                    TLRPC.messages_Chats chats = (TLRPC.messages_Chats) response;
                    controller.putChats(chats.chats, false);
                    for (TLRPC.Chat chat : chats.chats) {
                        if (!ChatObject.isNotInChat(chat) && (!ChatObject.isChannel(chat) || chat.megagroup)) {
                            groups.put(chat.id, chat.title);
                            if (ChatObject.isChannel(chat) && !polling.containsKey(chat.id)) {
                                polling.put(chat.id, chat);
                                controller.startShortPoll(chat, guid, false);
                            }
                        }
                    }
                    if (chats.chats.size() == request.limit) {
                        long next = chats.chats.get(chats.chats.size() - 1).id;
                        if (next != maxId) loadGroups(next);
                    }
                }));
        org.telegram.tgnet.ConnectionsManager.getInstance(account).bindRequestToGuid(requestId, guid);
    }

    static String findActivity(Map<Long, String> groups,
            Map<Long, java.util.concurrent.ConcurrentHashMap<Integer, java.util.ArrayList<MessagesController.PrintingUser>>> printingUsers,
            long userId, long now) {
        String result = "";
        long newest = 0;
        for (Map.Entry<Long, String> group : groups.entrySet()) {
            java.util.concurrent.ConcurrentHashMap<Integer, java.util.ArrayList<MessagesController.PrintingUser>> threads = printingUsers.get(-group.getKey());
            if (threads == null) continue;
            for (java.util.ArrayList<MessagesController.PrintingUser> users : threads.values()) {
                for (MessagesController.PrintingUser user : users) {
                    long lifetime = user.action instanceof TLRPC.TL_sendMessageGamePlayAction ? 30000 : 5900;
                    if (user.userId != userId || user.lastTime < newest || now - user.lastTime >= lifetime) continue;
                    String action = actionText(user.action);
                    if (action.isEmpty()) continue;
                    newest = user.lastTime;
                    result = action + " · " + group.getValue();
                }
            }
        }
        return result;
    }

    static String actionText(TLRPC.SendMessageAction action) {
        int resource;
        if (action instanceof TLRPC.TL_sendMessageTypingAction) resource = R.string.Typing;
        else if (action instanceof TLRPC.TL_sendMessageRecordAudioAction) resource = R.string.RecordingAudio;
        else if (action instanceof TLRPC.TL_sendMessageRecordRoundAction) resource = R.string.RecordingRound;
        else if (action instanceof TLRPC.TL_sendMessageUploadRoundAction || action instanceof TLRPC.TL_sendMessageUploadVideoAction) resource = R.string.SendingVideoStatus;
        else if (action instanceof TLRPC.TL_sendMessageRecordVideoAction) resource = R.string.RecordingVideoStatus;
        else if (action instanceof TLRPC.TL_sendMessageUploadAudioAction) resource = R.string.SendingAudio;
        else if (action instanceof TLRPC.TL_sendMessageUploadDocumentAction) resource = R.string.SendingFile;
        else if (action instanceof TLRPC.TL_sendMessageUploadPhotoAction) resource = R.string.SendingPhoto;
        else if (action instanceof TLRPC.TL_sendMessageChooseStickerAction) resource = R.string.ChoosingSticker;
        else if (action instanceof TLRPC.TL_sendMessageGamePlayAction) resource = R.string.SendingGame;
        else if (action instanceof TLRPC.TL_sendMessageGeoLocationAction) resource = R.string.SelectingLocation;
        else if (action instanceof TLRPC.TL_sendMessageChooseContactAction) resource = R.string.SelectingContact;
        else return "";
        return LocaleController.getString(resource);
    }
}
