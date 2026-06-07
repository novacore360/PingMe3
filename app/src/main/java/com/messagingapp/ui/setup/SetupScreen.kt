package com.messagingapp.ui.setup

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.ui.auth.GlassTextField
import com.messagingapp.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {
    private val repo = AuthRepository()
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _done = MutableStateFlow(false)
    val done = _done.asStateFlow()

    fun createProfile(nickname: String, visibility: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            repo.createProfile(nickname, visibility)
                .onSuccess { _done.value = true }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }
}

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onComplete: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val done by viewModel.done.collectAsState()
    var nickname by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("public") }

    LaunchedEffect(done) { if (done) onComplete() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundGradient)
    ) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
                .background(
                    Brush.radialGradient(listOf(Color(0x226C9EFF), Color.Transparent)),
                    RoundedCornerShape(50)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .glassSurface(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = GlassColors.primary, modifier = Modifier.size(30.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text("Set Up Profile", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "You're almost in! Just a few things.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassColors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(24.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                GlassTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(20) },
                    label = "Nickname",
                    leadingIcon = Icons.Default.Badge
                )

                if (nickname.isNotBlank()) {
                    val preview = "${nickname.lowercase().replace(" ", "_")}12345"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AlternateEmail, null,
                            tint = GlassColors.textTertiary, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Username preview: @$preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlassColors.textTertiary
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Visibility", style = MaterialTheme.typography.bodyMedium, color = GlassColors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VisibilityChip(
                            label = "Public",
                            icon = Icons.Default.Public,
                            selected = visibility == "public",
                            onClick = { visibility = "public" },
                            modifier = Modifier.weight(1f)
                        )
                        VisibilityChip(
                            label = "Private",
                            icon = Icons.Default.Lock,
                            selected = visibility == "private",
                            onClick = { visibility = "private" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = if (visibility == "public")
                            "Others can find and message you"
                        else
                            "Only people with your username can message you",
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.textTertiary
                    )
                }

                AnimatedVisibility(visible = error != null) {
                    Text(error ?: "", color = GlassColors.error, style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = { viewModel.createProfile(nickname, visibility) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !isLoading && nickname.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassColors.primary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Let's Go", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun VisibilityChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) GlassColors.primaryGlass else Color(0x0AFFFFFF)
    val borderColor = if (selected) GlassColors.primary else GlassColors.glassBorder
    val contentColor = if (selected) GlassColors.primary else GlassColors.textSecondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = contentColor)
    }
}
