package com.messagingapp.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.messagingapp.data.models.ConversationWithUser
import com.messagingapp.data.models.UserProfile
import com.messagingapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessagesListScreen(
    viewModel: MessagesViewModel,
    onOpenChat: (conversationId: String, userId: String) -> Unit
) {
    val state by viewModel.listState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadConversations() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.titleLarge,
                        color = GlassColors.textPrimary
                    )
                    if (state.totalUnreadCount > 0) {
                        Text(
                            "${state.totalUnreadCount} unread",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassColors.primary
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.loadConversations() }) {
                    Icon(Icons.Default.Refresh, null, tint = GlassColors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GlassColors.primary)
                }
            } else if (state.conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ChatBubbleOutline, null,
                            tint = GlassColors.textTertiary, modifier = Modifier.size(48.dp))
                        Text("No conversations yet", style = MaterialTheme.typography.bodyMedium, color = GlassColors.textTertiary)
                        Text("Find people in Explore", style = MaterialTheme.typography.labelSmall, color = GlassColors.textTertiary)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.conversations, key = { it.conversation.id }) { item ->
                        ConversationItem(item = item, onClick = {
                            val otherId = if (item.conversation.user1Id == viewModel.currentUserId)
                                item.conversation.user2Id else item.conversation.user1Id
                            onOpenChat(item.conversation.id, otherId)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(item: ConversationWithUser, onClick: () -> Unit) {
    val hasUnread = item.unreadCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(16.dp)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar with online indicator
        Box {
            AvatarCircle(
                name = item.otherUser.nickname,
                avatarUrl = item.otherUser.avatarUrl,
                size = 52
            )
            // Online dot
            if (item.otherUser.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00C853)) // green
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.otherUser.nickname,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = GlassColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.conversation.lastMessage ?: "Start a conversation",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (hasUnread) GlassColors.textPrimary else GlassColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item.conversation.lastMessageAt?.let { ts ->
                Text(
                    text = formatTime(ts),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) GlassColors.primary else GlassColors.textTertiary
                )
            }
            // Red badge for unread
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .background(Color(0xFFE53935), CircleShape)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AvatarCircle(name: String, avatarUrl: String? = null, size: Int = 40) {
    val initials = name.take(1).uppercase()
    val hue = (name.hashCode() % 360).toFloat().let { if (it < 0) it + 360 else it }
    val color = Color.hsl(hue, 0.5f, 0.45f)
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            val request = remember(avatarUrl) {
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initials,
                style = if (size >= 48) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

fun formatTime(timestamp: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp.take(19)) ?: return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { time = date }
        if (now.get(Calendar.DATE) == then.get(Calendar.DATE)) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) { "" }
}

fun formatLastSeen(lastSeen: String?): String {
    if (lastSeen == null) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(lastSeen.take(19)) ?: return ""
        val now = System.currentTimeMillis()
        val diffMs = now - date.time
        val diffMin = diffMs / 60_000
        val diffHr = diffMin / 60
        val diffDay = diffHr / 24
        when {
            diffMin < 1 -> "just now"
            diffMin < 60 -> "${diffMin}m ago"
            diffHr < 24 -> "${diffHr}h ago"
            diffDay == 1L -> "yesterday"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    } catch (e: Exception) { "" }
}
