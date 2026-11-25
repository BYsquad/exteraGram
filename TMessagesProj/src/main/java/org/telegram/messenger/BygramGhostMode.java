package org.telegram.messenger;

import android.content.SharedPreferences;

public final class BygramGhostMode {
    private static final ThreadLocal<Boolean> allowReadReceipt = new ThreadLocal<>();

    public static final String GHOST_ENABLED = "bygram_ghost_enabled";
    public static final String GHOST_NO_READ = "bygram_ghost_no_read";
    public static final String GHOST_NO_STORY_READ = "bygram_ghost_no_story_read";
    public static final String GHOST_NO_ONLINE = "bygram_ghost_no_online";
    public static final String GHOST_NO_TYPING = "bygram_ghost_no_typing";
    public static final String GHOST_READ_ON_ACTIONS = "bygram_ghost_read_on_actions";

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
        return isGhostEnabled() && getBoolean(GHOST_NO_READ) && !Boolean.TRUE.equals(allowReadReceipt.get());
    }

    public static boolean isReadReceiptAllowed() {
        return Boolean.TRUE.equals(allowReadReceipt.get());
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

    public static void withReadReceipt(Runnable action) {
        allowReadReceipt.set(true);
        try {
            action.run();
        } finally {
            allowReadReceipt.remove();
        }
    }

    public static int ghostOptionsEnabled() {
        int count = 0;
        if (getBoolean(GHOST_NO_READ)) count++;
        if (getBoolean(GHOST_NO_STORY_READ)) count++;
        if (getBoolean(GHOST_NO_ONLINE)) count++;
        if (getBoolean(GHOST_NO_TYPING)) count++;
        if (getBoolean(GHOST_READ_ON_ACTIONS)) count++;
        return count;
    }

}
