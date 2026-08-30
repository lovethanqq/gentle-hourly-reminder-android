package com.luoluo.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.luoluo.reminder.ScheduleMath.ReminderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 基于 AlarmManager 的“低功耗三类提醒”调度器。
 *
 * - 无论开启几个提醒类型，同一时刻系统里只有一个生产闹钟：
 *   “所有已开启类型中最早的下一次事件”（由 ScheduleMath.nextAfter 计算）。
 *   事件触发后由 Receiver 展示并重新安排下一次，形成链条。
 * - 使用 setAndAllowWhileIdle（非精确闹钟）：不需要精确闹钟权限，
 *   系统允许几分钟级误差，Doze 低电耗模式下也能按系统规则触发。
 * - 使用 RTC_WAKEUP：屏幕关闭时也能唤醒设备。
 * - 固定使用同一个 PendingIntent，重复安排自动替换，不会堆积。
 *
 * Debug 专用：scheduleDebugTest 用独立的 PendingIntent（requestCode 不同），
 * “2分钟后完整测试”不会干扰生产闹钟链。
 *
 * 除闹钟触发的一瞬间外，App 进程平时完全不运行：
 * 没有 Service、没有轮询、没有后台线程、没有长时 WakeLock。
 */
object ReminderScheduler {

    private const val TAG = "LuoluoReminder"
    private const val REQUEST_CODE = 1001
    private const val REQUEST_CODE_DEBUG = 2001

    const val ACTION_FIRE = "com.luoluo.reminder.ACTION_FIRE"
    const val ACTION_FIRE_DEBUG = "com.luoluo.reminder.ACTION_FIRE_DEBUG"
    const val EXTRA_DEBUG_TYPE = "debug_type"

    private val flags =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun debugPendingIntent(context: Context, type: ReminderType): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE_DEBUG)
            .putExtra(EXTRA_DEBUG_TYPE, type.name)
        return PendingIntent.getBroadcast(context, REQUEST_CODE_DEBUG, intent, flags)
    }

    /** 读取当前配置，安排下一次提醒。没有任何类型开启时，取消闹钟并返回。 */
    fun scheduleNext(context: Context) {
        val s = SettingsStore.load(context)
        val next = ScheduleMath.nextAfter(
            System.currentTimeMillis(),
            s.activityEnabled,
            s.mealEnabled,
            s.sleepEnabled,
        )
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (next == null) {
            am.cancel(pendingIntent(context))
            Log.d(TAG, "scheduleNext：所有提醒已关闭，已取消闹钟")
            return
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAt, pendingIntent(context))
        Log.d(
            TAG,
            "scheduleNext：已安排下一次提醒 ${next.type.label} @ ${fmt(next.triggerAt)}" +
                "（setAndAllowWhileIdle / RTC_WAKEUP）",
        )
    }

    /** 取消所有已计划的提醒。 */
    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context))
        Log.d(TAG, "cancel：已取消全部已计划提醒")
    }

    /** Debug 专用：安排一次完整链路测试（独立闹钟，不占用生产闹钟链）。 */
    fun scheduleDebugTest(context: Context, triggerAtMillis: Long, type: ReminderType) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, debugPendingIntent(context, type))
        Log.d(TAG, "Debug 测试已登记到 AlarmManager：$type @ ${fmt(triggerAtMillis)}")
    }

    private fun fmt(millis: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
