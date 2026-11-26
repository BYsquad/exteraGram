package org.telegram.ui.Components;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.Theme;

import java.util.Random;

public final class BygramSnow {
    private final boolean navigation;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] positions = new float[140];
    private final float[] phases = new float[140];

    public BygramSnow(boolean navigation) {
        this.navigation = navigation;
        Random random = new Random();
        for (int i = 0; i < positions.length; i++) {
            positions[i] = random.nextFloat();
            phases[i] = random.nextFloat();
        }
    }

    public void draw(View view, Canvas canvas) {
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        if (!prefs.getBoolean(navigation ? "bygram_snow_navigation" : "bygram_snow_pages", false)
                || !view.isShown() || view.getWidth() == 0 || view.getHeight() == 0) return;
        float density = prefs.getInt("bygram_snow_density", 35) / 100f;
        int count = Math.min(navigation ? 32 : 140, Math.max(1, (int) ((navigation ? 32 : 140) * density)));
        paint.setColor(Theme.isCurrentThemeDark() ? 0x9aeef8ff : 0x803e7ab1);
        float time = (SystemClock.uptimeMillis() % 3_600_000L) / 1000f;
        int width = view.getWidth(), height = view.getHeight();
        for (int i = 0; i < count; i++) {
            float speed = AndroidUtilities.dpf2(16 + i % 18);
            float y = (phases[i] * height + time * speed) % height;
            float x = positions[i] * width + AndroidUtilities.dpf2(5) * (float) Math.sin(time + i);
            canvas.drawCircle(x, y, AndroidUtilities.dpf2(0.6f + i % 5 * 0.2f), paint);
        }
        view.postInvalidateDelayed(32);
    }
}
