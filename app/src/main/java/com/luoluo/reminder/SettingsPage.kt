package com.luoluo.reminder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val AccentBlue = Color(0xFF4FA3E3)
private val AccentGreen = Color(0xFF34C759)
private val AccentPurple = Color(0xFFAF52DE)

/** 设置页：提醒管理（开关/时间/文案）+ 语音播报 + 个性化（标题/头像/背景）+ Debug 测试工具 */
@Composable
fun SettingsPage(
    settings: SettingsStore.Settings,
    headerTitle: String,
    onHeaderTitleChange: (String) -> Unit,
    onUpdate: (SettingsStore.Settings) -> Unit,
    onSaveReminders: () -> Unit,
    onDebugFullTest: (ScheduleMath.ReminderType) -> Unit,
    sendTest: (ScheduleMath.ReminderType) -> Unit,
    notifDenied: Boolean,
    isDebug: Boolean,
    personaPreview: ImageBitmap?,
    homeBgPreview: ImageBitmap?,
    homeBgOpacity: Int,
    onHomeBgOpacityChange: (Int) -> Unit,
    onPickPersona: (Uri?) -> Unit,
    onClearPersona: () -> Unit,
    onPickHomeBg: (Uri?) -> Unit,
    onClearHomeBg: () -> Unit,
    onBack: () -> Unit,
) {
    val personaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> onPickPersona(uri) }
    val homeBgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> onPickHomeBg(uri) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) {
                Text("‹ 返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            // ---------- 提醒管理 ----------
            Text(
                "提醒",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            ModuleCard(
                title = "活动提醒",
                accent = AccentBlue,
                enabled = settings.activityEnabled,
                onToggle = {
                    val next = settings.copy(activityEnabled = !settings.activityEnabled)
                    onUpdate(next)
                },
                schedule = "09:00 – 23:00（每个整点）",
                text = settings.activityText.ifBlank { ScheduleMath.ReminderType.ACTIVITY.text },
                onTextChange = { t -> onUpdate(settings.copy(activityText = t.trim())) },
            )
            Spacer(Modifier.height(12.dp))
            ModuleCard(
                title = "饮食提醒",
                accent = AccentGreen,
                enabled = settings.mealEnabled,
                onToggle = {
                    val next = settings.copy(mealEnabled = !settings.mealEnabled)
                    onUpdate(next)
                },
                schedule = "08:00 · 12:00 · 18:00",
                text = settings.mealText.ifBlank { ScheduleMath.ReminderType.MEAL.text },
                onTextChange = { t -> onUpdate(settings.copy(mealText = t.trim())) },
            )
            Spacer(Modifier.height(12.dp))
            ModuleCard(
                title = "睡觉提醒",
                accent = AccentPurple,
                enabled = settings.sleepEnabled,
                onToggle = {
                    val next = settings.copy(sleepEnabled = !settings.sleepEnabled)
                    onUpdate(next)
                },
                schedule = "00:00",
                text = settings.sleepText.ifBlank { ScheduleMath.ReminderType.SLEEP.text },
                onTextChange = { t -> onUpdate(settings.copy(sleepText = t.trim())) },
            )

            if (settings.anyEnabled && notifDenied) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "需要通知权限才能发送提醒。",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onSaveReminders, modifier = Modifier.fillMaxWidth()) {
                Text("保存提醒设置")
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ---------- 语音播报 ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("戴耳机时语音播报", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "佩戴耳机时用语音念出文案，正在播放的音乐只会压低、不会暂停",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.voiceEnabled,
                    onCheckedChange = { v ->
                        val next = settings.copy(voiceEnabled = v)
                        onUpdate(next)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            // ---------- 个性化 ----------
            Text(
                "个性化",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text("顶部标题文字", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "显示在首页最上方；留空则不显示，最多 12 个字",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = headerTitle,
                onValueChange = { onHeaderTitleChange(it.take(12)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("留空则不显示") },
                singleLine = true,
            )
            Spacer(Modifier.height(20.dp))

            Text("通知头像图片", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "从相册选择一张图，显示在通知文字左侧（可选）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (personaPreview != null) {
                    Image(
                        bitmap = personaPreview,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("无", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.size(12.dp))
                Button(onClick = { personaPicker.launch(arrayOf("image/*")) }) {
                    Text("从相册选择")
                }
                if (settings.personaPath.isNotBlank() || personaPreview != null) {
                    TextButton(onClick = onClearPersona) { Text("清除") }
                }
            }
            Spacer(Modifier.height(20.dp))

            Text("首页背景图片", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "从相册选择一张图作为首页背景，自动叠加白色蒙层保证文字可读",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (homeBgPreview != null) {
                Image(
                    bitmap = homeBgPreview,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
            Text("背景透明度：${homeBgOpacity}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = homeBgOpacity.toFloat(),
                onValueChange = { onHomeBgOpacityChange((it + 0.5f).toInt()) },
                valueRange = 0f..100f,
            )
                Button(onClick = { homeBgPicker.launch(arrayOf("image/*")) }) {
                    Text("从相册选择")
                }
                if (settings.homeBgPath.isNotBlank() || homeBgPreview != null) {
                    TextButton(onClick = onClearHomeBg) { Text("清除") }
                }
            }

            // ---------- Debug 测试工具 ----------
            if (isDebug) {
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(24.dp))
                Text(
                    "测试工具（仅 Debug 版）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TestChip("测试活动", AccentBlue, Modifier.weight(1f)) {
                        sendTest(ScheduleMath.ReminderType.ACTIVITY)
                    }
                    TestChip("测试饮食", AccentGreen, Modifier.weight(1f)) {
                        sendTest(ScheduleMath.ReminderType.MEAL)
                    }
                    TestChip("测试睡觉", AccentPurple, Modifier.weight(1f)) {
                        sendTest(ScheduleMath.ReminderType.SLEEP)
                    }
                }
                Spacer(Modifier.height(8.dp))
                var debugStep by remember { mutableIntStateOf(0) }
                val debugTypes = listOf(
                    ScheduleMath.ReminderType.ACTIVITY,
                    ScheduleMath.ReminderType.MEAL,
                    ScheduleMath.ReminderType.SLEEP,
                )
                Button(
                    onClick = {
                        val type = debugTypes[debugStep % debugTypes.size]
                        debugStep++
                        val triggerAt = System.currentTimeMillis() + 2 * 60_000L
                        onDebugFullTest(type)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("2分钟后完整测试")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "完整经过：AlarmManager → ReminderReceiver → WakeLock → " +
                        "Notification → scheduleNext。连续点击依次测试活动 / 饮食 / 睡觉。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    accent: Color,
    enabled: Boolean,
    onToggle: () -> Unit,
    schedule: String,
    text: String,
    onTextChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.6f else 0.22f)),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = accent,
                        checkedThumbColor = Color.White,
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "时间：$schedule",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("提醒文案") },
                textStyle = MaterialTheme.typography.bodySmall,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun TestChip(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
