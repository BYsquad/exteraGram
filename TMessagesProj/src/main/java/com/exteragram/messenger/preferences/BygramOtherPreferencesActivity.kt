package com.exteragram.messenger.preferences

import android.view.View
import org.telegram.messenger.BygramDeviceReporter
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class BygramOtherPreferencesActivity : BasePreferencesActivity() {
    private enum class Option { STATISTICS }

    override fun wrapSettingsText() = true

    override fun getTitle(): String = getString(R.string.BygramOther)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.DataSettings)))
        items.add(uCheck(Option.STATISTICS, getString(R.string.BygramUsageStatistics),
            BygramDeviceReporter.isEnabled(currentAccount), linkAlias = "bygramUsageStatistics"))
        items.add(UItem.asShadow(getString(R.string.BygramUsageStatisticsInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id != Option.STATISTICS.ordinal + 1) return
        toggleBooleanSettingAndRefresh(item) { BygramDeviceReporter.setEnabled(currentAccount, it) }
    }
}
