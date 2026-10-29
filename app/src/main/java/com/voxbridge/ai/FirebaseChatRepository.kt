package com.voxbridge.ai

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

data class RemoteChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderEmail: String = "",
    val text: String = "",
    val senderLanguage: String = "en",
    val messageType: String = "text",
    val createdAt: Long = 0L
)

object FirebaseChatRepository {
    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    private val messages
        get() = FirebaseFirestore.getInstance()
            .collection("publicRooms")
            .document("global")
            .collection("messages")

    fun currentUser(): FirebaseUser? = auth.currentUser

    fun signOut() {
        auth.signOut()
    }

    fun signIn(
        email: String,
        password: String,
        callback: (Boolean, String) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                callback(
                    task.isSuccessful,
                    if (task.isSuccessful) "Signed in successfully"
                    else task.exception?.localizedMessage ?: "Sign in failed"
                )
            }
    }

    fun signUp(
        email: String,
        password: String,
        callback: (Boolean, String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                callback(
                    task.isSuccessful,
                    if (task.isSuccessful) "Account created successfully"
                    else task.exception?.localizedMessage ?: "Sign up failed"
                )
            }
    }

    // Supports: sendMessage(text, language) { success, message -> ... }
    fun sendMessage(
        text: String,
        senderLanguage: String,
        callback: (Boolean, String) -> Unit
    ) {
        sendMessage(text, senderLanguage, callback, "text")
    }

    // Supports the older positional-callback voice call.
    fun sendMessage(
        text: String,
        senderLanguage: String,
        callback: (Boolean, String) -> Unit,
        messageType: String
    ) {
        saveMessage(text, senderLanguage, messageType, callback)
    }

    // Supports: sendMessage(text = ..., senderLanguage = ...,
    //                       messageType = "voice") { success, message -> ... }
    fun sendMessage(
        text: String,
        senderLanguage: String,
        messageType: String,
        callback: (Boolean, String) -> Unit
    ) {
        saveMessage(text, senderLanguage, messageType, callback)
    }

    private fun saveMessage(
        text: String,
        senderLanguage: String,
        messageType: String,
        callback: (Boolean, String) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            callback(false, "Please sign in first")
            return
        }

        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            callback(false, "Message cannot be empty")
            return
        }

        val data = hashMapOf<String, Any>(
            "senderId" to user.uid,
            "senderEmail" to (user.email ?: ""),
            "text" to cleanText,
            "senderLanguage" to senderLanguage.trim().lowercase().ifEmpty { "en" },
            "messageType" to if (messageType == "voice") "voice" else "text",
            "isFinal" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )

        messages.add(data)
            .addOnSuccessListener { callback(true, "Message sent") }
            .addOnFailureListener { error ->
                callback(false, error.localizedMessage ?: "Message send failed")
            }
    }

    fun listenMessages(
        onMessages: (List<RemoteChatMessage>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return messages
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Unable to load messages")
                    return@addSnapshotListener
                }

                val chatMessages: List<RemoteChatMessage> =
                    snapshot?.documents?.map { document ->
                        RemoteChatMessage(
                            id = document.id,
                            senderId = document.getString("senderId") ?: "",
                            senderEmail = document.getString("senderEmail") ?: "",
                            text = document.getString("text") ?: "",
                            senderLanguage = document.getString("senderLanguage") ?: "en",
                            messageType = document.getString("messageType") ?: "text",
                            createdAt = document.getTimestamp("createdAt")
                                ?.toDate()?.time ?: 0L
                        )
                    } ?: emptyList()

                onMessages(chatMessages)
            }
    }
}
