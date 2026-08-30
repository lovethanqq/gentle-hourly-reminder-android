package com.luoluo.reminder

import android.content.Context
import android.util.Log

/**
 * 配置保存在 SharedPreferences：三个提醒类型的开关 + 各自可编辑的提醒文案。
 * 时间均为固定常量（活动 09–23 整点、饮食 08/12/18、睡觉 00:00），因此无需持久化时间。
 *
 * 兼容 V1：旧版本只有一个总开关（key "enabled"），升级后默认迁移为“活动提醒”开关。
 */
object SettingsStore {

    data class Settings(
        val activityEnabled: Boolean = false,
        val mealEnabled: Boolean = false,
        val sleepEnabled: Boolean = false,
        val activityText: String = "",
        val mealText: String = "",
        val sleepText: String = "",
        val voiceEnabled: Boolean = true,
    ) {
        val anyEnabled: Boolean get() = activityEnabled || mealEnabled || sleepEnabled

        /** 某类型实际生效的文案：用户自定义优先，留空回退内置默认 */
        fun textFor(type: ScheduleMath.ReminderType): String = when (type) {
            ScheduleMath.ReminderType.ACTIVITY -> activityText.ifBlank { type.text }
            ScheduleMath.ReminderType.MEAL -> mealText.ifBlank { type.text }
            ScheduleMath.ReminderType.SLEEP -> sleepText.ifBlank { type.text }
        }
    }

    private const val FILE = "settings"

    fun load(context: Context): Settings {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Settings(
            activityEnabled = sp.getBoolean("activityEnabled", sp.getBoolean("enabled", false)),
            mealEnabled = sp.getBoolean("mealEnabled", false),
            sleepEnabled = sp.getBoolean("sleepEnabled", false),
            activityText = sp.getString("activityText", "") ?: "",
            mealText = sp.getString("mealText", "") ?: "",
            sleepText = sp.getString("sleepText", "") ?: "",
            voiceEnabled = sp.getBoolean("voiceEnabled", true),
        )
    }

    fun save(context: Context, s: Settings) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("activityEnabled", s.activityEnabled)
            .putBoolean("mealEnabled", s.mealEnabled)
            .putBoolean("sleepEnabled", s.sleepEnabled)
            .putString("activityText", s.activityText)
            .putString("mealText", s.mealText)
            .putString("sleepText", s.sleepText)
            .putBoolean("voiceEnabled", s.voiceEnabled)
            .apply()
    }

    /**
     * 最近一次已展示的事件标识（type@triggerAt）。
     * 系统极端迟到时可能把同一个闹钟重复投递多次，用它去重，避免同一事件连弹多条。
     */
    fun lastNotifiedKey(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("lastNotifiedKey", "") ?: ""

    fun setLastNotifiedKey(context: Context, key: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("lastNotifiedKey", key)
            .apply()
    }
}
