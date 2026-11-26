package com.exteragram.messenger.preferences

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import com.exteragram.messenger.config.floating
import org.telegram.messenger.BygramProfileBackground
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class BygramCustomizationActivity(private val page: Int = 0) : BasePreferencesActivity() {
    companion object {
        const val FOLDERS = "bygram_scroll_folders"
        const val NAVIGATION = "bygram_scroll_navigation"
        const val HEADER = "bygram_scroll_header"
        const val SNOW_NAVIGATION = "bygram_snow_navigation"
        const val SNOW_PAGES = "bygram_snow_pages"
        const val SNOW_DENSITY = "bygram_snow_density"
    }

    private val prefs get() = MessagesController.getGlobalMainSettings()
    private val userId get() = userConfig.clientUserId
    private var saving = false
    private var checkedPublicBackground = false
    private val options = listOf(
        FOLDERS to R.string.BygramCollapseFolders,
        NAVIGATION to R.string.BygramCollapseNavigation,
        HEADER to R.string.BygramCollapseHeader,
        SNOW_NAVIGATION to R.string.BygramSnowNavigation,
        SNOW_PAGES to R.string.BygramSnowPages
    )

    override fun getTitle() = getString(when (page) {
        1 -> R.string.BygramCustomizationGeneral
        2 -> R.string.MainTabsProfile
        else -> R.string.BygramCustomization
    })

    override fun wrapSettingsText() = true

    override fun onResume() {
        super.onResume()
        if (page == 2 && !checkedPublicBackground) {
            checkedPublicBackground = true
            saving = true
            BygramProfileBackground.restoreExisting(userId) {
                saving = false
                if (fragmentView != null) refreshPreferenceItems()
            }
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        when (page) {
            0 -> {
                items.add(UItem.asHeader(getString(R.string.Categories)))
                items.add(UItem.asButton(1, R.drawable.msg_settings, getString(R.string.BygramCustomizationGeneral)))
                items.add(UItem.asButton(2, R.drawable.msg_contacts, getString(R.string.MainTabsProfile)))
            }
            1 -> {
                items.add(UItem.asHeader(getString(R.string.SearchAllChatsShort)))
                options.forEachIndexed { index, (key, title) ->
                    if (index == 3) {
                        items.add(UItem.asShadow())
                        items.add(UItem.asHeader(getString(R.string.BygramWinterSettings)))
                    }
                    items.add(UItem.asCheck(index + 1, getString(title)).setChecked(prefs.getBoolean(key, key == NAVIGATION && floating())))
                }
                val density = prefs.getInt(SNOW_DENSITY, 35).coerceIn(10, 100)
                items.add(UItem.asHeader(getString(R.string.BygramSnowDensity)))
                items.add(UItem.asIntSlideView(0, 10, density, 100, { "$it%" }) { value ->
                    prefs.edit().putInt(SNOW_DENSITY, value).apply()
                    parentActivity?.window?.decorView?.invalidate()
                })
            }
            2 -> {
                items.add(UItem.asHeader(getString(R.string.BygramProfileBackgroundSection)))
                items.add(UItem.asButton(1, R.drawable.msg_gallery, getString(R.string.BygramProfileBackgroundChoose)))
                if (BygramProfileBackground.hasLocal(userId)) items.add(UItem.asButton(2, getString(R.string.Delete)))
                items.add(UItem.asShadow())
                items.add(UItem.asHeader(getString(R.string.Appearance)))
                items.add(UItem.asCheck(3, getString(R.string.BygramProfileBackgroundPrivate)).setChecked(BygramProfileBackground.isPrivate(userId)))
                items.add(UItem.asCheck(4, getString(R.string.BygramProfileBackgroundOthers)).setChecked(BygramProfileBackground.showOthers()))
                items.add(UItem.asCheck(5, getString(R.string.BygramProfileBackgroundStretch)).setChecked(BygramProfileBackground.isStretched(userId)))
            }
        }
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (page) {
            0 -> if (item.id in 1..2) presentFragment(BygramCustomizationActivity(item.id).apply {
                setCurrentAccount(this@BygramCustomizationActivity.currentAccount)
            })
            1 -> {
                val setting = options.getOrNull(item.id - 1)
                if (setting != null) {
                    prefs.edit().putBoolean(setting.first, !prefs.getBoolean(setting.first, setting.first == NAVIGATION && floating())).apply()
                    refreshPreferenceItems()
                    parentActivity?.window?.decorView?.invalidate()
                }
            }
            2 -> if (!saving) when (item.id) {
                1 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 8105)
                2 -> save(null, true)
                3 -> save(null, privateBackground = !BygramProfileBackground.isPrivate(userId))
                4 -> {
                    BygramProfileBackground.setShowOthers(!BygramProfileBackground.showOthers())
                    refreshPreferenceItems()
                }
                5 -> save(null, stretch = !BygramProfileBackground.isStretched(userId))
            }
        }
    }

    override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResultFragment(requestCode, resultCode, data)
        if (requestCode == 8105 && resultCode == Activity.RESULT_OK) data?.data?.let { save(it) }
    }

    private fun save(uri: Uri?, remove: Boolean = false,
                     privateBackground: Boolean = BygramProfileBackground.isPrivate(userId),
                     stretch: Boolean = BygramProfileBackground.isStretched(userId)) {
        if (saving || userId <= 0) return
        saving = true
        BygramProfileBackground.save(userId, uri, remove, privateBackground, stretch) { success, loginUrl ->
            saving = false
            if (fragmentView == null || parentActivity == null) return@save
            refreshPreferenceItems()
            if (!success) showDialog(AlertDialog.Builder(context)
                .setMessage(getString(if (loginUrl == null) R.string.BygramProfileBackgroundError else R.string.BygramProfileBackgroundLogin))
                .setPositiveButton(getString(R.string.OK)) { _, _ ->
                    if (loginUrl != null) org.telegram.messenger.BygramAccountService.openAccountLogin(context, loginUrl)
                }.create())
        }
    }
}
