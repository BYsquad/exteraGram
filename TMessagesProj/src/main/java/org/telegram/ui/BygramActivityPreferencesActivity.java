package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import com.exteragram.messenger.preferences.BasePreferencesActivity;

import org.telegram.messenger.BygramActivityController;
import org.telegram.messenger.BygramActivitySettings;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

public final class BygramActivityPreferencesActivity extends BasePreferencesActivity {
    private final ArrayList<Long> userIds = new ArrayList<>();
    private boolean expanded;

    @Override public String getTitle() {
        return getString(R.string.BygramShareActivity);
    }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        userIds.clear();
        Map<Long, Integer> selected = BygramActivitySettings.getActivityPermissions(currentAccount);
        HashSet<Long> added = new HashSet<>();
        items.add(UItem.asCheck(800, getString(R.string.BygramShareActivityMaster)).setChecked(BygramActivitySettings.isActivityEnabled(currentAccount)));
        items.add(UItem.asShadow(getString(R.string.BygramShareActivityPrivacy)));
        items.add(UItem.asHeader(getString(R.string.BygramActivitySelectedUsers)).setId(-1));
        for (Map.Entry<Long, Integer> entry : selected.entrySet()) {
            TLRPC.User user = getMessagesController().getUser(entry.getKey());
            String title = activityUserTitle(entry.getKey(), user);
            int index = userIds.size();
            userIds.add(entry.getKey());
            int permissions = entry.getValue();
            items.add(UItem.asButtonCheck(100_000 + index, title, activityPermissionsSummary(permissions)).setChecked(true));
            items.add(UItem.asCheck(200_000 + index, "    " + getString(R.string.BygramActivityDialogsPermission)).setChecked((permissions & BygramActivitySettings.ACTIVITY_DIALOGS) != 0));
            items.add(UItem.asCheck(300_000 + index, "    " + getString(R.string.BygramActivityProfilePermission)).setChecked((permissions & BygramActivitySettings.ACTIVITY_PROFILE) != 0));
            items.add(UItem.asCheck(400_000 + index, "    " + getString(R.string.BygramActivityChatPermission)).setChecked((permissions & BygramActivitySettings.ACTIVITY_CHAT) != 0));
            added.add(entry.getKey());
        }
        if (selected.isEmpty()) items.add(UItem.asShadow(getString(R.string.BygramActivityNobodySelected)));
        items.add(UItem.asButton(900, getString(R.string.BygramActivityAddUsers), expanded ? getString(R.string.BygramActivityHideUsers) : null));
        if (expanded) {
            items.add(UItem.asHeader(getString(R.string.BygramActivityAvailableUsers)).setId(-2));
            ArrayList<Long> candidates = new ArrayList<>();
            for (TLRPC.TL_contact contact : getContactsController().contacts) candidates.add(contact.user_id);
            for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) candidates.add(dialog.id);
            for (long userId : candidates) {
                if (!DialogObject.isUserDialog(userId) || !added.add(userId) || userId == getUserConfig().getClientUserId()) continue;
                TLRPC.User user = getMessagesController().getUser(userId);
                if (user != null && (user.bot || UserObject.isDeleted(user))) continue;
                if (user == null) continue;
                String title = activityUserTitle(userId, user);
                int index = userIds.size();
                userIds.add(userId);
                items.add(UItem.asCheck(100_000 + index, title).setChecked(false));
            }
        }
        items.add(UItem.asShadow(getString(R.string.BygramShareActivityInfo)));
    }

    private String activityUserTitle(long userId, TLRPC.User user) {
        if (user == null) return "ID " + userId;
        String username = UserObject.getPublicUsername(user);
        return UserObject.getUserName(user) + (username == null || username.isEmpty() ? "" : " (@" + username + ")");
    }

    private String activityPermissionsSummary(int permissions) {
        ArrayList<String> values = new ArrayList<>();
        if ((permissions & BygramActivitySettings.ACTIVITY_DIALOGS) != 0) values.add(getString(R.string.BygramActivityDialogsShort));
        if ((permissions & BygramActivitySettings.ACTIVITY_PROFILE) != 0) values.add(getString(R.string.BygramActivityProfileShort));
        if ((permissions & BygramActivitySettings.ACTIVITY_CHAT) != 0) values.add(getString(R.string.BygramActivityChatShort));
        return android.text.TextUtils.join(", ", values);
    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 800) {
            boolean enabled = !BygramActivitySettings.isActivityEnabled(currentAccount);
            BygramActivitySettings.setActivityEnabled(currentAccount, enabled);
            if (enabled) BygramActivityController.updateViewers(currentAccount); else BygramActivityController.clear(currentAccount);
            refreshPreferenceItems();
            return;
        }
        if (item.id == 900) {
            expanded = !expanded;
            refreshPreferenceItems();
            return;
        }
        int index = item.id - 100_000;
        int permission = 0;
        if (item.id >= 400_000) { index = item.id - 400_000; permission = BygramActivitySettings.ACTIVITY_CHAT; }
        else if (item.id >= 300_000) { index = item.id - 300_000; permission = BygramActivitySettings.ACTIVITY_PROFILE; }
        else if (item.id >= 200_000) { index = item.id - 200_000; permission = BygramActivitySettings.ACTIVITY_DIALOGS; }
        if (index < 0 || index >= userIds.size()) return;
        long userId = userIds.get(index);
        int permissions = BygramActivitySettings.getActivityPermissions(currentAccount).getOrDefault(userId, 0);
        if (permission != 0) permissions ^= permission;
        else permissions = permissions == 0 ? BygramActivitySettings.ACTIVITY_CHAT : 0;
        BygramActivitySettings.setActivityPermissions(currentAccount, userId, permissions);
        BygramActivityController.updateViewers(currentAccount);
        refreshPreferenceItems();
    }
}
