package com.exteragram.messenger.preferences

import android.view.View
import org.telegram.messenger.BygramSpyMode
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class BygramSpyPreferencesActivity : BasePreferencesActivity() {

    private enum class Option(val key: String, val label: Int, val info: Int = 0) {
        DELETED(BygramSpyMode.SPY_SAVE_DELETED, R.string.BygramSaveDeleted),
        OWN(BygramSpyMode.SPY_SAVE_OWN_DELETED, R.string.BygramSaveOwnDeleted),
        NOTIFY(BygramSpyMode.SPY_NOTIFY_DELETED, R.string.BygramNotifyDeleted),
        EDITS(BygramSpyMode.SPY_SAVE_EDITS, R.string.BygramSaveEdits),
        READ(BygramSpyMode.SPY_SAVE_READ_DATE, R.string.BygramSaveReadDate, R.string.BygramSaveReadDateInfo),
        ONLINE(BygramSpyMode.SPY_SAVE_LAST_ONLINE, R.string.BygramSaveLastOnline, R.string.BygramSaveLastOnlineInfo)
    }

    override fun getTitle(): String = getString(R.string.BygramSpyMode)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        for (option in Option.entries) {
            items.add(UItem.asCheck(option.ordinal + 1, getString(option.label))
                .setChecked(BygramSpyMode.getBoolean(option.key)))
            if (option.info != 0) items.add(UItem.asShadow(getString(option.info)))
        }
        items.add(UItem.asButton(Option.entries.size + 1, getString(R.string.BygramClearSpyData)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val option = Option.entries.getOrNull(item.id - 1)
        if (option != null) {
            BygramSpyMode.setBoolean(option.key, !BygramSpyMode.getBoolean(option.key))
            refreshPreferenceItems()
        } else if (item.id == Option.entries.size + 1) {
            showDialog(AlertDialog.Builder(context)
                .setTitle(getString(R.string.BygramClearSpyData))
                .setMessage(getString(R.string.BygramClearSpyDataConfirm))
                .setPositiveButton(getString(R.string.Delete)) { _, _ -> BygramSpyMode.clearSpyData() }
                .setNegativeButton(getString(R.string.Cancel), null)
                .create())
        }
    }
}
