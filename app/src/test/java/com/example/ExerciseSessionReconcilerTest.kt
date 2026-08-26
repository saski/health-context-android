package com.example

import androidx.health.connect.client.records.ExerciseSessionRecord
import com.example.data.repository.ExerciseSessionReconciler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExerciseSessionReconcilerTest {
    @Test
    fun `merges a mirrored Zepp and Google Fit walk and keeps the Zepp source`() {
        val sessions = ExerciseSessionReconciler.reconcile(
            listOf(
                session("zepp", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "10:06", "10:47", ZEPPA),
                session("fit", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "10:09", "10:44", GOOGLE_FIT)
            )
        )

        assertEquals(1, sessions.size)
        assertEquals("zepp", sessions.single().candidate.value)
        assertEquals(setOf(GOOGLE_FIT), sessions.single().excludedDuplicateSources)
    }

    @Test
    fun `keeps a concurrent FitOn workout distinct from a walk`() {
        val sessions = ExerciseSessionReconciler.reconcile(
            listOf(
                session("walk", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "10:06", "10:47", ZEPPA),
                session("fiton", ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, "10:06", "10:09", FITON)
            )
        )

        assertEquals(setOf("walk", "fiton"), sessions.map { it.candidate.value }.toSet())
    }

    @Test
    fun `keeps adjacent records distinct even when their source and type match`() {
        val sessions = ExerciseSessionReconciler.reconcile(
            listOf(
                session("zepp", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "16:12", "16:23", ZEPPA),
                session("fit", ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "16:23", "16:44", GOOGLE_FIT)
            )
        )

        assertEquals(2, sessions.size)
        assertTrue(sessions.all { it.excludedDuplicateSources.isEmpty() })
    }

    private fun session(
        value: String,
        exerciseType: Int,
        start: String,
        end: String,
        sourcePackage: String
    ) = ExerciseSessionReconciler.Candidate(
        value = value,
        recordId = value,
        exerciseType = exerciseType,
        startTime = Instant.parse("2026-08-25T${start}:00Z"),
        endTime = Instant.parse("2026-08-25T${end}:00Z"),
        sourcePackage = sourcePackage
    )

    private companion object {
        const val ZEPPA = "com.huami.watch.hmwatchmanager"
        const val GOOGLE_FIT = "com.google.android.apps.fitness"
        const val FITON = "com.fiton.android"
    }
}
