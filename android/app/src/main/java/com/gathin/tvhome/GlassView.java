package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: the white glow stays faint, a coloured one needs more alpha to read as warm light. */
    private int glowAlpha = 0x30;
    private boolean glowEnabled = true;
    private boolean outlineOnly;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Tints the rim and the outer glow; 0 restores the plain white glass. */
    void setAccent(int argb) {
        if (argb == 0) {
            glowRgb = 0xFFFFFF;
            glowAlpha = 0x30;
            rim.setColor(0xCCFFFFFF);
        } else {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            // Keep the outline saturated when the separate outer glow is switched off.
            rim.setColor(0xE6000000 | glowRgb);
        }
        invalidate();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    float getRadius() {
        return radius;
    }

    void setGlowEnabled(boolean enabled) {
        if (glowEnabled == enabled) return;
        glowEnabled = enabled;
        invalidate();
    }

    void setOutlineOnly(boolean enabled) {
        if (outlineOnly == enabled) return;
        outlineOnly = enabled;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        if (!outlineOnly) {
            // 1. Outer Shadow: a soft, dark blur that gives the glass depth
            Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setStyle(Paint.Style.STROKE);
            shadow.setStrokeWidth(4f * density);
            shadow.setColor(0x40000000);
            canvas.drawRoundRect(rect.left - 2f, rect.top - 2f, rect.right + 2f, rect.bottom + 2f, radius, radius, shadow);
        }

        // 2. Outer glow: thin rings across the inset, each a little fainter
        if (glowEnabled) {
            int rings = 12;
            float step = inset / rings;
            glow.setStrokeWidth(step + 0.5f);
            for (int i = 0; i < rings; i++) {
                float o = step * (i + 0.5f);
                float t = 1f - (float) i / rings;
                glow.setColor(Math.round(glowAlpha * t) << 24 | glowRgb);
                canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
            }
        }

        // Footer controls keep their own background; draw only the colored focus edge and glow over them.
        if (outlineOnly) {
            canvas.drawRoundRect(rect, radius, radius, rim);
            return;
        }

        // 3. Main glass body
        canvas.drawRoundRect(rect, radius, radius, fill);

        // 4. Specular Sheen: the top-down light reflection
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        // 5. Inner Edge Highlight: a bright, thin white rim to define the glass edge
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1f * density);
        edge.setColor(0x80FFFFFF);
        canvas.drawRoundRect(rect, radius, radius, edge);

        // 6. Outer Rim: the colored accent rim
        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
