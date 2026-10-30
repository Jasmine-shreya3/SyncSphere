package com.voxbridge.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ListenerRegistration
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SpherePalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val incoming: Color,
    val outgoing: Color
)

private fun palette(dark: Boolean) = if (dark) {
    SpherePalette(
        background = Color(0xFF0D111B),
        surface = Color(0xFF192131),
        text = Color(0xFFF7F8FF),
        muted = Color(0xFF9DAAC1),
        primary = Color(0xFFB6A1FF),
        incoming = Color(0xFF242F43),
        outgoing = Color(0xFF3A3465)
    )
} else {
    SpherePalette(
        background = Color(0xFFF7F8FD),
        surface = Color(0xFFFFFFFF),
        text = Color(0xFF1C2540),
        muted = Color(0xFF71809A),
        primary = Color(0xFF7056D8),
        incoming = Color(0xFFEDF2FA),
        outgoing = Color(0xFFEDE6FF)
    )
}

data class ChatLanguage(
    val label: String,
    val code: String
)

private val languages = listOf(
    ChatLanguage("English", "en"),
    ChatLanguage("தமிழ் (Tamil)", "ta"),
    ChatLanguage("हिन्दी (Hindi)", "hi")
)

fun languageLabel(code: String): String =
    languages.firstOrNull { it.code == code }?.label ?: "English"

