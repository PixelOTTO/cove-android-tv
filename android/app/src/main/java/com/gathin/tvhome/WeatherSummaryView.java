package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;

/** Compact current-weather summary paired with the home-screen clock. */
public final class WeatherSummaryView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final float density;
    private String city = "";
    private WeatherClient.Forecast forecast;

    public WeatherSummaryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setForecast(String place, WeatherClient.Forecast value) {
        city = place == null ? "" : place;
        forecast = value;
        String description = city;
        if (value != null) {
            description += ", " + WeatherClient.condition(value.code) + ", " + value.temperature + " degrees, high "
                    + value.high + ", low " + value.low;
        }
        setContentDescription(description);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth() / density;
        float h = getHeight() / density;
        canvas.save();
        canvas.scale(density, density);

        String label = city.isEmpty() ? "Weather" : city;
        TextPaint measure = new TextPaint(paint);
        measure.setTextSize(14);
        measure.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        float labelWidth = measure.measureText(label);
        float cityTextSize = labelWidth > w ? Math.max(9f, 14f * w / labelWidth) : 14f;
        text(canvas, label, 0, 15, cityTextSize, 0xFF809DCB, true);

        WeatherClient.Forecast data = forecast;
        if (data == null) {
            text(canvas, "Weather unavailable", 0, Math.min(48, h - 4), 13, 0xBFFFFFFF, false);
            canvas.restore();
            return;
        }

        drawWeather(canvas, 13, 42, data.code, 0xFFE8EDF6, 11);
        String unit = WeatherClient.useFahrenheit(getContext()) ? "°F" : "°C";
        text(canvas, data.temperature + unit, 39, 55, 36, 0xFFF5F5F7, false);
        text(canvas, WeatherClient.condition(data.code), 0, 71, 13, 0xFF809DCB, false);
        text(canvas, "H:" + data.high + unit + "   L:" + data.low + unit, 0, h - 1, 11, 0xFF809DCB, false);
        canvas.restore();
    }

    private void text(Canvas canvas, String value, float x, float y, float size, int color, boolean medium) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        canvas.drawText(value, x, y, paint);
    }

    private void drawWeather(Canvas canvas, float x, float y, int code, int color, float radius) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (code == 0 || code == 1) {
            canvas.drawCircle(x, y, radius * 0.38f, paint);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                float x1 = x + (float) Math.cos(angle) * radius * 0.66f;
                float y1 = y + (float) Math.sin(angle) * radius * 0.66f;
                float x2 = x + (float) Math.cos(angle) * radius * 0.94f;
                float y2 = y + (float) Math.sin(angle) * radius * 0.94f;
                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        } else {
            shape.set(x - radius * 0.9f, y - radius * 0.02f, x + radius * 0.9f, y + radius * 0.52f);
            canvas.drawRoundRect(shape, radius * 0.26f, radius * 0.26f, paint);
            canvas.drawArc(x - radius * 0.55f, y - radius * 0.62f, x + radius * 0.15f,
                    y + radius * 0.15f, 190, 160, false, paint);
            canvas.drawArc(x - radius * 0.06f, y - radius * 0.82f, x + radius * 0.71f,
                    y + radius * 0.15f, 185, 165, false, paint);
            if (code >= 51 && code <= 99) {
                canvas.drawLine(x - radius * 0.34f, y + radius * 0.72f,
                        x - radius * 0.48f, y + radius, paint);
                canvas.drawLine(x + radius * 0.30f, y + radius * 0.72f,
                        x + radius * 0.17f, y + radius, paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
