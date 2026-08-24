package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.review.NightlyReview
import com.example.review.NightlyReviewFeedback
import com.example.review.NightlyFeeling
import com.example.review.SharedPreferencesNightlyReviewStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NightlyReviewStoreTest {
    private lateinit var context: Context

    @Before
    fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("nightly_health_review", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `persists the latest review and local usefulness feedback`() {
        val store = SharedPreferencesNightlyReviewStore(context)
        val review = NightlyReview(
            date = LocalDate.of(2026, 8, 20),
            generatedAt = Instant.parse("2026-08-20T20:30:00Z"),
            summary = "Resumen factual",
            facts = listOf("Pasos observados"),
            gaps = listOf("Sueño no disponible"),
            nextActions = listOf("Decide según tus sensaciones")
        )

        store.save(review)
        store.recordFeedback(review.date, NightlyReviewFeedback.USEFUL)
        store.recordFeeling(review.date, NightlyFeeling.GOOD)

        assertEquals(review, store.latest())
        assertEquals(NightlyReviewFeedback.USEFUL, store.feedback(review.date))
        assertEquals(NightlyFeeling.GOOD, store.feeling(review.date))
    }
}
