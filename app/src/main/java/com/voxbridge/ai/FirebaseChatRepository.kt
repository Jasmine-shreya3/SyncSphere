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
    private val auth get() = FirebaseAuth.getInstance()
    private val messages get() = FirebaseFirestore.getInstance()
        .collection("publicRooms").document("global").collection("messages")

    fun currentUser(): FirebaseUser? = auth.currentUser
    fun signOut() = auth.signOut()

    fun signIn(email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
            callback(task.isSuccessful, if (task.isSuccessful) "Signed in successfully"
            else task.exception?.localizedMessage ?: "Sign in failed")
        }
    }

    fun signUp(email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnCompleteListener { task ->
            callback(task.isSuccessful, if (task.isSuccessful) "Account created successfully"
            else task.exception?.localizedMessage ?: "Sign up failed")
        }
    }

    fun sendMessage(text: String, senderLanguage: String, callback: (Boolean, String) -> Unit) =
        saveMessage(text, senderLanguage, "text", callback)

    fun sendMessage(text: String, senderLanguage: String,
                    callback: (Boolean, String) -> Unit, messageType: String) =
        saveMessage(text, senderLanguage, messageType, callback)

    fun sendMessage(text: String, senderLanguage: String, messageType: String,
                    callback: (Boolean, String) -> Unit) =
        saveMessage(text, senderLanguage, messageType, callback)

    private fun saveMessage(text: String, senderLanguage: String, messageType: String,
                            callback: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: run { callback(false, "Please sign in first"); return }
        val clean = text.trim()
        if (clean.isEmpty()) { callback(false, "Message cannot be empty"); return }
        val data = hashMapOf<String, Any>(
            "senderId" to user.uid, "senderEmail" to (user.email ?: ""),
            "text" to clean, "senderLanguage" to senderLanguage.trim().lowercase().ifEmpty { "en" },
            "messageType" to if (messageType == "voice") "voice" else "text",
            "isFinal" to true, "createdAt" to FieldValue.serverTimestamp()
        )
        messages.add(data).addOnSuccessListener { callback(true, "Message sent") }
            .addOnFailureListener { callback(false, it.localizedMessage ?: "Message send failed") }
    }

    fun deleteMessage(messageId: String, callback: (Boolean, String) -> Unit) {
        val user = auth.currentUser ?: run { callback(false, "Sign in first"); return }
        if (messageId.isBlank()) { callback(false, "Invalid message"); return }
        val ref = messages.document(messageId)
        ref.get().addOnSuccessListener { snap ->
            if (!snap.exists()) { callback(false, "Message not found"); return@addOnSuccessListener }
            if (snap.getString("senderId") != user.uid) {
                callback(false, "You can delete only your own messages")
                return@addOnSuccessListener
            }
            ref.delete().addOnSuccessListener { callback(true, "Deleted") }
                .addOnFailureListener { callback(false, it.localizedMessage ?: "Delete failed") }
        }.addOnFailureListener { callback(false, it.localizedMessage ?: "Delete failed") }
    }

    fun listenMessages(onMessages: (List<RemoteChatMessage>) -> Unit,
                       onError: (String) -> Unit): ListenerRegistration =
        messages.orderBy("createdAt", Query.Direction.ASCENDING).limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Unable to load messages")
                    return@addSnapshotListener
                }
                onMessages(snapshot?.documents?.map { doc ->
                    RemoteChatMessage(
                        id = doc.id, senderId = doc.getString("senderId") ?: "",
                        senderEmail = doc.getString("senderEmail") ?: "",
                        text = doc.getString("text") ?: "",
                        senderLanguage = doc.getString("senderLanguage") ?: "en",
                        messageType = doc.getString("messageType") ?: "text",
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                } ?: emptyList())
            }
}
