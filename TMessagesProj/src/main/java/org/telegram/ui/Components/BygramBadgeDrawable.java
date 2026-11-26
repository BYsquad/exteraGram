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
    private final int sizeDp;
    private final int size;
    private final int gap;

    public BygramBadgeDrawable(Context context, boolean resource, int sizeDp) {
        this(context, resource, sizeDp, null);
    }

    public BygramBadgeDrawable(Context context, boolean resource, int sizeDp, Drawable leading) {
        this.resource = resource;
        this.sizeDp = sizeDp;
        this.size = AndroidUtilities.dp(sizeDp);
        this.leading = leading instanceof BygramBadgeDrawable ? ((BygramBadgeDrawable) leading).leading : leading;
        this.gap = this.leading == null ? 0 : AndroidUtilities.dp(4);
        image = context.getResources().getDrawable(R.drawable.bygram_user_badge).mutate();
    }

    BygramBadgeDrawable copy(Context context) {
        return new BygramBadgeDrawable(context, resource, sizeDp, leading);
    }

    @Override public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float scale = bounds.width() / (float) getIntrinsicWidth();
        if (leading != null) {
            int width = Math.round(Math.max(0, leading.getIntrinsicWidth()) * scale);
            int height = Math.round(Math.max(0, leading.getIntrinsicHeight()) * scale);
            int top = bounds.top + (bounds.height() - height) / 2;
            leading.setBounds(bounds.left, top, bounds.left + width, top + height);
            leading.draw(canvas);
        }
        int scaledSize = Math.round(size * scale);
        int top = bounds.top + (bounds.height() - scaledSize) / 2;
        image.setBounds(bounds.right - scaledSize, top, bounds.right, top + scaledSize);
        image.draw(canvas);
    }

    public Rect badgeBounds() {
        return image.getBounds();
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
