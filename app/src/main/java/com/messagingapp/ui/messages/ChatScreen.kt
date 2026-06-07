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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.messagingapp.data.models.Message
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

    // imePadding on the outer Column fixes the "hidden behind keyboard" bug
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .imePadding()
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(0.dp)
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
            // Avatar with online indicator
            Box {
                AvatarCircle(
                    name = state.otherUser?.nickname ?: "?",
                    avatarUrl = state.otherUser?.avatarUrl,
                    size = 40
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.otherUser?.nickname ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassColors.textPrimary
                )
                AnimatedVisibility(visible = state.isOtherTyping) {
                    Text(
                        "typing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.primary
                    )
                }
            }
            // Info button
            IconButton(onClick = {}) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = GlassColors.textTertiary
                )
            }
        }

        HorizontalDivider(color = GlassColors.divider, thickness = 0.5.dp)

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                val isMine = message.senderId == viewModel.currentUserId
                MessageBubble(
                    message = message,
                    isMine = isMine,
                    onLongPress = { showReactionPickerFor = message.id },
                    onReply = { viewModel.setReplyTo(message) },
                    onDelete = if (isMine) ({ viewModel.deleteMessage(message.id) }) else null,
                    onReact = { emoji -> viewModel.reactToMessage(message.id, emoji) }
                )
            }
        }

        // Reply Preview
        AnimatedVisibility(visible = state.replyTo != null) {
            state.replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassColors.glassWhite)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassColors.primary
                        )
                        Text(
                            reply.content.take(60),
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
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Input Row — sits above keyboard thanks to imePadding above
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x26000000))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    viewModel.onTextChanged(it, conversationId)
                },
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

            // Send / Mic toggle
            AnimatedContent(
                targetState = inputText.isNotBlank(),
                transitionSpec = {
                    scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
                }
            ) { hasText ->
                if (hasText) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(GlassColors.primary, GlassColors.accent)
                                )
                            )
                            .clickable {
                                viewModel.sendMessage(inputText, conversationId)
                                inputText = ""
                            },
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
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    onLongPress: () -> Unit,
    onReply: () -> Unit,
    onDelete: (() -> Unit)?,
    onReact: (String) -> Unit
) {
    val isDeleted = message.deletedAt != null
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        // Reply quote
        if (message.replyToContent != null) {
            Row(
                modifier = Modifier
                    .padding(
                        bottom = 2.dp,
                        start = if (isMine) 40.dp else 0.dp,
                        end = if (isMine) 0.dp else 40.dp
                    )
                    .glassCard(8.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
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

        Row(
            modifier = Modifier
                .padding(
                    start = if (isMine) 48.dp else 0.dp,
                    end = if (isMine) 0.dp else 48.dp
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            if (!isDeleted) {
                                showMenu = true
                                onLongPress()
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMine) {
                Spacer(Modifier.width(4.dp))
            }

            Box {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = if (isMine) 20.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 20.dp
                            )
                        )
                        .background(
                            if (isMine)
                                Brush.linearGradient(
                                    listOf(
                                        GlassColors.primary.copy(alpha = 0.75f),
                                        GlassColors.accent.copy(alpha = 0.55f)
                                    )
                                )
                            else
                                Brush.linearGradient(
                                    listOf(
                                        Color(0x26FFFFFF),
                                        Color(0x14FFFFFF)
                                    )
                                )
                        )
                        .border(
                            0.5.dp,
                            if (isMine) GlassColors.primary.copy(0.25f) else GlassColors.glassBorder,
                            RoundedCornerShape(
                                topStart = 20.dp, topEnd = 20.dp,
                                bottomStart = if (isMine) 20.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 20.dp
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column {
                        Text(
                            text = if (isDeleted) "Message deleted" else message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isDeleted) GlassColors.textTertiary else GlassColors.textPrimary
                        )

                        Spacer(Modifier.height(3.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = formatTime(message.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMine) Color.White.copy(0.6f) else GlassColors.textTertiary
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
                            Text(
                                "Reply",
                                color = GlassColors.textPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        onClick = { showMenu = false; onReply() },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Reply,
                                contentDescription = null,
                                tint = GlassColors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete",
                                    color = GlassColors.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = GlassColors.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
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
                    modifier = Modifier.padding(
                        top = 2.dp,
                        start = if (isMine) 0.dp else 8.dp,
                        end = if (isMine) 8.dp else 0.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    reactionsMap.forEach { (emoji, count) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x26FFFFFF))
                                .border(0.5.dp, GlassColors.glassBorder, RoundedCornerShape(10.dp))
                                .clickable { onReact(emoji) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "$emoji ${if (count > 1) count.toString() else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = GlassColors.textPrimary
                            )
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
        "seen" -> Icons.Default.DoneAll to Color.White
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
