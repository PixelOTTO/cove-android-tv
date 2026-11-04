package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** A compact, remote-friendly city forecast card for the home screen. */
public final class WeatherPanelView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final float density;
    private WeatherClient.Forecast forecast;
    private int hourOffset;
    private float touchX, touchY;
    private Runnable onHoursScrolled;

    public WeatherPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setOnFocusChangeListener((view, focused) -> invalidate());
    }

    void setForecast(WeatherClient.Forecast value) {
        forecast = value;
        hourOffset = Math.min(hourOffset, Math.max(0, value == null ? 0 : value.hours.length - 6));
        invalidate();
    }

    void setOnHoursScrolledListener(Runnable listener) {
        onHoursScrolled = listener;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = density, w = getWidth() / d, h = getHeight() / d;
        canvas.save();
        canvas.scale(d, d);
        box.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        // Give the forecast card a soft charcoal glass surface. The shared focus glass draws
        // the accent outline, so the card itself stays understated instead of doubling it.
        cardPaint.setShader(new LinearGradient(0, 0, 0, h, 0xE12A302F, 0xD2181C1B, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(box, 24, 24, cardPaint);
        cardPaint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0x3DFFFFFF);
        canvas.drawRoundRect(box, 24, 24, paint);
        paint.setStyle(Paint.Style.FILL);

        WeatherClient.Forecast data = forecast;
        if (data == null) {
            text(canvas, "Weather", 20, 34, 16, 0xFFF5F5F7, true);
            text(canvas, "Choose a city in Home Settings to see the forecast", 20, 68, 14, 0xBFFFFFFF, false);
            canvas.restore();
            return;
        }

        float left = 20, right = w - 20;
        float separator = Math.min(220, w * 0.25f);
        Paint cityPaint = new Paint(paint);
        cityPaint.setTextSize(16);
        cityPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        float cityWidth = Math.max(1, separator - left - 18);
        java.util.ArrayList<String> cityLines = new java.util.ArrayList<>();
        StringBuilder cityLine = new StringBuilder();
        for (String word : data.city.split("\\s+")) {
            String candidate = cityLine.length() == 0 ? word : cityLine + " " + word;
            if (cityLine.length() > 0 && cityPaint.measureText(candidate) > cityWidth) {
                cityLines.add(cityLine.toString());
                cityLine.setLength(0);
            }
            if (cityLine.length() > 0) cityLine.append(' ');
            cityLine.append(word);
        }
        if (cityLine.length() > 0) cityLines.add(cityLine.toString());
        for (int i = 0; i < cityLines.size(); i++)
            text(canvas, cityLines.get(i), left, 30 + i * 19, 16, 0xFFE8E8EA, true);
        float down = cityLines.size() > 1 ? 20 : 0;
        paint.setColor(0x40FFFFFF);
        canvas.drawRect(separator, 18, separator + 1, h - 18, paint);

        drawWeather(canvas, left + 22, 77 + down, data.code, 0xFFF5F5F7, 18);
        String tempUnit = WeatherClient.useFahrenheit(getContext()) ? "°F" : "°C";
        String currentTemp = data.temperature + tempUnit;
        float tempSize = data.temperature >= 100 ? 48 : 58;
        Paint tempPaint = new Paint(paint);
        tempPaint.setTextSize(tempSize);
        float maxTempWidth = separator - (left + 64) - 10;
        if (tempPaint.measureText(currentTemp) > maxTempWidth)
            tempSize *= maxTempWidth / tempPaint.measureText(currentTemp);
        text(canvas, currentTemp, left + 64, 87 + down, tempSize, 0xFFF5F5F7, false);
        String condition = WeatherClient.condition(data.code);
        text(canvas, condition, left, 118 + down, 18, 0xFFF5F5F7, false);

        int count = Math.min(6, Math.max(0, data.hours.length - hourOffset));
        if (count > 0) {
            float start = separator + 26, end = right - 8;
            float slot = (end - start) / 6f;
            for (int i = 0; i < count; i++) {
                float cx = start + slot * (i + 0.5f);
                int index = hourOffset + i;
                textCentered(canvas, hour(data.hours[index]), cx, 44, 14, 0xFFE8E8EA, false);
                drawWeather(canvas, cx, 77, data.codes[index], 0xFFE8E8EA, 13);
                textCentered(canvas, data.temperatures[index] + tempUnit, cx, 108, 16, 0xFFF5F5F7, false);
            }
            if (hourOffset > 0) drawArrow(canvas, start + 3, 78, true);
            if (hourOffset + 6 < data.hours.length) drawArrow(canvas, end - 3, 78, false);
        }
        drawSun(canvas, separator + 28, h - 24, true);
        text(canvas, "Sunrise " + data.sunrise, separator + 47, h - 19, 14, 0xBFFFFFFF, false);
        float sunsetX = Math.min(w * 0.57f, separator + 170);
        drawSun(canvas, sunsetX, h - 24, false);
        String sunsetLabel = "Sunset " + data.sunset;
        text(canvas, sunsetLabel, sunsetX + 19, h - 19, 14, 0xBFFFFFFF, false);
        Paint footerPaint = new Paint(paint);
        footerPaint.setTextSize(14);
        float highLowX = sunsetX + 19 + footerPaint.measureText(sunsetLabel) + 18;
        text(canvas, "H:" + data.high + tempUnit + "   L:" + data.low + tempUnit, highLowX, h - 19, 14, 0xBFFFFFFF, false);
        canvas.restore();
    }

    private boolean scrollHours(int amount) {
        if (forecast == null) return false;
        int next = Math.max(0, Math.min(Math.max(0, forecast.hours.length - 6), hourOffset + amount));
        if (next == hourOffset) return false;
        hourOffset = next;
        invalidate();
        if (onHoursScrolled != null) onHoursScrolled.run();
        return true;
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && scrollHours(-1)) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && scrollHours(1)) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchX = event.getX(); touchY = event.getY(); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - touchX, dy = event.getY() - touchY;
            if (Math.abs(dx) > 36 * density && Math.abs(dx) > Math.abs(dy)) scrollHours(dx < 0 ? 4 : -4);
            else performClick();
            return true;
        }
        return event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_MOVE;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void drawArrow(Canvas c, float x, float y, boolean left) {
        paint.setColor(0xDFFFFFFF); paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f); paint.setStrokeCap(Paint.Cap.ROUND);
        float direction = left ? -1 : 1;
        c.drawLine(x - direction * 4, y - 6, x + direction * 2, y, paint);
        c.drawLine(x + direction * 2, y, x - direction * 4, y + 6, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color, boolean medium) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(medium ? android.graphics.Typeface.create("sans-serif-medium", 0)
                : android.graphics.Typeface.create("sans-serif", 0));
        c.drawText(value, x, y, paint);
    }

    private void textCentered(Canvas c, String value, float x, float y, float size, int color, boolean medium) {
        paint.setTextSize(size);
        paint.setTypeface(medium ? android.graphics.Typeface.create("sans-serif-medium", 0)
                : android.graphics.Typeface.create("sans-serif", 0));
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawText(value, x - paint.measureText(value) / 2, y, paint);
    }

    private void drawWeather(Canvas c, float x, float y, int code, int color, float radius) {
        float r = radius;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, r * 0.11f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (code == 0 || code == 1) {
            c.drawCircle(x, y, r * 0.42f, paint);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                float x1 = x + (float) Math.cos(a) * r * 0.68f;
                float y1 = y + (float) Math.sin(a) * r * 0.68f;
                float x2 = x + (float) Math.cos(a) * r * 0.96f;
                float y2 = y + (float) Math.sin(a) * r * 0.96f;
                c.drawLine(x1, y1, x2, y2, paint);
            }
        } else {
            box.set(x - r * 0.9f, y - r * 0.03f, x + r * 0.9f, y + r * 0.54f);
            c.drawRoundRect(box, r * 0.27f, r * 0.27f, paint);
            c.drawArc(x - r * 0.55f, y - r * 0.62f, x + r * 0.15f, y + r * 0.15f, 190, 160, false, paint);
            c.drawArc(x - r * 0.06f, y - r * 0.83f, x + r * 0.71f, y + r * 0.15f, 185, 165, false, paint);
            if (code >= 51 && code <= 99) {
                c.drawLine(x - r * 0.35f, y + r * 0.75f, x - r * 0.48f, y + r, paint);
                c.drawLine(x + r * 0.30f, y + r * 0.75f, x + r * 0.17f, y + r, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSun(Canvas c, float x, float y, boolean rising) {
        paint.setColor(0xBFFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float r = 5;
        c.drawArc(x - r, y - r, x + r, y + r, 180, 180, false, paint);
        c.drawLine(x - 9, y, x + 9, y, paint);
        c.drawLine(x, y - 11, x, y - 7, paint);
        if (rising) {
            c.drawLine(x - 6, y - 8, x - 8, y - 10, paint);
            c.drawLine(x + 6, y - 8, x + 8, y - 10, paint);
        } else {
            c.drawLine(x - 6, y - 8, x - 8, y - 10, paint);
            c.drawLine(x + 6, y - 8, x + 8, y - 10, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private static String hour(String raw) {
        try {
            int h = Integer.parseInt(raw.substring(0, 2));
            return String.format(Locale.getDefault(), "%d %s", h % 12 == 0 ? 12 : h % 12, h < 12 ? "AM" : "PM");
        } catch (Exception ignored) { return raw; }
    }
}
