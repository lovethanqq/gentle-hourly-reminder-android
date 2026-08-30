package com.luoluo.reminder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 设置页：顶部标题文字 / 通知头像图片 / 首页背景图片（改动即时生效） */
@Composable
fun SettingsPage(
    headerTitle: String,
    onHeaderTitleChange: (String) -> Unit,
    hasPersona: Boolean,
    personaPreview: ImageBitmap?,
    onPickPersona: (Uri?) -> Unit,
    onClearPersona: () -> Unit,
    hasHomeBg: Boolean,
    homeBgPreview: ImageBitmap?,
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
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("‹ 返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Text("顶部标题文字", style = MaterialTheme.typography.titleSmall)
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
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Text("通知头像图片", style = MaterialTheme.typography.titleSmall)
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
                if (hasPersona || personaPreview != null) {
                    TextButton(onClick = onClearPersona) { Text("清除") }
                }
            }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(24.dp))

            Text("首页背景图片", style = MaterialTheme.typography.titleSmall)
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
                Button(onClick = { homeBgPicker.launch(arrayOf("image/*")) }) {
                    Text("从相册选择")
                }
                if (hasHomeBg || homeBgPreview != null) {
                    TextButton(onClick = onClearHomeBg) { Text("清除") }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