fun mlKitLanguage(code: String): String? = when (code) {
    "en" -> TranslateLanguage.ENGLISH
    "ta" -> TranslateLanguage.TAMIL
    "hi" -> TranslateLanguage.HINDI
    else -> null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var darkMode by remember { mutableStateOf(false) }
            var signedIn by remember {
                mutableStateOf(FirebaseChatRepository.currentUser() != null)
            }

            val p = palette(darkMode)

            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = p.background
                ) {
                    if (signedIn &&
                        FirebaseChatRepository.currentUser() != null
                    ) {
                        ChatScreen(
                            p = p,
                            darkMode = darkMode,
                            onDarkModeChange = { darkMode = it },
                            onSignOut = {
                                FirebaseChatRepository.signOut()
                                signedIn = false
                            }
                        )
                    } else {
                        LoginScreen(
                            p = p,
                            onAuthenticated = { signedIn = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    p: SpherePalette,
    onAuthenticated: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "◈ SyncSphere",
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            color = p.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "ONE WORLD  •  EVERY VOICE",
            color = p.muted,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = p.surface),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (isSignUp) "Create your account" else "Welcome back",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.text
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    )
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (6+ characters)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    )
                )

                if (message.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = p.muted)
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (email.trim().isBlank() || password.length < 6) {
                            message =
                                "Enter a valid email and password of at least 6 characters."
                        } else {
                            loading = true
                            message = ""

                            val callback: (Boolean, String) -> Unit =
                                { success, result ->
                                    loading = false
                                    message = result
                                    if (success) onAuthenticated()
                                }

                            if (isSignUp) {
                                FirebaseChatRepository.signUp(
                                    email.trim(),
                                    password,
                                    callback
                                )
                            } else {
                                FirebaseChatRepository.signIn(
                                    email.trim(),
                                    password,
                                    callback
                                )
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = p.primary
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (isSignUp) "Create account" else "Sign in",
                            color = Color.White
                        )
                    }
                }

                TextButton(
                    onClick = {
                        isSignUp = !isSignUp
                        message = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isSignUp) {
                            "Already registered? Sign in"
                        } else {
                            "New here? Create an account"
                        },
                        color = p.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(
    p: SpherePalette,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val user = FirebaseChatRepository.currentUser()

    var myLanguage by remember { mutableStateOf("en") }
    var speechTargetLanguage by remember { mutableStateOf("ta") }
    var autoRead by remember { mutableStateOf(true) }
    var outputMode by remember { mutableStateOf("Both") }
    var draft by remember { mutableStateOf("") }
    var chatMessages by remember {
        mutableStateOf(emptyList<RemoteChatMessage>())
    }
    var status by remember { mutableStateOf("Connecting to chat…") }
    var languageMenu by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RemoteChatMessage?>(null) }
    val listState = rememberLazyListState()

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign out of SyncSphere?") },
            text = { Text("You can sign in again to continue chatting.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onSignOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Cancel") }
            }
        )
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) listState.animateScrollToItem(chatMessages.lastIndex)
    }

    DisposableEffect(Unit) {
        val registration: ListenerRegistration =
            FirebaseChatRepository.listenMessages(
                onMessages = { messages ->
                    chatMessages = messages
                    status = "Chat synced · ${messages.size} messages"
                },
                onError = { error ->
                    status = error
                }
            )

        onDispose {
            registration.remove()
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete for everyone?") },
            text = { Text("This message will disappear from all connected phones.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = pendingDelete?.id.orEmpty()
                    pendingDelete = null
                    FirebaseChatRepository.deleteMessage(id) { ok, result ->
                        status = if (ok) "Deleted for everyone" else result
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = p.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).background(
                        Brush.linearGradient(listOf(p.primary, Color(0xFF53D4D0))),
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) { Text("◈", color = Color.White, fontSize = 25.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("SyncSphere", color = p.text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(status, color = p.muted, fontSize = 11.sp, maxLines = 1)
                }
                TextButton(onClick = { showOptions = !showOptions }) {
                    Text(if (showOptions) "Close" else "Settings", color = p.primary, fontSize = 12.sp)
                }
            }
        }

        // Keep controls compact. Tap Options to change language, output or theme.
        if (showOptions) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = p.surface)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Your language", color = p.text, modifier = Modifier.weight(1f))
                        Box {
                            OutlinedButton(onClick = { languageMenu = true }) {
                                Text(languageLabel(myLanguage) + " ▾", color = p.text)
                            }
                            DropdownMenu(
                                expanded = languageMenu,
                                onDismissRequest = { languageMenu = false }
                            ) {
                                languages.forEach { language ->
                                    DropdownMenuItem(
                                        text = { Text(language.label) },
                                        onClick = {
                                            myLanguage = language.code
                                            languageMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text("Receive as", color = p.text, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Text", "Speech", "Both").forEach { mode ->
                            OutlinedButton(
                                onClick = {
                                    outputMode = mode
                                    autoRead = mode != "Text"
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                            ) { Text(if (outputMode == mode) "✓ $mode" else mode, fontSize = 12.sp) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark mode", color = p.text, modifier = Modifier.weight(1f))
                        Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
                        TextButton(onClick = { confirmLogout = true }) { Text("Sign out") }
                    }
                }
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✦  Global conversation", color = p.text, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(languageLabel(myLanguage), color = p.primary, fontSize = 12.sp)
        }
        Spacer(Modifier.height(5.dp))
        Spacer(Modifier.height(5.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp, horizontal = 3.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.senderId == user?.uid,
                    targetLanguage = myLanguage,
                    autoRead = autoRead,
                    p = p,
                    onDelete = { pendingDelete = message }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = p.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Write a message…") },
                    minLines = 1,
                    maxLines = 3,
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Button(
                    enabled = draft.isNotBlank() && !sending,
                    onClick = {
                        val textToSend = draft.trim()
                        sending = true
                        FirebaseChatRepository.sendMessage(textToSend, myLanguage) { success, result ->
                            sending = false
                            if (success) {
                                draft = ""
                                status = "Message sent"
                            } else status = result
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = p.primary),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp),
                    modifier = Modifier.height(54.dp)
                ) { Text(if (sending) "…" else "➤", color = Color.White, fontSize = 23.sp) }
            }
            // Real microphone and live controls stay mounted beside the composer.
            VoiceTranslationControls(
                p = p,
                sourceLanguage = myLanguage,
                targetLanguage = speechTargetLanguage,
                onTargetLanguageChange = { speechTargetLanguage = it },
                compact = true
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: RemoteChatMessage,
    isMine: Boolean,
    targetLanguage: String,
    autoRead: Boolean,
    p: SpherePalette,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .background(
                    color = if (isMine) p.outgoing else p.incoming,
                    shape = RoundedCornerShape(
                        topStart = 21.dp,
                        topEnd = 21.dp,
                        bottomStart = if (isMine) 21.dp else 6.dp,
                        bottomEnd = if (isMine) 6.dp else 21.dp
                    )
                )
                .combinedClickable(onClick = {}, onLongClick = { if (isMine) onDelete() })
                .padding(horizontal = 15.dp, vertical = 12.dp)
        ) {
            Text(
                if (isMine) "You"
                else message.senderEmail.ifBlank { "Other user" },
                color = p.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(5.dp))
            // On the receiving phone, show the translation instead of
            // presenting the original speech transcript as the main message.
            if (!isMine && message.senderLanguage != targetLanguage) {
                TranslatedText(message, targetLanguage, autoRead, p)
            } else {
                if (message.messageType == "voice") {
                    Text("✦ VOICE MESSAGE", color = p.primary, fontSize = 11.sp)
                }
                Text(message.text, color = p.text, fontSize = 16.sp, lineHeight = 23.sp)
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = if (message.createdAt > 0L) {
                    SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(Date(message.createdAt))
                } else {
                    ""
                },
                color = p.muted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun TranslatedText(
    message: RemoteChatMessage,
    targetLanguage: String,
    autoRead: Boolean,
    p: SpherePalette
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var translated by remember(message.id, targetLanguage) {
        mutableStateOf("")
    }
    var translationStatus by remember(message.id, targetLanguage) {
        mutableStateOf("Translating…")
    }

    LaunchedEffect(
        message.id,
        message.text,
        message.senderLanguage,
        targetLanguage
    ) {
        translated = ""
        translationStatus = "Translating…"

        val source = mlKitLanguage(message.senderLanguage)
        val target = mlKitLanguage(targetLanguage)

        if (source == null || target == null) {
            translationStatus = "Translation language not supported"
            return@LaunchedEffect
        }

        if (source == target) {
            translated = message.text
            translationStatus = ""
            return@LaunchedEffect
        }

        var translator: Translator? = null

        try {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()

            translator = Translation.getClient(options)

            translator.downloadModelIfNeeded(
                DownloadConditions.Builder().build()
            ).await()

            translated = translator.translate(message.text).await()
            translationStatus = ""
        } catch (_: Exception) {
            translationStatus =
                "Translation unavailable. Check internet and try again."
        } finally {
            translator?.close()
        }
    }

    DisposableEffect(message.id, targetLanguage, autoRead, translated) {
        var engine: android.speech.tts.TextToSpeech? = null
        if (autoRead && translated.isNotBlank()) {
            engine = android.speech.tts.TextToSpeech(context) { code ->
                if (code == android.speech.tts.TextToSpeech.SUCCESS) {
                    val locale = when (targetLanguage) {
                        "ta" -> Locale.forLanguageTag("ta-IN")
                        "hi" -> Locale.forLanguageTag("hi-IN")
                        else -> Locale.US
                    }
                    engine?.language = locale
                    engine?.speak(translated, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, message.id)
                }
            }
        }
        onDispose { engine?.stop(); engine?.shutdown() }
    }

    Column {
        Text(
            "✦ TRANSLATED · ${languageLabel(targetLanguage)}",
            color = p.primary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(3.dp))

        Text(
            translated.ifBlank { translationStatus },
            color = p.text,
            fontSize = 15.sp
        )
    }
}

