package com.gathin.tvhome;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): a clock and date bounce around a black screen like
 * the classic DVD logo. The clock changes colour when it reaches an edge, keeping the screen mostly black.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long FRAME_MS = 16L;
    /** The clock's measured bounds can reach the full-screen layout edge. */
    private static final int EDGE_INSET_DP = 0;
    private static final float SPEED_X_DP_PER_SECOND = 92f;
    private static final float SPEED_Y_DP_PER_SECOND = 68f;
    /** Familiar DVD-logo colours, applied to the time on each wall hit. */
    private static final int[] PALETTE = {
            0xFFF5F7FF, 0xFF54E5FF, 0xFFB594FF, 0xFFFF8BD5, 0xFFFFCC68, 0xFF89E8C0
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Rect clockGlyphBounds = new Rect();
    private final int[] parentScreenLocation = new int[2];
    private final View content;
    private final TextView clockText;
    private String lastClockText = "";
    private float clockGlyphTopInContent;
    private int colorIndex;
    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private long lastFrameAt;
    private boolean running;
    private boolean positioned;
    private int startGeneration;
    private Runnable pendingStart;

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            moveClock();
            handler.postDelayed(this, FRAME_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        clockText = root.findViewById(R.id.dreamClock);
        ClockFormat.applyScreensaver(root.getContext(), root);
        colorIndex = random.nextInt(PALETTE.length);
        setClockColor(PALETTE[colorIndex]);
    }

    void start() {
        final int generation = ++startGeneration;
        running = true;
        positioned = false;
        handler.removeCallbacks(frame);
        content.animate().cancel();
        content.setAlpha(0f);
        if (pendingStart != null) content.removeCallbacks(pendingStart);
        pendingStart = new Runnable() {
            @Override
            public void run() {
                if (!running || generation != startGeneration) return;
                View parent = (View) content.getParent();
                if (parent.getWidth() <= 0 || parent.getHeight() <= 0
                        || content.getWidth() <= 0 || content.getHeight() <= 0) {
                    content.postDelayed(this, 100L);
                    return;
            }
                pendingStart = null;
                placeClock(parent);
                content.animate().alpha(1f).setDuration(1200).start();
                lastFrameAt = SystemClock.uptimeMillis();
                handler.postDelayed(frame, FRAME_MS);
            }
        };
        content.post(pendingStart);
    }

    void stop() {
        running = false;
        startGeneration++;
        if (pendingStart != null) {
            content.removeCallbacks(pendingStart);
            pendingStart = null;
        }
        handler.removeCallbacks(frame);
        content.animate().cancel();
    }

    private void moveClock() {
        View parent = (View) content.getParent();
        if (parent.getWidth() <= 0 || parent.getHeight() <= 0
                || content.getWidth() <= 0 || content.getHeight() <= 0) return;

        long now = SystemClock.uptimeMillis();
        float seconds = Math.min(0.05f, Math.max(0f, (now - lastFrameAt) / 1000f));
        lastFrameAt = now;

        float margin = dp(EDGE_INSET_DP);
        float maxX = Math.max(0f, parent.getWidth() - content.getWidth() - margin);
        float maxY = Math.max(0f, parent.getHeight() - content.getHeight() - margin);
        float minX = Math.min(margin, maxX);
        float minY = topBounceY(parent, maxY);

        if (!positioned) {
            placeClock(parent);
            return;
        }

        // Keep the whole clock within the screen if the time text changes width at a minute boundary.
        if (x < minX || x > maxX || y < minY || y > maxY) {
            x = Math.max(minX, Math.min(maxX, x));
            y = Math.max(minY, Math.min(maxY, y));
        }

        float nextX = x + velocityX * seconds;
        float nextY = y + velocityY * seconds;
        boolean hitWall = false;
        if (nextX < minX) {
            x = minX + (minX - nextX);
            velocityX = Math.abs(velocityX);
            hitWall = true;
        } else if (nextX > maxX) {
            x = maxX - (nextX - maxX);
            velocityX = -Math.abs(velocityX);
            hitWall = true;
        } else {
            x = nextX;
        }
        if (nextY < minY) {
            y = minY + (minY - nextY);
            velocityY = Math.abs(velocityY);
            hitWall = true;
        } else if (nextY > maxY) {
            y = maxY - (nextY - maxY);
            velocityY = -Math.abs(velocityY);
            hitWall = true;
        } else {
            y = nextY;
        }
        x = Math.max(minX, Math.min(maxX, x));
        y = Math.max(minY, Math.min(maxY, y));
        content.setX(x);
        content.setY(y);
        if (hitWall) changeClockColor();
    }

    /** Start well inside the screen, then let the clock glide into its first edge bounce. */
    private void placeClock(View parent) {
        float margin = dp(EDGE_INSET_DP);
        float maxX = Math.max(0f, parent.getWidth() - content.getWidth() - margin);
        float maxY = Math.max(0f, parent.getHeight() - content.getHeight() - margin);
        float minX = Math.min(margin, maxX);
        float minY = topBounceY(parent, maxY);
        x = minX + Math.max(0f, maxX - minX) * 0.22f;
        y = minY + Math.max(0f, maxY - minY) * 0.26f;
        float density = content.getResources().getDisplayMetrics().density;
        velocityX = SPEED_X_DP_PER_SECOND * density;
        // Take the first pass toward the top so the marked edge is reached promptly after preview starts.
        velocityY = -SPEED_Y_DP_PER_SECOND * density;
        positioned = true;
        content.setX(x);
        content.setY(y);
        lastFrameAt = SystemClock.uptimeMillis();
    }

    /**
     * The layout's parent can start below Android's system-bar inset, and the visible digits sit below the
     * TextView's top. Offset the container by both so the drawn clock ink reaches physical screen row zero.
     */
    private float topBounceY(View parent, float maxY) {
        updateClockGlyphTop();
        parent.getLocationOnScreen(parentScreenLocation);
        float minY = -parentScreenLocation[1] - clockGlyphTopInContent;
        return Math.min(minY, maxY);
    }

    private void updateClockGlyphTop() {
        if (clockText == null) return;
        CharSequence text = clockText.getText();
        String currentText = text == null ? "" : text.toString();
        if (currentText.equals(lastClockText)) return;
        lastClockText = currentText;
        if (currentText.isEmpty()) return;

        clockGlyphBounds.setEmpty();
        clockText.getPaint().getTextBounds(currentText, 0, currentText.length(), clockGlyphBounds);
        float top = clockText.getBaseline() + clockGlyphBounds.top;
        View current = clockText;
        while (current != content) {
            top += current.getTop() + current.getTranslationY();
            ViewParent currentParent = current.getParent();
            if (!(currentParent instanceof View)) break;
            current = (View) currentParent;
        }
        clockGlyphTopInContent = top;
    }

    private void changeClockColor() {
        colorIndex = (colorIndex + 1) % PALETTE.length;
        setClockColor(PALETTE[colorIndex]);
    }

    private void setClockColor(int color) {
        tintClockText(content, color);
    }

    private void tintClockText(View view, int color) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            float density = content.getResources().getDisplayMetrics().density;
            int alpha = text.getTextSize() >= 60f * density ? 0xFF
                    : text.getTextSize() >= 20f * density ? 0xB0 : 0x8C;
            text.setTextColor(withAlpha(color, alpha));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintClockText(group.getChildAt(i), color);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private float dp(float value) {
        return value * content.getResources().getDisplayMetrics().density;
    }
}
