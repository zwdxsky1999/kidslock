package com.kidslock.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;

/**
 * 孩子模式守护服务（AlarmManager 精确闹钟版）
 */
public class LockService extends Service {

    private static final String TAG = "LockService";

    // Action 常量 —— MainActivity 里用这两个字符串启动/停止服务
    public static final String ACTION_LOCK_DEVICE = "com.kidslock.app.ACTION_LOCK_DEVICE";
    public static final String ACTION_UNLOCK_DEVICE = "com.kidslock.app.ACTION_UNLOCK_DEVICE";

    public static final String CHANNEL_ID = "kidslock_channel";
    public static final int NOTIFICATION_ID = 1001;

    private static final int ALARM_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_REQUEST_CODE = 2001;

    private PrefManager pref;
    private Handler handler;
    private WindowManager windowManager;
    private LockOverlayView overlayView;

    @Override
    public void onCreate() {
        super.onCreate();
        pref = new PrefManager(this);
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createNotificationChannel();
        Log.i(TAG, "LockService created");

        // Android 14+ 前台服务必须指定类型
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, 0x0000000F);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        Log.i(TAG, "startForeground done");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand called, intent=" + (intent != null ? intent.getAction() : "NULL"));

        if (intent == null) {
            restoreState();
            return START_STICKY;
        }

        String action = intent.getAction();
        if (action == null) {
            restoreState();
            return START_STICKY;
        }

        switch (action) {
            case ACTION_LOCK_DEVICE:
                if (!pref.isLocked()) {
					// 已经解锁了，只隐藏悬浮窗并调度下一次
					hideOverlay();
					scheduleNextQuiz();
					break;
				}
                pref.setLocked(true);
                showOverlayIfPermitted();
                scheduleNextQuiz();
                break;

            case ACTION_UNLOCK_DEVICE:
                Log.i(TAG, "ACTION_UNLOCK_DEVICE received");
                pref.setLocked(false);
                hideOverlay();
                cancelAlarm();
                stopForeground(true);
                stopSelf();
                break;

            default:
                Log.w(TAG, "Unknown action: " + action);
        }

        updateNotification();
        return START_STICKY;
    }

    private void restoreState() {
        Log.i(TAG, "restoreState: locked=" + pref.isLocked());
        if (pref.isLocked()) {
            showOverlayIfPermitted();
            scheduleNextQuiz();
        }
    }

    // ==================== 闹钟调度 ====================

    private void scheduleNextQuiz() {
        int minutes = pref.getIntervalMinutes();
        if (minutes <= 0) {
            Log.w(TAG, "scheduleNextQuiz: interval=" + minutes + ", skip");
            return;
        }

        long delayMs = minutes * 60_000L;
        long triggerAt = System.currentTimeMillis() + delayMs;

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.e(TAG, "scheduleNextQuiz: AlarmManager unavailable");
            return;
        }

        if (Build.VERSION.SDK_INT >= 31) {
            if (!am.canScheduleExactAlarms()) {
                Log.w(TAG, "scheduleNextQuiz: cannot schedule exact alarms, fallback to set()");
                scheduleInexact(am, triggerAt);
                return;
            }
        }

        Intent intent = new Intent(this, LockAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        Log.i(TAG, "scheduleNextQuiz: alarm set, interval=" + minutes + "min, triggerAt=" + triggerAt);
    }

    private void scheduleInexact(AlarmManager am, long triggerAt) {
        Intent intent = new Intent(this, LockAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        Log.i(TAG, "scheduleInexact: alarm set (non-exact), triggerAt=" + triggerAt);
    }

    private void cancelAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, LockAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
            Log.i(TAG, "cancelAlarm: alarm cancelled");
        }
    }

    // ==================== 通知 ====================

    public static Notification buildQuizNotification(Context context) {
        createChannel(context);

        Intent quizIntent = new Intent(context, LockScreenActivity.class);
        quizIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        PendingIntent fullScreenPi = PendingIntent.getActivity(
                context, NOTIFICATION_REQUEST_CODE, quizIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle("该答题啦")
                .setContentText("答对才能继续看电视")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPi, true)
                .setAutoCancel(true)
                .setOngoing(false)
                .build();
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock)
                .setContentTitle("孩子模式运行中")
                .setContentText("答题解锁 · 守护中")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID,
                        "孩子模式锁屏",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("答题解锁通知");
                if (Build.VERSION.SDK_INT >= 34) {
					ch.setBypassDontDisturb(true);
				}
                nm.createNotificationChannel(ch);
            }
        }
    }

    private void createNotificationChannel() {
        createChannel(this);
    }

    // ==================== 悬浮窗兜底 ====================

    private void showOverlayIfPermitted() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "showOverlayIfPermitted: SYSTEM_ALERT_WINDOW not granted, skip");
            return;
        }
        if (overlayView != null) {
            Log.i(TAG, "showOverlayIfPermitted: overlay already shown");
            return;
        }
        try {
            overlayView = new LockOverlayView(this);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    android.graphics.PixelFormat.TRANSLUCENT);
            windowManager.addView(overlayView, params);
            Log.i(TAG, "showOverlayIfPermitted: overlay shown");
        } catch (WindowManager.BadTokenException | SecurityException e) {
            Log.e(TAG, "showOverlayIfPermitted: addView failed", e);
            overlayView = null;
        }
    }

    private void hideOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
                Log.i(TAG, "hideOverlay: overlay removed");
            } catch (Exception e) {
                Log.w(TAG, "hideOverlay: remove failed", e);
            }
            overlayView = null;
        }
    }

    // ==================== 生命周期 ====================

    @Override
    public void onDestroy() {
        Log.i(TAG, "LockService onDestroy");
        hideOverlay();
        cancelAlarm();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}