package org.telegram.messenger;

import android.content.SharedPreferences;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

public final class BygramGhostMode {
    public static final String GHOST_ENABLED = "bygram_ghost_enabled";
    public static final String GHOST_NO_READ = "bygram_ghost_no_read";
    public static final String GHOST_NO_STORY_READ = "bygram_ghost_no_story_read";
    public static final String GHOST_NO_ONLINE = "bygram_ghost_no_online";
    public static final String GHOST_NO_TYPING = "bygram_ghost_no_typing";
    public static final String GHOST_READ_ON_ACTIONS = "bygram_ghost_read_on_actions";
    public static final String GHOST_AUTO_OFFLINE = "bygram_ghost_auto_offline";

    private BygramGhostMode() {}

    public static boolean getBoolean(String key) {
        boolean defaultValue = GHOST_NO_READ.equals(key) || GHOST_NO_STORY_READ.equals(key)
                || GHOST_NO_ONLINE.equals(key) || GHOST_NO_TYPING.equals(key);
        return preferences().getBoolean(key, defaultValue);
    }

    public static void setBoolean(String key, boolean value) {
        preferences().edit().putBoolean(key, value).apply();
    }

    private static SharedPreferences preferences() {
        return MessagesController.getGlobalMainSettings();
    }

    public static boolean isGhostEnabled() {
        return getBoolean(GHOST_ENABLED);
    }

    public static boolean shouldBlockReadReceipts() {
        return isGhostEnabled() && getBoolean(GHOST_NO_READ);
    }

    public static boolean shouldBlockStoryReads() {
        return isGhostEnabled() && getBoolean(GHOST_NO_STORY_READ);
    }

    public static boolean shouldBlockOnline() {
        return isGhostEnabled() && getBoolean(GHOST_NO_ONLINE);
    }

    public static boolean shouldBlockTyping() {
        return isGhostEnabled() && getBoolean(GHOST_NO_TYPING);
    }

    public static boolean shouldForceOffline() {
        return shouldBlockOnline();
    }

    public static boolean shouldReadOnActions() {
        return isGhostEnabled() && getBoolean(GHOST_READ_ON_ACTIONS);
    }

    public static boolean shouldAutoOffline() {
        return shouldBlockOnline() && getBoolean(GHOST_AUTO_OFFLINE);
    }

    public static boolean prepareRequest(TLObject request, boolean allowReadReceipt) {
        if (request instanceof TL_account.updateStatus status && shouldBlockOnline()) {
            status.offline = true;
        }
        if (shouldBlockStoryReads() && (request instanceof TL_stories.TL_stories_readStories
                || request instanceof TL_stories.TL_stories_incrementStoryViews)) {
            return false;
        }
        if (shouldBlockTyping() && request instanceof TLRPC.TL_messages_setTyping typing) {
            // These actions participate in the call or bot draft lifecycle.
            return typing.action instanceof TLRPC.TL_speakingInGroupCallAction
                    || typing.action instanceof TLRPC.TL_sendMessageStopDraftAction;
        }
        if (shouldBlockTyping() && request instanceof TLRPC.TL_messages_setEncryptedTyping) {
            return false;
        }
        return allowReadReceipt || !shouldBlockReadReceipts() || !isReadReceipt(request);
    }

    private static boolean isReadReceipt(TLObject request) {
        return request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readEncryptedHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readSavedHistory
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readReactions
                || request instanceof TLRPC.TL_messages_readMentions;
    }

    public static boolean canSurfaceOnline(TLObject request) {
        return sentMessage(request) != null || isReadReceipt(request)
                || request instanceof TLRPC.TL_messages_editMessage
                || request instanceof TLRPC.TL_messages_sendReaction
                || request instanceof TLRPC.TL_messages_sendPaidReaction
                || request instanceof TLRPC.TL_messages_sendVote
                || request instanceof TLRPC.TL_messages_getBotCallbackAnswer
                || request instanceof TLRPC.TL_messages_sendEncrypted
                || request instanceof TLRPC.TL_messages_sendEncryptedFile
                || request instanceof TLRPC.TL_messages_sendEncryptedMultiMedia
                || request instanceof TL_stories.TL_stories_sendStory
                || request instanceof TL_stories.TL_stories_readStories;
    }

    public record SentMessage(TLRPC.InputPeer peer, int scheduledAt, TLRPC.InputReplyTo replyTo, int topicId) {}

    public static int sentEncryptedChat(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendEncrypted r && r.peer != null) return r.peer.chat_id;
        if (request instanceof TLRPC.TL_messages_sendEncryptedFile r && r.peer != null) return r.peer.chat_id;
        return 0;
    }

    public static SentMessage sentMessage(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendMessage r && r.quick_reply_shortcut == null) {
            return new SentMessage(r.peer, r.schedule_date, r.reply_to, 0);
        }
        if (request instanceof TLRPC.TL_messages_sendMedia r && r.quick_reply_shortcut == null) {
            return new SentMessage(r.peer, r.schedule_date, r.reply_to, 0);
        }
        if (request instanceof TLRPC.TL_messages_sendMultiMedia r && r.quick_reply_shortcut == null) {
            return new SentMessage(r.peer, r.schedule_date, r.reply_to, 0);
        }
        if (request instanceof TLRPC.TL_messages_forwardMessages r && r.quick_reply_shortcut == null) {
            return new SentMessage(r.to_peer, r.schedule_date, r.reply_to, r.top_msg_id);
        }
        if (request instanceof TLRPC.TL_messages_sendInlineBotResult r && r.quick_reply_shortcut == null) {
            return new SentMessage(r.peer, r.schedule_date, r.reply_to, 0);
        }
        return null;
    }

    public static int ghostOptionsEnabled() {
        int count = 0;
        if (getBoolean(GHOST_NO_READ)) count++;
        if (getBoolean(GHOST_NO_STORY_READ)) count++;
        if (getBoolean(GHOST_NO_ONLINE)) count++;
        if (getBoolean(GHOST_NO_TYPING)) count++;
        if (getBoolean(GHOST_READ_ON_ACTIONS)) count++;
        if (getBoolean(GHOST_AUTO_OFFLINE)) count++;
        return count;
    }

}
