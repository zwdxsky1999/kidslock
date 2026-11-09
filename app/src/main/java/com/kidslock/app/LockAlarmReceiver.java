package com.kidslock.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import android.os.Build;

public class LockAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "LockAlarm";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Alarm fired! Re-locking now.");

        PrefManager pref = new PrefManager(context);

        // 关键：闹钟触发 = 自由窗口结束 = 直接重新上锁
        // 不要再判断 isLocked()，否则永远 return
        pref.setLocked(true);
        pref.setHomeAliasEnabled(context, true);

        // 唤醒屏幕
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = null;
        if (pm != null) {
            wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "KidsLock:AlarmWake");
            wl.acquire(10000);
        }

        // 直接启动锁屏 Activity
        try {
            Intent lockIntent = new Intent(context, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(lockIntent);
            Log.i(TAG, "LockScreenActivity started");
        } catch (Exception e) {
            Log.e(TAG, "startActivity failed, fallback to notification", e);
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(LockService.NOTIFICATION_ID + 1,
                        LockService.buildQuizNotification(context));
            }
        }

        // 重新排下一次闹钟（如果还在孩子模式）
        if (pref.isChildMode()) {
            Intent svc = new Intent(context, LockService.class);
            svc.setAction(LockService.ACTION_LOCK_DEVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }

        if (wl != null && wl.isHeld()) wl.release();
    }
}