package com.luoluo.reminder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luoluo.reminder.ScheduleMath.ReminderType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuoluoReminderApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // 自愈：系统“强行停止”会清掉已注册的闹钟；重新打开 App 时若有提醒开启，
        // 就重新安排下一次（闹钟是幂等替换的，重复调用无害）。
        if (SettingsStore.load(this).anyEnabled) {
            ReminderScheduler.scheduleNext(this)
        }
    }
}

private val AccentBlue = Color(0xFF4FA3E3)
private val AccentGreen = Color(0xFF34C759)
private val AccentPurple = Color(0xFFAF52DE)

/** 开源（Debug）构建的默认首页标题；Release（作者自用）默认空白 */
private const val DEFAULT_DEBUG_TITLE = "今天也要努力生活呀！加油鸭！"

@Composable
fun LuoluoReminderApp() {
    val context = LocalContext.current
    val isDebug = isDebugBuild(context)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 表单状态：提醒相关点“保存设置”时才生效；外观相关（标题/图片）即时生效
    val saved = remember { SettingsStore.load(context) }
    var activityOn by remember { mutableStateOf(saved.activityEnabled) }
    var mealOn by remember { mutableStateOf(saved.mealEnabled) }
    var sleepOn by remember { mutableStateOf(saved.sleepEnabled) }
    var activityText by remember {
        mutableStateOf(saved.activityText.ifBlank { ReminderType.ACTIVITY.text })
    }
    var mealText by remember {
        mutableStateOf(saved.mealText.ifBlank { ReminderType.MEAL.text })
    }
    var sleepText by remember {
        mutableStateOf(saved.sleepText.ifBlank { ReminderType.SLEEP.text })
    }
    var voiceOn by remember { mutableStateOf(saved.voiceEnabled) }
    var headerTitle by remember {
        mutableStateOf(settingsHeaderValue(saved.headerTitle, isDebug))
    }
    var personaPath by remember { mutableStateOf(saved.personaPath) }
    var homeBgPath by remember { mutableStateOf(saved.homeBgPath) }

    var showSettings by remember { mutableStateOf(false) }
    var notifDenied by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifDenied = !granted
        if (!granted) {
            scope.launch { snackbar.showSnackbar("需要通知权限才能发送提醒。") }
        }
    }

    // 回到前台时刷新权限状态（用户可能去系统设置里打开了通知权限）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifDenied = (activityOn || mealOn || sleepOn) && !Notifier.canNotify(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestPermissionIfNeeded() {
        if (Notifier.canNotify(context)) {
            notifDenied = false
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notifDenied = true
        }
    }

    fun applySettings() {
        val s = SettingsStore.Settings(
            activityOn, mealOn, sleepOn,
            activityText.trim(), mealText.trim(), sleepText.trim(),
            voiceOn,
            // 标题由设置页维护，这里不覆盖
            headerTitle = null,
            personaPath = personaPath,
            homeBgPath = homeBgPath,
        )
        SettingsStore.save(context, s)
        Notifier.ensureChannels(context)
        if (s.anyEnabled) {
            ReminderScheduler.scheduleNext(context)
            requestPermissionIfNeeded()
        } else {
            ReminderScheduler.cancel(context)
            notifDenied = false
        }
        refreshTick++
        scope.launch {
            snackbar.showSnackbar(if (s.anyEnabled) "设置已保存" else "已保存，所有提醒已关闭")
        }
    }

    fun sendTest(type: ReminderType) {
        if (!Notifier.canNotify(context)) {
            requestPermissionIfNeeded()
            return
        }
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val text = when (type) {
            ReminderType.ACTIVITY -> activityText.ifBlank { type.text }
            ReminderType.MEAL -> mealText.ifBlank { type.text }
            ReminderType.SLEEP -> sleepText.ifBlank { type.text }
        }
        Notifier.show(context, type, now, text.trim(), SettingsStore.load(context).personaPath)
        if (voiceOn && VoiceAnnouncer.headsetConnected(context)) {
            VoiceAnnouncer.announceAsync(context, text.trim()) { }
        }
    }

    fun pickPersona(uri: Uri?) {
        if (uri != null && ImageStore.copyFromUri(context, uri, ImageStore.PERSONA_FILE)) {
            personaPath = ImageStore.path(context, ImageStore.PERSONA_FILE)
            SettingsStore.setPersonaPath(context, personaPath)
        }
    }

    fun pickHomeBg(uri: Uri?) {
        if (uri != null && ImageStore.copyFromUri(context, uri, ImageStore.HOME_BG_FILE)) {
            homeBgPath = ImageStore.path(context, ImageStore.HOME_BG_FILE)
            SettingsStore.setHomeBgPath(context, homeBgPath)
        }
    }

    fun clearPersona() {
        ImageStore.clear(context, ImageStore.PERSONA_FILE)
        personaPath = ""
        SettingsStore.setPersonaPath(context, "")
    }

    fun clearHomeBg() {
        ImageStore.clear(context, ImageStore.HOME_BG_FILE)
        homeBgPath = ""
        SettingsStore.setHomeBgPath(context, "")
    }

    val anyOn = activityOn || mealOn || sleepOn
    val nextFire = remember(activityOn, mealOn, sleepOn, refreshTick) {
        if (activityOn || mealOn || sleepOn) {
            ScheduleMath.nextAfter(
                System.currentTimeMillis(), activityOn, mealOn, sleepOn,
            )
        } else {
            null
        }
    }

    val homeBgBitmap = remember(homeBgPath) {
        if (homeBgPath.isBlank()) {
            null
        } else {
            ImageStore.decode(context, ImageStore.HOME_BG_FILE, 1200)?.asImageBitmap()
        }
    }
    val personaPreview = remember(showSettings, personaPath) {
        if (showSettings && personaPath.isNotBlank()) {
            ImageStore.decode(context, ImageStore.PERSONA_FILE, 96)?.asImageBitmap()
        } else {
            null
        }
    }
    val homeBgPreview = remember(showSettings, homeBgPath) {
        if (showSettings && homeBgPath.isNotBlank()) {
            ImageStore.decode(context, ImageStore.HOME_BG_FILE, 420)?.asImageBitmap()
        } else {
            null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 自定义首页背景（可选）
        if (homeBgBitmap != null) {
            Image(
                bitmap = homeBgBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.86f)),
            )
        }
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                containerColor = if (homeBgBitmap != null) Color.Transparent else MaterialTheme.colorScheme.background,
            ) { innerPadding ->
                if (showSettings) {
                    SettingsPage(
                        headerTitle = headerTitle,
                        onHeaderTitleChange = {
                            headerTitle = it
                            SettingsStore.setHeaderTitle(context, it)
                        },
                        hasPersona = personaPath.isNotBlank(),
                        personaPreview = personaPreview,
                        onPickPersona = ::pickPersona,
                        onClearPersona = ::clearPersona,
                        hasHomeBg = homeBgPath.isNotBlank(),
                        homeBgPreview = homeBgPreview,
                        onPickHomeBg = ::pickHomeBg,
                        onClearHomeBg = ::clearHomeBg,
                        onBack = { showSettings = false },
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(12.dp))
                        if (headerTitle.isNotBlank()) {
                            Text(
                                headerTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(20.dp))
                        }

                        ModuleCard(
                            title = "活动提醒",
                            accent = AccentBlue,
                            enabled = activityOn,
                            onToggle = { activityOn = it },
                            schedule = "09:00 – 23:00（每个整点）",
                            text = activityText,
                            onTextChange = { activityText = it },
                        )
                        Spacer(Modifier.height(12.dp))
                        ModuleCard(
                            title = "饮食提醒",
                            accent = AccentGreen,
                            enabled = mealOn,
                            onToggle = { mealOn = it },
                            schedule = "08:00 · 12:00 · 18:00",
                            text = mealText,
                            onTextChange = { mealText = it },
                        )
                        Spacer(Modifier.height(12.dp))
                        ModuleCard(
                            title = "睡觉提醒",
                            accent = AccentPurple,
                            enabled = sleepOn,
                            onToggle = { sleepOn = it },
                            schedule = "00:00",
                            text = sleepText,
                            onTextChange = { sleepText = it },
                        )
                        Spacer(Modifier.height(12.dp))

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
                            Switch(checked = voiceOn, onCheckedChange = { voiceOn = it })
                        }
                        Spacer(Modifier.height(12.dp))

                        if ((activityOn || mealOn || sleepOn) && notifDenied) {
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
                                    TextButton(onClick = {
                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        context.startActivity(intent)
                                    }) {
                                        Text("去开启")
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { applySettings() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("保存设置")
                        }
                        Spacer(Modifier.height(16.dp))

                        // 以下两个测试区仅在 Debug 构建显示
                        if (isDebug) {
                            Text(
                                "测试预览",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TestButton("测试活动提醒", AccentBlue, Modifier.weight(1f)) {
                                    sendTest(ReminderType.ACTIVITY)
                                }
                                TestButton("测试饮食提醒", AccentGreen, Modifier.weight(1f)) {
                                    sendTest(ReminderType.MEAL)
                                }
                                TestButton("测试睡觉提醒", AccentPurple, Modifier.weight(1f)) {
                                    sendTest(ReminderType.SLEEP)
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            Text(
                                "完整链路测试（仅 Debug 版）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            var debugStep by remember { mutableIntStateOf(0) }
                            val debugTypes = listOf(
                                ReminderType.ACTIVITY, ReminderType.MEAL, ReminderType.SLEEP,
                            )
                            Button(
                                onClick = {
                                    val type = debugTypes[debugStep % debugTypes.size]
                                    debugStep++
                                    val triggerAt = System.currentTimeMillis() + 2 * 60_000L
                                    ReminderScheduler.scheduleDebugTest(context, triggerAt, type)
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            "已安排：2分钟后走完整链路弹出「${type.label}」，可先锁屏等待",
                                        )
                                    }
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
                            Spacer(Modifier.height(16.dp))
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val statusText = when {
                                !anyOn -> "提醒未开启"
                                nextFire != null ->
                                    "下一次提醒：${describeNext(nextFire.triggerAt)} · ${nextFire.type.label}"
                                else -> "时间范围无效"
                            }
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_gear),
                                    contentDescription = "设置",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleCard(
    title: String,
    accent: Color,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
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
                        .clip(CircleShape)
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
                    onCheckedChange = onToggle,
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
private fun TestButton(label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
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

private fun isDebugBuild(context: android.content.Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

/** 从未设置过标题时按构建类型给默认值：Debug 显示标语，Release 空白 */
private fun settingsHeaderValue(saved: String?, isDebug: Boolean): String =
    saved ?: if (isDebug) DEFAULT_DEBUG_TITLE else ""

private fun describeNext(nextMillis: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextMillis))
    val nextCal = Calendar.getInstance().apply { timeInMillis = nextMillis }
    val nowCal = Calendar.getInstance()
    val sameDay =
        nextCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
            nextCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    return (if (sameDay) "今天 " else "明天 ") + time
}
