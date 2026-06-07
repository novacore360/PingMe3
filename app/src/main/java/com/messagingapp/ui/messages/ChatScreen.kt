package com.messagingapp.ui.messages

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messagingapp.data.models.Message
import com.messagingapp.data.models.UserProfile
import com.messagingapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.openConversation(conversationId, otherUserId)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(state.messages.size - 1)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(scaffoldPadding)
                .imePadding()
        ) {
            // ── Chat Header ─────────────────────────────────────────────
            ChatHeader(
                user = state.otherUser,
                isTyping = state.isOtherTyping,
                onBack = onBack
            )

            HorizontalDivider(color = GlassColors.divider, thickness = 0.5.dp)

            // ── Messages ────────────────────────────────────────────────
            if (state.isLoading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GlassColors.primary, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Date separators + messages
                    var lastDate = ""
                    state.messages.forEachIndexed { idx, message ->
                        val dateLabel = getDateLabel(message.createdAt)
                        if (dateLabel != lastDate) {
                            lastDate = dateLabel
                            item(key = "date_$dateLabel") {
                                DateSeparator(label = dateLabel)
                            }
                        }
                        item(key = message.id) {
                            val isMine = message.senderId == viewModel.currentUserId
                            val showAvatar = !isMine && (
                                idx == state.messages.size - 1 ||
                                state.messages.getOrNull(idx + 1)?.senderId != message.senderId
                            )
                            MessageBubble(
                                message = message,
                                isMine = isMine,
                                otherUser = if (!isMine) state.otherUser else null,
                                showAvatar = showAvatar,
                                onLongPress = { showReactionPickerFor = message.id },
                                onReply = { viewModel.setReplyTo(message) },
                                onDelete = if (isMine) ({ viewModel.deleteMessage(message.id) }) else null,
                                onReact = { emoji -> viewModel.reactToMessage(message.id, emoji) }
                            )
                        }
                    }
                }
            }

            // ── Reply Preview ────────────────────────────────────────────
            AnimatedVisibility(visible = state.replyTo != null) {
                state.replyTo?.let { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1A6C9EFF))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(36.dp)
                                .background(GlassColors.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Replying",
                                style = MaterialTheme.typography.labelSmall.copy(color = GlassColors.primary)
                            )
                            Text(
                                reply.content.take(80),
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearReply() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear reply",
                                tint = GlassColors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ── Input Row ────────────────────────────────────────────────
            ChatInputRow(
                inputText = inputText,
                onTextChange = {
                    inputText = it
                    viewModel.onTextChanged(it, conversationId)
                },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText, conversationId)
                        inputText = ""
                    }
                }
            )
        }
    }

    // Reaction picker overlay
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

@Composable
private fun ChatHeader(
    user: UserProfile?,
    isTyping: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = GlassColors.textPrimary
            )
        }

        // Avatar with online dot
        Box {
            AvatarCircle(
                name = user?.nickname ?: "?",
                avatarUrl = user?.avatarUrl,
                size = 42
            )
            if (user?.isOnline == true) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00C853))
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                user?.nickname ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = GlassColors.textPrimary
            )
            AnimatedContent(
                targetState = isTyping,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { typing ->
                if (typing) {
                    Text(
                        "typing...",
                        style = MaterialTheme.typography.labelSmall.copy(color = GlassColors.primary)
                    )
                } else {
                    val statusText = when {
                        user?.isOnline == true -> "Online"
                        user?.lastSeen != null -> "Last seen ${formatLastSeen(user.lastSeen)}"
                        else -> ""
                    }
                    if (statusText.isNotEmpty()) {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (user?.isOnline == true) Color(0xFF00C853)
                                        else GlassColors.textTertiary
                            )
                        )
                    }
                }
            }
        }

        IconButton(onClick = {}) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More",
                tint = GlassColors.textTertiary
            )
        }
    }
}

@Composable
private fun ChatInputRow(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x1A000000))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    "Message...",
                    color = GlassColors.textTertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(28.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GlassColors.textPrimary,
                unfocusedTextColor = GlassColors.textPrimary,
                focusedContainerColor = Color(0x26FFFFFF),
                unfocusedContainerColor = Color(0x14FFFFFF),
                focusedBorderColor = GlassColors.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = GlassColors.glassBorder,
                cursorColor = GlassColors.primary
            )
        )

        AnimatedContent(
            targetState = inputText.isNotBlank(),
            transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }
        ) { hasText ->
            if (hasText) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(GlassColors.primary, GlassColors.accent))
                        )
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(GlassColors.glassWhite)
                        .border(1.dp, GlassColors.glassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = GlassColors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DateSeparator(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x26FFFFFF))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = GlassColors.textTertiary
            )
        }
    }
}

