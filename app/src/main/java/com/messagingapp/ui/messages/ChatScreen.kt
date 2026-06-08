package com.messagingapp.ui.messages

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagingapp.data.models.Message
import com.messagingapp.data.models.PendingMessage
import com.messagingapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ChatScreen(
    conversationId: String,
    otherUserId: String,
    viewModel: MessagesViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.chatState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var showReactionPickerFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(conversationId) {
        viewModel.openConversation(conversationId, otherUserId)
    }

    // Scroll to bottom when messages or pending messages change
    val totalCount = state.messages.size + state.pendingMessages.size
    LaunchedEffect(totalCount) {
        if (totalCount > 0) {
            scope.launch { listState.animateScrollToItem(totalCount - 1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
    ) {
        // ── Chat Header ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF060A14), Color(0x00060A14)))
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = GlassColors.textPrimary)
                }

                Box(Modifier.size(44.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(
                                if (state.otherUser?.isOnline == true)
                                    Modifier.border(
                                        2.dp,
                                        Brush.linearGradient(listOf(GlassColors.success, GlassColors.primary)),
                                        CircleShape
                                    )
                                else Modifier
                            )
                            .padding(if (state.otherUser?.isOnline == true) 2.dp else 0.dp)
                    ) {
                        AvatarCircle(
                            name = state.otherUser?.nickname ?: "?",
                            avatarUrl = state.otherUser?.avatarUrl,
                            size = 40
                        )
                    }
                    if (state.otherUser?.isOnline == true) {
                        Box(
                            Modifier.size(11.dp).align(Alignment.BottomEnd)
                                .background(Color(0xFF060A14), CircleShape)
                                .padding(2.dp)
                        ) {
                            Box(Modifier.fillMaxSize().background(GlassColors.success, CircleShape))
                        }
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        state.otherUser?.nickname ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = GlassColors.textPrimary,
                        maxLines = 1
                    )
                    AnimatedContent(
                        targetState = state.isOtherTyping,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { typing ->
                        if (typing) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TypingDots()
                                Text("typing...", style = MaterialTheme.typography.labelSmall, color = GlassColors.primary)
                            }
                        } else {
                            val otherUser = state.otherUser
                            val statusText = when {
                                otherUser == null -> ""
                                otherUser.isOnline -> "Online"
                                else -> formatLastSeen(otherUser.lastSeen)
                            }
                            val statusColor = if (otherUser?.isOnline == true) GlassColors.success else GlassColors.textTertiary
                            Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                        }
                    }
                }

                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, null, tint = GlassColors.textTertiary)
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, GlassColors.glassBorder, Color.Transparent)))
        )

        // ── Messages ─────────────────────────────────────────────────────
        if (state.isLoading) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.primary, strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val msgs = state.messages
                msgs.forEachIndexed { idx, msg ->
                    val prevMsg = if (idx > 0) msgs[idx - 1] else null
                    val showDate = prevMsg == null || !sameDay(prevMsg.createdAt, msg.createdAt)
                    if (showDate) {
                        item(key = "date_${msg.createdAt}") {
                            DateSeparator(msg.createdAt)
                        }
                    }
                    item(key = msg.id) {
                        val isMine = msg.senderId == viewModel.currentUserId
                        val showAvatar = !isMine && (idx == msgs.size - 1 ||
                                msgs[idx + 1].senderId != msg.senderId)
                        MessageBubble(
                            message = msg,
                            isMine = isMine,
                            showAvatar = showAvatar,
                            otherUser = state.otherUser,
                            onLongPress = { if (msg.deletedAt == null) showReactionPickerFor = msg.id },
                            onReply = { viewModel.setReplyTo(msg) },
                            onDelete = if (isMine) ({ viewModel.deleteMessage(msg.id) }) else null,
                            onReact = { emoji -> viewModel.reactToMessage(msg.id, emoji) }
                        )
                    }
                }

                // Optimistic / pending messages shown immediately after sending
                items(state.pendingMessages, key = { it.localId }) { pending ->
                    PendingMessageBubble(
                        pending = pending,
                        onRetry = { viewModel.retryPending(pending.localId, conversationId) }
                    )
                }

                // Typing indicator
                if (state.isOtherTyping) {
                    item(key = "typing") {
                        Row(
                            Modifier.padding(start = 8.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AvatarCircle(
                                name = state.otherUser?.nickname ?: "?",
                                avatarUrl = state.otherUser?.avatarUrl,
                                size = 28
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                                    .background(Color(0x26FFFFFF))
                                    .border(0.5.dp, GlassColors.glassBorder, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) { TypingDots() }
                        }
                    }
                }
            }
        }

        // ── Reply Preview ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.replyTo != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            state.replyTo?.let { reply ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x1A6C9EFF))
                        .border(width = 1.dp, color = GlassColors.primary.copy(0.2f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(36.dp).background(GlassColors.primary, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Replying", style = MaterialTheme.typography.labelSmall, color = GlassColors.primary)
                        Text(
                            reply.content.take(80), style = MaterialTheme.typography.bodyMedium,
                            color = GlassColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.clearReply() }, Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = GlassColors.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // ── Input Row ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x33000000))))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it; viewModel.onTextChanged(it, conversationId) },
                placeholder = { Text("Message...", color = GlassColors.textTertiary, style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GlassColors.textPrimary,
                    unfocusedTextColor = GlassColors.textPrimary,
                    focusedContainerColor = Color(0x26FFFFFF),
                    unfocusedContainerColor = Color(0x14FFFFFF),
                    focusedBorderColor = GlassColors.primary.copy(0.6f),
                    unfocusedBorderColor = GlassColors.glassBorder,
                    cursorColor = GlassColors.primary
                )
            )

            val hasText = inputText.isNotBlank()
            AnimatedContent(
                targetState = hasText,
                transitionSpec = { scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn() togetherWith scaleOut() + fadeOut() }
            ) { typing ->
                if (typing) {
                    Box(
                        modifier = Modifier
                            .size(50.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GlassColors.primary, GlassColors.accent)))
                            .shadow(4.dp, CircleShape)
                            .clickable {
                                val text = inputText.trim()
                                inputText = ""
                                viewModel.sendMessage(text, conversationId)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Use a clean paper-plane / send icon
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp).clip(CircleShape)
                            .background(Color(0x26FFFFFF))
                            .border(1.dp, GlassColors.glassBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, null, tint = GlassColors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    if (showReactionPickerFor != null) {
        ReactionPicker(
            onReact = { emoji ->
                showReactionPickerFor?.let { viewModel.reactToMessage(it, emoji) }
                showReactionPickerFor = null
            },
            onDismiss = { showReactionPickerFor = null }
        )
    }
}

// ── Optimistic "pending" bubble shown while sending ───────────────────────────
@Composable
fun PendingMessageBubble(pending: PendingMessage, onRetry: () -> Unit) {
    val bubbleShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 1.dp),
        horizontalAlignment = Alignment.End
    ) {
        Box(
            modifier = Modifier
                .clip(bubbleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF6C9EFF).copy(alpha = if (pending.status == "failed") 0.5f else 0.85f),
                            Color(0xFF9B6DFF).copy(alpha = if (pending.status == "failed") 0.5f else 0.85f)
                        )
                    )
                )
                .border(0.5.dp, Color(0x406C9EFF), bubbleShape)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = pending.content,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = Color.White.copy(alpha = if (pending.status == "failed") 0.6f else 1f)
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (pending.status) {
                        "sending" -> {
                            val inf = rememberInfiniteTransition(label = "dots_send")
                            val alpha by inf.animateFloat(
                                initialValue = 0.4f, targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                label = "alpha"
                            )
                            Text(
                                "Sending",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = alpha)
                            )
                            Spacer(Modifier.width(4.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = Color.White.copy(alpha = alpha),
                                strokeWidth = 1.5.dp
                            )
                        }
                        "failed" -> {
                            Text(
                                "Failed",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = GlassColors.error
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(GlassColors.error.copy(0.2f))
                                    .clickable(onClick = onRetry)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Retry",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = GlassColors.error
                                )
                            }
                        }
                        else -> {
                            Text(
                                "Sent",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = Color.White.copy(alpha = 0.65f)
                            )
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "sent",
                                tint = Color.White.copy(0.65f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TypingDots() {
    val inf = rememberInfiniteTransition(label = "dots")
    val offsets = (0..2).map { i ->
        inf.animateFloat(
            initialValue = 0f, targetValue = -5f,
            animationSpec = infiniteRepeatable(
                tween(400, delayMillis = i * 120, easing = EaseInOutSine),
                RepeatMode.Reverse
            ), label = "dot$i"
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        offsets.forEach { off ->
            Box(
                Modifier.size(6.dp).offset(y = off.value.dp)
                    .background(GlassColors.textSecondary, CircleShape)
            )
        }
    }
}

@Composable
fun DateSeparator(timestamp: String) {
    val label = runCatching {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = fmt.parse(timestamp.take(19)) ?: return@runCatching "Today"
        val cal = Calendar.getInstance()
        val tCal = Calendar.getInstance().apply { time = date }
        when {
            cal.get(Calendar.DATE) == tCal.get(Calendar.DATE) -> "Today"
            cal.get(Calendar.DATE) - tCal.get(Calendar.DATE) == 1 -> "Yesterday"
            else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
        }
    }.getOrDefault("Today")

    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f).height(0.5.dp).background(GlassColors.divider))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.textTertiary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1AFFFFFF))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Box(Modifier.weight(1f).height(0.5.dp).background(GlassColors.divider))
    }
}

