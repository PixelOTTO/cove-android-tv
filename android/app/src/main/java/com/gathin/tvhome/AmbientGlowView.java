package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Professional ambient glow: a soft radial gradient that follows the focused tile.
 * Instead of a flat color fade, it creates a "light leak" effect.
 */
public class AmbientGlowView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float centerX, centerY;
    private int glowColor = 0;
    private float radius = 0;

    public AmbientGlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Updates the glow position and color.
     * @param x The center X coordinate of the focused tile.
     * @param y The center Y coordinate of the focused tile.
     * @param color The accent color of the app.
     */
    public void updateGlow(float x, float y, int color) {
        this.centerX = x;
        this.centerY = y;
        this.glowColor = color;
        
        // The radius is usually a large portion of the screen to ensure a soft fade.
        this.radius = Math.max(getWidth(), getHeight()) * 0.8f;
        
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (glowColor == 0) return;

        // MAX VIBE: High-intensity radial gradient.
        // We use a very bright center and a wide, soft falloff.
        RadialGradient gradient = new RadialGradient(
                centerX, centerY,
                radius * 1.5f, // Increased radius for a wider spread
                new int[]{glowColor, 0x66000000, Color.TRANSPARENT},
                new float[]{0f, 0.2f, 1f},
                Shader.TileMode.CLAMP
        );

        paint.setShader(gradient);
        // Draw multiple times with different scales to create a "layered" light effect
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
