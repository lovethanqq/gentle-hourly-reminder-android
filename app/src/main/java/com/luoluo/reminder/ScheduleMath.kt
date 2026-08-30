package com.luoluo.reminder

import java.util.Calendar

/**
 * 三类提醒的调度网格（纯计算，可单元测试）：
 * - 活动提醒：09:00–23:00 每个整点（含 09:00 与 23:00）
 * - 饮食提醒：08:00 / 12:00 / 18:00
 * - 睡觉提醒：00:00
 *
 * 整个应用同一时刻只挂一个 AlarmManager 闹钟 = “所有已开启类型中最早的下一次事件”。
 * 闹钟触发后在碰撞窗口内合并同刻事件，按 饮食 > 活动 > 睡觉 的优先级只展示一条，
 * 因此 12:00 / 18:00 不会连续弹出两条通知。
 */
object ScheduleMath {

    enum class ReminderType(val label: String, val title: String, val text: String) {
        ACTIVITY("活动提醒", "洛洛提醒 · 活动", "多喝水，别低头。一小时到了，去活动一下。"),
        MEAL("饮食提醒", "洛洛提醒 · 吃饭", "吃饭啦，注意饮食结构。别吃太多，也不要不吃。"),
        SLEEP("睡觉提醒", "洛洛提醒 · 睡觉", "该睡觉了，别熬夜了，会变丑。");

        /** 同刻碰撞时的优先级：数值大者胜（12:00 / 18:00 饮食优先于活动）。 */
        val priority: Int
            get() = when (this) {
                MEAL -> 3
                ACTIVITY -> 2
                SLEEP -> 1
            }
    }

    data class Event(val type: ReminderType, val triggerAt: Long)

    const val ACTIVITY_START_HOUR = 9
    const val ACTIVITY_END_HOUR = 23 // 含端点：23:00 仍是当天最后一次活动提醒
    val MEAL_HOURS = listOf(8, 12, 18)
    const val SLEEP_HOUR = 0

    /** 触发时把同一分钟内的事件视为碰撞，一起合并处理 */
    private const val COLLISION_SLACK_MS = 90_000L

    /** 闹钟被系统推迟超过 30 分钟（极端省电场景）则放弃该次展示，避免冒出过时提醒 */
    private const val LATE_TOLERANCE_MS = 30 * 60_000L

    private const val HOUR_MS = 60L * 60_000L

    /** 供调度器使用：所有已开启类型中，碰撞窗口之后的最早下一次事件。全部关闭时返回 null。 */
    fun nextAfter(
        nowMillis: Long,
        activity: Boolean,
        meal: Boolean,
        sleep: Boolean,
    ): Event? {
        val limit = nowMillis + COLLISION_SLACK_MS
        val candidates = mutableListOf<Event>()
        if (activity) nextEventFor(ReminderType.ACTIVITY, limit)?.let { candidates.add(it) }
        if (meal) nextEventFor(ReminderType.MEAL, limit)?.let { candidates.add(it) }
        if (sleep) nextEventFor(ReminderType.SLEEP, limit)?.let { candidates.add(it) }
        return candidates.minByOrNull { it.triggerAt }
    }

    /** 供接收器使用：本次闹钟到达时应当展示的事件（同刻碰撞取优先级最高者）。 */
    fun dueEvent(
        nowMillis: Long,
        activity: Boolean,
        meal: Boolean,
        sleep: Boolean,
    ): Event? {
        val limit = nowMillis + COLLISION_SLACK_MS
        val earliest = nowMillis - LATE_TOLERANCE_MS
        val candidates = mutableListOf<Event>()
        if (activity) prevEventFor(ReminderType.ACTIVITY, limit)?.let { candidates.add(it) }
        if (meal) prevEventFor(ReminderType.MEAL, limit)?.let { candidates.add(it) }
        if (sleep) prevEventFor(ReminderType.SLEEP, limit)?.let { candidates.add(it) }
        return candidates
            .filter { it.triggerAt in earliest..limit }
            .maxByOrNull { it.type.priority }
    }

    /** 某类型在 limit 之后的下一次事件 */
    fun nextEventFor(type: ReminderType, limit: Long): Event? {
        var dayStart = dayStartOf(limit)
        repeat(2) {
            val hours: List<Int> = when (type) {
                ReminderType.ACTIVITY -> (ACTIVITY_START_HOUR..ACTIVITY_END_HOUR).toList()
                ReminderType.MEAL -> MEAL_HOURS
                ReminderType.SLEEP -> listOf(SLEEP_HOUR)
            }
            for (h in hours) {
                val t = dayStart + h * HOUR_MS
                if (t > limit) return Event(type, t)
            }
            dayStart = nextDayStart(dayStart)
        }
        return null
    }

    /** 某类型在 limit 之前（含）最近的一次事件 */
    private fun prevEventFor(type: ReminderType, limit: Long): Event? {
        var dayStart = dayStartOf(limit)
        repeat(2) {
            val hoursDesc: List<Int> = when (type) {
                ReminderType.ACTIVITY -> (ACTIVITY_END_HOUR downTo ACTIVITY_START_HOUR).toList()
                ReminderType.MEAL -> MEAL_HOURS.sortedDescending()
                ReminderType.SLEEP -> listOf(SLEEP_HOUR)
            }
            for (h in hoursDesc) {
                val t = dayStart + h * HOUR_MS
                if (t <= limit) return Event(type, t)
            }
            dayStart = prevDayStart(dayStart)
        }
        return null
    }

    private fun dayStartOf(millis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun nextDayStart(dayStartMillis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = dayStartMillis
        c.add(Calendar.DATE, 1) // 用日历加一天，正确处理夏令时等非 24 小时的日子
        return c.timeInMillis
    }

    private fun prevDayStart(dayStartMillis: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = dayStartMillis
        c.add(Calendar.DATE, -1)
        return c.timeInMillis
    }
}
