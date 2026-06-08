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

    val users = MutableStateFlow<List<UserProfile>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val currentUserId get() = authRepo.currentUserId() ?: ""

    init { loadUsers() }

    fun loadUsers() {
        viewModelScope.launch {
            isLoading.value = true
            msgRepo.getPublicUsers().onSuccess { users.value = it.filter { u -> u.id != currentUserId } }
            isLoading.value = false
        }
    }

    fun search(query: String) {
        searchQuery.value = query
        if (query.isBlank()) { loadUsers(); return }
        viewModelScope.launch {
            msgRepo.searchUsers(query).onSuccess { users.value = it.filter { u -> u.id != currentUserId } }
        }
    }

    fun openChat(userId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            msgRepo.getOrCreateConversation(currentUserId, userId).onSuccess { onReady(it) }
        }
    }
}

@Composable
fun ExploreScreen(viewModel: ExploreViewModel, onOpenChat: (String, String) -> Unit) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var previewUser by remember { mutableStateOf<UserProfile?>(null) }

    Column(Modifier.fillMaxSize().background(Color.Transparent)) {
        Text(
            "Explore",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.search(it) },
            placeholder = { Text("Search users...", color = GlassColors.textTertiary) },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = GlassColors.textSecondary, modifier = Modifier.size(20.dp))
            },
            trailingIcon = if (searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { viewModel.search("") }) {
                        Icon(Icons.Default.Close, null, tint = GlassColors.textSecondary)
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        when {
            isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.primary)
            }
            users.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PersonSearch, null, tint = GlassColors.textTertiary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("No users found", color = GlassColors.textTertiary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            else -> LazyVerticalGrid(
                // Fixed 2 columns — equal widths always
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(users, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        onMessage = { viewModel.openChat(user.id) { convId -> onOpenChat(convId, user.id) } },
                        onPreview = { previewUser = user }
                    )
                }
            }
        }
    }

    previewUser?.let { user ->
        ProfilePreviewSheet(
            user = user,
            onDismiss = { previewUser = null },
            onMessage = {
                previewUser = null
                viewModel.openChat(user.id) { convId -> onOpenChat(convId, user.id) }
            }
        )
    }
}

@Composable
fun UserCard(user: UserProfile, onMessage: () -> Unit, onPreview: () -> Unit) {
    // Fixed height so all cards are equal regardless of content length
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)          // equal card height for every cell
            .glassCard(18.dp)
            .clickable(onClick = onPreview)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Avatar + online dot
        Box {
            AvatarCircle(name = user.nickname, avatarUrl = user.avatarUrl, size = 54)
            if (user.isOnline) {
                Box(
                    Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color(0xFF0A0E1A), CircleShape)
                        .padding(2.dp)
                ) { Box(Modifier.fillMaxSize().background(GlassColors.success, CircleShape)) }
            }
        }

        // Name + handle block — constrained so they never push the button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                user.nickname,
                style = MaterialTheme.typography.titleMedium,
                color = GlassColors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                "@${user.username}",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        // Message button — always at bottom, same height
        Button(
            onClick = onMessage,
            modifier = Modifier.fillMaxWidth().height(34.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GlassColors.primaryGlass),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Icon(Icons.Default.Send, null, tint = GlassColors.primary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("Message", style = MaterialTheme.typography.labelSmall, color = GlassColors.primary)
        }
    }
}

@Composable
fun ProfilePreviewSheet(user: UserProfile, onDismiss: () -> Unit, onMessage: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x88000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF0D1525))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x10FFFFFF))),
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
                .clickable(enabled = false) {}
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.width(40.dp).height(4.dp).background(GlassColors.glassBorder, RoundedCornerShape(2.dp)))

            Box {
                AvatarCircle(name = user.nickname, avatarUrl = user.avatarUrl, size = 72)
                if (user.isOnline) {
                    Box(
                        Modifier.size(16.dp).align(Alignment.BottomEnd)
                            .background(Color(0xFF0D1525), CircleShape).padding(3.dp)
                    ) { Box(Modifier.fillMaxSize().background(GlassColors.success, CircleShape)) }
                }
            }

            Text(user.nickname, style = MaterialTheme.typography.headlineMedium, color = GlassColors.textPrimary)
            Text("@${user.username}", style = MaterialTheme.typography.bodyMedium, color = GlassColors.textSecondary)

            Row(
                Modifier.glassCard(12.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val (icon, color, label) = if (user.visibility == "public")
                    Triple(Icons.Default.Public, GlassColors.success, "Public account")
                else Triple(Icons.Default.Lock, GlassColors.warning, "Private account")
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = GlassColors.textSecondary)

                if (user.isOnline) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(6.dp).background(GlassColors.success, CircleShape))
                    Text("Online", style = MaterialTheme.typography.labelSmall, color = GlassColors.success)
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onMessage,
                Modifier.fillMaxWidth().height(50.dp),
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
