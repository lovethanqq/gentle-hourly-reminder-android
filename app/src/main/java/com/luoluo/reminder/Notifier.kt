package com.luoluo.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.luoluo.reminder.ScheduleMath.ReminderType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notifier {

    private const val TAG = "LuoluoReminder"

    private fun channelId(type: ReminderType) =
        "luoluo_" + type.name.lowercase(Locale.ROOT)

    /**
     * 每类提醒一个高重要性（IMPORTANCE_HIGH）渠道 → 系统以横幅（Heads-up）展示。
     * 默认静音（不设提示音），配合轻微震动；同时清理 V1 的旧渠道。
     */
    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.deleteNotificationChannel("luoluo_reminder_vibrate")
        nm.deleteNotificationChannel("luoluo_reminder_silent")
        for (type in ReminderType.entries) {
            val channel = NotificationChannel(
                channelId(type),
                type.label,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "洛洛提醒 · ${type.label}"
                setSound(null, null) // 静音：不播放铃声，靠横幅 + 轻震动
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** 展示一次真实调度出来的提醒（文案由调用方解析） */
    fun show(context: Context, event: ScheduleMath.Event, text: String, personaPath: String?) {
        show(context, event.type, formatTime(event.triggerAt), text, personaPath)
    }

    /**
     * 展示一条提醒通知。点击后打开 App 首页，几秒后横幅自动消失。
     * 视觉：完全跟随系统卡片底色；若用户设置了头像图，则显示在文字左侧。
     * 注意：刻意不调用 setColor() —— 系统模板会用强调色在通知边缘画出彩色条。
     */
    fun show(
        context: Context,
        type: ReminderType,
        timeLabel: String,
        text: String,
        personaPath: String?,
    ) {
        ensureChannels(context)

        val card = RemoteViews(context.packageName, R.layout.notification_card).apply {
            setTextViewText(R.id.notif_body, text)
            setTextViewText(R.id.notif_time, timeLabel)
            val persona = if (personaPath.isNullOrBlank()) {
                null
            } else {
                ImageStore.decode(context, java.io.File(personaPath).name, 96)
            }
            if (persona != null) {
                setImageViewBitmap(R.id.notif_person, persona)
                setViewVisibility(R.id.notif_person, android.view.View.VISIBLE)
            } else {
                setViewVisibility(R.id.notif_person, android.view.View.GONE)
            }
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId(type))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(type.title)
            .setContentText(text)
            .setCustomContentView(card)
            .setCustomBigContentView(card)
            .setCustomHeadsUpContentView(card)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            // 不同类型使用不同通知 id，避免互相顶掉
            NotificationManagerCompat.from(context).notify(type.ordinal + 1, notification)
            Log.d(TAG, "发通知：${type.label} id=${type.ordinal + 1} time=$timeLabel text=$text")
        } catch (_: SecurityException) {
            // 用户关闭了通知权限：安静忽略，App 不崩溃
            Log.d(TAG, "发通知失败：无通知权限（SecurityException），App 不崩溃")
        }
    }

    private fun formatTime(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}
