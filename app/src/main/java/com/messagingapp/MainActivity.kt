package com.messagingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagingapp.ui.HomeScreen
import com.messagingapp.ui.auth.AuthScreen
import com.messagingapp.ui.auth.AuthViewModel
import com.messagingapp.ui.setup.SetupScreen
import com.messagingapp.ui.setup.SetupViewModel
import com.messagingapp.ui.theme.*
import io.github.jan.supabase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }
}

sealed class AppScreen {
    object Splash : AppScreen()
    object Auth   : AppScreen()
    object Setup  : AppScreen()
    object Home   : AppScreen()
}

@Composable
fun AppNavigation() {
    var sessionKey by remember { mutableStateOf(0) }
    var screen     by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

    LaunchedEffect(sessionKey) {
        screen = AppScreen.Splash

        // Check whether a local session token exists — this does NOT need a network call.
        // currentUserOrNull() reads from the in-memory / persisted token cache.
        val hasLocalSession = runCatching {
            SupabaseClient.client.auth.currentUserOrNull() != null
        }.getOrDefault(false)

        if (hasLocalSession) {
            // User was previously logged in. Skip auth screen entirely — go straight home.
            // We intentionally do NOT call refreshCurrentSession() here so that no-network
            // launches still work. The session will refresh lazily on the first API call.
            val authRepo = com.messagingapp.data.repository.AuthRepository()
            val hasProfile = runCatching { authRepo.hasProfile() }.getOrDefault(true)
            screen = if (hasProfile) AppScreen.Home else AppScreen.Setup
        } else {
            // No local session → show sign in / sign up
            screen = AppScreen.Auth
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
    ) { currentScreen ->
        when (currentScreen) {
            AppScreen.Splash -> SplashScreen()

            AppScreen.Auth -> key(sessionKey) {
                val vm: AuthViewModel = viewModel()
                AuthScreen(viewModel = vm, onAuthenticated = { needsProfile ->
                    screen = if (needsProfile) AppScreen.Setup else AppScreen.Home
                })
            }

            AppScreen.Setup -> key(sessionKey) {
                val vm: SetupViewModel = viewModel()
                SetupScreen(viewModel = vm, onComplete = { screen = AppScreen.Home })
            }

            AppScreen.Home -> key(sessionKey) {
                HomeScreen(onLogout = {
                    // Increment key — wipes every ViewModel and cached state for the old account
                    sessionKey++
                    // Explicitly navigate to Auth so sign-in/sign-up is shown immediately
                    screen = AppScreen.Auth
                })
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(AppBackgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                "PingMe",
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                color = GlassColors.primary.copy(alpha = alpha)
            )
            CircularProgressIndicator(
                color     = GlassColors.primary.copy(alpha = 0.6f),
                strokeWidth = 2.dp,
                modifier  = Modifier.size(24.dp)
            )
        }
    }
}
