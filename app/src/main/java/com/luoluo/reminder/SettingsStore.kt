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
        /** 首页顶部标题：null = 从未设置过（Debug 默认显示标语 / Release 默认空白）；"" = 用户主动清空 */
        val headerTitle: String? = null,
        /** 通知头像图片（绝对路径），空串 = 不显示 */
        val personaPath: String = "",
        /** 首页背景图片（绝对路径），空串 = 无背景 */
        val homeBgPath: String = "",
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
            headerTitle = sp.getString("headerTitle", null),
            personaPath = sp.getString("personaPath", "") ?: "",
            homeBgPath = sp.getString("homeBgPath", "") ?: "",
        )
    }

    fun save(context: Context, s: Settings) {
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val e = sp.edit()
        e.putBoolean("activityEnabled", s.activityEnabled)
        e.putBoolean("mealEnabled", s.mealEnabled)
        e.putBoolean("sleepEnabled", s.sleepEnabled)
        e.putString("activityText", s.activityText)
        e.putString("mealText", s.mealText)
        e.putString("sleepText", s.sleepText)
        e.putBoolean("voiceEnabled", s.voiceEnabled)
        // headerTitle 为 null 表示用户从未在设置页改过：保留默认值不覆盖
        s.headerTitle?.let { e.putString("headerTitle", it) }
        e.putString("personaPath", s.personaPath)
        e.putString("homeBgPath", s.homeBgPath)
        e.apply()
    }

    /** 设置页专用：直接更新首页顶部标题（写入后即使用户清空也保持空白） */
    fun setHeaderTitle(context: Context, title: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("headerTitle", title)
            .apply()
    }

    fun setPersonaPath(context: Context, path: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("personaPath", path)
            .apply()
    }

    fun setHomeBgPath(context: Context, path: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("homeBgPath", path)
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
