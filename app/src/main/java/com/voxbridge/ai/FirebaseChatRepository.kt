package com.voxbridge.ai

import com.google.firebase.auth.FirebaseAuth
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
    val createdAt: Long = 0L
)

object FirebaseChatRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val messages
        get() = db.collection("publicRooms")
            .document("global")
            .collection("messages")

    fun currentUser() = auth.currentUser

    fun signUp(email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) callback(true, "Account created")
                else callback(false, task.exception?.localizedMessage ?: "Sign-up failed")
            }
    }

    fun signIn(email: String, password: String, callback: (Boolean, String) -> Unit) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) callback(true, "Signed in")
                else callback(false, task.exception?.localizedMessage ?: "Sign-in failed")
            }
    }

    fun signOut() = auth.signOut()

    fun sendMessage(
        text: String,
        senderLanguage: String,
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

        val data = hashMapOf(
            "senderId" to user.uid,
            "senderEmail" to (user.email ?: ""),
            "text" to cleanText,
            "senderLanguage" to senderLanguage,
            "createdAt" to FieldValue.serverTimestamp()
        )

        messages.add(data)
            .addOnSuccessListener { callback(true, "Sent") }
            .addOnFailureListener {
                callback(false, it.localizedMessage ?: "Could not send message")
            }
    }

    fun listenMessages(
        onMessages: (List<RemoteChatMessage>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return messages
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Unable to load messages")
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    onMessages(emptyList())
                    return@addSnapshotListener
                }

                val result = snapshot.documents.mapNotNull { document ->
                    val text = document.getString("text") ?: return@mapNotNull null
                    RemoteChatMessage(
                        id = document.id,
                        senderId = document.getString("senderId") ?: "",
                        senderEmail = document.getString("senderEmail") ?: "",
                        text = text,
                        senderLanguage = document.getString("senderLanguage") ?: "en",
                        createdAt = document.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                }
                onMessages(result)
            }
    }
}
