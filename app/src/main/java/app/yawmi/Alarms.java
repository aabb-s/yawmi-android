package app.yawmi;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Keeps the reminder list the page computed and turns it into system alarms. Works from Android 5. */
final class Alarms {
    static final String CHANNEL = "yawmi";
    private static final int MAX = 80;

    static int piFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "تنبيهات يومي", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("مواقيت الصلاة والأذكار والورد");
        ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    static void save(Context c, String json) {
        c.getSharedPreferences("yawmi", Context.MODE_PRIVATE).edit().putString("alarms", json).apply();
    }

    private static PendingIntent intent(Context c, int slot, JSONObject o) {
        Intent i = new Intent(c, AlarmReceiver.class);
        if (o != null) {
            i.putExtra("title", o.optString("title"));
            i.putExtra("text", o.optString("text"));
            i.putExtra("nid", o.optInt("nid", 1));
        }
        return PendingIntent.getBroadcast(c, slot, i, piFlags());
    }

    static void apply(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < MAX; i++) am.cancel(intent(c, i, null));
        try {
            JSONArray list = new JSONArray(c.getSharedPreferences("yawmi", Context.MODE_PRIVATE).getString("alarms", "[]"));
            long now = System.currentTimeMillis();
            boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
            int slot = 0;
            for (int i = 0; i < list.length() && slot < MAX; i++) {
                JSONObject o = list.getJSONObject(i);
                long at = o.optLong("at");
                if (at <= now) continue;
                PendingIntent pi = intent(c, slot++, o);
                if (Build.VERSION.SDK_INT >= 23) {
                    if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
