package com.roger.circuitcaller.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * Wraps TextToSpeech so callers never have to care that init is asynchronous.
 *
 * Anything spoken before the engine is ready is queued and flushed on init — without this
 * the very first callout of the very first workout silently disappears, which is exactly
 * the callout a user is most likely to be waiting for.
 *
 * Audio focus is requested as TRANSIENT_MAY_DUCK so music ducks for the callout and comes
 * back, instead of stopping.
 */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var toneGenerator: ToneGenerator? = null

    var rate: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var voiceName: String = ""
        set(value) {
            field = value
            applyVoice()
        }

    fun init(onReady: (() -> Unit)? = null) {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault().takeIf { it.language == "en" } ?: Locale.US
                tts?.setSpeechRate(rate)
                applyVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) = abandonFocus()

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = abandonFocus()
                })
                synchronized(pending) {
                    pending.forEach { speakNow(it, flush = false) }
                    pending.clear()
                }
                onReady?.invoke()
            }
        }
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 70)
        }.getOrNull()
    }

    fun availableVoices(): List<Voice> = tts?.voices
        ?.filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
        ?.sortedBy { it.name }
        ?: emptyList()

    private fun applyVoice() {
        val engine = tts ?: return
        if (voiceName.isBlank()) return
        engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
    }

    /** Speak immediately, cancelling anything mid-utterance. */
    fun say(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            synchronized(pending) { pending += text }
            return
        }
        speakNow(text, flush = true)
    }

    private fun speakNow(text: String, flush: Boolean) {
        requestFocus()
        tts?.speak(
            text,
            if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            null,
            "cc-${System.nanoTime()}"
        )
    }

    fun stopSpeaking() {
        tts?.stop()
        abandonFocus()
    }

    fun beep(highPitch: Boolean) {
        val tone = if (highPitch) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK
        runCatching { toneGenerator?.startTone(tone, 120) }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest != null) return
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    fun release() {
        abandonFocus()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        toneGenerator?.release()
        toneGenerator = null
    }
}
