package lv.dimy.eunlwidget;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuoteService extends Service {
    private static final String CHANNEL = "eunl_quotes";
    private static final int NOTIFICATION_ID = 8842;
    private static final long INTERVAL_MS = 60_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            executor.execute(() -> {
                Quote q = fetch("EUNL.XD");
                if (q == null) q = fetch("EUNL.DE");
                updateWidget(q);
                updateNotification(q);
            });
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    public static void start(Context context) {
        Intent i = new Intent(context, QuoteService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification("EUNL: connecting…"),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification("EUNL: connecting…"));
        }

        handler.post(poll);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void updateWidget(Quote q) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        android.content.ComponentName cn =
                new android.content.ComponentName(this, EunlWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(cn);

        RemoteViews v = new RemoteViews(getPackageName(), R.layout.eunl_widget);

        if (q == null) {
            v.setTextViewText(R.id.status, "OFFLINE");
            v.setTextViewText(R.id.price, "€ —");
            v.setTextViewText(R.id.change, "—");
            v.setTextViewText(R.id.time, "No quote");
        } else {
            v.setTextViewText(R.id.status, q.realtime ? "REAL-TIME" : "DELAYED");
            v.setTextViewText(R.id.price, String.format(Locale.US, "€ %.2f", q.price));
            v.setTextViewText(R.id.change,
                    String.format(Locale.US, "%+.2f  %+.2f%%", q.change, q.pct));
            v.setTextColor(R.id.change,
                    q.change >= 0 ? Color.rgb(70,190,110) : Color.rgb(245,90,90));
            v.setTextViewText(R.id.time, q.source + " • " + q.time);
        }

        Intent refresh = new Intent(this, QuoteService.class);
        PendingIntent pi = PendingIntent.getService(
                this, 11, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);

        for (int id : ids) manager.updateAppWidget(id, v);
    }

    private void updateNotification(Quote q) {
        String text = q == null
                ? "EUNL: no quote"
                : String.format(Locale.US,
                    "EUNL € %.2f  %+.2f%% • %s",
                    q.price, q.pct, q.realtime ? "REAL-TIME" : "DELAYED");

        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, notification(text));
    }

    private android.app.Notification notification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 20, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder b =
                Build.VERSION.SDK_INT >= 26
                        ? new android.app.Notification.Builder(this, CHANNEL)
                        : new android.app.Notification.Builder(this);

        return b.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("EUNL Widget")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(getString(R.string.channel_description));
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(ch);
        }
    }

    private Quote fetch(String symbol) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(
                    "https://query1.finance.yahoo.com/v8/finance/chart/" +
                    symbol + "?interval=1m&range=1d");

            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(7000);
            c.setReadTimeout(7000);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 EUNLWidget/1.3");

            if (c.getResponseCode() != 200) return null;

            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream()));
            StringBuilder s = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) s.append(line);
            r.close();

            JSONObject result = new JSONObject(s.toString())
                    .getJSONObject("chart")
                    .getJSONArray("result")
                    .getJSONObject(0);

            JSONObject meta = result.getJSONObject("meta");

            int delayedBy = meta.optInt("exchangeDataDelayedBy", -1);
            boolean actualRealtime = delayedBy == 0;

            double price = meta.optDouble("regularMarketPrice", Double.NaN);
            double prev = meta.optDouble("previousClose", Double.NaN);

            if (Double.isNaN(price)) {
                JSONArray closes = result.getJSONObject("indicators")
                        .getJSONArray("quote")
                        .getJSONObject(0)
                        .getJSONArray("close");

                for (int x = closes.length() - 1; x >= 0; x--) {
                    if (!closes.isNull(x)) {
                        price = closes.getDouble(x);
                        break;
                    }
                }
            }

            if (Double.isNaN(price) || Double.isNaN(prev)) return null;

            long ts = meta.optLong(
                    "regularMarketTime",
                    System.currentTimeMillis() / 1000);

            Quote q = new Quote();
            q.price = price;
            q.change = price - prev;
            q.pct = prev == 0 ? 0 : q.change * 100.0 / prev;
            q.realtime = actualRealtime;
            q.source = symbol;

            SimpleDateFormat f =
                    new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            f.setTimeZone(TimeZone.getDefault());
            q.time = f.format(new Date(ts * 1000));

            return q;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    static class Quote {
        double price, change, pct;
        boolean realtime;
        String source, time;
    }
}
