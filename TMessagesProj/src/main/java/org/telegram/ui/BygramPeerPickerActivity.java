package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;
import android.widget.EditText;

import com.exteragram.messenger.preferences.BasePreferencesActivity;

import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class BygramPeerPickerActivity extends BasePreferencesActivity {
    private final String title;
    private final boolean multiple;
    private final Utilities.Callback<ArrayList<Long>> onSelected;
    private final ArrayList<Long> ids = new ArrayList<>();
    private final ArrayList<TLObject> peers = new ArrayList<>();
    private final LinkedHashSet<Long> selected = new LinkedHashSet<>();
    private Runnable confirmed;
    private boolean allowEmpty;
    private String query = "";

    public BygramPeerPickerActivity(String title, boolean multiple, Utilities.Callback<ArrayList<Long>> onSelected) {
        this.title = title;
        this.multiple = multiple;
        this.onSelected = onSelected;
    }

    public void add(long id, TLObject peer) {
        if (peer == null || ids.contains(id)) return;
        ids.add(id);
        peers.add(peer);
    }

    public void setSelection(java.util.Collection<Long> ids) {
        selected.clear();
        selected.addAll(ids);
        allowEmpty = true;
    }

    @Override public String getTitle() { return title; }

    @Override protected void populateActionBarMenu(ActionBarMenu menu) {
        ActionBarMenuItem search = menu.addItem(2, R.drawable.msg_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override public void onSearchCollapse() {
                        query = "";
                        refreshPreferenceItems();
                    }

                    @Override public void onTextChanged(EditText editText) {
                        query = editText.getText().toString().trim().toLowerCase(Locale.ROOT);
                        refreshPreferenceItems();
                    }
                });
        search.setSearchFieldHint(getString(R.string.Search));
        search.setContentDescription(getString(R.string.Search));
        menu.addItem(1, R.drawable.ic_ab_done).setContentDescription(getString(R.string.Done));
    }

    @Override protected void onActionBarItemClick(int id) {
        if (id != 1 || !allowEmpty && selected.isEmpty() || confirmed != null) return;
        ArrayList<Long> result = new ArrayList<>(selected);
        confirmed = () -> onSelected.run(result);
        finishFragment();
    }

    @Override public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (!isOpen && confirmed != null) {
            Runnable callback = confirmed;
            confirmed = null;
            org.telegram.messenger.AndroidUtilities.runOnUIThread(callback);
        }
    }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        for (int i = 0; i < peers.size(); i++) {
            TLObject peer = peers.get(i);
            if (matchesSearch(peer)) {
                items.add(UItem.asUserCheckbox(i + 1, peer).setChecked(selected.contains(ids.get(i))));
            }
        }
    }

    private boolean matchesSearch(TLObject peer) {
        if (query.isEmpty()) return true;
        String name;
        String username;
        if (peer instanceof TLRPC.User user) {
            name = UserObject.getUserName(user);
            username = UserObject.getPublicUsername(user);
        } else if (peer instanceof TLRPC.Chat chat) {
            name = chat.title;
            username = ChatObject.getPublicUsername(chat);
        } else {
            return false;
        }
        return (name != null && name.toLowerCase(Locale.ROOT).contains(query))
                || (username != null && ("@" + username).toLowerCase(Locale.ROOT).contains(query));
    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id < 1 || item.id > ids.size()) return;
        long id = ids.get(item.id - 1);
        if (!selected.remove(id)) {
            if (!multiple) selected.clear();
            selected.add(id);
        }
        refreshPreferenceItems();
    }
}
