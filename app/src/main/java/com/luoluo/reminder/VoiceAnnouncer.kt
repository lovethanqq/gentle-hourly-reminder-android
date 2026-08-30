package com.luoluo.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 戴耳机时的非打断式语音播报（本地 TTS，不联网、无服务、说完即停）：
 * - 仅在检测到耳机（有线/蓝牙/USB）时播报，外放场景保持静默
 * - 申请“瞬时音频焦点 + 允许压低（MAY_DUCK）”，使用导航播报用途：
 *   正在播放的音乐只会被短暂压低音量，不会被暂停/打断，播完自动恢复
 */
object VoiceAnnouncer {

    private const val TAG = "LuoluoReminder"

    private val HEADSET_TYPES = intArrayOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    )

    /** 当前是否佩戴耳机（任一输出设备命中即算） */
    fun headsetConnected(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (t in HEADSET_TYPES) {
            if (outputs.any { it.type == t }) return true
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            outputs.any { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
        ) {
            return true
        }
        return false
    }

    /**
     * 异步播报，结束（完成/失败/无引擎）时回调 onDone。
     * 播报器用完即 shutdown，不常驻任何资源。
     */
    fun announceAsync(context: Context, text: String, onDone: () -> Unit) {
        val called = AtomicBoolean(false)
        fun finishOnce() {
            if (called.compareAndSet(false, true)) onDone()
        }
        try {
            val appContext = context.applicationContext
            var tts: TextToSpeech? = null
            tts = TextToSpeech(appContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.d(TAG, "TTS 初始化失败 status=$status，跳过播报")
                    try {
                        tts?.shutdown()
                    } catch (_: Exception) {
                    }
                    finishOnce()
                    return@TextToSpeech
                }
                val am = appContext.getSystemService(AudioManager::class.java)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val focusRequest = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                )
                    .setAudioAttributes(attrs)
                    .build()
                val focusGranted = am?.requestAudioFocus(focusRequest) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED

                fun cleanup() {
                    try {
                        am?.abandonAudioFocusRequest(focusRequest)
                    } catch (_: Exception) {
                    }
                    try {
                        tts?.shutdown()
                    } catch (_: Exception) {
                    }
                    Log.d(TAG, "语音播报结束，音频焦点已释放")
                    finishOnce()
                }

                try {
                    tts?.setAudioAttributes(attrs)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            cleanup()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            cleanup()
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            cleanup()
                        }
                    })
                    val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "luoluo_tts")
                    Log.d(TAG, "语音播报开始（mayDuck=$focusGranted）：$text")
                    if (result != TextToSpeech.SUCCESS) {
                        Log.d(TAG, "TTS speak 入队失败 result=$result")
                        cleanup()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "TTS 播报异常（${e.message}）")
                    cleanup()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "TTS 不可用（${e.message}），跳过播报")
            finishOnce()
        }
    }
}
