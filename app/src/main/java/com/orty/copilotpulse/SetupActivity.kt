package com.orty.copilotpulse

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SetupActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        val tokenInput = findViewById<EditText>(R.id.token_input)
        val connectButton = findViewById<Button>(R.id.connect_button)
        val statusText = findViewById<TextView>(R.id.status_text)
        val errorBanner = findViewById<LinearLayout>(R.id.error_banner)

        if (TokenManager.hasCredentials(this)) {
            statusText.text = "Validating token..."
            statusText.setTextColor(0xFFAAAAAA.toInt())
            statusText.visibility = View.VISIBLE
            connectButton.text = "Update Token"
            TokenManager.getMaskedToken(this)?.let { tokenInput.hint = it }

            val appContext = applicationContext
            executor.execute {
                val result = ApiClient.fetchUsage(appContext)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    when (result.error) {
                        null -> {
                            statusText.text = "Connected ✔"
                            statusText.setTextColor(0xFF4CAF50.toInt())
                            errorBanner.visibility = View.GONE
                        }
                        "auth_error" -> showErrorState(statusText, errorBanner, tokenInput)
                        "rate_limited" -> {
                            statusText.text = "Connected ✔ (rate limited)"
                            statusText.setTextColor(0xFF4CAF50.toInt())
                            statusText.visibility = View.VISIBLE
                            errorBanner.visibility = View.GONE
                        }
                        else -> {
                            statusText.text = "Connected ✔ Could not verify (offline?)"
                            statusText.setTextColor(0xFFAAAAAA.toInt())
                            statusText.visibility = View.VISIBLE
                            errorBanner.visibility = View.GONE
                        }
                    }
                }
            }
        } else {
            val prefs = getSharedPreferences("copilot_pulse_cache", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auth_error", false)) {
                showErrorState(statusText, errorBanner, tokenInput)
            }
        }

        connectButton.setOnClickListener {
            val token = tokenInput.text.toString().trim()
            if (token.isEmpty()) {
                statusText.text = "Please paste a GitHub token"
                statusText.setTextColor(0xFFF44336.toInt())
                statusText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            connectButton.isEnabled = false
            statusText.text = "Connecting..."
            statusText.setTextColor(0xFFAAAAAA.toInt())
            statusText.visibility = View.VISIBLE

            val appContext = applicationContext
            executor.execute {
                val result = ApiClient.fetchUsageWithToken(appContext, token)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    if (result.error == null) {
                        TokenManager.saveToken(appContext, token)
                        ApiClient.cacheUsage(appContext, result)
                        clearAuthError(appContext)
                        statusText.text = "Connected ✔"
                        statusText.setTextColor(0xFF4CAF50.toInt())
                        connectButton.text = "Update Token"
                        connectButton.isEnabled = true
                        tokenInput.text.clear()
                        errorBanner.visibility = View.GONE
                        TokenManager.getMaskedToken(appContext)?.let { tokenInput.hint = it }
                        triggerWidgetUpdate()
                        ensurePeriodicRefresh()
                    } else {
                        val msg = when (result.error) {
                            "auth_error" -> "Invalid token. Check scopes and try again."
                            else -> "Connection failed: ${result.error}"
                        }
                        statusText.text = msg
                        statusText.setTextColor(0xFFF44336.toInt())
                        connectButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun showErrorState(
        statusText: TextView,
        errorBanner: LinearLayout,
        tokenInput: EditText
    ) {
        statusText.text = "Token expired — paste a new one below"
        statusText.setTextColor(0xFFF44336.toInt())
        statusText.visibility = View.VISIBLE
        errorBanner.visibility = View.VISIBLE
        TokenManager.getMaskedToken(this)?.let { tokenInput.hint = it }
    }

    private fun clearAuthError(context: Context) {
        context.getSharedPreferences("copilot_pulse_cache", Context.MODE_PRIVATE)
            .edit().putBoolean("auth_error", false).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun ensurePeriodicRefresh() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "copilot_pulse_periodic_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun triggerWidgetUpdate() {
        val mgr = AppWidgetManager.getInstance(this)
        val ids = mgr.getAppWidgetIds(ComponentName(this, PulseWidget::class.java))
        if (ids.isNotEmpty()) {
            sendBroadcast(Intent(this, PulseWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            })
        }
    }
}
