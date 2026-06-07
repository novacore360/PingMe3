package com.messagingapp.ui.auth

import androidx.lifecycle.ViewModel
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

class AuthViewModel : ViewModel() {

    private val repo = AuthRepository()
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun signUp(email: String, password: String) {
        if (!validateInputs(email, password)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.signUp(email, password)
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
                        error = friendlyAuthError(e)
                    )
                }
        }
    }

    fun signIn(email: String, password: String) {
        if (!validateInputs(email, password)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.signIn(email, password)
                .onSuccess {
                    val hasProfile = repo.hasProfile()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthenticated = true,
                        needsProfile = !hasProfile
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyAuthError(e)
                    )
                }
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        return when {
            email.isBlank() -> {
                _uiState.value = _uiState.value.copy(error = "Please enter your email address.")
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                _uiState.value = _uiState.value.copy(error = "Please enter a valid email address.")
                false
            }
            password.isBlank() -> {
                _uiState.value = _uiState.value.copy(error = "Please enter your password.")
                false
            }
            password.length < 6 -> {
                _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters.")
                false
            }
            else -> true
        }
    }

    private fun friendlyAuthError(e: Throwable): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("invalid login") || msg.contains("invalid credentials") ||
            msg.contains("wrong password") -> "Incorrect email or password. Please try again."
            msg.contains("email not confirmed") -> "Please verify your email address before signing in."
            msg.contains("already registered") || msg.contains("user already exists") ->
                "An account with this email already exists. Try signing in instead."
            msg.contains("network") || msg.contains("connect") || msg.contains("timeout") ->
                "No internet connection. Please check your network and try again."
            msg.contains("rate limit") || msg.contains("too many") ->
                "Too many attempts. Please wait a moment and try again."
            msg.contains("weak password") -> "Please choose a stronger password (at least 6 characters)."
            else -> "Something went wrong. Please try again."
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
