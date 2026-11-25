package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.exteragram.messenger.preferences.BasePreferencesActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BygramChatPrivacy;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public final class BygramPrivateChatsActivity extends BasePreferencesActivity {
    private interface SecretCallback { void accept(String secret); }

    public static EditTextBoldCursor createDialogInput(Context context, String hint) {
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextSize(18);
        input.setMinHeight(AndroidUtilities.dp(56));
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        input.setCursorColor(Theme.getColor(Theme.key_dialogTextBlue));
        input.setHint(hint);
        input.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
        input.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
        for (boolean focused : new boolean[]{true, false}) {
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setColor(Theme.getColor(Theme.key_dialogBackgroundGray));
            shape.setCornerRadius(AndroidUtilities.dp(12));
            shape.setStroke(AndroidUtilities.dp(focused ? 2 : 1), Theme.getColor(focused ? Theme.key_dialogTextBlue : Theme.key_dialogInputField));
            background.addState(focused ? new int[]{android.R.attr.state_focused} : new int[]{}, shape);
        }
        input.setBackground(background);
        return input;
    }

    public static FrameLayout dialogInputContainer(EditText input) {
        FrameLayout container = new FrameLayout(input.getContext());
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 8));
        return container;
    }

    private static class PrivateDialogCellFactory extends UItem.UItemFactory<FrameLayout> {
        @Override public FrameLayout createView(Context context, org.telegram.ui.Components.RecyclerListView listView,
                                                int account, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            DialogCell cell = new DialogCell(null, context, true, false, account, resourcesProvider);
            FrameLayout row = new FrameLayout(context);
            row.addView(cell, LayoutHelper.createFrame(-1, -2, Gravity.TOP | Gravity.LEFT, 0, 0, 48, 0));
            ImageView remove = new ImageView(context);
            remove.setImageResource(R.drawable.msg_delete);
            remove.setColorFilter(0xffe53935);
            remove.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
            remove.setContentDescription(getString(R.string.Delete));
            row.addView(remove, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
            return row;
        }

        @Override public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                                       UniversalRecyclerView listView) {
            ((DialogCell) ((FrameLayout) view).getChildAt(0)).setDialog((TLRPC.Dialog) item.object, DialogsActivity.DIALOGS_TYPE_DEFAULT, 0);
        }
    }
    static { UItem.UItemFactory.setup(new PrivateDialogCellFactory()); }

    private final boolean hidden;
    private final ArrayList<Long> ids = new ArrayList<>();

    public BygramPrivateChatsActivity(boolean hidden) {
        this.hidden = hidden;
    }

    public static void openHidden(BaseFragment parent) {
        int account = parent.getCurrentAccount();
        BygramPrivateChatsActivity page = new BygramPrivateChatsActivity(true);
        page.setCurrentAccount(account);
        if (!BygramChatPrivacy.hasPin(account)) {
            parent.presentFragment(page);
            return;
        }
        String title = getString(R.string.BygramHiddenPin);
        EditText input = createDialogInput(parent.getContext(), title);
        input.setInputType(18);
        parent.showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(parent.getContext())
                .setTitle(title).setView(dialogInputContainer(input))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> {
                    if (BygramChatPrivacy.verifyPin(account, input.getText().toString())) {
                        parent.presentFragment(page);
                    } else {
                        new org.telegram.ui.ActionBar.AlertDialog.Builder(parent.getContext())
                                .setMessage(getString(R.string.BygramPrivacyError))
                                .setPositiveButton(getString(R.string.OK), null).show();
                    }
                }).create());
    }

    @Override public String getTitle() {
        return getString(hidden ? R.string.BygramHiddenChats : R.string.BygramProtectedChats);
    }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {

        ids.clear();
        items.add(UItem.asButton(2, getString(R.string.SelectChat)));
        items.add(UItem.asHeader(getString(hidden ? R.string.BygramHiddenChats : R.string.BygramProtectedChats)).setId(-1));
        for (TLRPC.Dialog dialog : getMessagesController().getAllDialogs()) {
            long id = dialog.id;
            boolean selected = hidden ? BygramChatPrivacy.isHidden(currentAccount, id)
                    : BygramChatPrivacy.isProtected(currentAccount, id);
            if (!selected) continue;
            if (id > 0) {
                TLRPC.User user = getMessagesController().getUser(id);
                if (user == null) continue;
            } else if (id < 0 && !DialogObject.isEncryptedDialog(id)) {
                TLRPC.Chat chat = getMessagesController().getChat(-id);
                if (chat == null) continue;
            } else continue;
            int index = ids.size();
            ids.add(id);
            UItem item = UItem.ofFactory(PrivateDialogCellFactory.class);
            item.id = 100 + index;
            item.object = dialog;
            item.onBind(view -> ((FrameLayout) view).getChildAt(1).setOnClickListener(v -> {
                if (hidden) {
                    BygramChatPrivacy.setHidden(currentAccount, id, false);
                    refreshPreferenceItems();
                } else {
                    askSecret(getString(R.string.BygramChatPassword), false, password -> {
                        if (!BygramChatPrivacy.verifyPassword(currentAccount, id, password)) {
                            privacyError();
                            return;
                        }
                        BygramChatPrivacy.removePassword(currentAccount, id);
                        refreshPreferenceItems();
                    });
                }
            }));
            items.add(item);
        }
        if (hidden) {
            items.add(UItem.asButton(1, getString(BygramChatPrivacy.hasPin(currentAccount)
                    ? R.string.BygramChangePin : R.string.BygramSetPin)));
        }
        items.add(UItem.asShadow(getString(hidden ? R.string.BygramHiddenChatsInfo : R.string.BygramProtectedChatsInfo)));
    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 2) {
            openChatPicker();
            return;
        }
        if (item.id == 1 && hidden) {
            if (BygramChatPrivacy.hasPin(currentAccount)) {
                askSecret(getString(R.string.BygramHiddenPin), true, old -> {
                    if (BygramChatPrivacy.verifyPin(currentAccount, old)) setPin();
                    else privacyError();
                });
            } else setPin();
            return;
        }
        int index = item.id - 100;
        if (index < 0 || index >= ids.size()) return;
        long id = ids.get(index);
        if (hidden && !BygramChatPrivacy.hasPin(currentAccount)) { setPin(); return; }
        android.os.Bundle args = new android.os.Bundle();
        if (id > 0) args.putLong("user_id", id); else args.putLong("chat_id", -id);
        if (hidden) BygramChatPrivacy.allowHiddenOnce(currentAccount, id);
        presentFragment(new ChatActivity(args));
    }

    private void openChatPicker() {
        android.os.Bundle args = new android.os.Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("checkCanWrite", false);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_DEFAULT);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            if (dids.isEmpty()) return false;
            long id = dids.get(0).dialogId;
            if (id == 0 || DialogObject.isEncryptedDialog(id)) return false;
            fragment.finishFragment();
            if (hidden) {
                if (BygramChatPrivacy.hasPin(currentAccount)) {
                    BygramChatPrivacy.setHidden(currentAccount, id, true);
                    refreshPreferenceItems();
                } else {
                    AndroidUtilities.runOnUIThread(() -> setPin(() -> {
                        BygramChatPrivacy.setHidden(currentAccount, id, true);
                        refreshPreferenceItems();
                    }), 300);
                }
            } else if (!BygramChatPrivacy.isProtected(currentAccount, id)) {
                AndroidUtilities.runOnUIThread(() -> setPassword(id), 300);
            }
            return true;
        });
        presentFragment(picker);
    }

    private void setPin() { setPin(null); }

    private void setPin(Runnable onSuccess) {
        askSecret(getString(R.string.BygramSetPin), true, pin -> {
            if (!pin.matches("[0-9]{4,}")) { privacyError(); return; }
            BygramChatPrivacy.setPin(currentAccount, pin);
            if (onSuccess != null) onSuccess.run();
            refreshPreferenceItems();
        });
    }

    private void setPassword(long id) {
        askSecret(getString(R.string.BygramChatPassword), false, password -> {
            if (password.length() < 4) { privacyError(); return; }
            BygramChatPrivacy.setPassword(currentAccount, id, password);
            refreshPreferenceItems();
        });
    }

    private void askSecret(String title, boolean numeric, SecretCallback callback) {
        EditText input = createDialogInput(getContext(), title);
        input.setInputType(numeric ? 18 : 129);
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle(title).setView(dialogInputContainer(input))
                .setNegativeButton(getString(R.string.Cancel), null)
                .setPositiveButton(getString(R.string.OK), (dialog, which) -> callback.accept(input.getText().toString())).create());
    }

    private void privacyError() {
        showDialog(new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.BygramPrivacyError))
                .setPositiveButton(getString(R.string.OK), null).create());
    }
}
