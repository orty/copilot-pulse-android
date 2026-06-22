package com.orty.copilotpulse

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object ApiClient {

    private const val API_URL = "https://api.github.com/copilot_internal/user"

    fun fetchUsage(context: Context): UsageData {
        val token = TokenManager.getToken(context)
            ?: return UsageData.placeholder().copy(error = "auth_error")
        return callApi(token)
    }

    fun fetchUsageWithToken(context: Context, token: String): UsageData =
        callApi(token)

    fun cacheUsage(context: Context, data: UsageData) {
        val prefs = context.getSharedPreferences("copilot_pulse_cache", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putInt("quota_count", data.quotas.size)
        data.quotas.forEachIndexed { i, q ->
            editor.putString("quota_${i}_id", q.id)
            editor.putString("quota_${i}_label", q.label)
            editor.putFloat("quota_${i}_pct_used", q.percentUsed.toFloat())
            editor.putInt("quota_${i}_remaining", q.remaining)
            editor.putInt("quota_${i}_entitlement", q.entitlement)
            editor.putInt("quota_${i}_overage", q.overageCount)
        }
        editor.putString("reset_date", data.resetDateUtc)
        editor.putString("cached_at", data.cachedAt)
        editor.apply()
    }

    private fun callApi(token: String): UsageData {
        val url = URL(API_URL)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "CopilotPulse/1.0.0")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                val resetDate = json.optString("quota_reset_date_utc", null)
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                UsageData.fromJson(json, resetDate).copy(cachedAt = now)
            } else {
                val errorType = when (conn.responseCode) {
                    401, 403 -> "auth_error"
                    429 -> "rate_limited"
                    else -> "HTTP ${conn.responseCode}"
                }
                UsageData.placeholder().copy(error = errorType)
            }
        } catch (e: Exception) {
            UsageData.placeholder().copy(error = "Offline")
        } finally {
            conn.disconnect()
        }
    }
}
