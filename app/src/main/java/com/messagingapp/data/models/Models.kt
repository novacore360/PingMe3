package com.messagingapp.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String = "",
    val nickname: String = "",
    val username: String = "",
    val visibility: String = "public",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Message(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("sender_id") val senderId: String = "",
    val content: String = "",
    val status: String = "sent",
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("reply_to_content") val replyToContent: String? = null,
    @SerialName("reply_to_sender") val replyToSender: String? = null,
    val reactions: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class Conversation(
    val id: String = "",
    @SerialName("user1_id") val user1Id: String = "",
    @SerialName("user2_id") val user2Id: String = "",
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class TypingStatus(
    val id: String = "",
    @SerialName("conversation_id") val conversationId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("is_typing") val isTyping: Boolean = false,
    @SerialName("updated_at") val updatedAt: String = ""
)

data class ConversationWithUser(
    val conversation: Conversation,
    val otherUser: UserProfile,
    val unreadCount: Int = 0
)
