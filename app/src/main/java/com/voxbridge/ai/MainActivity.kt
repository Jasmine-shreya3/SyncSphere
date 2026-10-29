
package com.voxbridge.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

private data class SpherePalette(
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
        background = Color(0xFF10111C),
        surface = Color(0xFF1B1D2D),
        text = Color(0xFFF5F2FF),
        muted = Color(0xFFB8B5CC),
        primary = Color(0xFFB9A3FF),
        incoming = Color(0xFF292B40),
        outgoing = Color(0xFF55418B)
    )
} else {
    SpherePalette(
        background = Color(0xFFF7F4FF),
        surface = Color.White,
        text = Color(0xFF24213A),
        muted = Color(0xFF77718D),
        primary = Color(0xFF7555D9),
        incoming = Color(0xFFECE7FA),
        outgoing = Color(0xFFDCD0FF)
    )
}

private data class ChatLanguage(
    val label: String,
    val code: String
)

private val languages = listOf(
    ChatLanguage("English", "en"),
    ChatLanguage("தமிழ் (Tamil)", "ta"),
    ChatLanguage("हिन्दी (Hindi)", "hi")
)

private fun languageLabel(code: String): String =
    languages.firstOrNull { it.code == code }?.label ?: "English"

private fun mlKitLanguage(code: String): String? = when (code) {
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
                    if (signedIn && FirebaseChatRepository.currentUser() != null) {
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
            "✦ SyncSphere",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = p.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Chat across languages. Stay connected.",
            color = p.muted,
            fontSize = 16.sp
        )
        Spacer(Modifier.height(28.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = p.surface),
            shape = RoundedCornerShape(24.dp)
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
                        if (email.isBlank() || password.length < 6) {
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
                                    email,
                                    password,
                                    callback
                                )
                            } else {
                                FirebaseChatRepository.signIn(
                                    email,
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
    var draft by remember { mutableStateOf("") }
    var draftLanguage by remember { mutableStateOf("en") }
    var chatMessages by remember {
        mutableStateOf(emptyList<RemoteChatMessage>())
    }
    var status by remember { mutableStateOf("Connecting to chat…") }
    var languageMenu by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val registration: ListenerRegistration =
            FirebaseChatRepository.listenMessages(
                onMessages = { messages ->
                    chatMessages = messages
                    status = "Live chat connected"
                },
                onError = { error ->
                    status = error
                }
            )

        onDispose {
            registration.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(p.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✦", color = Color.White, fontSize = 25.sp)
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    "SyncSphere",
                    color = p.text,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(status, color = p.muted, fontSize = 12.sp)
            }

            Text("Dark", color = p.text, fontSize = 12.sp)
            Switch(
                checked = darkMode,
                onCheckedChange = onDarkModeChange
            )
        }

        Spacer(Modifier.height(14.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = p.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Your writing language",
                    color = p.muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))

                Box {
                    OutlinedButton(
                        onClick = { languageMenu = true }
                    ) {
                        Text(languageLabel(myLanguage), color = p.text)
                        Text("  ▾", color = p.primary)
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

                Spacer(Modifier.height(4.dp))
                Text(
                    "Your messages are labelled with this language. " +
                            "Incoming messages translate to your language.",
                    color = p.muted,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Community chat",
                    color = p.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Signed in as ${user?.email ?: "user"}",
                    color = p.muted,
                    fontSize = 11.sp
                )
            }

            TextButton(onClick = onSignOut) {
                Text("Sign out", color = p.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.senderId == user?.uid,
                    targetLanguage = myLanguage,
                    p = p
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = p.surface)
        ) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write a message…") },
                    maxLines = 4
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        enabled = draft.isNotBlank() && !sending,
                        onClick = {
                            val textToSend = draft.trim()
                            sending = true

                            FirebaseChatRepository.sendMessage(
                                textToSend,
                                myLanguage
                            ) { success, result ->
                                sending = false

                                if (success) {
                                    draft = ""
                                    status = "Message sent"
                                } else {
                                    status = result
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = p.primary
                        )
                    ) {
                        Text(
                            if (sending) "Sending…" else "Send  ➤",
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MessageBubble(
    message: RemoteChatMessage,
    isMine: Boolean,
    targetLanguage: String,
    p: SpherePalette
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
                .fillMaxWidth(0.88f)
                .background(
                    color = if (isMine) p.outgoing else p.incoming,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp
                    )
                )
                .padding(14.dp)
        ) {
            Text(
                if (isMine) "You"
                else message.senderEmail.ifBlank { "Other user" },
                color = p.muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(5.dp))
            Text(message.text, color = p.text, fontSize = 16.sp)

            if (!isMine && message.senderLanguage != targetLanguage) {
                Spacer(Modifier.height(8.dp))
                TranslatedText(message, targetLanguage, p)
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
    p: SpherePalette
) {
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
        } catch (e: Exception) {
            translationStatus =
                "Translation unavailable. Check internet and try again."
        } finally {
            translator?.close()
        }
    }

    Column {
        Text(
            "TRANSLATION · ${languageLabel(targetLanguage)}",
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
