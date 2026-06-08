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
    }.mapError()

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
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
        }

    // Convert raw Supabase exceptions into friendly messages
    private fun <T> Result<T>.mapError(): Result<T> = this.recoverCatching { e ->
        val msg = e.message ?: "Unknown error"
        val friendly = when {
            "Invalid login credentials" in msg -> "Incorrect email or password."
            "Email not confirmed" in msg -> "Please verify your email before signing in."
            "User already registered" in msg -> "An account with this email already exists."
            "Password should be at least 6" in msg -> "Password must be at least 6 characters."
            "Unable to validate email" in msg -> "Please enter a valid email address."
            "network" in msg.lowercase() || "connect" in msg.lowercase() -> "No internet connection. Please check your network."
            "timeout" in msg.lowercase() -> "Request timed out. Please try again."
            "storage" in msg.lowercase() || "bucket" in msg.lowercase() -> "Failed to upload image. Check your connection and try again."
            else -> msg
        }
        throw Exception(friendly)
    }
}
