package com.luoluo.reminder

import com.luoluo.reminder.ScheduleMath.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/**
 * 验证三类提醒的调度网格与碰撞合并逻辑：
 * - 09:00–23:00 每小时活动提醒
 * - 08:00 / 12:00 / 18:00 饮食提醒
 * - 00:00 睡觉提醒
 * - 12:00 / 18:00 同刻碰撞：饮食优先，且不会重复弹两条
 */
class ScheduleMathTest {

    private fun at(hour: Int, minute: Int, extraMs: Long = 0): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis + extraMs
    }

    private fun hm(millis: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun tomorrowAt(hour: Int, minute: Int, from: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = from
        c.add(Calendar.DATE, 1)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    // ---------- 活动提醒：09:00–23:00 每个整点 ----------

    @Test
    fun 活动_早于开始时间_安排今天9点() {
        val next = ScheduleMath.nextAfter(at(7, 0), activity = true, meal = false, sleep = false)!!
        assertEquals(ReminderType.ACTIVITY, next.type)
        assertEquals("09:00", hm(next.triggerAt))
    }

    @Test
    fun 活动_整点中间_对齐到下一个整点() {
        val next = ScheduleMath.nextAfter(at(10, 37), activity = true, meal = false, sleep = false)!!
        assertEquals("11:00", hm(next.triggerAt))
    }

    @Test
    fun 活动_结束时间含端点_23点仍是最后一次() {
        val next = ScheduleMath.nextAfter(at(22, 37), activity = true, meal = false, sleep = false)!!
        assertEquals("23:00", hm(next.triggerAt))
    }

    @Test
    fun 活动_超过23点_安排次日9点() {
        val now = at(23, 30)
        val next = ScheduleMath.nextAfter(now, activity = true, meal = false, sleep = false)!!
        assertEquals(tomorrowAt(9, 0, now), next.triggerAt)
    }

    // ---------- 饮食提醒：08:00 / 12:00 / 18:00 ----------

    @Test
    fun 饮食_早晨_安排8点() {
        val next = ScheduleMath.nextAfter(at(7, 0), activity = false, meal = true, sleep = false)!!
        assertEquals(ReminderType.MEAL, next.type)
        assertEquals("08:00", hm(next.triggerAt))
    }

    @Test
    fun 饮食_12点后_安排18点() {
        val next = ScheduleMath.nextAfter(at(12, 37), activity = false, meal = true, sleep = false)!!
        assertEquals("18:00", hm(next.triggerAt))
    }

    @Test
    fun 饮食_18点后_安排次日8点() {
        val now = at(18, 30)
        val next = ScheduleMath.nextAfter(now, activity = false, meal = true, sleep = false)!!
        assertEquals(tomorrowAt(8, 0, now), next.triggerAt)
    }

    // ---------- 睡觉提醒：00:00 ----------

    @Test
    fun 睡觉_深夜23点半_安排次日零点() {
        val now = at(23, 30)
        val next = ScheduleMath.nextAfter(now, activity = false, meal = false, sleep = true)!!
        assertEquals(ReminderType.SLEEP, next.type)
        assertEquals(tomorrowAt(0, 0, now), next.triggerAt)
    }

    // ---------- 碰撞合并：饮食优先，不重复弹两条 ----------

    @Test
    fun 碰撞_12点同时有活动和饮食_饮食优先() {
        val now = at(12, 0, extraMs = 10_000)
        val due = ScheduleMath.dueEvent(now, activity = true, meal = true, sleep = true)!!
        assertEquals(ReminderType.MEAL, due.type)
        assertEquals(at(12, 0), due.triggerAt)
    }

    @Test
    fun 碰撞_18点同时有活动和饮食_饮食优先() {
        val now = at(18, 0, extraMs = 5_000)
        val due = ScheduleMath.dueEvent(now, activity = true, meal = true, sleep = true)!!
        assertEquals(ReminderType.MEAL, due.type)
    }

    @Test
    fun 碰撞_饮食关闭时_12点正常显示活动() {
        val now = at(12, 0, extraMs = 10_000)
        val due = ScheduleMath.dueEvent(now, activity = true, meal = false, sleep = true)!!
        assertEquals(ReminderType.ACTIVITY, due.type)
    }

    @Test
    fun 碰撞后下一次安排_跳过已消费的同刻活动_直接到13点() {
        // 12:00 碰撞已按饮食展示，下一次必须是 13:00 活动，而不是重复 12:00 活动
        val now = at(12, 0, extraMs = 10_000)
        val next = ScheduleMath.nextAfter(now, activity = true, meal = true, sleep = true)!!
        assertEquals(ReminderType.ACTIVITY, next.type)
        assertEquals("13:00", hm(next.triggerAt))
    }

    @Test
    fun 碰撞_闹钟迟到20分钟仍在容忍范围内_照常展示饮食() {
        val now = at(12, 20)
        val due = ScheduleMath.dueEvent(now, activity = true, meal = true, sleep = true)!!
        assertEquals(ReminderType.MEAL, due.type)
    }

    @Test
    fun 碰撞_闹钟迟到超过30分钟_放弃本次避免半夜冒出过时提醒() {
        val due = ScheduleMath.dueEvent(at(12, 35), activity = true, meal = true, sleep = true)
        assertNull(due)
    }

    // ---------- 多类型混合与兜底 ----------

    @Test
    fun 混合_9点触发后下一次是10点活动() {
        val next = ScheduleMath.nextAfter(at(9, 0, extraMs = 5_000), activity = true, meal = true, sleep = true)!!
        assertEquals(ReminderType.ACTIVITY, next.type)
        assertEquals("10:00", hm(next.triggerAt))
    }

    @Test
    fun 混合_23点活动之后_下一次是次日零点睡觉() {
        val now = at(23, 0, extraMs = 5_000)
        val next = ScheduleMath.nextAfter(now, activity = true, meal = true, sleep = true)!!
        assertEquals(ReminderType.SLEEP, next.type)
        assertEquals(tomorrowAt(0, 0, now), next.triggerAt)
    }

    @Test
    fun 全部关闭_返回空() {
        assertNull(ScheduleMath.nextAfter(at(10, 0), activity = false, meal = false, sleep = false))
    }
}
