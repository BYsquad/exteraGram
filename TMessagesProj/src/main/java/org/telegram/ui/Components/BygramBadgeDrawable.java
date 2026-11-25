package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public final class BygramBadgeDrawable extends Drawable {
    private final boolean resource;
    private final Drawable image;
    private final Drawable leading;
    private final int size;
    private final int gap;

    public BygramBadgeDrawable(Context context, boolean resource, int sizeDp) {
        this(context, resource, sizeDp, null);
    }

    public BygramBadgeDrawable(Context context, boolean resource, int sizeDp, Drawable leading) {
        this.resource = resource;
        this.size = AndroidUtilities.dp(sizeDp);
        this.leading = leading instanceof BygramBadgeDrawable ? ((BygramBadgeDrawable) leading).leading : leading;
        this.gap = this.leading == null ? 0 : AndroidUtilities.dp(4);
        image = context.getResources().getDrawable(resource ? R.drawable.bygram_resource_badge : R.drawable.bygram_user_badge).mutate();
    }

    @Override public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (leading != null) {
            int width = Math.max(0, leading.getIntrinsicWidth());
            int height = Math.max(0, leading.getIntrinsicHeight());
            leading.setBounds(bounds.left, bounds.top + (bounds.height() - height) / 2,
                    bounds.left + width, bounds.top + (bounds.height() + height) / 2);
            leading.draw(canvas);
        }
        image.setBounds(bounds.right - size, bounds.top + (bounds.height() - size) / 2,
                bounds.right, bounds.top + (bounds.height() + size) / 2);
        image.draw(canvas);
    }

    public Rect badgeBounds() {
        Rect bounds = getBounds();
        return new Rect(bounds.right - size, bounds.top + (bounds.height() - size) / 2,
                bounds.right, bounds.top + (bounds.height() + size) / 2);
    }

    public void showNotice() {
        BulletinFactory.global().createSimpleBulletin(R.raw.chats_infotip,
                LocaleController.getString(resource ? R.string.BygramResourceBadgeNotice : R.string.BygramUserBadgeNotice)).show();
    }

    @Override public void setAlpha(int alpha) { image.setAlpha(alpha); if (leading != null) leading.setAlpha(alpha); }
    @Override public void setColorFilter(@Nullable ColorFilter filter) { image.setColorFilter(filter); if (leading != null) leading.setColorFilter(filter); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return size + (leading == null ? 0 : Math.max(0, leading.getIntrinsicWidth()) + gap); }
    @Override public int getIntrinsicHeight() { return Math.max(size, leading == null ? 0 : Math.max(0, leading.getIntrinsicHeight())); }
}
