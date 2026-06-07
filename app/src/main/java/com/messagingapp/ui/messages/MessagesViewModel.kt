package com.messagingapp.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagingapp.data.models.*
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.data.repository.MessageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MessagesListUiState(
    val conversations: List<ConversationWithUser> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val totalUnreadCount: Int = 0
)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val otherUser: UserProfile? = null,
    val isLoading: Boolean = false,
    val isOtherTyping: Boolean = false,
    val replyTo: Message? = null,
    val error: String? = null
)

class MessagesViewModel : ViewModel() {

    private val messageRepo = MessageRepository()
    private val authRepo = AuthRepository()

    private val _listState = MutableStateFlow(MessagesListUiState())
    val listState = _listState.asStateFlow()

    private val _chatState = MutableStateFlow(ChatUiState())
    val chatState = _chatState.asStateFlow()

    val currentUserId get() = authRepo.currentUserId() ?: ""

    private var typingJob: Job? = null
    private var currentConversationId: String? = null
    private var realtimeJob: Job? = null
    private var onlineStatusJob: Job? = null

    init {
        // Set user online on ViewModel creation
        viewModelScope.launch {
            runCatching { authRepo.setOnlineStatus(true) }
        }
        startOnlineHeartbeat()
    }

    private fun startOnlineHeartbeat() {
        onlineStatusJob?.cancel()
        onlineStatusJob = viewModelScope.launch {
            while (true) {
                delay(30_000) // every 30 seconds
                runCatching { authRepo.setOnlineStatus(true) }
            }
        }
    }

    fun loadConversations() {
        viewModelScope.launch {
            _listState.value = _listState.value.copy(isLoading = true, error = null)
            messageRepo.getConversations(currentUserId)
                .onSuccess { conversations ->
                    val withUsers = conversations.mapNotNull { conv ->
                        val otherId = if (conv.user1Id == currentUserId) conv.user2Id else conv.user1Id
                        val user = authRepo.getProfile(otherId).getOrNull() ?: return@mapNotNull null
                        val unread = messageRepo.getUnreadCount(conv.id, currentUserId)
                        ConversationWithUser(conv, user, unread)
                    }
                    val totalUnread = withUsers.sumOf { it.unreadCount }
                    _listState.value = MessagesListUiState(
                        conversations = withUsers,
                        isLoading = false,
                        totalUnreadCount = totalUnread
                    )
                }
                .onFailure { e ->
                    _listState.value = _listState.value.copy(
                        isLoading = false,
                        error = friendlyError(e)
                    )
                }
        }
    }

    fun openConversation(conversationId: String, otherUserId: String) {
        currentConversationId = conversationId
        viewModelScope.launch {
            _chatState.value = ChatUiState(isLoading = true)
            val user = authRepo.getProfile(otherUserId).getOrNull()
            messageRepo.getMessages(conversationId)
                .onSuccess { messages ->
                    _chatState.value = ChatUiState(messages = messages, otherUser = user, isLoading = false)
                    // Mark all as seen when opening chat
                    messageRepo.markConversationSeen(conversationId, currentUserId)
                    // Refresh unread counts
                    loadConversations()
                }
                .onFailure { e ->
                    _chatState.value = ChatUiState(
                        otherUser = user,
                        isLoading = false,
                        error = friendlyError(e)
                    )
                }
        }
        listenToMessages(conversationId)
        listenToTyping(conversationId)
    }

    private fun listenToMessages(conversationId: String) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            messageRepo.listenToMessages(conversationId).collect { msg ->
                _chatState.update { state ->
                    val updated = state.messages.toMutableList()
                    val idx = updated.indexOfFirst { it.id == msg.id }
                    if (idx >= 0) updated[idx] = msg else updated.add(msg)
                    state.copy(messages = updated.sortedBy { it.createdAt })
                }
                if (msg.senderId != currentUserId) {
                    messageRepo.updateMessageStatus(msg.id, "seen")
                    // Update unread count in list
                    _listState.update { s ->
                        s.copy(conversations = s.conversations.map {
                            if (it.conversation.id == conversationId)
                                it.copy(unreadCount = 0)
                            else it
                        })
                    }
                }
            }
        }
    }

    private fun listenToTyping(conversationId: String) {
        viewModelScope.launch {
            messageRepo.listenToTyping(conversationId).collect { status ->
                if (status.userId != currentUserId) {
                    _chatState.update { it.copy(isOtherTyping = status.isTyping) }
                }
            }
        }
    }

    fun sendMessage(content: String, conversationId: String) {
        if (content.isBlank()) return
        val reply = _chatState.value.replyTo
        viewModelScope.launch {
            clearReply()
            messageRepo.sendMessage(
                conversationId = conversationId,
                senderId = currentUserId,
                content = content,
                replyToId = reply?.id,
                replyToContent = reply?.content,
                replyToSender = reply?.senderId
            ).onFailure { e ->
                _chatState.update { it.copy(error = friendlyError(e)) }
            }
            stopTyping(conversationId)
        }
    }

    fun onTextChanged(text: String, conversationId: String) {
        typingJob?.cancel()
        if (text.isNotBlank()) {
            viewModelScope.launch {
                messageRepo.setTyping(conversationId, currentUserId, true)
                typingJob = viewModelScope.launch {
                    delay(2000)
                    messageRepo.setTyping(conversationId, currentUserId, false)
                }
            }
        }
    }

    private fun stopTyping(conversationId: String) {
        typingJob?.cancel()
        viewModelScope.launch {
            messageRepo.setTyping(conversationId, currentUserId, false)
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepo.deleteMessage(messageId)
                .onSuccess {
                    _chatState.update { state ->
                        state.copy(messages = state.messages.map {
                            if (it.id == messageId) it.copy(deletedAt = "deleted", content = "This message was deleted")
                            else it
                        })
                    }
                }
                .onFailure { e ->
                    _chatState.update { it.copy(error = friendlyError(e)) }
                }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            messageRepo.addReaction(messageId, emoji, currentUserId)
                .onFailure { e ->
                    _chatState.update { it.copy(error = friendlyError(e)) }
                }
        }
    }

    fun setReplyTo(message: Message) {
        _chatState.update { it.copy(replyTo = message) }
    }

    fun clearReply() {
        _chatState.update { it.copy(replyTo = null) }
    }

    fun clearError() {
        _chatState.update { it.copy(error = null) }
        _listState.update { it.copy(error = null) }
    }

    fun openChatWithUser(userId: String, onConversationReady: (String) -> Unit) {
        viewModelScope.launch {
            messageRepo.getOrCreateConversation(currentUserId, userId)
                .onSuccess { onConversationReady(it) }
                .onFailure { e ->
                    _chatState.update { it.copy(error = friendlyError(e)) }
                }
        }
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message ?: "Unknown error"
        return when {
            msg.contains("network", ignoreCase = true) ||
            msg.contains("connect", ignoreCase = true) ||
            msg.contains("timeout", ignoreCase = true) -> "No internet connection. Please check your network."
            msg.contains("401") || msg.contains("unauthorized", ignoreCase = true) -> "Session expired. Please log in again."
            msg.contains("403") || msg.contains("forbidden", ignoreCase = true) -> "You don't have permission to do that."
            msg.contains("404") -> "Not found. This item may have been deleted."
            msg.contains("500") || msg.contains("server", ignoreCase = true) -> "Server error. Please try again later."
            else -> "Something went wrong. Please try again."
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        typingJob?.cancel()
        onlineStatusJob?.cancel()
        // Set offline when ViewModel is cleared
        viewModelScope.launch {
            runCatching { authRepo.setOnlineStatus(false) }
        }
    }
}
