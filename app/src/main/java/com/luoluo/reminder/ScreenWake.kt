package com.luoluo.reminder

import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * 短暂亮屏：闹钟触发时若屏幕未亮，用系统 WakeLock 点亮屏幕约 10 秒后自动释放。
 *
 * - 只在闹钟触发的一瞬间使用，几秒后自动结束，无 Service、无循环、无长时 WakeLock
 * - FULL_WAKE_LOCK 是官方弃用的旧 API，但在 Android 8–15 上仍然可用；
 *   在不申请 USE_FULL_SCREEN_INTENT（Android 14+ 对提醒类 App 默认收回）或
 *   SYSTEM_ALERT_WINDOW（本项目禁止）的前提下，这是从 Receiver 点亮屏幕的
 *   唯一低功耗官方途径，被大量闹钟/提醒类应用采用
 * - 屏幕已亮时直接跳过（横幅本身可见，无需唤醒）
 * - 不绕过锁屏：只点亮到锁屏界面，通知是否显示在锁屏上取决于系统设置
 */
@Suppress("DEPRECATION")
object ScreenWake {

    private const val WAKE_MS = 10_000L
    private const val TAG = "LuoluoReminder"

    fun wakeBriefly(context: Context) {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        if (pm.isInteractive) {
            Log.d(TAG, "WakeLock: 屏幕已亮（interactive），跳过唤醒")
            return
        }
        Log.d(TAG, "WakeLock: 屏幕非交互，申请 FULL_WAKE_LOCK|ACQUIRE_CAUSES_WAKEUP，${WAKE_MS}ms 后自动释放")
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "luoluo:reminderWake",
        )
        wakeLock.acquire(WAKE_MS) // 到时由系统自动释放，屏幕随后按系统设置自然熄灭
        Log.d(TAG, "WakeLock: 已获取（luoluo:reminderWake），10s 后系统自动释放")
    }
}
