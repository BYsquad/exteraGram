package com.exteragram.messenger.preferences

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.BygramAccountService
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.ThemeBind
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.messenger.MessagesController
import org.telegram.ui.ProfileActivity

class BygramProfileViewsPreferencesActivity : BasePreferencesActivity() {
    private data class Visit(val id: Long, val name: String, val username: String, val date: Int, val avatarUrl: String)

    private class VisitCell(context: Context, private val account: Int, resources: Theme.ResourcesProvider?) : LinearLayout(context) {
        private val avatar = AvatarDrawable()
        private val photo = BackupImageView(context).apply {
            setRoundRadius(dp(20f))
            imageReceiver.setCurrentAccount(account)
        }
        private val name = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            ThemeBind.textColor(this, Theme.key_windowBackgroundWhiteBlackText, resources)
        }
        private val date = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            ThemeBind.textColor(this, Theme.key_windowBackgroundWhiteGrayText2, resources)
        }

        init {
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = if (LocaleController.isRTL) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
            setPaddingRelative(dp(21f), dp(8f), dp(21f), dp(8f))
            addView(photo, LayoutHelper.createLinear(40, 40))
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                setPaddingRelative(dp(12f), 0, 0, 0)
                addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
                addView(date, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            }, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f))
        }

        fun bind(visit: Visit) {
            name.text = visit.name.ifEmpty { visit.id.toString() }
            date.visibility = if (visit.date > 0) VISIBLE else GONE
            date.text = if (visit.date > 0) LocaleController.formatDateTime(visit.date.toLong(), false) else null
            val user = MessagesController.getInstance(account).getUser(visit.id)
            avatar.setInfo(visit.id, visit.name, "")
            if (user?.photo?.photo_small != null) {
                avatar.setInfo(user)
                photo.setForUserOrChat(user, avatar)
            } else if (visit.avatarUrl.startsWith("https://byxarek.site/TEST-PROJECT/BYgram/api/avatar.php?")) {
                photo.setImage(ImageLocation.getForPath(visit.avatarUrl), "40_40", avatar, null)
            } else {
                photo.setImageDrawable(avatar)
            }
        }
    }

    private class VisitFactory : UItem.UItemFactory<VisitCell>() {
        override fun createView(context: Context, listView: RecyclerListView, account: Int, classGuid: Int, resourcesProvider: Theme.ResourcesProvider?) =
            VisitCell(context, account, resourcesProvider)

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter, listView: UniversalRecyclerView) {
            (view as VisitCell).bind(item.`object` as Visit)
        }
    }

    companion object {
        init { UItem.UItemFactory.setup(VisitFactory()) }
    }

    private var visits: List<Visit> = emptyList()
    private var updating = false
    private var loading = false
    private var loadFailed = false

    override fun wrapSettingsText() = true

    override fun getTitle(): String = getString(R.string.BygramProfileViews)

    override fun onResume() {
        super.onResume()
        loadVisits()
    }

    private fun loadVisits() {
        if (loading || updating || !BygramAccountService.canRefreshProfileViews(currentAccount)) return
        loading = true
        loadFailed = false
        if (parentLayout != null) refreshPreferenceItems()
        BygramAccountService.profileViewsRequest(currentAccount, JSONObject().put("action", "profile_views_list")) { response, _ ->
            loading = false
            if (parentLayout == null) return@profileViewsRequest
            if (response?.optBoolean("ok") != true) {
                visits = emptyList()
                loadFailed = true
                refreshPreferenceItems()
                return@profileViewsRequest
            }
            val data = response.optJSONArray("views")
            visits = (0 until (data?.length() ?: 0)).mapNotNull { index ->
                data?.optJSONObject(index)?.let {
                    Visit(it.optLong("viewer_id"), it.optString("viewer_name"), it.optString("viewer_username"),
                        it.optInt("visited_at"), it.optString("avatar_url"))
                }
            }
            refreshPreferenceItems()
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.PrivacySettings)))
        val enabled = BygramAccountService.profileViewsEnabled(currentAccount)
        items.add(UItem.asCheck(1, getTitle()).setChecked(enabled))
        items.add(UItem.asShadow(getString(R.string.BygramProfileViewsInfo)))
        if (!enabled) return
        items.add(UItem.asHeader(getString(R.string.BygramProfileViewsRecent)))
        if (visits.isEmpty()) {
            items.add(UItem.asShadow(getString(when {
                loading -> R.string.Loading
                loadFailed -> R.string.ErrorOccurred
                else -> R.string.BygramProfileViewsEmpty
            })))
        }
        for ((index, visit) in visits.withIndex()) {
            items.add(UItem.ofFactory(VisitFactory::class.java).setId(index + 2).apply { `object` = visit })
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id != 1) {
            val visit = visits.getOrNull(item.id - 2) ?: return
            if (messagesController.getUser(visit.id) != null) {
                presentFragment(ProfileActivity(Bundle().apply { putLong("user_id", visit.id) }).apply {
                    setCurrentAccount(this@BygramProfileViewsPreferencesActivity.currentAccount)
                })
            } else if (visit.username.isNotEmpty()) {
                messagesController.openByUserName(visit.username, this, 0, null)
            }
            return
        }
        if (updating || loading) return
        updating = true
        val request = JSONObject().put("action", "profile_views_set")
            .put("enabled", !BygramAccountService.profileViewsEnabled(currentAccount))
        BygramAccountService.profileViewsRequest(currentAccount, request) { response, authUrl ->
            updating = false
            if (parentLayout == null) return@profileViewsRequest
            if (authUrl.isNotEmpty()) {
                showDialog(AlertDialog.Builder(context)
                    .setTitle(getTitle())
                    .setMessage(getString(R.string.BygramProfileViewsVerify))
                    .setPositiveButton(getString(R.string.BygramTypingOpenLogin)) { _, _ ->
                        BygramAccountService.openAccountLogin(context, authUrl)
                    }
                    .setNegativeButton(getString(R.string.Cancel), null).create())
            } else if (response?.optBoolean("ok") == true) {
                visits = emptyList()
                loadVisits()
                refreshPreferenceItems()
            } else {
                showDialog(AlertDialog.Builder(context).setMessage(getString(R.string.ErrorOccurred))
                    .setPositiveButton(getString(R.string.OK), null).create())
            }
        }
    }
}
