package com.messagingapp.ui.explore

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagingapp.data.models.UserProfile
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.data.repository.MessageRepository
import com.messagingapp.ui.messages.AvatarCircle
import com.messagingapp.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel : ViewModel() {
    private val msgRepo = MessageRepository()
    private val authRepo = AuthRepository()

    private val _users = MutableStateFlow<List<UserProfile>>(emptyList())
    val users = _users.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val currentUserId get() = authRepo.currentUserId() ?: ""

    init { loadUsers() }

    fun loadUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            msgRepo.getPublicUsers()
                .onSuccess { users ->
                    _users.value = users.filter { it.id != currentUserId }
                }
            _isLoading.value = false
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) { loadUsers(); return }
        viewModelScope.launch {
            msgRepo.searchUsers(query)
                .onSuccess { _users.value = it.filter { u -> u.id != currentUserId } }
        }
    }

    fun openChat(userId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            msgRepo.getOrCreateConversation(currentUserId, userId)
                .onSuccess { onReady(it) }
        }
    }
}

@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onOpenChat: (conversationId: String, userId: String) -> Unit
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var previewUser by remember { mutableStateOf<UserProfile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        // Header
        Text(
            "Explore",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        // Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.search(it) },
            placeholder = { Text("Search users...", color = GlassColors.textTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = GlassColors.textSecondary, modifier = Modifier.size(20.dp)) },
            trailingIcon = if (searchQuery.isNotBlank()) {
                { IconButton(onClick = { viewModel.search("") }) {
                    Icon(Icons.Default.Close, null, tint = GlassColors.textSecondary, modifier = Modifier.size(18.dp))
                }}
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GlassColors.textPrimary,
                unfocusedTextColor = GlassColors.textPrimary,
                focusedContainerColor = Color(0x1AFFFFFF),
                unfocusedContainerColor = Color(0x0DFFFFFF),
                focusedBorderColor = GlassColors.primary.copy(0.5f),
                unfocusedBorderColor = GlassColors.glassBorder
            )
        )

        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.primary)
            }
        } else if (users.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PersonSearch, null,
                        tint = GlassColors.textTertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No users found", color = GlassColors.textTertiary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        onMessage = {
                            viewModel.openChat(user.id) { convId ->
                                onOpenChat(convId, user.id)
                            }
                        },
                        onPreview = { previewUser = user }
                    )
                }
            }
        }
    }

    // Profile Preview Sheet
    previewUser?.let { user ->
        ProfilePreviewSheet(user = user, onDismiss = { previewUser = null }, onMessage = {
            previewUser = null
            viewModel.openChat(user.id) { convId -> onOpenChat(convId, user.id) }
        })
    }
}

@Composable
fun UserCard(user: UserProfile, onMessage: () -> Unit, onPreview: () -> Unit) {
    Column(
        modifier = Modifier
            .glassCard(18.dp)
            .clickable(onClick = onPreview)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AvatarCircle(name = user.nickname, size = 56)
        Text(
            text = user.nickname,
            style = MaterialTheme.typography.titleMedium,
            color = GlassColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Button(
            onClick = onMessage,
            modifier = Modifier.fillMaxWidth().height(34.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassColors.primaryGlass),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Send, null, tint = GlassColors.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Message", style = MaterialTheme.typography.labelSmall, color = GlassColors.primary)
        }
    }
}

@Composable
fun ProfilePreviewSheet(user: UserProfile, onDismiss: () -> Unit, onMessage: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF0D1525))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clickable(enabled = false) {}
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.width(40.dp).height(4.dp)
                .background(GlassColors.glassBorder, RoundedCornerShape(2.dp)))

            AvatarCircle(name = user.nickname, size = 72)

            Text(user.nickname, style = MaterialTheme.typography.headlineMedium)
            Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = GlassColors.textSecondary)

            Row(
                modifier = Modifier
                    .glassCard(12.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Public, null, tint = GlassColors.success, modifier = Modifier.size(14.dp))
                Text("Public account", style = MaterialTheme.typography.labelSmall, color = GlassColors.textSecondary)
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onMessage,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassColors.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ChatBubble, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send Message", color = Color.White)
            }
        }
    }
}
