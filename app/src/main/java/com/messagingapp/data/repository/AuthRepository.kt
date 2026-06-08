package com.messagingapp.data.repository

import com.messagingapp.SupabaseClient
import com.messagingapp.data.models.UserProfile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.Clock

class AuthRepository {

    private val client = SupabaseClient.client

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }.mapError()

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Unit
    }.mapError()

    suspend fun signOut() {
        runCatching { setOnlineStatus(false) }
        runCatching { client.auth.signOut() }
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun isLoggedIn(): Boolean = client.auth.currentUserOrNull() != null

    suspend fun setOnlineStatus(isOnline: Boolean) {
        val uid = currentUserId() ?: return
        runCatching {
            val now = Clock.System.now().toString()
            client.postgrest["profiles"].update(
                buildJsonObject {
                    put("is_online", isOnline)
                    put("last_seen", now)
                }
            ) { filter { eq("id", uid) } }
        }
    }

    suspend fun hasProfile(): Boolean {
        val uid = currentUserId() ?: return false
        return runCatching {
            client.postgrest["profiles"]
                .select(Columns.raw("id")) {
                    filter { eq("id", uid) }
                    limit(1)
                }
                .decodeList<UserProfile>()
                .isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun createProfile(nickname: String, visibility: String): Result<Unit> = runCatching {
        val uid = currentUserId() ?: throw Exception("Not authenticated")
        val randomNumbers = (10000..99999).random()
        val username = "${nickname.lowercase().replace(" ", "_")}$randomNumbers"
        val profile = UserProfile(
            id = uid,
            nickname = nickname,
            username = username,
            visibility = visibility,
            isOnline = true
        )
        client.postgrest["profiles"].upsert(profile)
        Unit
    }.mapError()

    suspend fun getProfile(userId: String): Result<UserProfile> = runCatching {
        client.postgrest["profiles"]
            .select {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<UserProfile>()
            .first()
    }

    suspend fun uploadAvatar(userId: String, bytes: ByteArray): Result<String> =
        runCatching {
            val bucket = client.storage["avatars"]
            val path = "$userId/avatar.jpg"
            bucket.upload(path, bytes) { upsert = true }
            bucket.publicUrl(path)
        }.mapError()

    suspend fun uploadCover(userId: String, bytes: ByteArray): Result<String> =
        runCatching {
            val bucket = client.storage["avatars"]
            val path = "$userId/cover.jpg"
            bucket.upload(path, bytes) { upsert = true }
            bucket.publicUrl(path)
        }.mapError()

    suspend fun updateProfilePhoto(userId: String, avatarUrl: String?, coverUrl: String?): Result<Unit> =
        runCatching {
            val updates = buildJsonObject {
                if (avatarUrl != null) put("avatar_url", avatarUrl)
                if (coverUrl != null) put("cover_url", coverUrl)
            }
            client.postgrest["profiles"].update(updates) {
                filter { eq("id", userId) }
            }
            Unit
        }

    // Convert raw Supabase / network exceptions into friendly messages.
    // AuthViewModel does an up-front network check before calling these, so most
    // network errors are already caught. This handles any remaining edge cases.
    private fun <T> Result<T>.mapError(): Result<T> = this.recoverCatching { e ->
        val msg = e.message ?: "Unknown error"
        val lo  = msg.lowercase()
        val friendly = when {
            "network"  in lo || "connect" in lo ||
            "unable to resolve" in lo || "unreachable" in lo ->
                "No internet connection. Please turn on Wi-Fi or mobile data and try again."
            "timeout"  in lo || "timed out" in lo ->
                "The request timed out. Please check your connection and try again."
            "invalid login" in lo || "invalid credentials" in lo ->
                "Incorrect email or password. Please check and try again."
            "email not confirmed" in lo || "not verified" in lo ->
                "Please verify your email address before signing in."
            "user already registered" in lo || "already registered" in lo ->
                "An account with this email already exists. Try signing in instead."
            "password should be" in lo || "password must be" in lo ->
                "Password must be at least 6 characters."
            "unable to validate email" in lo || "invalid email" in lo ->
                "Please enter a valid email address."
            "rate limit" in lo || "too many requests" in lo ->
                "Too many attempts. Please wait a moment and try again."
            "storage" in lo || "bucket" in lo ->
                "Failed to upload image. Please check your connection and try again."
            else ->
                "Something went wrong. Please try again."
        }
        throw Exception(friendly)
    }
}
