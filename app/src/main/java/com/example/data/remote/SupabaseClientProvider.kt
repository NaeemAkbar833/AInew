package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClientProvider {
  private const val TAG = "SupabaseClientProvider"

  val client: SupabaseClient by lazy {
    val url = BuildConfig.SUPABASE_URL.ifBlank { "https://placeholder.supabase.co" }
    val anonKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "placeholder_anon_key" }

    Log.d(TAG, "Initializing SupabaseClient with url=$url")

    createSupabaseClient(
      supabaseUrl = url,
      supabaseKey = anonKey
    ) {
      install(Auth)
      install(Postgrest)
      install(Storage)
    }
  }

  fun isConfigured(): Boolean {
    val url = BuildConfig.SUPABASE_URL
    val anonKey = BuildConfig.SUPABASE_ANON_KEY
    return url.isNotBlank() &&
        !url.contains("placeholder") &&
        anonKey.isNotBlank() &&
        !anonKey.contains("placeholder")
  }
}
