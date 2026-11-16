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

/** Keeps the reminder list the page computed and turns it into system alarms. */
final class Alarms {
    static final String CHANNEL = "yawmi";
    private static final int MAX = 80;

    static void ensureChannel(Context c) {
        NotificationChannel ch = new NotificationChannel(CHANNEL, "تنبيهات يومي", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("مواقيت الصلاة والأذكار والورد");
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
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
        return PendingIntent.getBroadcast(c, slot, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void apply(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
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
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Exception ignored) {
        }
    }
}
