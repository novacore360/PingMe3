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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagingapp.data.repository.AuthRepository
import com.messagingapp.ui.HomeScreen
import com.messagingapp.ui.auth.AuthScreen
import com.messagingapp.ui.auth.AuthViewModel
import com.messagingapp.ui.setup.SetupScreen
import com.messagingapp.ui.setup.SetupViewModel
import com.messagingapp.ui.theme.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authRepo = AuthRepository()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            // App came to foreground
            lifecycleScope.launch {
                runCatching { authRepo.setOnlineStatus(true) }
            }
        }
        override fun onStop(owner: LifecycleOwner) {
            // App went to background
            lifecycleScope.launch {
                runCatching { authRepo.setOnlineStatus(false) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle uncaught exceptions gracefully (prevent "app keeps stopping" dialog)
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            android.util.Log.e("PingMe", "Uncaught exception: ${throwable.message}", throwable)
            // Let system handle it — don't crash silently in ways that loop
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)

        setContent {
            AppTheme {
                AppNavigation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(appLifecycleObserver)
        lifecycleScope.launch {
            runCatching { authRepo.setOnlineStatus(false) }
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
    var sessionKey by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Splash) }

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
        } catch (e: Exception) {
            screen = AppScreen.Auth
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
    ) { currentScreen ->
        when (currentScreen) {
            AppScreen.Splash -> SplashScreen()
            AppScreen.Auth -> {
                key(sessionKey) {
                    val vm: AuthViewModel = viewModel()
                    AuthScreen(viewModel = vm, onAuthenticated = { needsProfile ->
                        screen = if (needsProfile) AppScreen.Setup else AppScreen.Home
                    })
                }
            }
            AppScreen.Setup -> {
                val vm: SetupViewModel = viewModel()
                SetupScreen(viewModel = vm, onComplete = { screen = AppScreen.Home })
            }
            AppScreen.Home -> {
                key(sessionKey) {
                    HomeScreen(onLogout = {
                        sessionKey++
                        screen = AppScreen.Auth
                    })
                }
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
