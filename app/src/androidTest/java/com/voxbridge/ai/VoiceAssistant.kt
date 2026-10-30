package com.voxbridge.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun VoiceTranslationControls(
    p: SpherePalette,
    sourceLanguage: String,
    targetLanguage: String,
    onTargetLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val handler = remember { Handler(Looper.getMainLooper()) }
    var live by remember { mutableStateOf(false) }
    var recordOnce by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var permissionForLive by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Mic ON for automatic translated conversation.") }
    val recognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context))
            SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    var restart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRestart by remember { mutableStateOf<Runnable?>(null) }

    fun scheduleRestart() {
        pendingRestart?.let { handler.removeCallbacks(it) }
        if (!live || recordOnce) return
        val task = Runnable { if (live && !listening) restart?.invoke() }
        pendingRestart = task
        handler.postDelayed(task, 650L)
    }

    fun startListening() {
        if (recognizer == null) { status = "Speech recognition unavailable"; live = false; return }
        if (listening) return
        val locale = when (sourceLanguage) {
            "ta" -> "ta-IN"
            "hi" -> "hi-IN"
            else -> "en-US"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            listening = true
            status = "Listening in ${languageLabel(sourceLanguage)}..."
            recognizer.startListening(intent)
        } catch (e: Exception) {
            listening = false
            status = e.localizedMessage ?: "Could not start microphone"
            scheduleRestart()
        }
    }
    restart = { startListening() }

    DisposableEffect(recognizer) {
        if (recognizer != null) recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status = "Processing phrase..." }
            override fun onError(error: Int) {
                listening = false
                if (live && !recordOnce && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
                    error != SpeechRecognizer.ERROR_CLIENT) {
                    status = "Reconnecting microphone..."
                    scheduleRestart()
                } else {
                    if (recordOnce) { recordOnce = false; live = false }
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) live = false
                    status = "Speech error $error"
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (spoken.isBlank()) {
                    if (recordOnce) { recordOnce = false; live = false; status = "No speech detected" }
                    else scheduleRestart()
                    return
                }
                if (recordOnce) { recordOnce = false; live = false }
                sending = true
                status = "Phrase captured · listening again..."
                if (live) scheduleRestart()
                FirebaseChatRepository.sendMessage(
                    text = spoken, senderLanguage = sourceLanguage, messageType = "voice"
                ) { ok, msg ->
                    sending = false
                    status = if (ok) "Phrase sent · ${languageLabel(sourceLanguage)}" else "Send failed: $msg"
                    // Recognition restarts immediately; network send may finish later.
                }
                if (live) scheduleRestart()
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            live = false
            pendingRestart?.let { handler.removeCallbacks(it) }
            recognizer?.cancel()
            recognizer?.destroy()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recordOnce = !permissionForLive
            live = permissionForLive
            startListening()
        }
        else status = "Microphone permission denied"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = p.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("VOICE STUDIO", color = p.text, fontSize = 17.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (live) "● LIVE" else if (recordOnce || listening) "● RECORDING" else "● READY",
                    color = if (live || recordOnce) Color(0xFFEF4444) else Color(0xFF16A085),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("Speaking: ${languageLabel(sourceLanguage)}", color = p.primary,
                fontWeight = FontWeight.SemiBold)
            Text("Choose how to speak. Other phones receive translated messages in their own selected language.",
                color = p.muted, fontSize = 12.sp)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !live && !recordOnce && !listening,
                onClick = {
                    recordOnce = true
                    permissionForLive = false
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED) startListening()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            ) {
                Text(if (recordOnce) "● Recording one message…" else "🎤  Record Once & Send",
                    color = p.text)
            }
            if (live) {
                Button(
                    onClick = {
                        live = false
                        recordOnce = false
                        pendingRestart?.let { handler.removeCallbacks(it) }
                        recognizer?.cancel()
                        listening = false
                        status = "Live session stopped"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC3545))
                ) { Text("■  End Live Session", color = Color.White) }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !recordOnce && !listening,
                    colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                    onClick = {
                        permissionForLive = true
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                            recordOnce = false
                            live = true
                            startListening()
                        } else permission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) { Text("🔴  Start Continuous Live Session", color = Color.White) }
            }
            Text(if (sending) "Sending voice message…" else status,
                color = p.muted, fontSize = 12.sp)
        }
    }
}
