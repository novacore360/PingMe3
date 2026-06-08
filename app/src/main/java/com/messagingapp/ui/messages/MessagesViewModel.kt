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
    val totalUnread: Int = 0
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
    private var realtimeJob: Job? = null
    private var presenceJob: Job? = null

    // ── Conversation list ──────────────────────────────────────────────
    fun loadConversations() {
        viewModelScope.launch {
            _listState.update { it.copy(isLoading = true) }
            messageRepo.getConversations(currentUserId)
                .onSuccess { convos ->
                    val withUsers = convos.mapNotNull { conv ->
                        val otherId = if (conv.user1Id == currentUserId) conv.user2Id else conv.user1Id
                        authRepo.getProfile(otherId).getOrNull()?.let { user ->
                            val unread = messageRepo.getUnreadCount(conv.id, currentUserId)
                            ConversationWithUser(conv, user, unread)
                        }
                    }
                    val total = withUsers.sumOf { it.unreadCount }
                    _listState.value = MessagesListUiState(withUsers, false, total)
                }
                .onFailure { _listState.update { it.copy(isLoading = false) } }
        }
    }

    // ── Chat ────────────────────────────────────────────────────────────
    fun openConversation(conversationId: String, otherUserId: String) {
        realtimeJob?.cancel()
        presenceJob?.cancel()
        viewModelScope.launch {
            _chatState.value = ChatUiState(isLoading = true)
            val user = authRepo.getProfile(otherUserId).getOrNull()
            messageRepo.getMessages(conversationId)
                .onSuccess { msgs ->
                    _chatState.value = ChatUiState(messages = msgs, otherUser = user, isLoading = false)
                    // Mark all as seen
                    messageRepo.markConversationRead(conversationId, currentUserId)
                    // Refresh unread counts in list
                    loadConversations()
                }
                .onFailure { _chatState.update { it.copy(otherUser = user, isLoading = false) } }
        }
        startRealtimeListeners(conversationId, otherUserId)
    }

    private fun startRealtimeListeners(conversationId: String, otherUserId: String) {
        realtimeJob = viewModelScope.launch {
            messageRepo.listenToMessages(conversationId).collect { msg ->
                _chatState.update { s ->
                    val list = s.messages.toMutableList()
                    val idx = list.indexOfFirst { it.id == msg.id }
                    if (idx >= 0) list[idx] = msg else list.add(msg)
                    s.copy(messages = list.sortedBy { it.createdAt })
                }
                if (msg.senderId != currentUserId) {
                    messageRepo.updateMessageStatus(msg.id, "seen")
                }
            }
        }
        viewModelScope.launch {
            messageRepo.listenToTyping(conversationId).collect { st ->
                if (st.userId != currentUserId)
                    _chatState.update { it.copy(isOtherTyping = st.isTyping) }
            }
        }
        // Live presence updates in the chat header
        presenceJob = viewModelScope.launch {
            messageRepo.listenToUserProfile(otherUserId).collect { updated ->
                _chatState.update { it.copy(otherUser = updated) }
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
            )
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
        viewModelScope.launch { messageRepo.setTyping(conversationId, currentUserId, false) }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepo.deleteMessage(messageId)
            _chatState.update { s ->
                s.copy(messages = s.messages.map {
                    if (it.id == messageId) it.copy(deletedAt = "deleted", content = "This message was deleted")
                    else it
                })
            }
        }
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch { messageRepo.addReaction(messageId, emoji, currentUserId) }
    }

    fun setReplyTo(message: Message) { _chatState.update { it.copy(replyTo = message) } }
    fun clearReply() { _chatState.update { it.copy(replyTo = null) } }

    fun openChatWithUser(userId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            messageRepo.getOrCreateConversation(currentUserId, userId).onSuccess { onReady(it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        typingJob?.cancel()
        presenceJob?.cancel()
    }
}
