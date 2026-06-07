package com.messagingapp.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.messagingapp.data.models.UserProfile
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.ui.messages.AvatarCircle
import com.messagingapp.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val repo = AuthRepository()
    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile = _profile.asStateFlow()
    private val _loggedOut = MutableStateFlow(false)
    val loggedOut = _loggedOut.asStateFlow()
    private val _uploadingAvatar = MutableStateFlow(false)
    val uploadingAvatar = _uploadingAvatar.asStateFlow()
    private val _uploadingCover = MutableStateFlow(false)
    val uploadingCover = _uploadingCover.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            val uid = repo.currentUserId() ?: return@launch
            repo.getProfile(uid).onSuccess { _profile.value = it }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.signOut()
            _loggedOut.value = true
        }
    }

    fun uploadAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val uid = repo.currentUserId() ?: return@launch
            _uploadingAvatar.value = true
            _error.value = null
            repo.uploadAvatar(uid, bytes, "image/jpeg")
                .onSuccess { url ->
                    repo.updateProfilePhoto(uid, avatarUrl = url, coverUrl = null)
                    loadProfile()
                }
                .onFailure { _error.value = it.message }
            _uploadingAvatar.value = false
        }
    }

    fun uploadCover(bytes: ByteArray) {
        viewModelScope.launch {
            val uid = repo.currentUserId() ?: return@launch
            _uploadingCover.value = true
            _error.value = null
            repo.uploadCover(uid, bytes, "image/jpeg")
                .onSuccess { url ->
                    repo.updateProfilePhoto(uid, avatarUrl = null, coverUrl = url)
                    loadProfile()
                }
                .onFailure { _error.value = it.message }
            _uploadingCover.value = false
        }
    }

    fun clearError() { _error.value = null }
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogout: () -> Unit
) {
    val profile by viewModel.profile.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val uploadingAvatar by viewModel.uploadingAvatar.collectAsState()
    val uploadingCover by viewModel.uploadingCover.collectAsState()
    val error by viewModel.error.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    // Image pickers
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: return@let
            viewModel.uploadAvatar(bytes)
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes() ?: return@let
            viewModel.uploadCover(bytes)
        }
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            // ── Cover Photo ──────────────────────────────────────────────
            val ctx = LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                // Cover image or gradient placeholder
                if (!profile?.coverUrl.isNullOrBlank()) {
                    // Append bust= timestamp so Coil treats each upload as a new image
                    val coverRequest = remember(profile?.coverUrl) {
                        ImageRequest.Builder(ctx)
                            .data("${profile?.coverUrl}?bust=${System.currentTimeMillis()}")
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .memoryCachePolicy(CachePolicy.DISABLED)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = "Cover photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF1A1F3C),
                                        Color(0xFF0D1525),
                                        GlassColors.primary.copy(0.2f)
                                    )
                                )
                            )
                    )
                }

                // Dim overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )

                // Camera icon to change cover
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.5f))
                        .border(1.dp, GlassColors.glassBorder, CircleShape)
                        .clickable(enabled = !uploadingCover) {
                            coverPicker.launch("image/*")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uploadingCover) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = GlassColors.primary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Change cover",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Page title at top left
                Text(
                    "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp)
                )
            }

            // ── Avatar overlapping the cover ─────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-44).dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterStart)) {
                    // Avatar ring
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .border(
                                3.dp,
                                Brush.linearGradient(
                                    listOf(GlassColors.primary, GlassColors.accent)
                                ),
                                CircleShape
                            )
                            .padding(3.dp)
                    ) {
                        AvatarCircle(
                            name = profile?.nickname ?: "?",
                            avatarUrl = profile?.avatarUrl,
                            size = 82
                        )
                    }

                    // Camera badge on avatar
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(GlassColors.primary)
                            .border(2.dp, GlassColors.background, CircleShape)
                            .clickable(enabled = !uploadingAvatar) {
                                avatarPicker.launch("image/*")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change avatar",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // ── Name / username ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-32).dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                profile?.let { p ->
                    Text(
                        p.nickname,
                        style = MaterialTheme.typography.headlineMedium,
                        color = GlassColors.textPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "@${p.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassColors.textSecondary
                        )
                        // Visibility badge
                        val (icon, color, label) = if (p.visibility == "public")
                            Triple(Icons.Default.Public, GlassColors.success, "Public")
                        else
                            Triple(Icons.Default.Lock, GlassColors.warning, "Private")

                        Row(
                            modifier = Modifier
                                .glassCard(10.dp)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                } ?: CircularProgressIndicator(
                    color = GlassColors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // ── Stats row ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .offset(y = (-20).dp)
                    .glassCard(16.dp)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(value = "—", label = "Messages")
                VerticalDivider(
                    modifier = Modifier.height(36.dp),
                    color = GlassColors.divider
                )
                StatItem(value = "—", label = "Friends")
                VerticalDivider(
                    modifier = Modifier.height(36.dp),
                    color = GlassColors.divider
                )
                StatItem(value = "—", label = "Groups")
            }

            Spacer(Modifier.height(8.dp))

            // ── Settings rows ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                SettingsRow(icon = Icons.Default.Edit, label = "Edit Profile", onClick = {})
                SettingsRow(icon = Icons.Default.Notifications, label = "Notifications", onClick = {})
                SettingsRow(icon = Icons.Default.Security, label = "Privacy & Security", onClick = {})
                SettingsRow(icon = Icons.Default.Palette, label = "Appearance", onClick = {})
                SettingsRow(icon = Icons.Default.Info, label = "About PingMe", onClick = {})
            }

            Spacer(Modifier.height(24.dp))

            // ── Sign Out ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassColors.error),
                border = BorderStroke(1.dp, GlassColors.error.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = null,
                    tint = GlassColors.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", color = GlassColors.error)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = Color(0xFF0D1525),
            shape = RoundedCornerShape(20.dp),
            title = { Text("Sign Out", color = GlassColors.textPrimary) },
            text = { Text("Are you sure you want to sign out?", color = GlassColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.logout() }) {
                    Text("Sign Out", color = GlassColors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = GlassColors.textSecondary)
                }
            }
        )
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = GlassColors.textPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.textTertiary
        )
    }
}

@Composable
fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(14.dp)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(GlassColors.primaryGlass, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = GlassColors.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = GlassColors.textPrimary
        )
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = GlassColors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}
