package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private static final int BACKUP_SETTINGS_REQUEST = 41;
    private static final int RESTORE_SETTINGS_REQUEST = 42;
    private static final int MAX_BACKUP_BYTES = 1024 * 1024;
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private static final int[] FOCUS_ACCENTS = {0, 0x75AFFF, 0xB79CFF, 0xFF93A4, 0x70DEC0, 0xFFD074};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private TextView wallpaperScheduleChoice;
    private TextView themeChoice;
    private TextView weatherLocationChoice;
    private TextView weatherUnitChoice;
    private TextView dockNamesChoice;
    private TextView focusSpeedChoice;
    private TextView focusAccentChoice;
    private TextView featuredShelfChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView focusHighlightChoice;
    private TextView focusSoundChoice;
    private TextView followThemeChoice;
    private TextView automaticUpdatesChoice;
    private TextView clockFormatChoice;
    private TextView homeScreensaverChoice;
    private TextView testScreensaverChoice;
    private AppFocusSound focusSound;
    private View focusSoundRoot;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        wallpaperScheduleChoice = findViewById(R.id.wallpaperScheduleChoice);
        wallpaperScheduleChoice.setOnClickListener(v -> showWallpaperSchedulePicker());
        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> showThemePicker());
        weatherLocationChoice = findViewById(R.id.weatherLocationChoice);
        weatherLocationChoice.setOnClickListener(v -> showWeatherCityPrompt());
        weatherUnitChoice = findViewById(R.id.weatherUnitChoice);
        weatherUnitChoice.setOnClickListener(v -> showChoiceDialog(
                getString(R.string.weather_unit_title), "Choose how temperatures appear on the weather card.",
                new String[]{getString(R.string.unit_fahrenheit), getString(R.string.unit_celsius)},
                WeatherClient.useFahrenheit(this) ? 0 : 1, which -> {
                    choices.edit().putString(WeatherClient.UNIT_KEY, which == 0 ? "fahrenheit" : "celsius")
                            .remove("weather_updated").apply();
                    changed();
                    refresh();
                }));
        dockNamesChoice = findViewById(R.id.dockNamesChoice);
        dockNamesChoice.setOnClickListener(v -> {
            choices.edit().putBoolean("always_show_names", !choices.getBoolean("always_show_names", config.alwaysShowNames)).apply();
            changed();
            refresh();
        });
        focusSpeedChoice = findViewById(R.id.focusSpeedChoice);
        focusSpeedChoice.setOnClickListener(v -> {
            String[] speeds = {getString(R.string.focus_speed_slow), getString(R.string.focus_speed_normal), getString(R.string.focus_speed_fast)};
            int current = choices.getInt("focus_speed", 100);
            int checked = current <= 80 ? 0 : current >= 120 ? 2 : 1;
            new AlertDialog.Builder(this).setTitle(R.string.focus_speed_title)
                    .setSingleChoiceItems(speeds, checked, (dialog, which) -> {
                        choices.edit().putInt("focus_speed", which == 0 ? 75 : which == 2 ? 130 : 100).apply();
                        changed();
                        dialog.dismiss();
                        refresh();
                    }).show();
        });
        focusAccentChoice = findViewById(R.id.focusAccentChoice);
        focusAccentChoice.setOnClickListener(v -> {
            int selected = indexOf(FOCUS_ACCENTS, choices.getInt("focus_accent", 0));
            new AlertDialog.Builder(this).setTitle(R.string.focus_accent_title)
                    .setSingleChoiceItems(R.array.focus_accent_options, selected, (dialog, which) -> {
                        choices.edit().putInt("focus_accent", FOCUS_ACCENTS[which]).apply();
                        changed();
                        dialog.dismiss();
                        refresh();
                    }).show();
        });
        featuredShelfChoice = findViewById(R.id.featuredShelfChoice);
        featuredShelfChoice.setOnClickListener(v -> {
            int selected = !choices.contains("cinematic_home") ? 0
                    : choices.getBoolean("cinematic_home", true) ? 1 : 2;
            new AlertDialog.Builder(this).setTitle(R.string.featured_shelf_title)
                    .setSingleChoiceItems(R.array.featured_shelf_options, selected, (dialog, which) -> {
                        if (which == 0) choices.edit().remove("cinematic_home").apply();
                        else choices.edit().putBoolean("cinematic_home", which == 1).apply();
                        changed();
                        dialog.dismiss();
                        refresh();
                    }).show();
        });
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        if (!choices.contains("focus_glow") && choices.contains("focus_highlight")) {
            choices.edit().putBoolean("focus_glow", choices.getBoolean("focus_highlight", true))
                    .remove("focus_highlight").apply();
        }
        focusHighlightChoice = findViewById(R.id.focusHighlightChoice);
        focusHighlightChoice.setOnClickListener(v -> {
            boolean enabled = !choices.getBoolean("focus_glow", true);
            choices.edit().putBoolean("focus_glow", enabled).apply();
            changed();
            refresh();
        });
        focusSoundChoice = findViewById(R.id.focusSoundChoice);
        focusSoundChoice.setOnClickListener(v -> showFocusSoundChoices());
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            clearWallpaperSchedules();
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove("focus_highlight").remove("focus_glow")
                    .remove(Config.LIQUID_GLASS_KEY).remove("always_show_names").remove("focus_speed")
                    .remove("focus_accent").remove("cinematic_home")
                    .remove(AppFocusSound.PREFERENCE_KEY)
                    .remove(Config.HOME_IDLE_CLOCK_MINUTES_KEY).remove(Config.HOME_IDLE_CLOCK_CUSTOM_KEY)
                    .remove(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY)
                    .remove(ClockFormat.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> showDefaultHomeChooser());
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        findViewById(R.id.backupSettingsChoice).setOnClickListener(v -> createSettingsBackup());
        findViewById(R.id.restoreSettingsChoice).setOnClickListener(v -> chooseSettingsBackup());
        addTimeAndScreensaverChoices(config.homeIdleClockMinutes);
        addMaintenanceHeading();
        addAutomaticUpdatesChoice();
        focusSoundRoot = findViewById(android.R.id.content);
        focusSoundRoot.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (oldFocus == null || newFocus == null || oldFocus == newFocus
                    || !choices.getBoolean(AppFocusSound.PREFERENCE_KEY, AppFocusSound.DEFAULT_ENABLED)) return;
            newFocus.setSoundEffectsEnabled(false);
            getFocusSound().play();
        });
        if (choices.getBoolean(AppFocusSound.PREFERENCE_KEY, AppFocusSound.DEFAULT_ENABLED)) {
            disablePlatformFocusSounds(focusSoundRoot);
            getFocusSound();
        }
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void createSettingsBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "Cove-settings.json");
        try {
            startActivityForResult(intent, BACKUP_SETTINGS_REQUEST);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private void chooseSettingsBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "Cove-settings.json");
        try {
            startActivityForResult(intent, RESTORE_SETTINGS_REQUEST);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != BACKUP_SETTINGS_REQUEST && requestCode != RESTORE_SETTINGS_REQUEST) return;
        View returnFocus = findViewById(requestCode == BACKUP_SETTINGS_REQUEST
                ? R.id.backupSettingsChoice : R.id.restoreSettingsChoice);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            returnFocus.requestFocus();
            return;
        }
        Uri uri = data.getData();
        if (requestCode == BACKUP_SETTINGS_REQUEST) {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("No file writer");
                output.write(LauncherSettingsBackup.create(choices).toString(2).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, R.string.settings_backup_saved, Toast.LENGTH_SHORT).show();
            } catch (IOException | JSONException | SecurityException e) {
                Toast.makeText(this, R.string.settings_backup_failed, Toast.LENGTH_LONG).show();
            }
            returnFocus.requestFocus();
            return;
        }
        try {
            JSONObject values = LauncherSettingsBackup.parse(readSettingsBackup(uri));
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.settings_restore_title)
                    .setMessage(R.string.settings_restore_message)
                    .setPositiveButton(R.string.settings_restore, (d, which) -> restoreSettings(values))
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
            dialog.setOnDismissListener(ignored -> returnFocus.requestFocus());
            dialog.show();
        } catch (IOException | JSONException | SecurityException e) {
            Toast.makeText(this, R.string.settings_file_unsupported, Toast.LENGTH_LONG).show();
            returnFocus.requestFocus();
        }
    }

    private String readSettingsBackup(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("No file reader");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_BACKUP_BYTES) throw new IOException("Settings file is too large");
                bytes.write(buffer, 0, count);
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void restoreSettings(JSONObject values) {
        if (!LauncherSettingsBackup.restore(choices, values)) {
            Toast.makeText(this, R.string.settings_restore_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        setResult(RESULT_OK);
        refresh();
        Toast.makeText(this, R.string.settings_restore_done, Toast.LENGTH_SHORT).show();
        findViewById(R.id.restoreSettingsChoice).requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<BundledThemes.WallpaperOption> images = BundledThemes.wallpaperOptions(this, dir);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).label;
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOfWallpaper(images, current);
        showCoveListDialog(getString(R.string.wallpaper_label),
                "Choose an image for your home screen.", labels, checked,
                getString(R.string.wallpaper_theme_default), () -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                }, which -> {
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        BundledThemes.WallpaperOption selected = images.get(which);
                        if (!BundledThemes.selectWallpaper(this, dir, selected)) {
                            Toast.makeText(this, R.string.wallpaper_failed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                selected.choiceName).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                });
    }

    private void showWeatherCityPrompt() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setTextColor(0xFFF4F6FA);
        input.setHintTextColor(0xFF8D98AC);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        input.setTypeface(getResources().getFont(R.font.outfit_regular));
        input.setPaddingRelative(dp(18), 0, dp(18), 0);
        input.setMinHeight(dp(54));
        input.setBackgroundResource(R.drawable.settings_choice_bg);
        String current = WeatherClient.city(this);
        if (current != null) input.setText(current.split(",")[0]);
        input.setHint(R.string.weather_location_not_set);
        LinearLayout panel = newDialogPanel();
        addDialogHeader(panel, getString(R.string.weather_location), getString(R.string.weather_city_prompt));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(8);
        panel.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        actionsParams.topMargin = dp(10);
        panel.addView(actions, actionsParams);
        TextView weatherOff = newDialogAction(getString(R.string.weather_off));
        TextView cancel = newDialogAction(getString(android.R.string.cancel));
        TextView search = newDialogAction(getString(R.string.search));
        search.setTextColor(0xFF70E1E8);
        addWeightedAction(actions, weatherOff);
        addWeightedAction(actions, cancel);
        addWeightedAction(actions, search);
        weatherOff.setOnClickListener(v -> {
            dismissChoiceDialog(panel);
            WeatherClient.clearPlace(this);
            changed();
            refresh();
        });
        cancel.setOnClickListener(v -> dismissChoiceDialog(panel));
        search.setOnClickListener(v -> {
            String query = input.getText().toString().trim();
            dismissChoiceDialog(panel);
            searchWeatherCity(query);
        });
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            String query = input.getText().toString().trim();
            dismissChoiceDialog(panel);
            searchWeatherCity(query);
            return true;
        });
        showCoveDialog(panel, input, 760);
    }

    private void searchWeatherCity(String query) {
        if (query.isEmpty()) return;
        Toast.makeText(this, R.string.weather_searching, Toast.LENGTH_SHORT).show();
        WeatherClient.searchCity(query, (places, error) -> {
            if (isFinishing() || isDestroyed()) return;
            if (error != null) {
                Toast.makeText(this, R.string.weather_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            if (places.isEmpty()) {
                Toast.makeText(this, R.string.weather_no_cities, Toast.LENGTH_LONG).show();
                return;
            }
            String[] names = new String[places.size()];
            for (int i = 0; i < places.size(); i++) names[i] = places.get(i).label;
            showCoveListDialog(getString(R.string.weather_location),
                    "Choose the matching city for your forecast.", names, -1,
                    null, null, index -> {
                        WeatherClient.savePlace(this, places.get(index));
                        changed();
                        refresh();
                    });
        });
    }

    private void showWallpaperSchedulePicker() {
        String[] moods = WallpaperMood.ALL;
        int[] labels = {R.string.good_morning, R.string.good_afternoon, R.string.good_evening, R.string.good_night};
        String[] names = new String[labels.length];
        for (int i = 0; i < labels.length; i++) names[i] = getString(labels[i]);
        showCoveListDialog(getString(R.string.wallpaper_schedule),
                "Choose which time of day you want to personalize.", names, -1,
                null, null, which -> showWallpaperForMood(moods[which], names[which]));
    }

    private void showWallpaperForMood(String mood, String label) {
        File dir = Config.dir(this);
        List<BundledThemes.WallpaperOption> images = BundledThemes.wallpaperOptions(this, dir);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).label;
        labels[images.size()] = getString(R.string.wallpaper_theme_default);
        String key = "wallpaper_mood_" + mood;
        String current = choices.getString(key, null);
        int checked = current == null ? images.size() : indexOfWallpaper(images, current);
        showCoveListDialog(label, "Choose a wallpaper for " + label.toLowerCase(java.util.Locale.ROOT) + ".",
                labels, checked, null, null, which -> {
                    if (which == images.size()) {
                        choices.edit().remove(key).apply();
                    } else {
                        BundledThemes.WallpaperOption selected = images.get(which);
                        if (!BundledThemes.selectWallpaper(this, dir, selected)) {
                            Toast.makeText(this, R.string.wallpaper_failed, Toast.LENGTH_LONG).show();
                            return;
                        }
                        choices.edit().putString(key, selected.choiceName).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                });
    }
    private void showThemePicker() {
        String[] names = BundledThemes.names();
        String[] labels = new String[names.length + 1];
        for (int i = 0; i < names.length; i++) labels[i] = BundledThemes.label(names[i]);
        labels[names.length] = getString(R.string.wallpaper_by_time_of_day);
        int checked = wallpaperByTimeOfDay() ? names.length : indexOfName(names,
                choices.getString(BundledThemes.CURRENT_THEME_KEY, "scenes"));
        showCoveListDialog(getString(R.string.theme_choice),
                "Pick a look for CoveTV. The list scrolls for more themes.", labels, checked,
                null, null, which -> {
                    if (which == names.length) enableWallpaperSchedule();
                    else confirmTheme(names[which]);
                });
    }

    private void enableWallpaperSchedule() {
        choices.edit().putBoolean("wallpaper_time_of_day", true).apply();
        changed();
        refresh();
    }
    private void confirmTheme(String name) {
        LinearLayout panel = newDialogPanel();
        addDialogHeader(panel, BundledThemes.label(name), getString(R.string.theme_apply_warning));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        actionsParams.topMargin = dp(8);
        panel.addView(actions, actionsParams);

        TextView credits = newDialogAction("Credits");
        TextView cancel = newDialogAction(getString(android.R.string.cancel));
        TextView apply = newDialogAction(getString(android.R.string.ok));
        apply.setTextColor(0xFF70E1E8);
        addWeightedAction(actions, credits);
        addWeightedAction(actions, cancel);
        addWeightedAction(actions, apply);

        credits.setOnClickListener(v -> {
            dismissChoiceDialog(panel);
            showThemeCredits(name);
        });
        cancel.setOnClickListener(v -> dismissChoiceDialog(panel));
        apply.setOnClickListener(v -> {
            dismissChoiceDialog(panel);
            applyTheme(name);
        });
        showCoveDialog(panel, apply, 760);
    }

    private void showThemeCredits(String name) {
        TextView text = new TextView(this);
        text.setText(BundledThemes.credits(this, name));
        text.setTextColor(0xFFD6DEEC);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTypeface(getResources().getFont(R.font.outfit_regular));
        text.setLineSpacing(dp(4), 1f);
        text.setAutoLinkMask(Linkify.WEB_URLS);
        text.setLinksClickable(true);
        text.setMovementMethod(LinkMovementMethod.getInstance());

        LinearLayout panel = newDialogPanel();
        addDialogHeader(panel, BundledThemes.label(name) + " credits",
                "Sources, licenses, and artwork credits for this theme.");

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int scrollHeight = Math.max(dp(150), Math.min(dp(430), screenHeight - dp(280)));
        LinearLayout scrollRow = new LinearLayout(this);
        scrollRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams scrollRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, scrollHeight);
        scrollRowParams.topMargin = dp(2);
        panel.addView(scrollRow, scrollRowParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        text.setPadding(dp(4), dp(4), dp(10), dp(4));
        scroll.addView(text, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollRow.addView(scroll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout scrollTrack = new FrameLayout(this);
        scrollTrack.setBackground(roundBackground(0xFF36404E, 4));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(dp(6),
                ViewGroup.LayoutParams.MATCH_PARENT);
        trackParams.setMarginStart(dp(12));
        trackParams.topMargin = dp(4);
        trackParams.bottomMargin = dp(4);
        scrollRow.addView(scrollTrack, trackParams);
        View scrollThumb = new View(this);
        scrollThumb.setBackground(roundBackground(0xFF65D9E5, 4));
        scrollTrack.addView(scrollThumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42), Gravity.TOP));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        footerParams.topMargin = dp(8);
        panel.addView(footer, footerParams);
        TextView back = newDialogAction("Back");
        back.setTextColor(0xFF70E1E8);
        footer.addView(back, new LinearLayout.LayoutParams(dp(150),
                ViewGroup.LayoutParams.MATCH_PARENT));
        back.setOnClickListener(v -> {
            dismissChoiceDialog(panel);
            confirmTheme(name);
        });

        AlertDialog dialog = showCoveDialog(panel, back, 760);
        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) ->
                updateScrollIndicator(scroll, scrollTrack, scrollThumb));
        scroll.post(() -> updateScrollIndicator(scroll, scrollTrack, scrollThumb));
    }

    private void applyTheme(String name) {
        File dir = Config.dir(this);
        clearWallpaperSchedules();
        if (!BundledThemes.apply(this, dir, name)) {
            Toast.makeText(this, R.string.theme_apply_failed, Toast.LENGTH_LONG).show();
            return;
        }
        choices.edit().putString(BundledThemes.CURRENT_THEME_KEY, name)
                .remove("columns").remove("animations").remove("advanced_effects")
                .remove(Config.ROWS_BELOW_DOCK_KEY).remove(Config.WALLPAPER_CHOICE_KEY)
                .remove(Config.DOCK_OPACITY_KEY).remove(Config.HOME_IDLE_CLOCK_MINUTES_KEY)
                .remove(Config.HOME_IDLE_CLOCK_CUSTOM_KEY)
                .remove(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY)
                .remove(ClockFormat.KEY).apply();
        changed();
        Toast.makeText(this, R.string.theme_applied, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static int indexOfWallpaper(List<BundledThemes.WallpaperOption> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).choiceName.equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void showFocusSoundChoices() {
        boolean enabled = choices.getBoolean(AppFocusSound.PREFERENCE_KEY, AppFocusSound.DEFAULT_ENABLED);
        new AlertDialog.Builder(this)
                .setTitle(R.string.focus_sound_title)
                .setSingleChoiceItems(new String[]{getString(R.string.on), getString(R.string.off)}, enabled ? 0 : 1,
                        (dialog, which) -> {
                            boolean selected = which == 0;
                            if (selected != enabled) {
                                choices.edit().putBoolean(AppFocusSound.PREFERENCE_KEY, selected).apply();
                                changed();
                                refresh();
                                if (selected) {
                                    disablePlatformFocusSounds(focusSoundRoot);
                                    getFocusSound();
                                } else {
                                    closeFocusSound();
                                }
                            }
                            dialog.dismiss();
                            if (selected) playFocusSoundPreview();
                        })
                .setNeutralButton(R.string.focus_sound_preview, (dialog, which) -> playFocusSoundPreview())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void playFocusSoundPreview() {
        getFocusSound().play();
    }

    private AppFocusSound getFocusSound() {
        if (focusSound == null) focusSound = new AppFocusSound(this);
        return focusSound;
    }

    private void closeFocusSound() {
        if (focusSound != null) {
            focusSound.close();
            focusSound = null;
        }
    }

    private static void disablePlatformFocusSounds(View view) {
        view.setSoundEffectsEnabled(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disablePlatformFocusSounds(group.getChildAt(i));
            }
        }
    }

    @Override
    protected void onDestroy() {
        closeFocusSound();
        super.onDestroy();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    /** Add this row at runtime so it also appears in the resource-stable sideload build. */
    private void addAutomaticUpdatesChoice() {
        View anchor = findViewById(R.id.restoreSettingsChoice);
        if (!(anchor.getParent() instanceof LinearLayout)) return;
        LinearLayout section = (LinearLayout) anchor.getParent();
        automaticUpdatesChoice = newChoiceRow();
        section.addView(automaticUpdatesChoice, settingRowParams(8));
        automaticUpdatesChoice.setOnClickListener(v -> {
            boolean enabled = !choices.getBoolean(LauncherUpdater.AUTO_CHECK_KEY, true);
            choices.edit().putBoolean(LauncherUpdater.AUTO_CHECK_KEY, enabled).apply();
            refresh();
        });
    }

    /** Keep clock and screensaver controls together, above the TV and maintenance actions. */
    private void addTimeAndScreensaverChoices(int themeMinutes) {
        View anchor = findViewById(R.id.tvSettingsChoice);
        if (!(anchor.getParent() instanceof LinearLayout)) return;
        LinearLayout section = (LinearLayout) anchor.getParent();

        int insertAt = section.indexOfChild(anchor) + 1;
        section.addView(newSettingsHeading("TIME & SCREENSAVER"), insertAt++, headingParams(18));

        clockFormatChoice = newChoiceRow();
        homeScreensaverChoice = newChoiceRow();
        testScreensaverChoice = newChoiceRow();
        section.addView(clockFormatChoice, insertAt++, settingRowParams(8));
        section.addView(homeScreensaverChoice, insertAt++, settingRowParams(8));
        section.addView(testScreensaverChoice, insertAt, settingRowParams(8));
        clockFormatChoice.setOnClickListener(v -> showClockFormatPicker());
        homeScreensaverChoice.setOnClickListener(v -> showScreensaverPicker(themeMinutes));
        testScreensaverChoice.setText("Preview screensaver");
        testScreensaverChoice.setOnClickListener(v -> ClockActivity.launch(this));
    }

    private void addMaintenanceHeading() {
        View anchor = findViewById(R.id.backupSettingsChoice);
        if (!(anchor.getParent() instanceof LinearLayout)) return;
        LinearLayout section = (LinearLayout) anchor.getParent();
        section.addView(newSettingsHeading("BACKUP & UPDATES"), section.indexOfChild(anchor), headingParams(18));
    }

    private TextView newSettingsHeading(String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(0xFFF4F6FA);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        heading.setLetterSpacing(0.14f);
        heading.setTypeface(getResources().getFont(R.font.outfit_medium));
        return heading;
    }

    private LinearLayout.LayoutParams headingParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams settingRowParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private void showClockFormatPicker() {
        String[] options = {"Follow TV setting", "12-hour clock", "24-hour clock"};
        showChoiceDialog("Clock format", "Choose how time appears on the home screen and screensaver.",
                options, ClockFormat.selected(this), which -> {
                    if (which == ClockFormat.FOLLOW_TV) choices.edit().remove(ClockFormat.KEY).apply();
                    else choices.edit().putInt(ClockFormat.KEY, which).apply();
                    changed();
                    refresh();
                });
    }

    private void showScreensaverPicker(int themeMinutes) {
        int[] minutes = {0, 5, 10, 15, 30};
        int customIndex = minutes.length;
        int followThemeIndex = minutes.length + 1;
        String[] options = {"Off", "5 minutes", "10 minutes", "15 minutes", "30 minutes",
                "Custom time…", "Follow theme defaults"};
        int checked = followThemeIndex;
        if (!choices.contains(Config.HOME_IDLE_CLOCK_MINUTES_KEY)) {
            checked = followThemeIndex;
        } else {
            int current = choices.getInt(Config.HOME_IDLE_CLOCK_MINUTES_KEY, themeMinutes);
            boolean custom = choices.getBoolean(Config.HOME_IDLE_CLOCK_CUSTOM_KEY, false);
            checked = custom ? customIndex : -1;
            for (int i = 0; i < minutes.length; i++) if (minutes[i] == current && !custom) checked = i;
            if (checked < 0) checked = customIndex;
        }
        showChoiceDialog("Home screensaver",
                "The bouncing clock appears while CoveTV Home is idle. It will not interrupt another app.",
                options, checked, which -> {
                    if (which == customIndex) {
                        showCustomScreensaverTime(themeMinutes);
                    } else if (which == followThemeIndex) {
                        choices.edit().remove(Config.HOME_IDLE_CLOCK_MINUTES_KEY)
                                .remove(Config.HOME_IDLE_CLOCK_CUSTOM_KEY)
                                .remove(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY).apply();
                        changed();
                        refresh();
                    } else {
                        choices.edit().putInt(Config.HOME_IDLE_CLOCK_MINUTES_KEY, minutes[which])
                                .remove(Config.HOME_IDLE_CLOCK_CUSTOM_KEY)
                                .remove(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY).apply();
                        changed();
                        refresh();
                    }
                });
    }

    private void showCustomScreensaverTime(int themeMinutes) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setTextColor(0xFFF4F6FA);
        input.setHintTextColor(0xFF8D98AC);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        input.setTypeface(getResources().getFont(R.font.outfit_medium));
        input.setGravity(Gravity.CENTER);
        input.setPaddingRelative(dp(18), 0, dp(18), 0);
        input.setMinHeight(dp(60));
        input.setBackgroundResource(R.drawable.settings_choice_bg);
        input.setHint("00:00:00");
        int current = choices.getInt(Config.HOME_IDLE_CLOCK_MINUTES_KEY, themeMinutes);
        int currentSeconds = choices.getBoolean(Config.HOME_IDLE_CLOCK_CUSTOM_KEY, false)
                ? choices.getInt(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY, current * 60)
                : current * 60;
        input.setText(formatScreensaverDelay(Math.max(0, Math.min(86_400, currentSeconds))));
        input.setSelectAllOnFocus(true);

        LinearLayout panel = newDialogPanel();
        addDialogHeader(panel, "Custom screensaver delay",
                "Enter hours, minutes, and seconds as HH:MM:SS. For example, 00:05:30 waits 5 minutes and 30 seconds. Enter 00:00:00 to turn it off.");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(4);
        panel.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        actionsParams.topMargin = dp(10);
        panel.addView(actions, actionsParams);
        TextView cancel = newDialogAction(getString(android.R.string.cancel));
        TextView save = newDialogAction("Save");
        save.setTextColor(0xFF70E1E8);
        addWeightedAction(actions, cancel);
        addWeightedAction(actions, save);

        Runnable saveTime = () -> {
            Integer totalSeconds = parseScreensaverDelaySeconds(input.getText().toString());
            if (totalSeconds == null) {
                Toast.makeText(this, "Enter a time from 00:00:00 to 24:00:00 in HH:MM:SS format.", Toast.LENGTH_LONG).show();
                input.requestFocus();
                return;
            }
            SharedPreferences.Editor editor = choices.edit()
                    .putInt(Config.HOME_IDLE_CLOCK_MINUTES_KEY, totalSeconds / 60)
                    .putBoolean(Config.HOME_IDLE_CLOCK_CUSTOM_KEY, true)
                    .putInt(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY, totalSeconds);
            editor.apply();
            dismissChoiceDialog(panel);
            changed();
            refresh();
        };
        cancel.setOnClickListener(v -> dismissChoiceDialog(panel));
        save.setOnClickListener(v -> saveTime.run());
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            saveTime.run();
            return true;
        });
        showCoveDialog(panel, input, 600);
    }

    private Integer parseScreensaverDelaySeconds(String value) {
        String normalized = value == null ? "" : value.trim();
        int hours;
        int minutes;
        int seconds = 0;
        if (normalized.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
            String[] parts = normalized.split(":", 3);
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);
            seconds = Integer.parseInt(parts[2]);
        } else if (normalized.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = normalized.split(":", 2);
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);
        } else if (normalized.matches("\\d{5,6}")) {
            int split = normalized.length() - 4;
            hours = Integer.parseInt(normalized.substring(0, split));
            minutes = Integer.parseInt(normalized.substring(split, split + 2));
            seconds = Integer.parseInt(normalized.substring(split + 2));
        } else if (normalized.matches("\\d{3,4}")) {
            int split = normalized.length() - 2;
            hours = Integer.parseInt(normalized.substring(0, split));
            minutes = Integer.parseInt(normalized.substring(split));
        } else {
            return null;
        }
        if (minutes > 59 || seconds > 59 || hours > 24
                || (hours == 24 && (minutes != 0 || seconds != 0))) return null;
        return hours * 3600 + minutes * 60 + seconds;
    }

    private String formatScreensaverDelay(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private interface ChoiceAction {
        void onSelected(int which);
    }

    private interface DialogAction {
        void run();
    }

    private LinearLayout newDialogPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(18), dp(22), dp(14));
        panel.setBackgroundResource(R.drawable.settings_panel_bg);
        return panel;
    }

    private void addDialogHeader(LinearLayout panel, String title, String description) {
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xFFF4F6FA);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        heading.setTypeface(getResources().getFont(R.font.outfit_medium));
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (description == null || description.trim().isEmpty()) return;
        TextView explanation = new TextView(this);
        explanation.setText(description);
        explanation.setTextColor(0xFFADB8CC);
        explanation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        explanation.setTypeface(getResources().getFont(R.font.outfit_regular));
        explanation.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        params.bottomMargin = dp(10);
        panel.addView(explanation, params);
    }

    /** CoveTV-styled list dialog with a persistent side indicator whenever the list overflows. */
    private void showCoveListDialog(String title, String description, String[] options, int checked,
                                    String leftActionLabel, DialogAction leftAction,
                                    ChoiceAction onSelected) {
        LinearLayout panel = newDialogPanel();
        addDialogHeader(panel, title, description);

        LinearLayout listShell = new LinearLayout(this);
        listShell.setOrientation(LinearLayout.HORIZONTAL);
        listShell.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams shellParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shellParams.topMargin = dp(2);
        panel.addView(listShell, shellParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        LinearLayout optionList = new LinearLayout(this);
        optionList.setOrientation(LinearLayout.VERTICAL);
        optionList.setPadding(dp(2), dp(4), dp(2), dp(4));
        scroll.addView(optionList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView firstOption = null;
        TextView selectedOption = null;
        for (int i = 0; i < options.length; i++) {
            TextView option = newChoiceRow();
            option.setText((i == checked ? "\u2713   " : "      ") + options[i]);
            option.setSelected(i == checked);
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            optionParams.topMargin = dp(4);
            optionList.addView(option, optionParams);
            if (firstOption == null) firstOption = option;
            if (i == checked) selectedOption = option;
        }

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int listHeight = Math.max(dp(126), Math.min(dp(370), screenHeight - dp(300)));
        listShell.addView(scroll, new LinearLayout.LayoutParams(0, listHeight, 1f));

        FrameLayout scrollTrack = new FrameLayout(this);
        scrollTrack.setBackground(roundBackground(0xFF36404E, 4));
        // Keep it in layout until the first measurement; the update callback hides it for short lists.
        scrollTrack.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(dp(6),
                ViewGroup.LayoutParams.MATCH_PARENT);
        trackParams.setMarginStart(dp(12));
        trackParams.topMargin = dp(8);
        trackParams.bottomMargin = dp(8);
        listShell.addView(scrollTrack, trackParams);
        View scrollThumb = new View(this);
        scrollThumb.setBackground(roundBackground(0xFF65D9E5, 4));
        scrollTrack.addView(scrollThumb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42), Gravity.TOP));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(leftActionLabel == null ? Gravity.END | Gravity.CENTER_VERTICAL
                : Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        footerParams.topMargin = dp(10);
        panel.addView(footer, footerParams);
        if (leftActionLabel != null) {
            TextView left = newDialogAction(leftActionLabel);
            LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            leftParams.setMarginEnd(dp(6));
            footer.addView(left, leftParams);
            left.setOnClickListener(v -> {
                dismissChoiceDialog(panel);
                if (leftAction != null) leftAction.run();
            });
        }
        TextView cancel = newDialogAction(getString(android.R.string.cancel));
        LinearLayout.LayoutParams cancelParams = leftActionLabel == null
                ? new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.MATCH_PARENT)
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (leftActionLabel != null) cancelParams.setMarginStart(dp(6));
        footer.addView(cancel, cancelParams);
        cancel.setOnClickListener(v -> dismissChoiceDialog(panel));

        for (int i = 0; i < optionList.getChildCount(); i++) {
            final int selectedIndex = i;
            optionList.getChildAt(i).setOnClickListener(v -> {
                dismissChoiceDialog(panel);
                onSelected.onSelected(selectedIndex);
            });
        }

        AlertDialog dialog = showCoveDialog(panel, null, 760);
        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) ->
                updateScrollIndicator(scroll, scrollTrack, scrollThumb));
        final TextView selectedRow = selectedOption;
        final TextView initialFocusOption = selectedRow != null ? selectedRow : firstOption;
        scroll.post(() -> {
            if (selectedRow != null) scroll.scrollTo(0, Math.max(0, selectedRow.getTop()));
            updateScrollIndicator(scroll, scrollTrack, scrollThumb);
            if (initialFocusOption != null && dialog.isShowing()) initialFocusOption.requestFocus();
        });
    }

    private void updateScrollIndicator(ScrollView scroll, FrameLayout track, View thumb) {
        if (scroll.getChildCount() == 0 || track.getHeight() <= 0 || scroll.getHeight() <= 0) return;
        int contentHeight = scroll.getChildAt(0).getHeight();
        int viewportHeight = scroll.getHeight();
        int trackHeight = track.getHeight();
        if (contentHeight <= viewportHeight + dp(2)) {
            track.setVisibility(View.GONE);
            return;
        }
        track.setVisibility(View.VISIBLE);
        int thumbHeight = Math.max(dp(42), Math.round(trackHeight * (viewportHeight / (float) contentHeight)));
        thumbHeight = Math.min(trackHeight, thumbHeight);
        int maxScroll = Math.max(1, contentHeight - viewportHeight);
        int maxThumbTop = Math.max(0, trackHeight - thumbHeight);
        int thumbTop = Math.round(maxThumbTop * (scroll.getScrollY() / (float) maxScroll));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) thumb.getLayoutParams();
        if (params.height != thumbHeight || params.topMargin != thumbTop) {
            params.height = thumbHeight;
            params.topMargin = thumbTop;
            thumb.setLayoutParams(params);
        }
    }

    private GradientDrawable roundBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private TextView newDialogAction(String label) {
        TextView action = newChoiceRow();
        action.setText(label);
        action.setGravity(Gravity.CENTER);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        action.setTextColor(0xFFC3D0E1);
        action.setPaddingRelative(dp(8), 0, dp(8), 0);
        return action;
    }

    private void addWeightedAction(LinearLayout row, TextView action) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMarginEnd(dp(6));
        row.addView(action, params);
    }

    private int indexOfName(String[] names, String current) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(current)) return i;
        return Math.max(0, names.length - 1);
    }

    /** A remote-friendly choice dialog that does not depend on OEM single-choice list rendering. */
    private void showChoiceDialog(String title, String description, String[] options, int checked,
                                  ChoiceAction action) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(16), dp(20), dp(12));
        panel.setBackgroundResource(R.drawable.settings_panel_bg);

        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(0xFFF4F6FA);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        heading.setTypeface(getResources().getFont(R.font.outfit_medium));
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView explanation = new TextView(this);
        explanation.setText(description);
        explanation.setTextColor(0xFFADB8CC);
        explanation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        explanation.setTypeface(getResources().getFont(R.font.outfit_regular));
        explanation.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        explanationParams.topMargin = dp(4);
        explanationParams.bottomMargin = dp(10);
        panel.addView(explanation, explanationParams);

        LinearLayout optionList = new LinearLayout(this);
        optionList.setOrientation(LinearLayout.VERTICAL);
        TextView firstOption = null;
        for (int i = 0; i < options.length; i++) {
            TextView option = newChoiceRow();
            // Unicode escape keeps the check mark intact in the local manual build, regardless of Windows code page.
            option.setText((i == checked ? "\u2713   " : "      ") + options[i]);
            option.setSelected(i == checked);
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            optionParams.topMargin = dp(3);
            optionList.addView(option, optionParams);
            if (firstOption == null || i == checked) firstOption = option;
        }
        panel.addView(optionList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cancel = newChoiceRow();
        cancel.setGravity(Gravity.CENTER);
        cancel.setText(android.R.string.cancel);
        cancel.setTextColor(0xFF8EDFE5);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        cancelParams.topMargin = dp(8);
        panel.addView(cancel, cancelParams);

        for (int i = 0; i < optionList.getChildCount(); i++) {
            final int selectedIndex = i;
            optionList.getChildAt(i).setOnClickListener(choice -> {
                dismissChoiceDialog(panel);
                action.onSelected(selectedIndex);
            });
        }
        cancel.setOnClickListener(v -> dismissChoiceDialog(panel));
        showCoveDialog(panel, firstOption);
    }

    private void dismissChoiceDialog(View panel) {
        Object dialogWindow = panel.getTag();
        if (dialogWindow instanceof AlertDialog) ((AlertDialog) dialogWindow).dismiss();
    }

    private AlertDialog showCoveDialog(LinearLayout panel, View initialFocus) {
        return showCoveDialog(panel, initialFocus, 600);
    }

    private AlertDialog showCoveDialog(LinearLayout panel, View initialFocus, int maxWidthDp) {
        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        panel.setTag(dialog);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                int width = Math.min(dp(maxWidthDp), getResources().getDisplayMetrics().widthPixels - dp(40));
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            if (initialFocus != null) initialFocus.requestFocus();
        });
        dialog.show();
        return dialog;
    }

    private TextView newChoiceRow() {
        TextView row = new TextView(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(18), 0, dp(18), 0);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setTypeface(getResources().getFont(R.font.outfit_medium));
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setFocusable(true);
        row.setClickable(true);
        row.setOnFocusChangeListener((view, focused) -> {
            if (focused && choices.getBoolean(AppFocusSound.PREFERENCE_KEY,
                    AppFocusSound.DEFAULT_ENABLED)) {
                getFocusSound().play();
            }
        });
        return row;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        focusHighlightChoice.setText(getString(R.string.focus_glow_state,
                getString(choices.getBoolean("focus_glow", choices.getBoolean("focus_highlight", true))
                        ? R.string.on : R.string.off)));
        boolean focusSounds = choices.getBoolean(AppFocusSound.PREFERENCE_KEY, AppFocusSound.DEFAULT_ENABLED);
        focusSoundChoice.setText(getString(R.string.focus_sound_choice,
                getString(focusSounds ? R.string.on : R.string.off)));
        focusSoundChoice.setSelected(focusSounds);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        wallpaperScheduleChoice.setText(R.string.wallpaper_schedule);
        wallpaperScheduleChoice.setVisibility(wallpaperByTimeOfDay() ? View.VISIBLE : View.GONE);
        themeChoice.setText(getString(R.string.theme_state,
                wallpaperByTimeOfDay() ? getString(R.string.wallpaper_by_time_of_day)
                        : BundledThemes.label(choices.getString(BundledThemes.CURRENT_THEME_KEY, "scenes"))));
        String weatherCity = WeatherClient.city(this);
        weatherLocationChoice.setText(getString(R.string.weather_location_state,
                weatherCity == null ? getString(R.string.weather_off) : weatherCity));
        weatherUnitChoice.setText(getString(R.string.weather_unit_choice,
                getString(WeatherClient.useFahrenheit(this) ? R.string.unit_fahrenheit : R.string.unit_celsius)));
        dockNamesChoice.setText(getString(R.string.dock_names_choice,
                getString(choices.getBoolean("always_show_names", Config.load(this).alwaysShowNames) ? R.string.on : R.string.off)));
        int focusSpeed = choices.getInt("focus_speed", 100);
        focusSpeedChoice.setText(getString(R.string.focus_speed_choice,
                getString(focusSpeed <= 80 ? R.string.focus_speed_slow : focusSpeed >= 120 ? R.string.focus_speed_fast : R.string.focus_speed_normal)));
        String[] accents = getResources().getStringArray(R.array.focus_accent_options);
        focusAccentChoice.setText(getString(R.string.focus_accent_choice,
                accents[indexOf(FOCUS_ACCENTS, choices.getInt("focus_accent", 0))]));
        String shelfState = !choices.contains("cinematic_home") ? getString(R.string.featured_shelf_follow)
                : getString(choices.getBoolean("cinematic_home", true)
                ? R.string.featured_shelf_on : R.string.featured_shelf_off);
        featuredShelfChoice.setText(getString(R.string.featured_shelf_choice, shelfState));
        followThemeChoice.setSelected(!hasOnTvChoices());
        if (homeScreensaverChoice != null) {
            int minutes = choices.contains(Config.HOME_IDLE_CLOCK_MINUTES_KEY)
                    ? choices.getInt(Config.HOME_IDLE_CLOCK_MINUTES_KEY, 10)
                    : Config.load(this).homeIdleClockMinutes;
            boolean custom = choices.getBoolean(Config.HOME_IDLE_CLOCK_CUSTOM_KEY, false);
            int customSeconds = custom
                    ? choices.getInt(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY, minutes * 60)
                    : minutes * 60;
            String label = custom
                    ? formatScreensaverDelay(Math.max(0, Math.min(86_400, customSeconds)))
                    : minutes == 0 ? "Off"
                    : minutes + (minutes == 1 ? " minute" : " minutes");
            if (!choices.contains(Config.HOME_IDLE_CLOCK_MINUTES_KEY)) label += " (theme default)";
            homeScreensaverChoice.setText("Home screensaver: " + label);
            homeScreensaverChoice.setSelected(minutes > 0);
        }
        if (clockFormatChoice != null) {
            int format = ClockFormat.selected(this);
            clockFormatChoice.setText("Clock format: " + ClockFormat.label(format));
            clockFormatChoice.setSelected(format != ClockFormat.FOLLOW_TV);
        }
        if (automaticUpdatesChoice != null) {
            boolean enabled = choices.getBoolean(LauncherUpdater.AUTO_CHECK_KEY, true);
            automaticUpdatesChoice.setText("Automatic update checks: " + (enabled ? "On" : "Off"));
            automaticUpdatesChoice.setSelected(enabled);
        }
    }

    private boolean wallpaperByTimeOfDay() {
        return choices.getBoolean("wallpaper_time_of_day", hasWallpaperSchedules());
    }

    private boolean hasWallpaperSchedules() {
        for (String key : choices.getAll().keySet()) if (key.startsWith("wallpaper_mood_")) return true;
        return false;
    }

    private void clearWallpaperSchedules() {
        SharedPreferences.Editor editor = choices.edit();
        for (String key : choices.getAll().keySet()) if (key.startsWith("wallpaper_mood_")) editor.remove(key);
        editor.remove("wallpaper_time_of_day").apply();
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains("focus_highlight") || choices.contains("focus_glow")
                || choices.contains("always_show_names") || choices.contains("focus_speed")
                || choices.contains("focus_accent") || choices.contains("cinematic_home")
                || choices.contains(AppFocusSound.PREFERENCE_KEY)
                || choices.contains(Config.LIQUID_GLASS_KEY)
                || choices.contains(ClockFormat.KEY)
                || choices.contains(Config.HOME_IDLE_CLOCK_MINUTES_KEY)
                || choices.contains(Config.HOME_IDLE_CLOCK_CUSTOM_KEY)
                || choices.contains(Config.HOME_IDLE_CLOCK_CUSTOM_SECONDS_KEY)
                || choices.contains("wallpaper_time_of_day") || hasWallpaperSchedules();
    }

    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    private void showDefaultHomeChooser() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(16), dp(20), dp(12));
        panel.setBackgroundResource(R.drawable.settings_panel_bg);

        TextView title = new TextView(this);
        title.setText(R.string.home_settings);
        title.setTextColor(0xFFF4F6FA);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setTypeface(getResources().getFont(R.font.outfit_medium));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(this);
        description.setText(R.string.home_settings_help);
        description.setTextColor(0xFFADB8CC);
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        description.setTypeface(getResources().getFont(R.font.outfit_regular));
        description.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(4);
        descriptionParams.bottomMargin = dp(10);
        panel.addView(description, descriptionParams);

        TextView useCove = newChoiceRow();
        useCove.setText(R.string.use_cleanyourtv_home);
        TextView chooseAnother = newChoiceRow();
        chooseAnother.setText(R.string.choose_another_home);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        actionParams.bottomMargin = dp(6);
        panel.addView(useCove, actionParams);
        panel.addView(chooseAnother, actionParams);

        TextView cancel = new TextView(this);
        cancel.setText(android.R.string.cancel);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(0xFF8EDFE5);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cancel.setTypeface(getResources().getFont(R.font.outfit_medium));
        cancel.setBackgroundResource(R.drawable.settings_choice_bg);
        cancel.setFocusable(true);
        cancel.setClickable(true);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        cancelParams.topMargin = dp(2);
        panel.addView(cancel, cancelParams);

        useCove.setOnClickListener(v -> {
            dismissChoiceDialog(panel);
            requestCleanYourTvHome();
        });
        chooseAnother.setOnClickListener(v -> {
            dismissChoiceDialog(panel);
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        cancel.setOnClickListener(v -> dismissChoiceDialog(panel));
        cancel.setOnFocusChangeListener((view, focused) -> {
            if (focused && choices.getBoolean(AppFocusSound.PREFERENCE_KEY,
                    AppFocusSound.DEFAULT_ENABLED)) {
                getFocusSound().play();
            }
        });
        showCoveDialog(panel, useCove);
    }

    private void requestCleanYourTvHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                try {
                    startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 2401);
                    return;
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    // Some TV firmware implements the Home settings page but not the role picker.
                }
            }
        }
        open(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
}
