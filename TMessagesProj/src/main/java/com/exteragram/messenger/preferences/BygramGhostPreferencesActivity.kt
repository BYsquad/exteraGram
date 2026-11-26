package com.exteragram.messenger.preferences

import android.view.View
import org.telegram.messenger.BygramGhostMode
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class BygramGhostPreferencesActivity : BasePreferencesActivity() {

    private enum class Option(val key: String, val label: Int) {
        ENABLED(BygramGhostMode.GHOST_ENABLED, R.string.BygramGhostMode),
        MESSAGES(BygramGhostMode.GHOST_NO_READ, R.string.BygramNoReadMessages),
        STORIES(BygramGhostMode.GHOST_NO_STORY_READ, R.string.BygramNoReadStories),
        ONLINE(BygramGhostMode.GHOST_NO_ONLINE, R.string.BygramNoOnline),
        TYPING(BygramGhostMode.GHOST_NO_TYPING, R.string.BygramNoTyping),
        ACTIONS(BygramGhostMode.GHOST_READ_ON_ACTIONS, R.string.BygramReadOnActions),
        AUTO_OFFLINE(BygramGhostMode.GHOST_AUTO_OFFLINE, R.string.BygramGhostAutoOffline)
    }

    override fun wrapSettingsText() = true

    override fun getTitle(): String = getString(R.string.BygramGhostMode)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val enabled = BygramGhostMode.isGhostEnabled(currentAccount)
        val title = if (enabled) {
            LocaleController.formatString(R.string.BygramGhostMaster, BygramGhostMode.ghostOptionsEnabled(currentAccount), Option.entries.size - 1)
        } else {
            getTitle()
        }
        items.add(UItem.asHeader(getString(R.string.General)))
        items.add(UItem.asCheck(Option.ENABLED.ordinal + 1, title).setChecked(enabled))
        items.add(UItem.asShadow(getString(R.string.BygramGhostInfo)))
        if (enabled) {
            items.add(UItem.asHeader(getString(R.string.PrivacySettings)))
            for (option in Option.entries.drop(1)) {
                items.add(UItem.asCheck(option.ordinal + 1, getString(option.label))
                    .setChecked(BygramGhostMode.getBoolean(currentAccount, option.key)))
            }
            items.add(UItem.asShadow(getString(R.string.BygramReadOnActionsInfo)))
            items.add(UItem.asShadow(getString(R.string.BygramGhostAutoOfflineInfo)))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val option = Option.entries.getOrNull(item.id - 1) ?: return
        BygramGhostMode.setBoolean(currentAccount, option.key, !BygramGhostMode.getBoolean(currentAccount, option.key))
        if (option == Option.ENABLED || option == Option.ONLINE) {
            messagesController.updateTimerProc()
        }
        listView.adapter.update(true)
    }
}
