
package com.voxbridge.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

@Composable
fun VoiceAssistant(
    sourceLanguage: String,
    targetLanguage: String,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current

    var status by remember { mutableStateOf("Tap to speak") }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        status = if (granted) {
            "Permission granted. Tap to speak"
        } else {
            "Microphone permission denied"
        }
    }

    val tts = remember {
        TextToSpeech(context) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Button(
        onClick = {
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return@Button
            }

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                status = "Speech recognition unavailable"
                return@Button
            }

            val source = languageCode(sourceLanguage)
            val target = languageCode(targetLanguage)

            if (source == null || target == null) {
                status = "Unsupported language"
                return@Button
            }

            val recognizer =
                SpeechRecognizer.createSpeechRecognizer(context)

            recognizer.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        status = "Listening..."
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        status = "Processing speech..."
                    }

                    override fun onError(error: Int) {
                        status = "Speech error: $error. Please try again."
                        recognizer.destroy()
                    }

                    override fun onResults(results: Bundle?) {
                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.trim()

                        if (spoken.isNullOrBlank()) {
                            status = "No speech detected"
                            recognizer.destroy()
                            return
                        }

                        // Display the recognized speech locally.
                        onResult(spoken)

                        // Send the original voice transcript to shared Firebase chat.
                        status = "Sending voice message..."

                        FirebaseChatRepository.sendMessage(
                            text = spoken,
                            senderLanguage = source
                        ) { success, message ->
                            status = if (success) {
                                "Voice message sent. Translating..."
                            } else {
                                "Message not sent: $message"
                            }
                        }

                        val options = TranslatorOptions.Builder()
                            .setSourceLanguage(source)
                            .setTargetLanguage(target)
                            .build()

                        val translator = Translation.getClient(options)

                        translator.downloadModelIfNeeded()
                            .addOnSuccessListener {
                                translator.translate(spoken)
                                    .addOnSuccessListener { translated ->
                                        // Return the translation to the caller.
                                        // Do not send it as a second Firebase message.
                                        onResult(translated)

                                        status = "Translation complete. Speaking..."

                                        val ttsLanguage =
                                            Locale.forLanguageTag(
                                                speechLocaleTag(targetLanguage)
                                            )

                                        val result = tts.setLanguage(ttsLanguage)

                                        if (
                                            result != TextToSpeech.LANG_MISSING_DATA &&
                                            result != TextToSpeech.LANG_NOT_SUPPORTED
                                        ) {
                                            tts.speak(
                                                translated,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "voxbridge_translation"
                                            )
                                        } else {
                                            status =
                                                "Translated; speech language unavailable"
                                        }

                                        translator.close()
                                        recognizer.destroy()
                                    }
                                    .addOnFailureListener {
                                        status = "Translation failed"
                                        translator.close()
                                        recognizer.destroy()
                                    }
                            }
                            .addOnFailureListener {
                                status =
                                    "Could not download translation model. Check internet."
                                translator.close()
                                recognizer.destroy()
                            }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    speechLocaleTag(sourceLanguage)
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
            }

            recognizer.startListening(intent)
        }
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Microphone"
        )
        Text(" Speak and Translate")
    }

    Text(status)

    Button(
        onClick = {
            status =
                "LiveKit calling is not connected yet. Voice translation works separately."
        }
    ) {
        Text("Prepare LiveKit Call")
    }
}

private fun languageCode(language: String): String? {
    return when (language.trim().lowercase()) {
        "en", "en-us", "english" ->
            TranslateLanguage.ENGLISH

        "ta", "ta-in", "tamil" ->
            TranslateLanguage.TAMIL

        "hi", "hi-in", "hindi" ->
            TranslateLanguage.HINDI

        else ->
            TranslateLanguage.fromLanguageTag(language)
    }
}

private fun speechLocaleTag(language: String): String {
    return when (language.trim().lowercase()) {
        "en", "english" -> "en-US"
        "ta", "tamil" -> "ta-IN"
        "hi", "hindi" -> "hi-IN"
        else -> language
    }
}
