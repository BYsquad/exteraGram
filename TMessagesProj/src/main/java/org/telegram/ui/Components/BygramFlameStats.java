package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailCell;

public final class BygramFlameStats {
    public static void show(BaseFragment fragment, int account, TLRPC.InputPeer peer, int days,
                            Theme.ResourcesProvider resourcesProvider) {
        Context context = fragment.getParentActivity();
        if (context == null || days <= 0 || peer instanceof TLRPC.TL_inputPeerEmpty || peer == null) return;

        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        TextDetailCell streak = new TextDetailCell(context, resourcesProvider);
        streak.setTextAndValue(LocaleController.formatNumber(days, ','), getString(R.string.BygramFlameStatsDays), true);
        rows.addView(streak, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextDetailCell messages = new TextDetailCell(context, resourcesProvider);
        String label = getString(R.string.BygramFlameStatsMessages);
        messages.setTextAndValue(getString(R.string.Loading), label, false);
        rows.addView(messages, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        BottomSheet sheet = new BottomSheet.Builder(context, false, resourcesProvider)
                .setTitle(getString(R.string.BygramFlameStats))
                .setCustomView(rows)
                .create();
        if (fragment.showDialog(sheet) == null) return;

        long userId = UserConfig.getInstance(account).getClientUserId();
        TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
        request.peer = peer;
        request.limit = 1;
        ConnectionsManager.getInstance(account).sendRequest(request, (result, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (sheet.isDismissed() || UserConfig.getInstance(account).getClientUserId() != userId) return;
                    String value = getString(R.string.ErrorOccurred);
                    if (result instanceof TLRPC.messages_Messages && !(result instanceof TLRPC.TL_messages_messagesNotModified)) {
                        TLRPC.messages_Messages history = (TLRPC.messages_Messages) result;
                        value = LocaleController.formatNumber(Math.max(history.count, history.messages.size()), ',');
                        if (history.inexact) value = "≈ " + value;
                    }
                    messages.setTextAndValue(value, label, false);
                }));
    }

    private BygramFlameStats() {}
}
