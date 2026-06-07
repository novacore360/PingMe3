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
    val isLoading: Boolean = false
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

    fun loadConversations() {
        viewModelScope.launch {
            _listState.value = _listState.value.copy(isLoading = true)
            messageRepo.getConversations(currentUserId)
                .onSuccess { conversations ->
                    val withUsers = conversations.mapNotNull { conv ->
                        val otherId = if (conv.user1Id == currentUserId) conv.user2Id else conv.user1Id
                        authRepo.getProfile(otherId).getOrNull()?.let { user ->
                            ConversationWithUser(conv, user)
                        }
                    }
                    _listState.value = MessagesListUiState(conversations = withUsers, isLoading = false)
                }
                .onFailure { _listState.value = _listState.value.copy(isLoading = false) }
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
                    markMessagesDelivered(messages, conversationId)
                }
                .onFailure { _chatState.value = ChatUiState(otherUser = user, isLoading = false) }
        }
        listenToMessages(conversationId)
        listenToTyping(conversationId)
    }

    private fun markMessagesDelivered(messages: List<Message>, conversationId: String) {
        viewModelScope.launch {
            messages.filter { it.senderId != currentUserId && it.status == "sent" }
                .forEach { messageRepo.updateMessageStatus(it.id, "delivered") }
        }
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
            ).onSuccess {
                // Optimistic update handled by realtime listener
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
            _chatState.update { state ->
                state.copy(messages = state.messages.map {
                    if (it.id == messageId) it.copy(deletedAt = "deleted", content = "This message was deleted")
                    else it
                })
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            messageRepo.addReaction(messageId, emoji, currentUserId)
        }
    }

    fun setReplyTo(message: Message) {
        _chatState.update { it.copy(replyTo = message) }
    }

    fun clearReply() {
        _chatState.update { it.copy(replyTo = null) }
    }

    fun openChatWithUser(userId: String, onConversationReady: (String) -> Unit) {
        viewModelScope.launch {
            messageRepo.getOrCreateConversation(currentUserId, userId)
                .onSuccess { onConversationReady(it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        typingJob?.cancel()
    }
}
