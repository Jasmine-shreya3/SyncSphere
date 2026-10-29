
package com.voxbridge.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.launch

@Composable
fun LiveCallScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("syncsphere-room") }
    var status by remember { mutableStateOf("Not connected") }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var room by remember { mutableStateOf<Room?>(null) }
    var micEnabled by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            room?.let { activeRoom ->
                scope.launch {
                    activeRoom.disconnect()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("SyncSphere Live Call",
            style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("LiveKit WebSocket URL") },
            placeholder = { Text("wss://your-project.livekit.cloud") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = roomName,
            onValueChange = { roomName = it },
            label = { Text("Room name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Participant access token") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )

        Text(status)

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !connecting && !connected &&
                    serverUrl.startsWith("wss://") &&
                    token.isNotBlank() &&
                    roomName.isNotBlank(),
            onClick = {
                connecting = true
                status = "Connecting…"

                scope.launch {
                    var newRoom: Room? = null
                    try {
                        newRoom = LiveKit.create(context.applicationContext)
                        newRoom.connect(serverUrl.trim(), token.trim())
                        newRoom.localParticipant
                            .setMicrophoneEnabled(true)

                        room = newRoom
                        connected = true
                        micEnabled = true
                        status = "Connected to $roomName"
                    } catch (e: Exception) {
                        newRoom?.disconnect()
                        status = "Connection failed: ${
                            e.localizedMessage ?: "Check URL and token"
                        }"
                    } finally {
                        connecting = false
                    }
                }
            }
        ) {
            Text(if (connecting) "Connecting…" else "Join call")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = connected,
            onClick = {
                scope.launch {
                    try {
                        room?.localParticipant
                            ?.setMicrophoneEnabled(!micEnabled)
                        micEnabled = !micEnabled
                        status = if (micEnabled) {
                            "Microphone on"
                        } else {
                            "Microphone muted"
                        }
                    } catch (e: Exception) {
                        status = "Could not change microphone"
                    }
                }
            }
        ) {
            Text(if (micEnabled) "Mute microphone" else "Unmute microphone")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = connected,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            onClick = {
                scope.launch {
                    try {
                        room?.disconnect()
                        room = null
                        connected = false
                        status = "Call ended"
                    } catch (e: Exception) {
                        status = "Error ending call"
                    }
                }
            }
        ) {
            Text("End call")
        }
    }
}
