package agape.app.data.audio

import agape.app.R
import agape.app.core.model.AppLanguage
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * One text-to-speech engine for the whole app. It reads a list of parts (the verses of a chapter, the paragraphs of a
 * prayer) one after another, so the screen can follow along. [State.key] tells which screen's text is being read.
 */
class Speaker(context: Context) {
    data class State(val key: String? = null, val index: Int = 0, val playing: Boolean = false)

    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false
    private var parts: List<String> = emptyList()
    private var language = AppLanguage.ENGLISH

    /** Starts reading [parts] from [from]; the engine starts on first use, which takes a moment. */
    fun play(key: String, parts: List<String>, language: AppLanguage, from: Int = 0) {
        val clean = parts.map { it.trim() }
        if (clean.none { it.isNotEmpty() }) return
        this.parts = clean
        this.language = language
        _state.value = State(key, from.coerceIn(0, clean.lastIndex), playing = true)
        when {
            tts == null -> tts = TextToSpeech(context) { status -> main.post { onInit(status) } }
            ready -> speakCurrent()
        }
    }

    /** Play when nothing of [key] is playing, pause when it is. */
    fun toggle(key: String, parts: () -> List<String>, language: AppLanguage) {
        val s = _state.value
        when {
            s.key == key && s.playing -> pause()
            s.key == key && language == this.language -> play(key, this.parts, language, s.index)
            else -> play(key, parts(), language)
        }
    }

    fun pause() {
        _state.value = _state.value.copy(playing = false)
        tts?.stop()
    }

    fun skip(by: Int) {
        val s = _state.value
        if (s.key == null || parts.isEmpty()) return
        val next = (s.index + by).coerceIn(0, parts.lastIndex)
        _state.value = s.copy(index = next)
        if (s.playing && ready) speakCurrent()
    }

    fun stop() {
        _state.value = State()
        tts?.stop()
    }

    /** Stops reading, but only when [key] is what is being read (a screen closing must not stop another one). */
    fun stopIf(key: String) {
        if (_state.value.key == key) stop()
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            tts = null
            stop()
            toast(R.string.speaker_unavailable)
            return
        }
        ready = true
        engine.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                main.post { onPartDone(utteranceId) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { onPartDone(utteranceId) }
            }
        })
        if (_state.value.playing) speakCurrent()
    }

    /** Utterance ids are "index:last"; only the last piece of the current part moves on to the next part. */
    private fun onPartDone(utteranceId: String?) {
        val (index, last) = utteranceId?.split(':')?.let { it.getOrNull(0)?.toIntOrNull() to (it.getOrNull(1) == "last") } ?: return
        val s = _state.value
        if (!last || !s.playing || index != s.index) return
        if (s.index < parts.lastIndex) {
            _state.value = s.copy(index = s.index + 1)
            speakCurrent()
        } else {
            stop()
        }
    }

    private fun speakCurrent() {
        val engine = tts ?: return
        val index = _state.value.index
        val locale = if (language == AppLanguage.DUTCH) Locale("nl", "NL") else Locale.US
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            stop()
            toast(if (language == AppLanguage.DUTCH) R.string.speaker_no_voice_nl else R.string.speaker_no_voice_en)
            return
        }
        engine.setSpeechRate(0.95f)
        val pieces = split(parts[index], TextToSpeech.getMaxSpeechInputLength())
        if (pieces.isEmpty()) {
            onPartDone("$index:last")
            return
        }
        pieces.forEachIndexed { i, piece ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(piece, mode, null, "$index:${if (i == pieces.lastIndex) "last" else i}")
        }
    }

    private fun toast(message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    companion object {
        /** Splits [text] at sentence ends into pieces the engine accepts (it refuses input longer than [max]). */
        fun split(text: String, max: Int): List<String> {
            if (text.isBlank()) return emptyList()
            if (text.length <= max) return listOf(text)
            val out = mutableListOf<String>()
            val current = StringBuilder()
            for (sentence in text.split(Regex("(?<=[.!?;])\\s+"))) {
                if (current.isNotEmpty() && current.length + sentence.length + 1 > max) {
                    out += current.toString()
                    current.clear()
                }
                if (sentence.length > max) sentence.chunked(max).forEach { out += it }
                else current.append(if (current.isEmpty()) "" else " ").append(sentence)
            }
            if (current.isNotEmpty()) out += current.toString()
            return out
        }
    }
}
