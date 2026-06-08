package com.messagingapp.data.repository

import com.messagingapp.SupabaseClient
import com.messagingapp.data.models.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

class MessageRepository {

    private val client = SupabaseClient.client

    suspend fun getOrCreateConversation(user1Id: String, user2Id: String): Result<String> = runCatching {
        val existing = client.postgrest["conversations"]
            .select {
                filter {
                    or {
                        and { eq("user1_id", user1Id); eq("user2_id", user2Id) }
                        and { eq("user1_id", user2Id); eq("user2_id", user1Id) }
                    }
                }
                limit(1)
            }
            .decodeList<Conversation>()

        if (existing.isNotEmpty()) {
            existing.first().id
        } else {
            client.postgrest["conversations"]
                .insert(buildJsonObject {
                    put("user1_id", user1Id)
                    put("user2_id", user2Id)
                }) { select() }
                .decodeList<Conversation>()
                .first().id
        }
    }

    suspend fun getConversations(userId: String): Result<List<Conversation>> = runCatching {
        client.postgrest["conversations"]
            .select {
                filter {
                    or { eq("user1_id", userId); eq("user2_id", userId) }
                }
                order("last_message_at", Order.DESCENDING)
            }
            .decodeList<Conversation>()
    }

    suspend fun getUnreadCount(conversationId: String, userId: String): Int = runCatching {
        client.postgrest["messages"]
            .select {
                filter {
                    eq("conversation_id", conversationId)
                    neq("sender_id", userId)
                    or { eq("status", "sent"); eq("status", "delivered") }
                }
            }
            .decodeList<Message>()
            .size
    }.getOrDefault(0)

    suspend fun getMessages(conversationId: String): Result<List<Message>> = runCatching {
        client.postgrest["messages"]
            .select {
                filter { eq("conversation_id", conversationId) }
                order("created_at", Order.ASCENDING)
            }
            .decodeList<Message>()
            .filter { it.deletedAt == null }
    }

    suspend fun sendMessage(
        conversationId: String, senderId: String, content: String,
        replyToId: String? = null, replyToContent: String? = null, replyToSender: String? = null
    ): Result<Message> = runCatching {
        val body = buildJsonObject {
            put("conversation_id", conversationId)
            put("sender_id", senderId)
            put("content", content)
            put("status", "sent")
            if (replyToId != null) put("reply_to_id", replyToId)
            if (replyToContent != null) put("reply_to_content", replyToContent)
            if (replyToSender != null) put("reply_to_sender", replyToSender)
        }
        val msg = client.postgrest["messages"].insert(body) { select() }
            .decodeList<Message>().first()
        client.postgrest["conversations"].update(buildJsonObject {
            put("last_message", content)
            put("last_message_at", msg.createdAt)
            put("updated_at", msg.createdAt)
        }) { filter { eq("id", conversationId) } }
        msg
    }

    suspend fun markConversationRead(conversationId: String, userId: String): Result<Unit> = runCatching {
        client.postgrest["messages"].update(buildJsonObject { put("status", "seen") }) {
            filter {
                eq("conversation_id", conversationId)
                neq("sender_id", userId)
                or { eq("status", "sent"); eq("status", "delivered") }
            }
        }
        Unit
    }

    suspend fun updateMessageStatus(messageId: String, status: String): Result<Unit> = runCatching {
        client.postgrest["messages"].update(buildJsonObject { put("status", status) }) {
            filter { eq("id", messageId) }
        }
        Unit
    }

    suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        val now = Clock.System.now().toString()
        client.postgrest["messages"].update(buildJsonObject {
            put("deleted_at", now)
            put("content", "This message was deleted")
        }) { filter { eq("id", messageId) } }
        Unit
    }

    suspend fun addReaction(messageId: String, emoji: String, userId: String): Result<Unit> = runCatching {
        val msg = client.postgrest["messages"]
            .select { filter { eq("id", messageId) }; limit(1) }
            .decodeList<Message>().firstOrNull() ?: return@runCatching

        val reactionsMap: MutableMap<String, MutableList<String>> =
            if (msg.reactions != null) {
                runCatching {
                    Json.parseToJsonElement(msg.reactions).jsonObject.entries.associate { (k, v) ->
                        k to v.jsonArray.map { it.jsonPrimitive.content }.toMutableList()
                    }.toMutableMap()
                }.getOrDefault(mutableMapOf())
            } else mutableMapOf()

        val users = reactionsMap.getOrPut(emoji) { mutableListOf() }
        if (userId in users) users.remove(userId) else users.add(userId)
        if (users.isEmpty()) reactionsMap.remove(emoji)

        val newReactions = buildJsonObject {
            reactionsMap.forEach { (k, v) -> put(k, buildJsonArray { v.forEach { add(it) } }) }
        }.toString()

        client.postgrest["messages"].update(buildJsonObject { put("reactions", newReactions) }) {
            filter { eq("id", messageId) }
        }
        Unit
    }

    suspend fun setTyping(conversationId: String, userId: String, isTyping: Boolean): Result<Unit> = runCatching {
        client.postgrest["typing_status"].upsert(buildJsonObject {
            put("conversation_id", conversationId)
            put("user_id", userId)
            put("is_typing", isTyping)
        })
        Unit
    }

    suspend fun getPublicUsers(): Result<List<UserProfile>> = runCatching {
        client.postgrest["profiles"]
            .select { filter { eq("visibility", "public") }; order("created_at", Order.DESCENDING) }
            .decodeList<UserProfile>()
    }

    suspend fun searchUsers(query: String): Result<List<UserProfile>> = runCatching {
        client.postgrest["profiles"]
            .select {
                filter {
                    eq("visibility", "public")
                    or { ilike("username", "%$query%"); ilike("nickname", "%$query%") }
                }
            }
            .decodeList<UserProfile>()
    }

    // Each flow creates a uniquely-named channel to avoid duplicate-subscription crashes
    fun listenToMessages(conversationId: String): Flow<Message> = callbackFlow {
        val ch = client.realtime.channel("msgs_${conversationId.take(8)}_${System.currentTimeMillis()}")
        val job = launch {
            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "messages" }
                .mapNotNull { runCatching { Json.decodeFromJsonElement<Message>(it.record) }.getOrNull() }
                .filter { it.conversationId == conversationId && it.deletedAt == null }
                .collect { trySend(it) }
        }
        runCatching { ch.subscribe() }
        awaitClose { job.cancel(); launch { runCatching { ch.unsubscribe() } } }
    }

    fun listenToTyping(conversationId: String): Flow<TypingStatus> = callbackFlow {
        val ch = client.realtime.channel("typing_${conversationId.take(8)}_${System.currentTimeMillis()}")
        val j1 = launch {
            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "typing_status" }
                .mapNotNull { runCatching { Json.decodeFromJsonElement<TypingStatus>(it.record) }.getOrNull() }
                .filter { it.conversationId == conversationId }.collect { trySend(it) }
        }
        val j2 = launch {
            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "typing_status" }
                .mapNotNull { runCatching { Json.decodeFromJsonElement<TypingStatus>(it.record) }.getOrNull() }
                .filter { it.conversationId == conversationId }.collect { trySend(it) }
        }
        runCatching { ch.subscribe() }
        awaitClose { j1.cancel(); j2.cancel(); launch { runCatching { ch.unsubscribe() } } }
    }

    fun listenToUserProfile(userId: String): Flow<UserProfile> = callbackFlow {
        val ch = client.realtime.channel("profile_${userId.take(8)}_${System.currentTimeMillis()}")
        val job = launch {
            ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "profiles" }
                .mapNotNull { runCatching { Json.decodeFromJsonElement<UserProfile>(it.record) }.getOrNull() }
                .filter { it.id == userId }.collect { trySend(it) }
        }
        runCatching { ch.subscribe() }
        awaitClose { job.cancel(); launch { runCatching { ch.unsubscribe() } } }
    }

    fun listenToAllIncomingMessages(userId: String): Flow<Message> = callbackFlow {
        val ch = client.realtime.channel("inbox_${userId.take(8)}_${System.currentTimeMillis()}")
        val job = launch {
            ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "messages" }
                .mapNotNull { runCatching { Json.decodeFromJsonElement<Message>(it.record) }.getOrNull() }
                .filter { it.senderId != userId && it.deletedAt == null }
                .collect { trySend(it) }
        }
        runCatching { ch.subscribe() }
        awaitClose { job.cancel(); launch { runCatching { ch.unsubscribe() } } }
    }
}
