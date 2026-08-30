package com.luoluo.reminder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File

/** 相册选图后的本地持久化与解码（图片复制进应用私有目录，重启/授权变化都不失效） */
object ImageStore {

    const val PERSONA_FILE = "persona.img"
    const val HOME_BG_FILE = "home_bg.img"

    fun copyFromUri(context: Context, uri: Uri, name: String): Boolean = try {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        val out = File(context.filesDir, name)
        input.use { ins -> out.outputStream().use { ins.copyTo(it) } }
        true
    } catch (e: Exception) {
        Log.d("LuoluoReminder", "复制图片失败：${e.message}")
        false
    }

    fun clear(context: Context, name: String) {
        File(context.filesDir, name).delete()
    }

    fun path(context: Context, name: String): String =
        File(context.filesDir, name).absolutePath

    fun exists(context: Context, name: String): Boolean =
        File(context.filesDir, name).exists()

    /** 按目标边长解码，避免整图占内存 */
    fun decode(context: Context, name: String, targetSize: Int): Bitmap? = try {
        val file = File(context.filesDir, name)
        if (!file.exists()) {
            null
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSize) sample *= 2
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }
    } catch (e: Exception) {
        Log.d("LuoluoReminder", "解码图片失败：${e.message}")
        null
    }
}
