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

class AuthRepository {

    private val client = SupabaseClient.client

    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        runCatching {
            setOnlineStatus(false)
            client.auth.signOut()
        }
    }

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    fun isLoggedIn(): Boolean = client.auth.currentUserOrNull() != null

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
    }

    suspend fun getProfile(userId: String): Result<UserProfile> = runCatching {
        client.postgrest["profiles"]
            .select {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<UserProfile>()
            .first()
    }

    suspend fun uploadAvatar(userId: String, bytes: ByteArray, mimeType: String): Result<String> =
        runCatching {
            val bucket = client.storage["avatars"]
            val path = "$userId/avatar.jpg"
            bucket.upload(path, bytes) { upsert = true }
            bucket.publicUrl(path)
        }

    suspend fun uploadCover(userId: String, bytes: ByteArray, mimeType: String): Result<String> =
        runCatching {
            val bucket = client.storage["avatars"]
            val path = "$userId/cover.jpg"
            bucket.upload(path, bytes) { upsert = true }
            bucket.publicUrl(path)
        }

    suspend fun updateProfilePhoto(userId: String, avatarUrl: String?, coverUrl: String?): Result<Unit> =
        runCatching {
            val updates = buildMap<String, String?> {
                if (avatarUrl != null) put("avatar_url", avatarUrl)
                if (coverUrl != null) put("cover_url", coverUrl)
            }
            client.postgrest["profiles"].update(updates) {
                filter { eq("id", userId) }
            }
        }

    suspend fun setOnlineStatus(isOnline: Boolean): Result<Unit> = runCatching {
        val uid = currentUserId() ?: return@runCatching
        val now = kotlinx.datetime.Clock.System.now().toString()
        client.postgrest["profiles"].update(
            buildJsonObject {
                put("is_online", isOnline)
                put("last_seen", now)
            }
        ) {
            filter { eq("id", uid) }
        }
    }
}
