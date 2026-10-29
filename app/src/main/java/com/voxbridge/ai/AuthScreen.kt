package com.voxbridge.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

private val AuthBackground = Color(0xFFF7F4FF)
private val AuthPurple = Color(0xFF7555D9)
private val AuthText = Color(0xFF24213A)
private val AuthMuted = Color(0xFF77718D)

@Composable
fun AuthScreen(onSignedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = AuthBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AuthBackground)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "✦ SyncSphere",
                        color = AuthPurple,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Chat across languages. Stay connected.",
                        color = AuthMuted,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(28.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(22.dp)) {
                            Text(
                                text = if (createAccount) "Create your account" else "Welcome back",
                                color = AuthText,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(18.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it; problem = "" },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Email address") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; problem = "" },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Password (6+ characters)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )

                            if (problem.isNotBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(text = problem, color = Color(0xFFB3261E), fontSize = 13.sp)
                            }

                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    val cleanEmail = email.trim()
                                    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                        problem = "Please enter a valid email address."
                                    } else if (password.length < 6) {
                                        problem = "Password must be at least 6 characters."
                                    } else {
                                        busy = true
                                        problem = ""
                                        val callback: (Boolean, String) -> Unit = { success, result ->
                                            busy = false
                                            problem = if (success) "" else result
                                            if (success) onSignedIn()
                                        }
                                        if (createAccount) {
                                            FirebaseChatRepository.signUp(cleanEmail, password, callback)
                                        } else {
                                            FirebaseChatRepository.signIn(cleanEmail, password, callback)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AuthPurple)
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (createAccount) "Create account" else "Sign in",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    createAccount = !createAccount
                                    problem = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !busy
                            ) {
                                Text(
                                    text = if (createAccount) "Already registered? Sign in" else "New here? Create an account",
                                    color = AuthPurple
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
