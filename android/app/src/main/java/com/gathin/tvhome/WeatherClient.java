package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** City-based weather for Home. Location is selected by the owner; the launcher does not read GPS. */
final class WeatherClient {
    interface Callback { void onResult(String line); }
    interface ForecastCallback { void onResult(Forecast forecast); }
    interface SearchCallback { void onResult(List<Place> places, Exception error); }

    static final class Forecast {
        final String city, sunrise, sunset;
        final int temperature, code, high, low;
        final String[] hours;
        final int[] temperatures, codes;

        Forecast(String city, String sunrise, String sunset, int temperature, int code, int high, int low,
                 String[] hours, int[] temperatures, int[] codes) {
            this.city = city;
            this.sunrise = sunrise;
            this.sunset = sunset;
            this.temperature = temperature;
            this.code = code;
            this.high = high;
            this.low = low;
            this.hours = hours;
            this.temperatures = temperatures;
            this.codes = codes;
        }
    }

    static final class Place {
        final String label;
        final double latitude;
        final double longitude;
        Place(String label, double latitude, double longitude) {
            this.label = label;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static final String CITY = "weather_city";
    private static final String LAT = "weather_lat";
    private static final String LON = "weather_lon";
    private static final String CACHE = "weather_cache";
    private static final String FORECAST_CACHE = "weather_forecast_cache_v2";
    private static final String UPDATED = "weather_updated";
    static final String UNIT_KEY = "weather_temperature_unit";
    private static final long REFRESH_MS = 30L * 60L * 1000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WeatherClient() { }

    static boolean useFahrenheit(Context context) {
        return prefs(context).getString(UNIT_KEY,
                Locale.getDefault().getCountry().equalsIgnoreCase("US") ? "fahrenheit" : "celsius")
                .equals("fahrenheit");
    }

    static String cached(Context context) {
        return prefs(context).getString(CACHE, null);
    }

    static String city(Context context) {
        SharedPreferences p = prefs(context);
        return p.getString(CITY, p.getBoolean("weather_configured", false) ? null : "Los Angeles");
    }

    static void savePlace(Context context, Place place) {
        prefs(context).edit().putString(CITY, place.label).putString(LAT, Double.toString(place.latitude))
                .putString(LON, Double.toString(place.longitude)).putBoolean("weather_configured", true)
                .remove(CACHE).remove(FORECAST_CACHE).remove(UPDATED).apply();
    }

    static void clearPlace(Context context) {
        prefs(context).edit().remove(CITY).remove(LAT).remove(LON).remove(CACHE).remove(FORECAST_CACHE)
                .putBoolean("weather_configured", true).remove(UPDATED).apply();
    }

    static void refresh(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        String lat = p.getString(LAT, null), lon = p.getString(LON, null);
        if (lat == null || lon == null) {
            if (p.getBoolean("weather_configured", false)) {
                MAIN.post(() -> callback.onResult(null));
                return;
            }
            p.edit().putString(CITY, "Los Angeles").putString(LAT, "34.0522").putString(LON, "-118.2437").apply();
            lat = "34.0522";
            lon = "-118.2437";
        }
        String old = p.getString(CACHE, null);
        if (old != null) MAIN.post(() -> callback.onResult(old));
        if (System.currentTimeMillis() - p.getLong(UPDATED, 0) < REFRESH_MS && old != null) return;
        final String requestLat = lat, requestLon = lon;
        IO.execute(() -> {
            String line = null;
            try {
                boolean fahrenheit = useFahrenheit(app);
                String unit = fahrenheit ? "fahrenheit" : "celsius";
                JSONObject root = getJson("https://api.open-meteo.com/v1/forecast?latitude=" + requestLat
                        + "&longitude=" + requestLon + "&current=temperature_2m,weather_code&timezone=auto&temperature_unit=" + unit);
                JSONObject current = root.getJSONObject("current");
                int temperature = (int) Math.round(current.getDouble("temperature_2m"));
                int code = current.getInt("weather_code");
                String condition = condition(code);
            String city = p.getString(CITY, "Los Angeles");
                line = (city.isEmpty() ? "" : city + "  ·  ") + condition + "  ·  " + temperature + "°" + (fahrenheit ? "F" : "C");
                p.edit().putString(CACHE, line).putLong(UPDATED, System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
                // Keep the last saved reading visible when the TV is offline or the provider is unavailable.
            }
            String result = line == null ? old : line;
            MAIN.post(() -> callback.onResult(result));
        });
    }

    static Forecast cachedForecast(Context context) {
        String raw = prefs(context).getString(FORECAST_CACHE, null);
        if (raw == null) return null;
        try { return parseForecast(new JSONObject(raw), city(context)); }
        catch (Exception ignored) { return null; }
    }

    static void refreshForecast(Context context, ForecastCallback callback) {
        Context app = context.getApplicationContext();
        SharedPreferences p = prefs(app);
        if (!p.contains(LAT) || !p.contains(LON)) {
            if (p.getBoolean("weather_configured", false)) {
                MAIN.post(() -> callback.onResult(null));
                return;
            }
            p.edit().putString(CITY, "Los Angeles").putString(LAT, "34.0522").putString(LON, "-118.2437").apply();
        }
        String lat = p.getString(LAT, "34.0522"), lon = p.getString(LON, "-118.2437");
        Forecast cached = cachedForecast(app);
        if (cached != null) MAIN.post(() -> callback.onResult(cached));
        if (System.currentTimeMillis() - p.getLong(UPDATED, 0) < REFRESH_MS && cached != null) return;
        IO.execute(() -> {
            Forecast result = cached;
            try {
                boolean fahrenheit = useFahrenheit(app);
                String unit = fahrenheit ? "fahrenheit" : "celsius";
                JSONObject root = getJson("https://api.open-meteo.com/v1/forecast?latitude=" + lat
                        + "&longitude=" + lon + "&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code"
                        + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=2&timezone=auto&temperature_unit=" + unit);
                result = parseForecast(root, p.getString(CITY, "Los Angeles"));
                p.edit().putString(FORECAST_CACHE, root.toString()).putLong(UPDATED, System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
                // Keep the last saved forecast available when the TV is offline or the provider is unavailable.
            }
            Forecast update = result;
            MAIN.post(() -> callback.onResult(update));
        });
    }

    private static Forecast parseForecast(JSONObject root, String city) throws Exception {
        JSONObject current = root.getJSONObject("current");
        JSONObject hourly = root.getJSONObject("hourly");
        JSONObject daily = root.getJSONObject("daily");
        JSONArray times = hourly.getJSONArray("time"), temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        String currentTime = current.getString("time").substring(0, 13);
        int start = 0;
        while (start < times.length() && !times.getString(start).startsWith(currentTime)) start++;
        if (start >= times.length()) start = 0;
        int count = Math.min(24, times.length() - start);
        String[] labels = new String[count];
        int[] hourTemps = new int[count], hourCodes = new int[count];
        for (int i = 0; i < count; i++) {
            String time = times.getString(start + i);
            labels[i] = time.substring(11, 16);
            hourTemps[i] = (int) Math.round(temps.getDouble(start + i));
            hourCodes[i] = codes.getInt(start + i);
        }
        return new Forecast(city == null ? "" : city, clock(daily.getJSONArray("sunrise").getString(0)),
                clock(daily.getJSONArray("sunset").getString(0)),
                (int) Math.round(current.getDouble("temperature_2m")), current.getInt("weather_code"),
                (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)), labels, hourTemps, hourCodes);
    }

    private static String clock(String value) {
        try {
            int hour = Integer.parseInt(value.substring(11, 13));
            int minute = Integer.parseInt(value.substring(14, 16));
            return String.format(Locale.getDefault(), "%d:%02d %s", (hour % 12 == 0 ? 12 : hour % 12), minute,
                    hour < 12 ? "AM" : "PM");
        } catch (Exception ignored) { return value; }
    }

    static void searchCity(String query, SearchCallback callback) {
        IO.execute(() -> {
            List<Place> places = new ArrayList<>();
            Exception failure = null;
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                JSONObject root = getJson("https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&format=json&name=" + encoded);
                JSONArray results = root.optJSONArray("results");
                if (results != null) for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    StringBuilder label = new StringBuilder(item.optString("name", ""));
                    String admin = item.optString("admin1", "");
                    String country = item.optString("country", "");
                    if (!admin.isEmpty() && !admin.equals(label.toString())) label.append(", ").append(admin);
                    if (!country.isEmpty()) label.append(", ").append(country);
                    places.add(new Place(label.toString(), item.getDouble("latitude"), item.getDouble("longitude")));
                }
            } catch (Exception e) {
                failure = e;
            }
            List<Place> found = places;
            Exception error = failure;
            MAIN.post(() -> callback.onResult(found, error));
        });
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    private static JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IllegalStateException("Weather service returned " + status);
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            if (status < 200 || status >= 300) throw new IllegalStateException("Weather service returned " + status);
            return new JSONObject(body.toString());
        } finally {
            connection.disconnect();
        }
    }

    static String condition(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "Weather";
    }
}
