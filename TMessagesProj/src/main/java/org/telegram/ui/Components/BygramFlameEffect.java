package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.Random;

public final class BygramFlameEffect extends View {
    private static final int[] COLORS = {0xffff592e, 0xffff842f, 0xffffb552, 0xffffd18c};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int BOTTOM_DURATION_MS = 1800;
    private static final int DURATION_MS = 2900;
    private final Drawable flame;
    private final String milestone;
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Camera camera = new Camera();
    private final Path outerFlame = new Path();
    private final Path innerFlame = new Path();
    private final Shader halo = new RadialGradient(0, 16, 100, 0x99ff7128, 0x00ff7128, Shader.TileMode.CLAMP);
    private final Shader outerColor = new RadialGradient(-30, 54, 146,
            new int[] {0xffffee91, 0xffff9b22, 0xfff54516, 0xffaf1830}, null, Shader.TileMode.CLAMP);
    private final Shader innerColor = new LinearGradient(-35, 10, 44, 78,
            new int[] {0xfffff7c6, 0xffffba39, 0xfff85515}, null, Shader.TileMode.CLAMP);
    private final Shader numberLight = new LinearGradient(0, -24, 0, 22,
            0xfffffff0, 0xffffc85c, Shader.TileMode.CLAMP);
    private final Spark[] sparks = new Spark[84];
    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    private float time;

    public BygramFlameEffect(FrameLayout chat, int days) {
        super(chat.getContext());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setTranslationZ(AndroidUtilities.dp(24));
        milestone = LocaleController.formatNumber(days, ',');
        numberPaint.setTypeface(AndroidUtilities.bold());
        numberPaint.setTextAlign(Paint.Align.CENTER);
        numberPaint.setTextSize(56);
        numberPaint.setTextSize(56 * Math.min(1, 106 / numberPaint.measureText(milestone)));
        numberPaint.setStrokeJoin(Paint.Join.ROUND);
        camera.setLocation(0, 0, -18);
        flame = ContextCompat.getDrawable(getContext(), R.drawable.filled_fire).mutate();
        Random random = new Random();
        for (int i = 0; i < sparks.length; i++) {
            sparks[i] = new Spark(
                    random.nextFloat(), random.nextFloat() * .37f, .45f + random.nextFloat() * .55f,
                    random.nextFloat() - .5f, AndroidUtilities.dpf2(2 + random.nextFloat() * 12),
                    COLORS[random.nextInt(COLORS.length)]);
        }
        chat.addView(this, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            time = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (getParent() == chat) chat.removeView(BygramFlameEffect.this);
            }
        });
        animator.start();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (height > 0) paint.setShader(new LinearGradient(0, height * .30f, 0, height,
                new int[] {0x00ff662e, 0x50ff6328, 0xccff531c}, new float[] {0, .65f, 1}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float progress = Math.min(1f, time * DURATION_MS / BOTTOM_DURATION_MS);
        float glow = Math.min(1, Math.min(progress * 6f, (1f - progress) * 2.4f));
        paint.setAlpha((int) (150 * glow));
        canvas.drawRect(0, 0, width, height, paint);
        for (Spark spark : sparks) {
            float age = (progress - spark.delay) / (1 - spark.delay);
            if (age <= 0 || age >= 1) continue;
            float rise = (float) Math.pow(age, .78f);
            float x = width * (spark.x + spark.drift * rise * .28f);
            float y = height * (1.04f - spark.rise * rise);
            float radius = spark.radius * (1.22f - age * .6f);
            float alpha = Math.min(1, age * 9) * (1 - age);
            flame.setTint(spark.color);
            flame.setAlpha((int) (255 * alpha));
            flame.setBounds((int) (x - radius), (int) (y - radius), (int) (x + radius), (int) (y + radius));
            flame.draw(canvas);
        }
        drawMilestone(canvas, width, height);
    }

    private void drawMilestone(Canvas canvas, int width, int height) {
        float entrance = Math.min(1f, time * 7f);
        float alpha = Math.min(entrance, (1f - time) * 7f);
        float scale = Math.min(AndroidUtilities.dpf2(1), Math.min(width, height) / 225f);
        float flicker = (float) Math.sin(time * 26f);
        canvas.save();
        canvas.translate(width * .5f, height * .5f);
        float size = scale * (.76f + .24f * entrance);
        canvas.scale(size, size);

        centerPaint.setShader(halo);
        centerPaint.setAlpha(Math.round(alpha * 235));
        canvas.drawCircle(0, 16, 100, centerPaint);

        outerFlame.rewind();
        outerFlame.moveTo(-9 + 6 * flicker, -88);
        outerFlame.cubicTo(37, -59, 36, -12, 50, -21);
        outerFlame.cubicTo(60, -29, 57, -39, 57 + 5 * flicker, -45);
        outerFlame.cubicTo(114, 18, 75, 85, 8, 89);
        outerFlame.cubicTo(-73, 95, -99, 39, -61, -9);
        outerFlame.cubicTo(-60, 18, -45, 21, -42, 7);
        outerFlame.cubicTo(-41, -27, 0, -54, -9 + 6 * flicker, -88);
        outerFlame.close();
        centerPaint.setShader(outerColor);
        centerPaint.setAlpha(Math.round(alpha * 255));
        canvas.drawPath(outerFlame, centerPaint);

        innerFlame.rewind();
        innerFlame.moveTo(12 + 6 * flicker, -42);
        innerFlame.cubicTo(17, -4, 59, 18, 58, 45);
        innerFlame.cubicTo(57, 72, 34, 83, 5, 84);
        innerFlame.cubicTo(-35, 86, -60, 64, -48, 39);
        innerFlame.cubicTo(-35, 50, -31, 31, -27, 16);
        innerFlame.cubicTo(-23, -9, 5, -21, 12 + 6 * flicker, -42);
        innerFlame.close();
        centerPaint.setShader(innerColor);
        canvas.drawPath(innerFlame, centerPaint);

        canvas.translate(0, 22);
        float spin = Math.min(1f, time / .78f);
        float remaining = 1f - spin;
        float angle = 1080f * (1f - remaining * remaining * remaining);
        boolean front = Math.cos(Math.toRadians(angle)) >= 0;
        for (int layer = 7; layer >= 0; layer--) {
            float depth = front ? layer : 7 - layer;
            canvas.save();
            camera.save();
            camera.rotateY(angle);
            camera.translate(0, 0, depth);
            camera.applyToCanvas(canvas);
            camera.restore();
            boolean face = layer == 0;
            numberPaint.setShader(null);
            numberPaint.setColor(face ? 0xff812515 : 0xffa34615);
            numberPaint.setAlpha(Math.round(alpha * 255));
            numberPaint.setStyle(Paint.Style.STROKE);
            numberPaint.setStrokeWidth(face ? 5f : 3f);
            canvas.drawText(milestone, 0, 18, numberPaint);
            numberPaint.setStyle(Paint.Style.FILL);
            if (face) numberPaint.setShader(numberLight);
            canvas.drawText(milestone, 0, 18, numberPaint);
            canvas.restore();
        }
        canvas.restore();
    }

    public void stop() {
        animator.cancel();
    }

    private static final class Spark {
        final float x, delay, rise, drift, radius;
        final int color;

        Spark(float x, float delay, float rise, float drift, float radius, int color) {
            this.x = x;
            this.delay = delay;
            this.rise = rise;
            this.drift = drift;
            this.radius = radius;
            this.color = color;
        }
    }
}
