package com.example.review

import android.content.Context
import java.time.Instant
import java.time.LocalDate

interface NightlyReviewStore {
    fun isEnabled(): Boolean
    fun setEnabled(enabled: Boolean)
    fun latest(): NightlyReview?
    fun save(review: NightlyReview)
    fun status(): String?
    fun recordStatus(status: String)
    fun feedback(date: LocalDate): NightlyReviewFeedback?
    fun recordFeedback(date: LocalDate, feedback: NightlyReviewFeedback)
    fun feeling(date: LocalDate): NightlyFeeling?
    fun recordFeeling(date: LocalDate, feeling: NightlyFeeling)
}

class SharedPreferencesNightlyReviewStore(context: Context) : NightlyReviewStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun isEnabled(): Boolean = preferences.getBoolean(ENABLED_KEY, false)

    override fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(ENABLED_KEY, enabled).apply()
    }

    override fun latest(): NightlyReview? = runCatching {
        val date = LocalDate.parse(preferences.getString(LATEST_DATE_KEY, null) ?: return null)
        NightlyReview(
            date = date,
            generatedAt = Instant.parse(preferences.getString(LATEST_GENERATED_AT_KEY, null) ?: return null),
            summary = preferences.getString(LATEST_SUMMARY_KEY, null) ?: return null,
            facts = decode(preferences.getString(LATEST_FACTS_KEY, null)),
            gaps = decode(preferences.getString(LATEST_GAPS_KEY, null)),
            nextActions = decode(preferences.getString(LATEST_ACTIONS_KEY, null))
        )
    }.getOrNull()

    override fun save(review: NightlyReview) {
        preferences.edit()
            .putString(LATEST_DATE_KEY, review.date.toString())
            .putString(LATEST_GENERATED_AT_KEY, review.generatedAt.toString())
            .putString(LATEST_SUMMARY_KEY, review.summary)
            .putString(LATEST_FACTS_KEY, encode(review.facts))
            .putString(LATEST_GAPS_KEY, encode(review.gaps))
            .putString(LATEST_ACTIONS_KEY, encode(review.nextActions))
            .apply()
    }

    override fun status(): String? = preferences.getString(STATUS_KEY, null)

    override fun recordStatus(status: String) {
        preferences.edit().putString(STATUS_KEY, status).apply()
    }

    override fun feedback(date: LocalDate): NightlyReviewFeedback? =
        preferences.getString("$FEEDBACK_PREFIX$date", null)
            ?.let { runCatching { NightlyReviewFeedback.valueOf(it) }.getOrNull() }

    override fun recordFeedback(date: LocalDate, feedback: NightlyReviewFeedback) {
        preferences.edit().putString("$FEEDBACK_PREFIX$date", feedback.name).apply()
    }

    override fun feeling(date: LocalDate): NightlyFeeling? =
        preferences.getString("$FEELING_PREFIX$date", null)
            ?.let { runCatching { NightlyFeeling.valueOf(it) }.getOrNull() }

    override fun recordFeeling(date: LocalDate, feeling: NightlyFeeling) {
        preferences.edit().putString("$FEELING_PREFIX$date", feeling.name).apply()
    }

    private fun encode(values: List<String>): String = values.joinToString(SEPARATOR)

    private fun decode(value: String?): List<String> = value
        ?.takeIf { it.isNotEmpty() }
        ?.split(SEPARATOR)
        .orEmpty()

    companion object {
        private const val PREFERENCES_NAME = "nightly_health_review"
        private const val ENABLED_KEY = "enabled"
        private const val STATUS_KEY = "status"
        private const val LATEST_DATE_KEY = "latest_date"
        private const val LATEST_GENERATED_AT_KEY = "latest_generated_at"
        private const val LATEST_SUMMARY_KEY = "latest_summary"
        private const val LATEST_FACTS_KEY = "latest_facts"
        private const val LATEST_GAPS_KEY = "latest_gaps"
        private const val LATEST_ACTIONS_KEY = "latest_actions"
        private const val FEEDBACK_PREFIX = "feedback_"
        private const val FEELING_PREFIX = "feeling_"
        private const val SEPARATOR = "\u001F"
    }
}
