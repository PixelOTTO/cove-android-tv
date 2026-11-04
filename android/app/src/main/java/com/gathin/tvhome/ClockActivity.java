package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home.
 */
public class ClockActivity extends Activity {

    private static final int IMMERSIVE_FLAGS = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private ClockScene scene;
    private boolean activityResumed;
    private boolean windowFocused;
    private int sceneStartGeneration;

    static int immersiveFlags() {
        return IMMERSIVE_FLAGS;
    }

    /** One launch path for both the settings preview and the Home idle timer. */
    static void launch(Context context) {
        context.startActivity(new Intent(context, ClockActivity.class));
    }

    private void applyImmersiveFlags() {
        getWindow().getDecorView().setSystemUiVisibility(IMMERSIVE_FLAGS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Apply full-screen layout before the clock measures itself, so the top-edge bounce uses
        // the same screen bounds regardless of whether Home timed out or the user tapped Preview.
        applyImmersiveFlags();
        setContentView(R.layout.dream_clock);
        applyImmersiveFlags();
        scene = new ClockScene(getWindow().getDecorView());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        windowFocused = hasFocus;
        if (hasFocus) {
            applyImmersiveFlags();
            startSceneAfterFullscreenLayout();
        } else {
            sceneStartGeneration++;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        windowFocused = getWindow().getDecorView().hasWindowFocus();
        if (windowFocused) startSceneAfterFullscreenLayout();
    }

    /** Wait until the immersive window has completed layout before measuring the first bounce. */
    private void startSceneAfterFullscreenLayout() {
        if (!activityResumed || !windowFocused || scene == null) return;
        final int generation = ++sceneStartGeneration;
        View decor = getWindow().getDecorView();
        applyImmersiveFlags();
        decor.post(() -> {
            if (generation != sceneStartGeneration || !activityResumed || !windowFocused || scene == null) return;
            applyImmersiveFlags();
            decor.post(() -> {
                if (generation == sceneStartGeneration && activityResumed && windowFocused && scene != null) {
                    scene.start();
                }
            });
        });
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        sceneStartGeneration++;
        scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The key that wakes the clock should not also act on Home, so it is consumed here.
        if (event.getAction() == KeyEvent.ACTION_UP) finish();
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
