package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import com.exteragram.messenger.preferences.BasePreferencesActivity;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public final class BygramPeerPickerActivity extends BasePreferencesActivity {
    private final String title;
    private final boolean multiple;
    private final Utilities.Callback<ArrayList<Long>> onSelected;
    private final ArrayList<Long> ids = new ArrayList<>();
    private final ArrayList<TLObject> peers = new ArrayList<>();
    private final LinkedHashSet<Long> selected = new LinkedHashSet<>();
    private Runnable confirmed;
    private boolean allowEmpty;

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
            items.add(UItem.asUserCheckbox(i + 1, peers.get(i)).setChecked(selected.contains(ids.get(i))));
        }
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
