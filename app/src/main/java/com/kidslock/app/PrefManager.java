package com.kidslock.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.json.JSONObject;

/**
 * SharedPreferences 封装：管理所有持久化设置和状态。
 * 状态在重启后仍然保留，保证锁屏不会被绕过。
 *
 * 改造新增：运行模式(CHILD/PARENT)、年级、教材版、出题间隔、知识点掌握度。
 */
public class PrefManager {

    public static final String MODE_CHILD = "CHILD";
    public static final String MODE_PARENT = "PARENT";

    private static final String PREF_NAME = "kids_lock_prefs";
    private final SharedPreferences prefs;

    // Keys
    private static final String KEY_AUTO_START = "auto_start";
    private static final String KEY_WATCH_LIMIT_MIN = "watch_limit_min"; // 兼容保留
    private static final String KEY_INTERVAL_MIN = "interval_min";
    private static final String KEY_UNLOCK_COUNT = "unlock_count";
    private static final String KEY_IS_LOCKED = "is_locked";
    private static final String KEY_TIMER_ACTIVE = "timer_active";
    private static final String KEY_TIMER_END_TIME = "timer_end_time";
    private static final String KEY_PARENT_PIN = "parent_pin";
    private static final String KEY_PIN_FAIL_COUNT = "pin_fail_count";
    private static final String KEY_PIN_LOCK_UNTIL = "pin_lock_until";
    private static final String KEY_MODE = "mode";
    private static final String KEY_GRADE = "grade";
    private static final String KEY_EDITION = "edition";
    private static final String KEY_MASTERY = "mastery_json";
    private static final String KEY_ONLINE = "online_enabled";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_CHILD_ID = "child_id";

    // Defaults
    private static final boolean DEFAULT_AUTO_START = true;
    private static final int DEFAULT_INTERVAL_MIN = 5;
    private static final int DEFAULT_UNLOCK_COUNT = 3;
    private static final String DEFAULT_PARENT_PIN = "1234";
    private static final int DEFAULT_GRADE = 5;
    private static final String DEFAULT_EDITION = "人教版";
    private static final int MAX_PIN_FAILS = 5;
    private static final long PIN_LOCKOUT_MS = 30_000;

    public PrefManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- Auto Start ---
    public boolean isAutoStart() {
        return prefs.getBoolean(KEY_AUTO_START, DEFAULT_AUTO_START);
    }

    public void setAutoStart(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply();
    }

    // --- Mode ---
    public String getMode() {
        return prefs.getString(KEY_MODE, MODE_CHILD);
    }

    public void setMode(String mode) {
        prefs.edit().putString(KEY_MODE, mode).apply();
    }

    public boolean isChildMode() {
        return MODE_CHILD.equals(getMode());
    }

    // --- Grade / Edition ---
    public int getGrade() {
        return prefs.getInt(KEY_GRADE, DEFAULT_GRADE);
    }

    public void setGrade(int grade) {
        prefs.edit().putInt(KEY_GRADE, grade).apply();
    }

    public String getEdition() {
        return prefs.getString(KEY_EDITION, DEFAULT_EDITION);
    }

    public void setEdition(String edition) {
        prefs.edit().putString(KEY_EDITION, edition).apply();
    }

    // --- 在线题库（NAS 后台）---
    /** 是否启用联网拉题（关闭则用内置题库）。 */
    public boolean isOnline() {
        return prefs.getBoolean(KEY_ONLINE, false);
    }

    public void setOnline(boolean enabled) {
        prefs.edit().putBoolean(KEY_ONLINE, enabled).apply();
    }

