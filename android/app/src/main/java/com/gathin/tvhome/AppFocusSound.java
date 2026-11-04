package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

/** The short CoveTV focus cue used while moving through launcher controls. */
final class AppFocusSound implements AutoCloseable {
    static final String PREFERENCE_KEY = "app_focus_sound";
    static final boolean DEFAULT_ENABLED = true;

    private final SoundPool soundPool;
    private final Context context;
    private int soundId;
    private boolean loaded;
    private boolean loadFailed;
    private boolean pendingPlay;
    private boolean closed;
    private MediaPlayer fallbackPlayer;

    AppFocusSound(Context context) {
        this.context = context.getApplicationContext();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                // Route through the TV's regular audio output; some TV firmwares mute UI-sonification usage.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();
        soundPool.setOnLoadCompleteListener((pool, sample, status) -> {
            if (closed || sample != soundId) return;
            loaded = status == 0;
            loadFailed = status != 0;
            if (pendingPlay) {
                pendingPlay = false;
                if (loaded) playLoaded();
                else playFallback();
            }
        });
        soundId = soundPool.load(context, R.raw.cove_focus, 1);
    }

    void play() {
        if (closed) return;
        if (loadFailed) {
            playFallback();
            return;
        }
        if (!loaded) {
            pendingPlay = true;
            return;
        }
        playLoaded();
    }

    private void playLoaded() {
        int stream = soundPool.play(soundId, 0.9f, 0.9f, 1, 0, 1f);
        if (stream == 0) playFallback();
    }

    /** MediaPlayer fallback for TV firmware that cannot play the supplied MP3 through SoundPool. */
    private void playFallback() {
        if (closed) return;
        try {
            if (fallbackPlayer == null) {
                fallbackPlayer = MediaPlayer.create(context, R.raw.cove_focus);
                if (fallbackPlayer == null) return;
                fallbackPlayer.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
                fallbackPlayer.setVolume(1f, 1f);
                fallbackPlayer.setOnCompletionListener(player -> {
                    try { player.seekTo(0); } catch (IllegalStateException ignored) { }
                });
            }
            if (fallbackPlayer.isPlaying()) fallbackPlayer.seekTo(0);
            else {
                fallbackPlayer.seekTo(0);
                fallbackPlayer.start();
            }
        } catch (IllegalStateException ignored) {
            // Audio is optional; a device that has no active output must not disrupt launcher navigation.
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        soundPool.release();
        if (fallbackPlayer != null) {
            fallbackPlayer.release();
            fallbackPlayer = null;
        }
    }
}
