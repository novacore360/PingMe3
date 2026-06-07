package com.messagingapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messagingapp.ui.HomeScreen
import com.messagingapp.ui.auth.AuthScreen
import com.messagingapp.ui.auth.AuthViewModel
import com.messagingapp.ui.setup.SetupScreen
import com.messagingapp.ui.setup.SetupViewModel
import com.messagingapp.ui.theme.AppTheme
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

@Composable
fun AppNavigation() {
    val isLoggedIn = remember {
        try {
            SupabaseClient.client.auth.currentUserOrNull() != null
        } catch (e: Exception) {
            false
        }
    }

    var screen by remember {
        mutableStateOf(if (isLoggedIn) "checking" else "auth")
    }

    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            try {
                val authRepo = com.messagingapp.data.repository.AuthRepository()
                val hasProfile = authRepo.hasProfile()
                screen = if (hasProfile) "home" else "setup"
            } catch (e: Exception) {
                screen = "auth"
            }
        }
    }

    when (screen) {
        "auth" -> {
            val vm: AuthViewModel = viewModel()
            AuthScreen(viewModel = vm, onAuthenticated = { needsProfile ->
                screen = if (needsProfile) "setup" else "home"
            })
        }
        "setup" -> {
            val vm: SetupViewModel = viewModel()
            SetupScreen(viewModel = vm, onComplete = { screen = "home" })
        }
        "home" -> {
            HomeScreen(onLogout = { screen = "auth" })
        }
        else -> {
            // checking
        }
    }
}
