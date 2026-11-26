package com.exteragram.messenger.preferences

import android.content.Context
import android.view.View
import com.exteragram.messenger.preferences.components.HeaderSettingsCell
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BygramUpdates
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.BygramActivityPreferencesActivity
import org.telegram.ui.BygramPrivateChatsActivity
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.LaunchActivity

class BygramPreferencesActivity : BasePreferencesActivity() {

    private enum class Option {
        GHOST, HIDDEN, PROTECTED, SPY, ACTIVITY, PROFILE_VIEWS, CUSTOMIZATION, SOURCE_CODE, UPSTREAM, OTHER, HEADER, UPDATE
    }

    private lateinit var header: HeaderSettingsCell

    override fun wrapSettingsText() = true

    override fun needHideTitle() = true

    override fun getListTopPadding(topInset: Int) =
        topInset + ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp(20f)

    override fun createView(context: Context): View {
        header = HeaderSettingsCell(context, true)
        return super.createView(context).also {
            if (parentLayout?.isRightLayout == true) actionBar.setBackButtonImage(R.drawable.ic_ab_close)
        }
    }

    override fun getTitle(): String = getString(R.string.BygramPreferences)

    override fun onResume() {
        super.onResume()
        val previousUrl = BygramUpdates.getDownloadUrl()
        BygramUpdates.check {
            if (parentActivity != null && BygramUpdates.getDownloadUrl() != previousUrl) refreshPreferenceItems()
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asCustomShadow(header, 198).setId(Option.HEADER.ordinal + 1))
        if (BygramUpdates.getDownloadUrl() != null) {
            items.add(uButton(Option.UPDATE, getString(R.string.BygramUpdateButton), R.drawable.msg_download))
        }

        items.add(UItem.asHeader(getString(R.string.Categories)))
        items.add(uButton(Option.CUSTOMIZATION, getString(R.string.BygramCustomization), R.drawable.msg_theme))
        items.add(uButton(Option.OTHER, getString(R.string.BygramOther), R.drawable.msg_settings, linkAlias = "bygramOther"))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.PrivacySettings)))
        items.add(uButton(Option.GHOST, getString(R.string.BygramGhostMode), R.drawable.msg_permissions, linkAlias = "bygramGhost"))
        items.add(uButton(Option.HIDDEN, getString(R.string.BygramHiddenChats), R.drawable.msg_permissions, linkAlias = "bygramHidden"))
        items.add(uButton(Option.PROTECTED, getString(R.string.BygramProtectedChats), R.drawable.msg_permissions, linkAlias = "bygramProtected"))
        items.add(uButton(Option.SPY, getString(R.string.BygramSpyMode), R.drawable.msg_info, linkAlias = "bygramSpy"))
        items.add(uButton(Option.ACTIVITY, getString(R.string.BygramShareActivity), R.drawable.msg_location, linkAlias = "bygramActivity"))
        items.add(uButton(Option.PROFILE_VIEWS, getString(R.string.BygramProfileViews), R.drawable.msg_info, linkAlias = "bygramProfileViews"))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.Links)))
        items.add(uButton(Option.SOURCE_CODE, getString(R.string.BygramSourceCode), R.drawable.msg_delete, "GitHub", linkAlias = "bygramSourceCode"))
        items.add(uButton(Option.UPSTREAM, getString(R.string.BygramBasedOnExtera), R.drawable.msg_info, "GitHub", showDivider = false))
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (Option.entries.getOrNull(item.id - 1) ?: return) {
            Option.HEADER -> (parentActivity as? LaunchActivity)?.checkAppUpdate(true)
            Option.UPDATE -> BygramUpdates.getDownloadUrl()?.let { Browser.openUrlInSystemBrowser(parentActivity, it) }
            Option.GHOST -> presentFragment(BygramGhostPreferencesActivity().apply { setCurrentAccount(this@BygramPreferencesActivity.currentAccount) })
            Option.HIDDEN -> BygramPrivateChatsActivity.openHidden(this)
            Option.PROTECTED -> presentFragment(BygramPrivateChatsActivity(false).apply { setCurrentAccount(this@BygramPreferencesActivity.currentAccount) })
            Option.SPY -> presentFragment(BygramSpyPreferencesActivity().apply { setCurrentAccount(this@BygramPreferencesActivity.currentAccount) })
            Option.ACTIVITY -> presentFragment(BygramActivityPreferencesActivity().apply { setCurrentAccount(this@BygramPreferencesActivity.currentAccount) })
            Option.PROFILE_VIEWS -> presentFragment(BygramProfileViewsPreferencesActivity().apply { setCurrentAccount(this@BygramPreferencesActivity.currentAccount) })
            Option.CUSTOMIZATION -> presentFragment(BygramCustomizationActivity().apply { setCurrentAccount(this@BygramPreferencesActivity.currentAccount) })
            Option.OTHER -> presentFragment(BygramOtherPreferencesActivity().apply { setCurrentAccount(this@BygramPreferencesActivity.currentAccount) })
            Option.SOURCE_CODE -> Browser.openUrl(parentActivity, "https://github.com/BYsquad/exteraGram/tree/bygram/exteragram-base")
            Option.UPSTREAM -> Browser.openUrl(parentActivity, "https://github.com/exteraSquad/exteraGram")
        }
    }
}
