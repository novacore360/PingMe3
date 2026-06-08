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
    object Auth : AppScreen()
    object Setup : AppScreen()
    object Home : AppScreen()
}

@Composable
fun AppNavigation() {
    // sessionKey is incremented on every logout — forces ALL ViewModels to be recreated,
    // clearing any cached data from the previous account.
    var sessionKey by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

    // Re-run auth check any time sessionKey changes (i.e. on logout)
    LaunchedEffect(sessionKey) {
        screen = AppScreen.Splash
        try {
            val isLoggedIn = SupabaseClient.client.auth.currentUserOrNull() != null
            if (isLoggedIn) {
                try { SupabaseClient.client.auth.refreshCurrentSession() } catch (_: Exception) {}
                val authRepo = com.messagingapp.data.repository.AuthRepository()
                screen = if (authRepo.hasProfile()) AppScreen.Home else AppScreen.Setup
            } else {
                screen = AppScreen.Auth
            }
        } catch (_: Exception) {
            screen = AppScreen.Auth
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
    ) { currentScreen ->
        // Wrapping every branch in key(sessionKey) ensures that all composables —
        // and their associated ViewModels — are fully destroyed and recreated when
        // the user logs out and a new session starts.
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
                    // Bump the key → every ViewModel and its state gets wiped
                    sessionKey++
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
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
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
                color = GlassColors.primary.copy(alpha = 0.6f),
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
