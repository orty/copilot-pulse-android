package com.orty.copilotpulse

import org.json.JSONObject

data class QuotaItem(
    val id: String,
    val label: String,
    val percentUsed: Double,
    val remaining: Int,
    val entitlement: Int,
    val overageCount: Int,
    val overagePermitted: Boolean,
    val resetDateUtc: String?
)

data class UsageData(
    val quotas: List<QuotaItem>,
    val resetDateUtc: String?,
    val cachedAt: String?,
    val error: String? = null
) {
    companion object {

        private val LABEL_MAP = mapOf(
            "premium_interactions" to "Premium"
        )

        private fun humanize(id: String): String =
            LABEL_MAP[id] ?: id.split("_").joinToString(" ") { w ->
                w.replaceFirstChar { it.uppercaseChar() }
            }

        fun fromJson(json: JSONObject, resetDate: String?): UsageData {
            val snapshots = json.optJSONObject("quota_snapshots") ?: JSONObject()
            val quotas = mutableListOf<QuotaItem>()
            val keys = snapshots.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val snap = snapshots.optJSONObject(key) ?: continue
                if (snap.optBoolean("unlimited", false)) continue
                val percentRemaining = snap.optDouble("percent_remaining", 100.0)
                val percentUsed = 100.0 - percentRemaining
                quotas.add(
                    QuotaItem(
                        id = key,
                        label = humanize(key),
                        percentUsed = percentUsed,
                        remaining = snap.optInt("remaining", 0),
                        entitlement = snap.optInt("entitlement", 0),
                        overageCount = snap.optInt("overage_count", 0),
                        overagePermitted = snap.optBoolean("overage_permitted", false),
                        resetDateUtc = resetDate
                    )
                )
            }
            // premium_interactions first, then alphabetically
            quotas.sortWith(compareBy({ if (it.id == "premium_interactions") 0 else 1 }, { it.id }))
            return UsageData(quotas = quotas, resetDateUtc = resetDate, cachedAt = null)
        }

        fun placeholder(): UsageData = UsageData(
            quotas = emptyList(),
            resetDateUtc = null,
            cachedAt = null,
            error = "No data yet"
        )
    }
}
