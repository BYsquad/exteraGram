package com.exteragram.messenger.preferences

import android.os.Bundle
import android.view.View
import org.json.JSONObject
import org.telegram.messenger.BygramAccountService
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.ProfileActivity

class BygramProfileViewsPreferencesActivity : BasePreferencesActivity() {
    private data class Visit(val id: Long, val name: String, val username: String, val date: Int)
    private var visits: List<Visit> = emptyList()
    private var updating = false

    override fun getTitle(): String = getString(R.string.BygramProfileViews)

    override fun onResume() {
        super.onResume()
        loadVisits()
    }

    private fun loadVisits() {
        if (!BygramAccountService.profileViewsEnabled(currentAccount)) return
        BygramAccountService.profileViewsRequest(currentAccount, JSONObject().put("action", "profile_views_list")) { response, _ ->
            if (response?.optBoolean("ok") != true || parentLayout == null) return@profileViewsRequest
            val data = response.optJSONArray("views")
            visits = (0 until (data?.length() ?: 0)).mapNotNull { index ->
                data?.optJSONObject(index)?.let {
                    Visit(it.optLong("viewer_id"), it.optString("viewer_name"), it.optString("viewer_username"), it.optInt("visited_at"))
                }
            }
            refreshPreferenceItems()
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val enabled = BygramAccountService.profileViewsEnabled(currentAccount)
        items.add(UItem.asCheck(1, getTitle()).setChecked(enabled))
        items.add(UItem.asShadow(getString(R.string.BygramProfileViewsInfo)))
        if (!enabled) return
        items.add(UItem.asHeader(getString(R.string.BygramProfileViewsRecent)))
        if (visits.isEmpty()) items.add(UItem.asShadow(getString(R.string.BygramProfileViewsEmpty)))
        for ((index, visit) in visits.withIndex()) {
            val date = if (visit.date > 0) LocaleController.formatDateTime(visit.date.toLong(), false) else null
            items.add(UItem.asButton(index + 2, visit.name.ifEmpty { visit.id.toString() }, date))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id != 1) {
            val visit = visits.getOrNull(item.id - 2) ?: return
            if (messagesController.getUser(visit.id) != null) {
                presentFragment(ProfileActivity(Bundle().apply { putLong("user_id", visit.id) }))
            } else if (visit.username.isNotEmpty()) {
                messagesController.openByUserName(visit.username, this, 0, null)
            }
            return
        }
        if (updating) return
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
                refreshPreferenceItems()
                loadVisits()
            } else {
                showDialog(AlertDialog.Builder(context).setMessage(getString(R.string.ErrorOccurred))
                    .setPositiveButton(getString(R.string.OK), null).create())
            }
        }
    }
}
