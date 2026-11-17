package app.yawmi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent in) {
        Alarms.ensureChannel(c);
        String title = in.getStringExtra("title");
        String text = in.getStringExtra("text");
        if (title == null) return;
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), Alarms.piFlags());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, Alarms.CHANNEL)
                : new Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        Notification n = b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build();
        try {
            ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(in.getIntExtra("nid", 1), n);
        } catch (SecurityException ignored) {
        }
    }
}
