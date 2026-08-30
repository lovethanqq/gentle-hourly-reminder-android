package com.luoluo.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.luoluo.reminder.ScheduleMath.ReminderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 系统闹钟触发时被唤醒，只做几件事然后立即结束：
 * 1) 计算本次应展示的事件（同刻碰撞按 饮食 > 活动 > 睡觉 合并成一条）
 * 2) 若屏幕未亮，短暂点亮屏幕（约 10 秒后自动释放）
 * 3) 发通知；若开启了语音播报且佩戴耳机，用 TTS 非打断播报文案
 * 4) 安排下一次提醒，返回
 *
 * Debug 专用：ACTION_FIRE_DEBUG 走“2分钟后完整测试”链路，
 * 除“选哪个提醒”来自 Intent 外，闹钟/亮屏/通知/播报/重排全部复用真实代码路径。
 *
 * 不启动任何 Service/线程（TTS 播报期间用 goAsync 短暂保活，上限 8 秒），
 * 不持有长时 WakeLock —— 结束后进程随即可以继续休眠。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Receiver 触发：action=$action")
        when (action) {
            ReminderScheduler.ACTION_FIRE -> handleRegularFire(context)
            ReminderScheduler.ACTION_FIRE_DEBUG -> handleDebugFire(context, intent)
            else -> return
        }
    }

    private fun handleRegularFire(context: Context) {
        val s = SettingsStore.load(context)
        if (!s.anyEnabled) {
            Log.d(TAG, "所有提醒均已关闭，忽略本次触发")
            return
        }
        val now = System.currentTimeMillis()
        val due = ScheduleMath.dueEvent(now, s.activityEnabled, s.mealEnabled, s.sleepEnabled)
        if (due == null) {
            Log.d(TAG, "闹钟迟到超过容忍范围，本次展示已放弃，仅安排下一次")
            ReminderScheduler.scheduleNext(context)
            return
        }
        Log.d(TAG, "命中事件：${due.type.label} @ ${fmt(due.triggerAt)}（当前 ${fmt(now)}）")
        val key = "${due.type.name}@${due.triggerAt}"
        if (key == SettingsStore.lastNotifiedKey(context)) {
            // 系统把迟到的闹钟重复投递了多次：同一次事件只展示一条
            Log.d(TAG, "该事件刚已展示过（系统重复投递），跳过通知")
            ReminderScheduler.scheduleNext(context)
            return
        }
        ScreenWake.wakeBriefly(context)
        val text = s.textFor(due.type)
        Notifier.show(context, due, text, s.personaPath)
        SettingsStore.setLastNotifiedKey(context, key)
        finishWithAnnouncement(context, text)
    }

    private fun handleDebugFire(context: Context, intent: Intent) {
        val typeName = intent.getStringExtra(ReminderScheduler.EXTRA_DEBUG_TYPE)
        val type = ReminderType.entries.firstOrNull { it.name == typeName } ?: ReminderType.ACTIVITY
        Log.d(TAG, "Debug 完整链路测试触发：$type")
        ScreenWake.wakeBriefly(context)
        val now = System.currentTimeMillis()
        val text = SettingsStore.load(context).textFor(type)
        Notifier.show(context, type, fmt(now), text, SettingsStore.load(context).personaPath)
        finishWithAnnouncement(context, text)
    }

    /**
     * 通知之后的收尾：需要语音播报时用 goAsync 短暂保活（8 秒上限），
     * 播报完成（或跳过播报）后安排下一次提醒并立即结束。
     */
    private fun finishWithAnnouncement(context: Context, text: String) {
        val s = SettingsStore.load(context)
        if (!s.voiceEnabled) {
            Log.d(TAG, "语音播报开关关闭，跳过")
            ReminderScheduler.scheduleNext(context)
            return
        }
        if (!VoiceAnnouncer.headsetConnected(context)) {
            Log.d(TAG, "语音播报：未佩戴耳机，跳过")
            ReminderScheduler.scheduleNext(context)
            return
        }
        Log.d(TAG, "语音播报：佩戴耳机，开始 TTS")
        val pending = goAsync()
        var finished = false
        fun finish() {
            if (!finished) {
                finished = true
                ReminderScheduler.scheduleNext(context)
                pending.finish()
                Log.d(TAG, "本次触发处理完毕，进程可以继续休眠")
            }
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ finish() }, 8000) // 兜底：播报卡死也不会拖住进程
        VoiceAnnouncer.announceAsync(context, text) {
            handler.post { finish() }
        }
    }

    private fun fmt(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

    companion object {
        private const val TAG = "LuoluoReminder"
    }
}
