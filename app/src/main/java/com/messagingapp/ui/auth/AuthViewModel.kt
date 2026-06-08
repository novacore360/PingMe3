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
        if (email.isBlank()) { _uiState.value = _uiState.value.copy(error = "Please enter your email."); return }
        if (password.length < 6) { _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters."); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.signUp(email.trim(), password)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true, needsProfile = true)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Sign up failed. Please try again.")
                }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank()) { _uiState.value = _uiState.value.copy(error = "Please enter your email."); return }
        if (password.isBlank()) { _uiState.value = _uiState.value.copy(error = "Please enter your password."); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.signIn(email.trim(), password)
                .onSuccess {
                    val hasProfile = runCatching { repo.hasProfile() }.getOrDefault(false)
                    _uiState.value = _uiState.value.copy(isLoading = false, isAuthenticated = true, needsProfile = !hasProfile)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Sign in failed. Please try again.")
                }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
