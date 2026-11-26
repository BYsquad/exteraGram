package com.exteragram.messenger.preferences

import android.view.View
import org.telegram.messenger.BygramSpyMode
import org.telegram.messenger.DialogObject
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.BygramPeerPickerActivity
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class BygramSpyPreferencesActivity : BasePreferencesActivity() {
    private val clearId = 100
    private val exclusionsId = 101

    private enum class Option(val key: String, val label: Int, val info: Int = 0) {
        DELETED(BygramSpyMode.SPY_SAVE_DELETED, R.string.BygramSaveDeleted),
        OWN(BygramSpyMode.SPY_SAVE_OWN_DELETED, R.string.BygramSaveOwnDeleted),
        NOTIFY(BygramSpyMode.SPY_NOTIFY_DELETED, R.string.BygramNotifyDeleted),
        EDITS(BygramSpyMode.SPY_SAVE_EDITS, R.string.BygramSaveEdits),
        READ(BygramSpyMode.SPY_SAVE_READ_DATE, R.string.BygramSaveReadDate, R.string.BygramSaveReadDateInfo),
        ONLINE(BygramSpyMode.SPY_SAVE_LAST_ONLINE, R.string.BygramSaveLastOnline, R.string.BygramSaveLastOnlineInfo)
    }

    override fun wrapSettingsText() = true

    override fun getTitle(): String = getString(R.string.BygramSpyMode)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.SearchAllChatsShort)))
        for (option in Option.entries) {
            items.add(UItem.asCheck(option.ordinal + 1, getString(option.label))
                .setChecked(BygramSpyMode.getBoolean(option.key)))
            if (option.info != 0) items.add(UItem.asShadow(getString(option.info)))
        }
        items.add(UItem.asButton(exclusionsId, R.drawable.msg_permissions, getString(R.string.BygramSpyExcludedChats)))
        items.add(UItem.asShadow(getString(R.string.BygramSpyExcludedChatsInfo)))
        items.add(UItem.asHeader(getString(R.string.DataSettings)))
        items.add(UItem.asButton(clearId, R.drawable.msg_delete, getString(R.string.BygramClearSpyData)))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val option = Option.entries.getOrNull(item.id - 1)
        if (option != null) {
            BygramSpyMode.setBoolean(option.key, !BygramSpyMode.getBoolean(option.key))
            refreshPreferenceItems()
        } else if (item.id == exclusionsId) {
            val selected = BygramSpyMode.excludedChats(currentAccount).mapNotNull { it.toLongOrNull() }
            val picker = BygramPeerPickerActivity(getString(R.string.BygramSpyExcludedChats), true) { ids ->
                BygramSpyMode.setExcludedChats(currentAccount, ids)
            }
            picker.setCurrentAccount(currentAccount)
            for (id in (messagesController.getAllDialogs().map { it.id } + selected).distinct()) {
                val peer = when {
                    DialogObject.isUserDialog(id) -> messagesController.getUser(id)
                    DialogObject.isChatDialog(id) -> messagesController.getChat(-id)
                    DialogObject.isEncryptedDialog(id) -> messagesController.getEncryptedChat(DialogObject.getEncryptedChatId(id))
                        ?.let { messagesController.getUser(it.user_id) }
                    else -> null
                }
                picker.add(id, peer ?: if (id in selected) TLRPC.TL_user().apply {
                    first_name = id.toString()
                } else null)
            }
            picker.setSelection(selected)
            presentFragment(picker)
        } else if (item.id == clearId) {
            showDialog(AlertDialog.Builder(context)
                .setTitle(getString(R.string.BygramClearSpyData))
                .setMessage(getString(R.string.BygramClearSpyDataConfirm))
                .setPositiveButton(getString(R.string.Delete)) { _, _ -> BygramSpyMode.clearSpyData() }
                .setNegativeButton(getString(R.string.Cancel), null)
                .create())
        }
    }
}
