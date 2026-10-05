package com.example.data.repository

import android.util.Log
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AuthRepository {
  suspend fun signIn(email: String, password: String): Result<String>
  suspend fun signUp(email: String, password: String): Result<String>
  suspend fun signOut(): Result<Unit>
  fun getCurrentUserEmail(): String?
  fun getCurrentUserId(): String?
  fun isUserSignedIn(): Boolean
  suspend fun checkSession(): Boolean
}

class SupabaseAuthRepository : AuthRepository {
  private val TAG = "AuthRepository"

  override suspend fun signIn(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet. Please provide SUPABASE_URL and SUPABASE_ANON_KEY in your settings.")
        )
      }

      val supabase = SupabaseClientProvider.client
      supabase.auth.signInWith(Email) {
        this.email = email.trim()
        this.password = password
      }

      val currentUser = supabase.auth.currentUserOrNull()
      val userEmail = currentUser?.email ?: email.trim()
      Result.success(userEmail)
    } catch (e: Exception) {
      Log.e(TAG, "Sign in failed", e)
      Result.failure(e)
    }
  }

  override suspend fun signUp(email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) {
        return@withContext Result.failure(
          IllegalStateException("Supabase is not configured yet. Please provide SUPABASE_URL and SUPABASE_ANON_KEY in your settings.")
        )
      }

      val supabase = SupabaseClientProvider.client
      supabase.auth.signUpWith(Email) {
        this.email = email.trim()
        this.password = password
      }

      val currentUser = supabase.auth.currentUserOrNull()
      val userEmail = currentUser?.email ?: email.trim()
      Result.success(userEmail)
    } catch (e: Exception) {
      Log.e(TAG, "Sign up failed", e)
      Result.failure(e)
    }
  }

  override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (SupabaseClientProvider.isConfigured()) {
        SupabaseClientProvider.client.auth.signOut()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Sign out failed", e)
      Result.failure(e)
    }
  }

  override fun getCurrentUserEmail(): String? {
    return try {
      if (SupabaseClientProvider.isConfigured()) {
        SupabaseClientProvider.client.auth.currentUserOrNull()?.email
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  override fun getCurrentUserId(): String? {
    return try {
      if (SupabaseClientProvider.isConfigured()) {
        SupabaseClientProvider.client.auth.currentUserOrNull()?.id
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }

  override fun isUserSignedIn(): Boolean {
    return try {
      if (SupabaseClientProvider.isConfigured()) {
        SupabaseClientProvider.client.auth.currentSessionOrNull() != null
      } else {
        false
      }
    } catch (e: Exception) {
      false
    }
  }

  override suspend fun checkSession(): Boolean = withContext(Dispatchers.IO) {
    try {
      if (!SupabaseClientProvider.isConfigured()) return@withContext false
      val session = SupabaseClientProvider.client.auth.currentSessionOrNull()
      session != null
    } catch (e: Exception) {
      false
    }
  }
}
