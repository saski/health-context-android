package com.example.review

import java.time.Instant
import java.time.LocalDate

data class NightlyReview(
    val date: LocalDate,
    val generatedAt: Instant,
    val summary: String,
    val facts: List<String>,
    val gaps: List<String>,
    val nextActions: List<String>
) {
    fun renderPlainText(): String = buildList {
        add(summary)
        addAll(facts)
        addAll(gaps)
        addAll(nextActions)
    }.joinToString("\n")
}

enum class NightlyReviewFeedback {
    USEFUL,
    NOT_USEFUL
}