fun sameDay(ts1: String, ts2: String): Boolean = runCatching {
    ts1.take(10) == ts2.take(10)
}.getOrDefault(true)

@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    showAvatar: Boolean,
    otherUser: com.messagingapp.data.models.UserProfile?,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onDelete: (() -> Unit)?,
    onReact: (String) -> Unit
) {
    val isDeleted = message.deletedAt != null
    var showMenu by remember { mutableStateOf(false) }
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomStart = if (isMine) 18.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 18.dp
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (isMine) 0.dp else if (showAvatar) 0.dp else 38.dp,
                end = if (isMine) 0.dp else 48.dp,
                bottom = 1.dp
            ),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        // Reply quote
        if (!message.replyToContent.isNullOrBlank()) {
            Box(
                Modifier
                    .padding(bottom = 3.dp, start = if (isMine) 40.dp else 38.dp, end = if (isMine) 0.dp else 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x1AFFFFFF))
                    .border(0.5.dp, GlassColors.primary.copy(0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.width(2.dp).height(30.dp).background(GlassColors.primary, RoundedCornerShape(1.dp)))
                    Text(
                        message.replyToContent.take(80),
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.textSecondary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isMine) {
                if (showAvatar) {
                    AvatarCircle(
                        name = otherUser?.nickname ?: "?",
                        avatarUrl = otherUser?.avatarUrl,
                        size = 28
                    )
                } else {
                    Spacer(Modifier.size(28.dp))
                }
            }

            Box {
                Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(
                            if (isMine)
                                Brush.linearGradient(listOf(Color(0xFF6C9EFF), Color(0xFF9B6DFF)))
                            else
                                Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))
                        )
                        .border(
                            0.5.dp,
                            if (isMine) Color(0x406C9EFF) else GlassColors.glassBorder,
                            bubbleShape
                        )
                        .pointerInput(message.id) {
                            detectTapGestures(onLongPress = { if (!isDeleted) { showMenu = true; onLongPress() } })
                        }
                        // Limit bubble width so short messages aren't stretched
                        .widthIn(min = 0.dp, max = 280.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            if (isDeleted) "🚫 Message deleted" else message.content,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            color = if (isDeleted) GlassColors.textTertiary
                                    else if (isMine) Color.White
                                    else GlassColors.textPrimary
                        )
                        // Time + status in a tight row at bottom-end
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 3.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatTime(message.createdAt),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isMine) Color.White.copy(0.65f) else GlassColors.textTertiary
                            )
                            if (isMine && !isDeleted) {
                                Spacer(Modifier.width(4.dp))
                                MessageStatusIcon(message.status)
                            }
                        }
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF0E1629))
                ) {
                    DropdownMenuItem(
                        text = { Text("Reply", color = GlassColors.textPrimary) },
                        onClick = { showMenu = false; onReply() },
                        leadingIcon = { Icon(Icons.Default.Reply, null, tint = GlassColors.primary, modifier = Modifier.size(16.dp)) }
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = GlassColors.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = GlassColors.error, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }

        // Reactions
        if (!message.reactions.isNullOrBlank()) {
            val reactMap = runCatching {
                Json.parseToJsonElement(message.reactions).jsonObject.entries.associate { (k, v) -> k to v.jsonArray.size }
            }.getOrDefault(emptyMap())
            if (reactMap.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 3.dp, start = if (isMine) 0.dp else 36.dp, end = if (isMine) 4.dp else 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reactMap.forEach { (emoji, count) ->
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(Color(0x26FFFFFF))
                                .border(0.5.dp, GlassColors.glassBorder, RoundedCornerShape(10.dp))
                                .clickable { onReact(emoji) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text("$emoji${if (count > 1) " $count" else ""}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: String) {
    val (icon, tint) = when (status) {
        "seen" -> Icons.Default.DoneAll to Color(0xFF64FFDA)
        "delivered" -> Icons.Default.DoneAll to Color.White.copy(0.6f)
        else -> Icons.Default.Check to Color.White.copy(0.5f)
    }
    Icon(icon, contentDescription = status, tint = tint, modifier = Modifier.size(14.dp))
}

@Composable
fun ReactionPicker(onReact: (String) -> Unit, onDismiss: () -> Unit) {
    val emojis = listOf("❤️", "😂", "😮", "😢", "👍", "🔥", "🎉", "👏")
    Box(
        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.glassCard(32.dp).padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            emojis.forEach { emoji ->
                Text(emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { onReact(emoji) })
            }
        }
    }
}
