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
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Map;

public final class BygramActivityPreferencesActivity extends BasePreferencesActivity {
    private final ArrayList<Long> userIds = new ArrayList<>();

    @Override public String getTitle() {
        return getString(R.string.BygramShareActivity);
    }

    @Override protected void populateActionBarMenu(ActionBarMenu menu) {
        menu.addItem(1, R.drawable.msg_add).setContentDescription(getString(R.string.BygramActivityAddUsers));
    }

    @Override protected void onActionBarItemClick(int id) {
        if (id != 1) return;
        BygramPeerPickerActivity picker = new BygramPeerPickerActivity(getString(R.string.BygramActivityAddUsers), true, ids -> {
            Map<Long, Integer> selected = BygramActivitySettings.getActivityPermissions(currentAccount);
            for (long userId : ids) {
                if (!selected.containsKey(userId)) {
                    BygramActivitySettings.setActivityPermissions(currentAccount, userId, BygramActivitySettings.ACTIVITY_CHAT);
                }
            }
            BygramActivityController.updateViewers(currentAccount);
            refreshPreferenceItems();
        });
        picker.setCurrentAccount(currentAccount);
        Map<Long, Integer> selected = BygramActivitySettings.getActivityPermissions(currentAccount);
        for (TLRPC.TL_contact contact : getContactsController().contacts) addCandidate(picker, selected, contact.user_id);
        for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) addCandidate(picker, selected, dialog.id);
        presentFragment(picker);
    }

    private void addCandidate(BygramPeerPickerActivity picker, Map<Long, Integer> selected, long id) {
        if (!DialogObject.isUserDialog(id) || id == getUserConfig().getClientUserId() || selected.containsKey(id)) return;
        TLRPC.User user = getMessagesController().getUser(id);
        if (user != null && !user.bot && !UserObject.isDeleted(user)) picker.add(id, user);
    }

    @Override protected boolean wrapSettingsText() { return true; }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        userIds.clear();
        Map<Long, Integer> selected = BygramActivitySettings.getActivityPermissions(currentAccount);
        items.add(UItem.asHeader(getString(R.string.PrivacySettings)));
        items.add(UItem.asCheck(800, getString(R.string.BygramShareActivityMaster)).setChecked(BygramActivitySettings.isActivityEnabled(currentAccount)));
        items.add(UItem.asShadow(getString(R.string.BygramShareActivityPrivacy)));
        items.add(UItem.asHeader(getString(R.string.BygramActivitySelectedUsers)).setId(-1));
        for (Map.Entry<Long, Integer> entry : selected.entrySet()) {
            TLRPC.User user = getMessagesController().getUser(entry.getKey());
            String title = activityUserTitle(entry.getKey(), user);
            int index = userIds.size();
            userIds.add(entry.getKey());
            int permissions = entry.getValue();
            items.add(UItem.asCheck(100_000 + index, title, activityPermissionsSummary(permissions), true).setChecked(true));
            items.add(UItem.asCheck(200_000 + index, "    " + getString(R.string.BygramActivityDialogsPermission)).setChecked((permissions & BygramActivitySettings.ACTIVITY_DIALOGS) != 0));
            items.add(UItem.asCheck(300_000 + index, "    " + getString(R.string.BygramActivityProfilePermission)).setChecked((permissions & BygramActivitySettings.ACTIVITY_PROFILE) != 0));
            items.add(UItem.asCheck(400_000 + index, "    " + getString(R.string.BygramActivityChatPermission)).setChecked((permissions & BygramActivitySettings.ACTIVITY_CHAT) != 0));
        }
        if (selected.isEmpty()) items.add(UItem.asShadow(getString(R.string.BygramActivityNobodySelected)));
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
