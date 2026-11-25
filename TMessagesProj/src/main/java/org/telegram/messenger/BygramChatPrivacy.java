package org.telegram.messenger;

import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local, account-scoped chat privacy settings. Verifiers are never exported. */
public final class BygramChatPrivacy {
    private static final int ITERATIONS = 120_000;
    private static final Set<String> allowedOnce = new HashSet<>();
    private static final Set<String> hiddenAllowedOnce = new HashSet<>();

    private BygramChatPrivacy() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bygram_chat_privacy", 0);
    }

    private static String key(int account, String kind, long dialogId) {
        return UserConfig.getInstance(account).getClientUserId() + "_" + kind + "_" + dialogId;
    }

    private static String verifier(String secret) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + hash(secret, salt);
    }

    private static String hash(String secret, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matches(String secret, String stored) {
        if (secret == null || stored == null || !stored.contains(":")) return false;
        try {
            String[] parts = stored.split(":", 2);
            byte[] expected = Base64.decode(parts[1], Base64.NO_WRAP);
            byte[] actual = Base64.decode(hash(secret, Base64.decode(parts[0], Base64.NO_WRAP)), Base64.NO_WRAP);
            return java.security.MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasPin(int account) {
        return prefs().contains(key(account, "pin", 0));
    }

    public static boolean verifyPin(int account, String pin) {
        return matches(pin, prefs().getString(key(account, "pin", 0), null));
    }

    public static void setPin(int account, String pin) {
        if (pin == null || !pin.matches("[0-9]{4,}")) throw new IllegalArgumentException("PIN must contain at least four digits");
        prefs().edit().putString(key(account, "pin", 0), verifier(pin)).apply();
    }

    public static Set<String> hiddenIds(int account) {
        return new HashSet<>(prefs().getStringSet(key(account, "hidden", 0), java.util.Collections.emptySet()));
    }

    public static boolean isHidden(int account, long dialogId) {
        return prefs().getStringSet(key(account, "hidden", 0), java.util.Collections.emptySet())
                .contains(Long.toString(dialogId));
    }

    public static void setHidden(int account, long dialogId, boolean hidden) {
        Set<String> ids = hiddenIds(account);
        if (hidden) ids.add(Long.toString(dialogId)); else ids.remove(Long.toString(dialogId));
        prefs().edit().putStringSet(key(account, "hidden", 0), ids).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        NotificationsController.getInstance(account).showNotifications();
    }

    public static boolean isProtected(int account, long dialogId) {
        return prefs().contains(key(account, "lock", dialogId));
    }

    public static boolean hidesNotification(int account, long dialogId) {
        return isHidden(account, dialogId) || isProtected(account, dialogId);
    }

    public static boolean verifyPassword(int account, long dialogId, String password) {
        return matches(password, prefs().getString(key(account, "lock", dialogId), null));
    }

    public static void setPassword(int account, long dialogId, String password) {
        if (password == null || password.length() < 4) throw new IllegalArgumentException("Password too short");
        prefs().edit().putString(key(account, "lock", dialogId), verifier(password)).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        NotificationsController.getInstance(account).showNotifications();
    }

    public static void removePassword(int account, long dialogId) {
        prefs().edit().remove(key(account, "lock", dialogId)).apply();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        NotificationsController.getInstance(account).showNotifications();
    }

    public static synchronized void allowOnce(int account, long dialogId) {
        allowedOnce.add(key(account, "open", dialogId));
    }

    public static synchronized boolean consumeAllowed(int account, long dialogId) {
        return allowedOnce.remove(key(account, "open", dialogId));
    }

    public static synchronized boolean isAllowed(int account, long dialogId) {
        return allowedOnce.contains(key(account, "open", dialogId));
    }

    public static synchronized void allowHiddenOnce(int account, long dialogId) {
        hiddenAllowedOnce.add(key(account, "hidden_open", dialogId));
    }

    public static synchronized boolean isHiddenAllowed(int account, long dialogId) {
        return hiddenAllowedOnce.contains(key(account, "hidden_open", dialogId));
    }

    public static synchronized boolean consumeHiddenAllowed(int account, long dialogId) {
        return hiddenAllowedOnce.remove(key(account, "hidden_open", dialogId));
    }

    public static void reset() {
        allowedOnce.clear();
        hiddenAllowedOnce.clear();
        prefs().edit().clear().apply();
    }
}