private fun getDateLabel(timestamp: String): String {
    return try {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val date = fmt.parse(timestamp.take(19)) ?: return ""
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { time = date }
        when {
            now.get(java.util.Calendar.DATE) == then.get(java.util.Calendar.DATE) -> "Today"
            now.get(java.util.Calendar.DATE) - then.get(java.util.Calendar.DATE) == 1 -> "Yesterday"
            else -> java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(date)
        }
    } catch (e: Exception) { "" }
}

@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    otherUser: com.messagingapp.data.models.UserProfile?,
    showAvatar: Boolean,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onDelete: (() -> Unit)?,
    onReact: (String) -> Unit
) {
    val isDeleted = message.deletedAt != null
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar for received messages
        if (!isMine) {
            if (showAvatar) {
                AvatarCircle(
                    name = otherUser?.nickname ?: "?",
                    avatarUrl = otherUser?.avatarUrl,
                    size = 30
                )
            } else {
                Spacer(Modifier.width(30.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Reply quote
            if (message.replyToContent != null) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x14FFFFFF))
                        .border(
                            0.5.dp,
                            GlassColors.glassBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(GlassColors.primary.copy(0.7f), RoundedCornerShape(1.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message.replyToContent.take(60),
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassColors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Message bubble
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                if (!isDeleted) {
                                    showMenu = true
                                    onLongPress()
                                }
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isMine) 18.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 18.dp
                            )
                        )
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(
                                topStart = 18.dp, topEnd = 18.dp,
                                bottomStart = if (isMine) 18.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 18.dp
                            )
                        )
                        .background(
                            if (isMine)
                                Brush.linearGradient(
                                    listOf(
                                        GlassColors.primary.copy(alpha = 0.85f),
                                        GlassColors.accent.copy(alpha = 0.65f)
                                    )
                                )
                            else
                                Brush.linearGradient(
                                    listOf(Color(0x2EFFFFFF), Color(0x18FFFFFF))
                                )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        if (isDeleted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = GlassColors.textTertiary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Message deleted",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = GlassColors.textTertiary
                                )
                            }
                        } else {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isMine) Color.White else GlassColors.textPrimary,
                                lineHeight = 21.sp
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(message.createdAt),
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

                // Context menu
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .glassCard(12.dp)
                        .background(Color(0xFF0E1629))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("Reply", color = GlassColors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                        },
                        onClick = { showMenu = false; onReply() },
                        leadingIcon = {
                            Icon(Icons.Default.Reply, null, tint = GlassColors.primary, modifier = Modifier.size(16.dp))
                        }
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = {
                                Text("Delete", color = GlassColors.error, style = MaterialTheme.typography.bodyMedium)
                            },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = GlassColors.error, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            // Reactions row
            if (!message.reactions.isNullOrBlank()) {
                val reactionsMap = runCatching {
                    Json.parseToJsonElement(message.reactions).jsonObject.entries.associate { (k, v) ->
                        k to v.jsonArray.size
                    }
                }.getOrDefault(emptyMap())

                if (reactionsMap.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        reactionsMap.forEach { (emoji, count) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x26FFFFFF))
                                    .border(0.5.dp, GlassColors.glassBorder, RoundedCornerShape(10.dp))
                                    .clickable { onReact(emoji) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    "$emoji${if (count > 1) " $count" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GlassColors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isMine) Spacer(Modifier.width(4.dp))
    }
}

@Composable
fun MessageStatusIcon(status: String) {
    val (icon, tint) = when (status) {
        "seen" -> Icons.Default.DoneAll to Color(0xFF64B5F6)
        "delivered" -> Icons.Default.DoneAll to Color.White.copy(0.5f)
        else -> Icons.Default.Check to Color.White.copy(0.5f)
    }
    Icon(
        icon,
        contentDescription = status,
        tint = tint,
        modifier = Modifier.size(14.dp)
    )
}

@Composable
fun ReactionPicker(onReact: (String) -> Unit, onDismiss: () -> Unit) {
    val emojis = listOf("❤️", "😂", "😮", "😢", "👍", "🔥", "🎉", "👏")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x66000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .glassCard(32.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            emojis.forEach { emoji ->
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clickable { onReact(emoji) }
                )
            }
        }
    }
}
