package com.messagingapp.ui.auth

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.messagingapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val needsProfile: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository()
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    // ── Network check ──────────────────────────────────────────────────
    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    private fun noNetworkError() {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = "No internet connection. Please turn on Wi-Fi or mobile data and try again."
        )
    }

    // ── Sign Up ────────────────────────────────────────────────────────
    fun signUp(email: String, password: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your email.")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters.")
            return
        }
        if (!isNetworkAvailable()) { noNetworkError(); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.signUp(email.trim(), password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        needsProfile = true
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyError(e.message)
                    )
                }
        }
    }

    // ── Sign In ────────────────────────────────────────────────────────
    fun signIn(email: String, password: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your email.")
            return
        }
        if (password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your password.")
            return
        }
        if (!isNetworkAvailable()) { noNetworkError(); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.signIn(email.trim(), password)
                .onSuccess {
                    val hasProfile = runCatching { repo.hasProfile() }.getOrDefault(false)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        needsProfile = !hasProfile
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyError(e.message)
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // ── Friendly error messages — never expose raw Supabase text ──────
    private fun friendlyError(raw: String?): String {
        val msg = raw?.lowercase() ?: return "Something went wrong. Please try again."
        return when {
            "network"  in msg || "connect" in msg ||
            "unable to resolve" in msg || "failed to connect" in msg ||
            "unreachable" in msg || "no address" in msg ->
                "No internet connection. Please turn on Wi-Fi or mobile data and try again."

            "timeout"  in msg || "timed out" in msg ->
                "The request timed out. Please check your connection and try again."

            "invalid login" in msg || "invalid credentials" in msg ||
            "wrong password" in msg || "bad credentials" in msg ->
                "Incorrect email or password. Please check and try again."

            "email not confirmed" in msg || "not verified" in msg ->
                "Please verify your email address before signing in."

            "user already registered" in msg || "already exists" in msg ||
            "already registered" in msg ->
                "An account with this email already exists. Try signing in instead."

            "password should be" in msg || "password must be" in msg ->
                "Password must be at least 6 characters."

            "invalid email" in msg || "validate email" in msg ||
            "malformed" in msg ->
                "Please enter a valid email address."

            "rate limit" in msg || "too many requests" in msg ->
                "Too many attempts. Please wait a moment and try again."

            "server" in msg || "503" in msg || "502" in msg ->
                "The server is temporarily unavailable. Please try again in a moment."

            else ->
                "Something went wrong. Please try again."
        }
    }
}
