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
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

    LaunchedEffect(Unit) {
        try {
            val isLoggedIn = SupabaseClient.client.auth.currentUserOrNull() != null
            if (isLoggedIn) {
                // Refresh session silently — this is what makes auto-login work
                try { SupabaseClient.client.auth.refreshCurrentSession() } catch (_: Exception) {}
                val authRepo = com.messagingapp.data.repository.AuthRepository()
                screen = if (authRepo.hasProfile()) AppScreen.Home else AppScreen.Setup
            } else {
                screen = AppScreen.Auth
            }
        } catch (e: Exception) {
            screen = AppScreen.Auth
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
        }
    ) { currentScreen ->
        when (currentScreen) {
            AppScreen.Splash -> SplashScreen()
            AppScreen.Auth -> {
                val vm: AuthViewModel = viewModel()
                AuthScreen(viewModel = vm, onAuthenticated = { needsProfile ->
                    screen = if (needsProfile) AppScreen.Setup else AppScreen.Home
                })
            }
            AppScreen.Setup -> {
                val vm: SetupViewModel = viewModel()
                SetupScreen(viewModel = vm, onComplete = { screen = AppScreen.Home })
            }
            AppScreen.Home -> {
                HomeScreen(onLogout = { screen = AppScreen.Auth })
            }
        }
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundGradient),
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
