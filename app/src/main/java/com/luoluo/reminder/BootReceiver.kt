package com.luoluo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机（或系统时间/时区变化）后恢复提醒计划：
 * - 任一提醒类型开启 → 重新安排下一次事件；
 * - 全部关闭 → 不安排任何闹钟。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                if (SettingsStore.load(context).anyEnabled) {
                    ReminderScheduler.scheduleNext(context)
                }
            }
        }
    }
}
