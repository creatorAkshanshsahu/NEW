package com.akshansh.aktv

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One shared client = connection reuse + faster repeat requests. */
object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Blocking – always call from Dispatchers.IO. */
    fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "AK-TV/1.3 (Android TV app)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string() ?: error("Empty response")
        }
    }
}
