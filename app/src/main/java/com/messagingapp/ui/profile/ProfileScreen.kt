package com.messagingapp.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.messagingapp.data.models.UserProfile
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.ui.messages.AvatarCircle
import com.messagingapp.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val repo = AuthRepository()
    val profile = MutableStateFlow<UserProfile?>(null)
    val loggedOut = MutableStateFlow(false)
    val uploadingAvatar = MutableStateFlow(false)
    val uploadingCover = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val imageKey = MutableStateFlow(0L)

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            val uid = repo.currentUserId() ?: return@launch
            repo.getProfile(uid).onSuccess { profile.value = it }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.signOut()
            loggedOut.value = true
        }
    }

    fun uploadAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val uid = repo.currentUserId() ?: return@launch
            uploadingAvatar.value = true; error.value = null
            repo.uploadAvatar(uid, bytes)
                .onSuccess { url ->
                    repo.updateProfilePhoto(uid, avatarUrl = url, coverUrl = null)
                    imageKey.value = System.currentTimeMillis()
                    loadProfile()
                }
                .onFailure { error.value = it.message }
            uploadingAvatar.value = false
        }
    }

    fun uploadCover(bytes: ByteArray) {
        viewModelScope.launch {
            val uid = repo.currentUserId() ?: return@launch
            uploadingCover.value = true; error.value = null
            repo.uploadCover(uid, bytes)
                .onSuccess { url ->
                    repo.updateProfilePhoto(uid, avatarUrl = null, coverUrl = url)
                    imageKey.value = System.currentTimeMillis()
                    loadProfile()
                }
                .onFailure { error.value = it.message }
            uploadingCover.value = false
        }
    }

    fun clearError() { error.value = null }
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onLogout: () -> Unit) {
    val profile by viewModel.profile.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val uploadingAvatar by viewModel.uploadingAvatar.collectAsState()
    val uploadingCover by viewModel.uploadingCover.collectAsState()
    val error by viewModel.error.collectAsState()
    val imageKey by viewModel.imageKey.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(loggedOut) { if (loggedOut) onLogout() }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { context.contentResolver.openInputStream(it)?.readBytes()?.let(viewModel::uploadAvatar) }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { context.contentResolver.openInputStream(it)?.readBytes()?.let(viewModel::uploadCover) }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = Color.Transparent) { pad ->
        Column(
            Modifier.fillMaxSize().background(Color.Transparent)
                .verticalScroll(rememberScrollState()).padding(pad)
        ) {
            // ── Cover Photo ─────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                val coverUrl = profile?.coverUrl
                if (!coverUrl.isNullOrBlank()) {
                    key(imageKey) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (imageKey > 0) "$coverUrl?v=$imageKey" else coverUrl)
                                .diskCachePolicy(CachePolicy.DISABLED)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .crossfade(true).build(),
                            contentDescription = "Cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Color(0xFF0D1B3E), Color(0xFF1A0D3C), GlassColors.primary.copy(0.25f)))
                        )
                    )
                }
                // Gradient overlay for readability
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Color(0x99000000)))
                    )
                )
                // Title
                Text(
                    "Profile",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp)
                )
                // Camera button
                Box(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                        .size(36.dp).clip(CircleShape)
                        .background(Color(0x88000000))
                        .border(1.dp, Color(0x40FFFFFF), CircleShape)
                        .clickable(enabled = !uploadingCover) { coverPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (uploadingCover) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            // ── Avatar ──────────────────────────────────────────────────
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-44).dp)) {
                Box(Modifier.align(Alignment.CenterStart)) {
                    Box(
                        Modifier.size(88.dp).clip(CircleShape)
                            .border(3.dp, Brush.linearGradient(listOf(GlassColors.primary, GlassColors.accent)), CircleShape)
                            .padding(3.dp)
                    ) {
                        key(imageKey) {
                            AvatarCircle(
                                name = profile?.nickname ?: "?",
                                avatarUrl = profile?.avatarUrl?.let { if (imageKey > 0) "$it?v=$imageKey" else it },
                                size = 82
                            )
                        }
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape)
                            .background(GlassColors.primary).border(2.dp, GlassColors.background, CircleShape)
                            .clickable(enabled = !uploadingAvatar) { avatarPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uploadingAvatar) CircularProgressIndicator(Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                        else Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                // Online status badge
                if (profile?.isOnline == true) {
                    Row(
                        Modifier.align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33000000))
                            .border(1.dp, GlassColors.success.copy(0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(7.dp).background(GlassColors.success, CircleShape))
                        Text("Online", style = MaterialTheme.typography.labelSmall, color = GlassColors.success)
                    }
                }
            }

            // ── Name / Username ─────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-30).dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                profile?.let { p ->
                    Text(p.nickname, style = MaterialTheme.typography.headlineMedium, color = GlassColors.textPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("@${p.username}", style = MaterialTheme.typography.bodyMedium, color = GlassColors.textSecondary)
                        val (icon, color, label) = if (p.visibility == "public")
                            Triple(Icons.Default.Public, GlassColors.success, "Public")
                        else Triple(Icons.Default.Lock, GlassColors.warning, "Private")
                        Row(
                            Modifier.glassCard(10.dp).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                } ?: CircularProgressIndicator(color = GlassColors.primary, modifier = Modifier.size(24.dp))
            }

            // ── Stats ───────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).offset(y = (-16).dp)
                    .glassCard(16.dp).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("—", "Messages")
                VerticalDivider(Modifier.height(36.dp), color = GlassColors.divider)
                StatItem("—", "Friends")
                VerticalDivider(Modifier.height(36.dp), color = GlassColors.divider)
                StatItem("—", "Groups")
            }

            Spacer(Modifier.height(8.dp))

            // ── Settings ────────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Settings", style = MaterialTheme.typography.labelSmall, color = GlassColors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                SettingsRow(Icons.Default.Edit, "Edit Profile") {}
                SettingsRow(Icons.Default.Notifications, "Notifications") {}
                SettingsRow(Icons.Default.Security, "Privacy & Security") {}
                SettingsRow(Icons.Default.Palette, "Appearance") {}
                SettingsRow(Icons.Default.Info, "About PingMe") {}
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = { showLogoutDialog = true },
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassColors.error),
                border = BorderStroke(1.dp, GlassColors.error.copy(0.5f))
            ) {
                Icon(Icons.Default.Logout, null, tint = GlassColors.error, modifier = Modifier.size(18.dp))
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
                TextButton(onClick = { showLogoutDialog = false; viewModel.logout() }) {
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
        Text(value, style = MaterialTheme.typography.titleLarge, color = GlassColors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = GlassColors.textTertiary)
    }
}

@Composable
fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().glassCard(14.dp).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(36.dp).background(GlassColors.primaryGlass, RoundedCornerShape(10.dp)), Alignment.Center) {
            Icon(icon, null, tint = GlassColors.primary, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = GlassColors.textPrimary, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = GlassColors.textTertiary, modifier = Modifier.size(18.dp))
    }
}
