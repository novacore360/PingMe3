package com.messagingapp.ui.auth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.messagingapp.ui.theme.*

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthenticated: (needsProfile: Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isAuthenticated) {
        if (uiState.isAuthenticated) {
            onAuthenticated(uiState.needsProfile)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundGradient)
    ) {
        // Ambient blobs
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset((-80).dp, (-60).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x226C9EFF),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
        )
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.BottomEnd)
                .offset(60.dp, 60.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x22B388FF),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App icon/logo area
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .glassSurface(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = GlassColors.primary,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Glimpse",
                style = MaterialTheme.typography.headlineLarge,
                color = GlassColors.textPrimary
            )
            Text(
                text = if (isLogin) "Welcome back" else "Create account",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassColors.textSecondary
            )

            Spacer(Modifier.height(32.dp))

            // Auth card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(24.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email",
                    leadingIcon = Icons.Default.Email,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                GlassTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    leadingIcon = Icons.Default.Lock,
                    trailingIcon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    onTrailingIconClick = { passwordVisible = !passwordVisible },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                AnimatedVisibility(visible = uiState.error != null) {
                    Text(
                        text = uiState.error ?: "",
                        color = GlassColors.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        viewModel.clearError()
                        if (isLogin) viewModel.signIn(email, password)
                        else viewModel.signUp(email, password)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !uiState.isLoading && email.isNotBlank() && password.length >= 6,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlassColors.primary,
                        disabledContainerColor = GlassColors.primary.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isLogin) "Sign In" else "Sign Up",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row {
                Text(
                    text = if (isLogin) "Don't have an account? " else "Already have an account? ",
                    color = GlassColors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (isLogin) "Sign Up" else "Sign In",
                    color = GlassColors.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        isLogin = !isLogin
                        viewModel.clearError()
                    }
                )
            }
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = GlassColors.textSecondary) },
        leadingIcon = if (leadingIcon != null) {
            { Icon(leadingIcon, null, tint = GlassColors.textSecondary, modifier = Modifier.size(20.dp)) }
        } else null,
        trailingIcon = if (trailingIcon != null) {
            {
                IconButton(onClick = { onTrailingIconClick?.invoke() }) {
                    Icon(trailingIcon, null, tint = GlassColors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        } else null,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = GlassColors.textPrimary,
            unfocusedTextColor = GlassColors.textPrimary,
            focusedContainerColor = Color(0x0DFFFFFF),
            unfocusedContainerColor = Color(0x06FFFFFF),
            focusedBorderColor = GlassColors.primary.copy(alpha = 0.7f),
            unfocusedBorderColor = GlassColors.glassBorder,
            cursorColor = GlassColors.primary
        )
    )
}