    /** NAS 后台地址，如 http://192.168.1.10:8000（可为空=未配置）。 */
    public String getServerUrl() {
        return prefs.getString(KEY_SERVER_URL, "");
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER_URL, url == null ? "" : url).apply();
    }

    /** 孩子ID（后台 /api/next 用；后台会在缺失时自动创建）。 */
    public String getChildId() {
        return prefs.getString(KEY_CHILD_ID, "");
    }

    public void setChildId(String childId) {
        prefs.edit().putString(KEY_CHILD_ID, childId == null ? "" : childId).apply();
    }

    // --- Interval (自由观看窗口，分钟) ---
    public int getIntervalMinutes() {
        int v = prefs.getInt(KEY_INTERVAL_MIN, DEFAULT_INTERVAL_MIN);
        return v > 0 ? v : DEFAULT_INTERVAL_MIN;
    }

    public void setIntervalMinutes(int minutes) {
        prefs.edit().putInt(KEY_INTERVAL_MIN, minutes).apply();
    }

    // --- Unlock Question Count ---
    public int getUnlockCount() {
        return prefs.getInt(KEY_UNLOCK_COUNT, DEFAULT_UNLOCK_COUNT);
    }

    public void setUnlockCount(int count) {
        prefs.edit().putInt(KEY_UNLOCK_COUNT, count).apply();
    }

    // --- Lock State ---
    public boolean isLocked() {
        return prefs.getBoolean(KEY_IS_LOCKED, false);
    }

    public void setLocked(boolean locked) {
        prefs.edit().putBoolean(KEY_IS_LOCKED, locked).apply();
    }

    // --- Timer (复用为"自由观看窗口") ---
    public boolean isTimerActive() {
        return prefs.getBoolean(KEY_TIMER_ACTIVE, false);
    }

    public void setTimerActive(boolean active) {
        prefs.edit().putBoolean(KEY_TIMER_ACTIVE, active).apply();
    }

    public long getTimerEndTime() {
        return prefs.getLong(KEY_TIMER_END_TIME, 0);
    }

    public void setTimerEndTime(long endTime) {
        prefs.edit().putLong(KEY_TIMER_END_TIME, endTime).apply();
    }

    public long getRemainingMillis() {
        long end = getTimerEndTime();
        long now = System.currentTimeMillis();
        return Math.max(0, end - now);
    }

    // --- Parent PIN ---
    public String getParentPin() {
        return prefs.getString(KEY_PARENT_PIN, DEFAULT_PARENT_PIN);
    }

    public void setParentPin(String pin) {
        prefs.edit().putString(KEY_PARENT_PIN, pin).apply();
    }

    // --- PIN 防暴力尝试 ---
    public boolean isPinLocked() {
        return System.currentTimeMillis() < prefs.getLong(KEY_PIN_LOCK_UNTIL, 0);
    }

    public long getPinLockRemainingMillis() {
        long until = prefs.getLong(KEY_PIN_LOCK_UNTIL, 0);
        return Math.max(0, until - System.currentTimeMillis());
    }

    public void recordPinFailure() {
        int fails = prefs.getInt(KEY_PIN_FAIL_COUNT, 0) + 1;
        if (fails >= MAX_PIN_FAILS) {
            prefs.edit()
                    .putInt(KEY_PIN_FAIL_COUNT, 0)
                    .putLong(KEY_PIN_LOCK_UNTIL, System.currentTimeMillis() + PIN_LOCKOUT_MS)
                    .apply();
        } else {
            prefs.edit().putInt(KEY_PIN_FAIL_COUNT, fails).apply();
        }
    }

    public void resetPinFailures() {
        prefs.edit().putInt(KEY_PIN_FAIL_COUNT, 0).putLong(KEY_PIN_LOCK_UNTIL, 0).apply();
    }

    // --- 自由窗口计时：复用原计时机制 ---
    public void startTimer(int minutes) {
        long endTime = System.currentTimeMillis() + (long) minutes * 60 * 1000;
        setTimerActive(true);
        setTimerEndTime(endTime);
        setLocked(false);
    }

    public void stopTimer() {
        setTimerActive(false);
        setTimerEndTime(0);
    }

    public boolean isTimerExpired() {
        return isTimerActive() && System.currentTimeMillis() >= getTimerEndTime();
    }

    // --- 知识点掌握度（进度模型） ---
    public String getMasteryJson() {
        return prefs.getString(KEY_MASTERY, "{}");
    }

    /**
     * 记录一次答题结果，更新知识点掌握度(0~1)：
     * 答对 +0.15（封顶1），答错 -0.2（保底0）。
     */
    public void recordAnswer(String knowledge, boolean correct) {
        if (knowledge == null || knowledge.isEmpty()) return;
        JSONObject m;
        try {
            m = new JSONObject(prefs.getString(KEY_MASTERY, "{}"));
        } catch (Exception e) {
            m = new JSONObject();
        }
        double v = m.optDouble(knowledge, 0.5);
        v = correct ? Math.min(1.0, v + 0.15) : Math.max(0.0, v - 0.2);
        try {
            m.put(knowledge, v);
        } catch (Exception ignore) {
        }
        prefs.edit().putString(KEY_MASTERY, m.toString()).apply();
    }

    /**
     * 启用/禁用 HOME launcher alias。
     * 锁屏时启用，让按 HOME 键回到锁屏界面。
     * 解锁时禁用，让按 HOME 键回到正常电视桌面。
     */
    public void setHomeAliasEnabled(Context context, boolean enabled) {
        ComponentName alias = new ComponentName(context, "com.kidslock.app.LockScreenHome");
        PackageManager pm = context.getPackageManager();
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP);
    }
}
