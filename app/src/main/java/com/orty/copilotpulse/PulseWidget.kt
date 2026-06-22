package com.orty.copilotpulse

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PulseWidget : AppWidgetProvider() {

    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        private const val COLOR_BLUE = 0xFF0969da.toInt()
        private const val COLOR_YELLOW = 0xFFd4a017.toInt()
        private const val COLOR_RED = 0xFFcf222e.toInt()
        private const val ACTION_REFRESH = "com.orty.copilotpulse.ACTION_REFRESH"
        private const val WORK_NAME = "copilot_pulse_periodic_refresh"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PulseWidget::class.java))
            onUpdate(context, mgr, ids)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueuePeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        enqueuePeriodicRefresh(context)
        for (id in appWidgetIds) {
            renderWidget(context, appWidgetManager, id)
            scheduleRefresh(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        renderWidget(context, appWidgetManager, appWidgetId)
    }

    private fun enqueuePeriodicRefresh(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work
        )
    }

    private fun renderWidget(context: Context, mgr: AppWidgetManager, appWidgetId: Int) {
        val data = loadCachedData(context) ?: UsageData.placeholder()
        val prefs = context.getSharedPreferences("copilot_pulse_cache", Context.MODE_PRIVATE)
        val hasAuthError = prefs.getBoolean("auth_error", false)
        val options = mgr.getAppWidgetOptions(appWidgetId)
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val isCompact = minWidth < 200

        val views = if (isCompact) buildCompactViews(context, data)
        else buildFullViews(context, data, hasAuthError)

        if (!isCompact) {
            if (hasAuthError) {
                val setupPi = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, SetupActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, setupPi)
            }
            val refreshPi = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, PulseWidget::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.refresh_button, refreshPi)

            val copilotPi = PendingIntent.getActivity(
                context, 2,
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/features/copilot")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.copilot_button, copilotPi)
        }

        mgr.updateAppWidget(appWidgetId, views)
    }

    private fun scheduleRefresh(context: Context, mgr: AppWidgetManager, appWidgetId: Int) {
        try {
            executor.execute {
                try {
                    val data = ApiClient.fetchUsage(context)
                    if (data.error == null) {
                        ApiClient.cacheUsage(context, data)
                        renderWidget(context, mgr, appWidgetId)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun getColor(pct: Int): Int = when {
        pct >= 90 -> COLOR_RED
        pct >= 75 -> COLOR_YELLOW
        else -> COLOR_BLUE
    }

    private fun setBarTint(views: RemoteViews, barId: Int, pct: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                barId, "setProgressTintList",
                ColorStateList.valueOf(getColor(pct))
            )
        }
    }

    private fun buildFullViews(
        context: Context,
        data: UsageData,
        hasAuthError: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        if (hasAuthError) {
            views.setTextViewText(R.id.updated_ago, "Token expired · Tap to fix")
            views.setTextColor(R.id.updated_ago, COLOR_RED)
            views.setViewVisibility(R.id.row_0, View.VISIBLE)
            views.setTextViewText(R.id.label_0, "Premium")
            views.setProgressBar(R.id.bar_0, 100, 0, false)
            views.setTextViewText(R.id.pct_0, "—")
            views.setTextColor(R.id.pct_0, COLOR_RED)
            views.setTextViewText(R.id.reset_0, "")
            views.setViewVisibility(R.id.row_1, View.GONE)
            views.setViewVisibility(R.id.row_2, View.GONE)
            return views
        }

        views.setTextViewText(R.id.updated_ago, formatTimeSince(data.cachedAt))
        views.setTextColor(R.id.updated_ago, 0x80FFFFFF.toInt())

        val rows = listOf(
            intArrayOf(R.id.row_0, R.id.label_0, R.id.bar_0, R.id.pct_0, R.id.reset_0),
            intArrayOf(R.id.row_1, R.id.label_1, R.id.bar_1, R.id.pct_1, R.id.reset_1),
            intArrayOf(R.id.row_2, R.id.label_2, R.id.bar_2, R.id.pct_2, R.id.reset_2)
        )

        for (i in rows.indices) {
            val ids = rows[i]
            val quota = data.quotas.getOrNull(i)
            if (quota == null) {
                views.setViewVisibility(ids[0], View.GONE)
                continue
            }
            val pct = quota.percentUsed.toInt().coerceIn(0, 100)
            views.setViewVisibility(ids[0], View.VISIBLE)
            views.setTextViewText(ids[1], quota.label)
            views.setProgressBar(ids[2], 100, pct, false)
            views.setTextViewText(ids[3], "$pct%")
            views.setTextColor(ids[3], getColor(pct))
            setBarTint(views, ids[2], pct)
            views.setTextViewText(ids[4], formatResetTime(quota.resetDateUtc))
        }

        return views
    }

    private fun buildCompactViews(context: Context, data: UsageData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_layout_small)

        val rows = listOf(
            intArrayOf(R.id.row_0, R.id.bar_0, R.id.pct_0),
            intArrayOf(R.id.row_1, R.id.bar_1, R.id.pct_1),
            intArrayOf(R.id.row_2, R.id.bar_2, R.id.pct_2)
        )

        for (i in rows.indices) {
            val ids = rows[i]
            val quota = data.quotas.getOrNull(i)
            if (quota == null) {
                views.setViewVisibility(ids[0], View.GONE)
                continue
            }
            val pct = quota.percentUsed.toInt().coerceIn(0, 100)
            views.setViewVisibility(ids[0], View.VISIBLE)
            views.setProgressBar(ids[1], 100, pct, false)
            views.setTextViewText(ids[2], "$pct%")
            views.setTextColor(ids[2], getColor(pct))
            setBarTint(views, ids[1], pct)
        }

        return views
    }

    private fun formatResetTime(isoTime: String?): String {
        if (isoTime.isNullOrEmpty() || isoTime == "null") return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val cleaned = isoTime
                .replace(Regex("[+-]\\d{2}:\\d{2}$"), "")
                .replace("Z", "")
                .replace(".000", "")
            val resetDate = sdf.parse(cleaned) ?: return ""
            val diffMs = resetDate.time - System.currentTimeMillis()
            if (diffMs <= 0) return "Resetting..."
            val hours = (diffMs / 3_600_000).toInt()
            val minutes = ((diffMs % 3_600_000) / 60_000).toInt()
            if (hours >= 24) {
                val days = hours / 24
                "Resets in ${days}d ${hours % 24}h"
            } else {
                "Resets in ${hours}h ${minutes}m"
            }
        } catch (e: Exception) { "" }
    }

    private fun formatTimeSince(isoTime: String?): String {
        if (isoTime.isNullOrEmpty() || isoTime == "null") return "Updated just now"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val cached = sdf.parse(isoTime) ?: return "Updated just now"
            val diffMs = System.currentTimeMillis() - cached.time
            val minutes = (diffMs / 60_000).toInt()
            when {
                minutes < 1 -> "Updated just now"
                minutes < 60 -> "Updated ${minutes}m ago"
                else -> "Updated ${minutes / 60}h ago"
            }
        } catch (e: Exception) { "Updated just now" }
    }

    private fun loadCachedData(context: Context): UsageData? {
        val prefs = context.getSharedPreferences("copilot_pulse_cache", Context.MODE_PRIVATE)
        val count = prefs.getInt("quota_count", -1)
        if (count < 0) return null
        val quotas = (0 until count).map { i ->
            QuotaItem(
                id = prefs.getString("quota_${i}_id", "") ?: "",
                label = prefs.getString("quota_${i}_label", "") ?: "",
                percentUsed = prefs.getFloat("quota_${i}_pct_used", 0f).toDouble(),
                remaining = prefs.getInt("quota_${i}_remaining", 0),
                entitlement = prefs.getInt("quota_${i}_entitlement", 0),
                overageCount = prefs.getInt("quota_${i}_overage", 0),
                overagePermitted = false,
                resetDateUtc = prefs.getString("reset_date", null)
            )
        }
        return UsageData(
            quotas = quotas,
            resetDateUtc = prefs.getString("reset_date", null),
            cachedAt = prefs.getString("cached_at", null)
        )
    }
}
