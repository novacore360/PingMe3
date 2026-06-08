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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.messagingapp.data.models.ConversationWithUser
import com.messagingapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessagesListScreen(
    viewModel: MessagesViewModel,
    onOpenChat: (conversationId: String, userId: String) -> Unit
) {
    val state by viewModel.listState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadConversations() }

    Column(Modifier.fillMaxSize().background(Color.Transparent)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Messages", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            if (state.totalUnread > 0) {
                Box(
                    Modifier.size(22.dp).background(GlassColors.error, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (state.totalUnread > 99) "99+" else state.totalUnread.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { viewModel.loadConversations() }) {
                Icon(Icons.Default.Refresh, null, tint = GlassColors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.primary)
            }
            state.conversations.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ChatBubbleOutline, null, tint = GlassColors.textTertiary, modifier = Modifier.size(52.dp))
                    Text("No conversations yet", style = MaterialTheme.typography.bodyMedium, color = GlassColors.textTertiary)
                    Text("Find people in Explore", style = MaterialTheme.typography.labelSmall, color = GlassColors.textTertiary)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.conversations, key = { it.conversation.id }) { item ->
                    val otherId = if (item.conversation.user1Id == viewModel.currentUserId)
                        item.conversation.user2Id else item.conversation.user1Id
                    ConversationItem(item = item, onClick = {
                        onOpenChat(item.conversation.id, otherId)
                    })
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
        // Avatar with online dot
        Box {
            AvatarCircle(name = item.otherUser.nickname, avatarUrl = item.otherUser.avatarUrl, size = 50)
            if (item.otherUser.isOnline) {
                Box(
                    Modifier
                        .size(13.dp)
                        .align(Alignment.BottomEnd)
                        .background(Color(0xFF0A0E1A), CircleShape)
                        .padding(2.dp)
                ) {
                    Box(Modifier.fillMaxSize().background(GlassColors.success, CircleShape))
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.otherUser.nickname,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = GlassColors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                item.conversation.lastMessage ?: "Start a conversation",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (hasUnread) GlassColors.textPrimary else GlassColors.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item.conversation.lastMessageAt?.let { ts ->
                Text(
                    formatTime(ts),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) GlassColors.primary else GlassColors.textTertiary
                )
            }
            if (hasUnread) {
                Box(
                    Modifier.defaultMinSize(minWidth = 20.dp).height(20.dp)
                        .background(GlassColors.error, RoundedCornerShape(10.dp))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AvatarCircle(name: String, avatarUrl: String? = null, size: Int = 40) {
    val hue = (name.hashCode() % 360).toFloat().let { if (it < 0) it + 360 else it }
    val color = Color.hsl(hue, 0.5f, 0.45f)
    val context = LocalContext.current

    Box(
        modifier = Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.8f)),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = remember(avatarUrl) {
                    ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .crossfade(true)
                        .build()
                },
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                name.take(1).uppercase(),
                style = if (size >= 48) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

fun formatTime(ts: String): String = runCatching {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val date = fmt.parse(ts.take(19)) ?: return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    when {
        now.get(Calendar.DATE) == then.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        now.get(Calendar.WEEK_OF_YEAR) == then.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}.getOrDefault("")

fun formatLastSeen(lastSeen: String?): String {
    if (lastSeen.isNullOrBlank()) return "Last seen recently"
    return runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = fmt.parse(lastSeen.take(19)) ?: return "Last seen recently"
        val diffMs = System.currentTimeMillis() - date.time
        val mins = diffMs / 60_000
        val hours = mins / 60
        val days = hours / 24
        when {
            mins < 2 -> "Just now"
            mins < 60 -> "Last seen $mins min ago"
            hours < 24 -> "Last seen ${hours}h ago"
            days == 1L -> "Last seen yesterday"
            else -> "Last seen ${SimpleDateFormat("MMM d", Locale.getDefault()).format(date)}"
        }
    }.getOrDefault("Last seen recently")
}
