package com.gathin.tvhome;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Draws a shortcut icon a few pixels lower without shifting its label or changing layout size. */
final class DownshiftDrawable extends Drawable implements Drawable.Callback {
    private final Drawable child;
    private final int offsetPx;

    DownshiftDrawable(Drawable child, int offsetPx) {
        this.child = child;
        this.offsetPx = offsetPx;
        child.setCallback(this);
    }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(0, offsetPx);
        child.setBounds(getBounds());
        child.draw(canvas);
        canvas.restoreToCount(save);
    }

    @Override protected void onBoundsChange(Rect bounds) { child.setBounds(bounds); }
    @Override public int getIntrinsicWidth() { return child.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return child.getIntrinsicHeight(); }
    @Override public int getOpacity() { return child.getOpacity(); }
    @Override public boolean isStateful() { return child.isStateful(); }
    @Override protected boolean onStateChange(int[] state) { return child.setState(state); }
    @Override public void setAlpha(int alpha) { child.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter colorFilter) { child.setColorFilter(colorFilter); }
    @Override public void setTint(int tintColor) { child.setTint(tintColor); }
    @Override public void setTintList(ColorStateList tint) { child.setTintList(tint); }
    @Override public void setTintMode(PorterDuff.Mode tintMode) { child.setTintMode(tintMode); }
    @Override public void jumpToCurrentState() { child.jumpToCurrentState(); }
    @Override public void invalidateDrawable(Drawable who) { invalidateSelf(); }
    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }
    @Override public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
}
