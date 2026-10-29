package com.voxbridge.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

@Composable
fun VoiceTranslationControls(
    p: SpherePalette,
    sourceLanguage: String,
    targetLanguage: String,
    onTargetLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Speak to send a voice transcript to OTHER phones.") }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) { onDispose { recognizer?.destroy() } }

    fun begin() {
        if (recognizer == null) {
            status = "Speech recognition unavailable. Install Google speech services."
            return
        }
        if (listening || sending) return
        partial = ""
        val locale = when(sourceLanguage) {
            "ta" -> "ta-IN"
            "hi" -> "hi-IN"
            else -> "en-US"
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                status = "Listening in ${languageLabel(sourceLanguage)}..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status = "Processing speech..." }
            override fun onError(error: Int) {
                listening = false
                status = "Speech error $error. Try again."
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (spoken.isBlank()) {
                    status = "No speech detected. Try again."
                    return
                }
                partial = spoken
                sending = true
                status = "Sending to other phones..."
                FirebaseChatRepository.sendMessage(
                    text = spoken,
                    senderLanguage = sourceLanguage,
                    messageType = "voice"
                ) { ok, msg ->
                    sending = false
                    status = if (ok) "Sent! Other phones translate into their selected language."
                    else "Send failed: $msg"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            listening = true
            recognizer.startListening(intent)
        } catch (e: Exception) {
            listening = false
            status = e.localizedMessage ?: "Could not start microphone."
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) begin() else status = "Microphone permission denied."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = p.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("🎤 Send speech to other phones", color = p.text)
            Text("Your language: ${languageLabel(sourceLanguage)}", color = p.muted)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !listening && !sending,
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) begin()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            ) { Text(if (sending) "Sending..." else "🎙 Speak & Send") }
            if (listening) OutlinedButton(onClick = {
                recognizer?.stopListening()
                status = "Finishing..."
            }) { Text("Stop speaking") }
            if (partial.isNotBlank()) Text("Recognized: $partial", color = p.text)
            Text(status, color = p.muted)
        }
    }
}
