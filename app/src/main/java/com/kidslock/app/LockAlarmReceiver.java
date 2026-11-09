package com.kidslock.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class LockAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "LockAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm fired! Triggering quiz lock screen");

        PrefManager pref = new PrefManager(context);
        if (!pref.isLocked()) {
            Log.i(TAG, "Not locked, ignore alarm");
            return;
        }

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            Log.e(TAG, "NotificationManager unavailable");
            return;
        }
		// 唤醒屏幕
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		PowerManager.WakeLock wl = pm.newWakeLock(
				PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
				"KidsLock:AlarmWakeLock");
		wl.acquire(10000); // 10秒后自动释放

		// 然后再发通知
        nm.notify(LockService.NOTIFICATION_ID + 1,
                LockService.buildQuizNotification(context));
        Log.i(TAG, "Full-screen notification posted");
    }
}