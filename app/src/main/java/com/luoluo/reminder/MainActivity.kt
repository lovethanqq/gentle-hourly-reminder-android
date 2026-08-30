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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** 开源（Debug）构建的默认首页标题；Release（作者自用）默认空白 */
private const val DEFAULT_DEBUG_TITLE = "今天也要努力生活呀！加油鸭！"

@Composable
fun LuoluoReminderApp() {
    val context = LocalContext.current
    val isDebug = isDebugBuild(context)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var settings by remember { mutableStateOf(SettingsStore.load(context)) }
    var headerTitle by remember {
        mutableStateOf(settings.headerTitle ?: if (isDebug) DEFAULT_DEBUG_TITLE else "")
    }
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
                notifDenied = settings.anyEnabled && !Notifier.canNotify(context)
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

    fun sendTest(type: ReminderType) {
        if (!Notifier.canNotify(context)) {
            requestPermissionIfNeeded()
            return
        }
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val text = settings.textFor(type)
        Notifier.show(context, type, now, text, settings.personaPath)
        if (settings.voiceEnabled && VoiceAnnouncer.headsetConnected(context)) {
            VoiceAnnouncer.announceAsync(context, text) { }
        }
    }

    fun onDebugFullTest(type: ReminderType) {
        val triggerAt = System.currentTimeMillis() + 2 * 60_000L
        ReminderScheduler.scheduleDebugTest(context, triggerAt, type)
        scope.launch {
            snackbar.showSnackbar("已安排：2分钟后走完整链路弹出「${type.label}」，可先锁屏等待")
        }
    }

    fun pickPersona(uri: Uri?) {
        if (uri != null && ImageStore.copyFromUri(context, uri, ImageStore.PERSONA_FILE)) {
            settings = settings.copy(personaPath = ImageStore.path(context, ImageStore.PERSONA_FILE))
            SettingsStore.setPersonaPath(context, settings.personaPath)
        }
    }

    fun pickHomeBg(uri: Uri?) {
        if (uri != null && ImageStore.copyFromUri(context, uri, ImageStore.HOME_BG_FILE)) {
            settings = settings.copy(homeBgPath = ImageStore.path(context, ImageStore.HOME_BG_FILE))
            SettingsStore.setHomeBgPath(context, settings.homeBgPath)
        }
    }

    fun clearPersona() {
        ImageStore.clear(context, ImageStore.PERSONA_FILE)
        settings = settings.copy(personaPath = "")
        SettingsStore.setPersonaPath(context, "")
    }

    fun clearHomeBg() {
        ImageStore.clear(context, ImageStore.HOME_BG_FILE)
        settings = settings.copy(homeBgPath = "")
        SettingsStore.setHomeBgPath(context, "")
    }

    fun onSaveReminders() {
        SettingsStore.save(context, settings)
        Notifier.ensureChannels(context)
        if (settings.anyEnabled) {
            ReminderScheduler.scheduleNext(context)
            requestPermissionIfNeeded()
        } else {
            ReminderScheduler.cancel(context)
            notifDenied = false
        }
        refreshTick++
        scope.launch {
            snackbar.showSnackbar(if (settings.anyEnabled) "提醒设置已保存" else "已保存，所有提醒已关闭")
        }
    }

    val anyOn = settings.anyEnabled
    val nextFire = remember(anyOn, refreshTick) {
        if (anyOn) {
            ScheduleMath.nextAfter(
                System.currentTimeMillis(),
                settings.activityEnabled,
                settings.mealEnabled,
                settings.sleepEnabled,
            )
        } else {
            null
        }
    }

    val homeBgBitmap = remember(settings.homeBgPath) {
        if (settings.homeBgPath.isBlank()) {
            null
        } else {
            ImageStore.decode(context, ImageStore.HOME_BG_FILE, 1200)?.asImageBitmap()
        }
    }
    val personaPreview = remember(showSettings, settings.personaPath) {
        if (showSettings && settings.personaPath.isNotBlank()) {
            ImageStore.decode(context, ImageStore.PERSONA_FILE, 96)?.asImageBitmap()
        } else {
            null
        }
    }
    val homeBgPreview = remember(showSettings, settings.homeBgPath) {
        if (showSettings && settings.homeBgPath.isNotBlank()) {
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
                        settings = settings,
                        headerTitle = headerTitle,
                        onUpdate = { settings = it },
                        onSaveReminders = ::onSaveReminders,
                        onDebugFullTest = ::onDebugFullTest,
                        sendTest = ::sendTest,
                        notifDenied = notifDenied,
                        isDebug = isDebug,
                        personaPreview = personaPreview,
                        homeBgPreview = homeBgPreview,
                        onPickPersona = ::pickPersona,
                        onClearPersona = ::clearPersona,
                        onPickHomeBg = ::pickHomeBg,
                        onClearHomeBg = ::clearHomeBg,
                        onHeaderTitleChange = {
                            headerTitle = it
                            SettingsStore.setHeaderTitle(context, it)
                        },
                        onBack = { showSettings = false },
                    )
                } else {
                    // ---------- 首页：纯状态面板 ----------
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(12.dp))
                        if (headerTitle.isNotBlank()) {
                            Text(
                                headerTitle,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Box(Modifier.padding(20.dp)) {
                                Column(Modifier.padding(end = 44.dp)) {
                                    Text(
                                        if (anyOn) "提醒进行中" else "提醒未开启",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = when {
                                            !anyOn -> "去 ⚙ 里开启提醒"
                                            nextFire != null ->
                                                "下一次：${describeNext(nextFire.triggerAt)} · ${nextFire.type.label}"
                                            else -> "提醒已开启"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (anyOn && notifDenied) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "需要通知权限才能发送提醒。",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showSettings = true },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .align(Alignment.TopEnd),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_gear),
                                        contentDescription = "设置",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isDebugBuild(context: android.content.Context): Boolean =
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

private fun describeNext(nextMillis: Long): String {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nextMillis))
    val nextCal = java.util.Calendar.getInstance().apply { timeInMillis = nextMillis }
    val nowCal = java.util.Calendar.getInstance()
    val sameDay =
        nextCal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
            nextCal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)
    return (if (sameDay) "今天 " else "明天 ") + time
}
