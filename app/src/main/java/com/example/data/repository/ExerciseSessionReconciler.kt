package com.example.data.repository

import java.time.Duration
import java.time.Instant

/**
 * Reconciles raw Health Connect sessions that are near-identical mirrors from
 * different apps. Health Connect keeps those source records intentionally;
 * this class only prevents the product from counting a mirrored activity twice.
 */
internal object ExerciseSessionReconciler {
    private const val ZEPPA_AMAZFIT_PACKAGE = "com.huami.watch.hmwatchmanager"
    private const val MINIMUM_SHARED_DURATION = 0.80

    fun <T> reconcile(candidates: List<Candidate<T>>): List<Reconciled<T>> {
        val groups = mutableListOf<MutableList<Candidate<T>>>()

        candidates
            .sortedWith(compareBy<Candidate<T>> { it.startTime }.thenBy { it.recordId })
            .forEach { candidate ->
                val matchingGroups = groups.filter { group ->
                    group.any { existing -> areMirrored(candidate, existing) }
                }
                when (matchingGroups.size) {
                    0 -> groups += mutableListOf(candidate)
                    else -> {
                        val mergedGroup = matchingGroups.first()
                        mergedGroup += candidate
                        matchingGroups.drop(1).forEach { group ->
                            mergedGroup += group
                            groups.remove(group)
                        }
                    }
                }
            }

        return groups
            .map { group ->
                val canonical = group.minWithOrNull(canonicalComparator())!!
                Reconciled(
                    candidate = canonical,
                    excludedDuplicateSources = group
                        .filterNot { it.recordId == canonical.recordId }
                        .map { it.sourcePackage }
                        .filter { it.isNotBlank() && it != canonical.sourcePackage }
                        .toSortedSet()
                )
            }
            .sortedWith(compareBy<Reconciled<T>> { it.candidate.startTime }.thenBy { it.candidate.recordId })
    }

    private fun <T> areMirrored(first: Candidate<T>, second: Candidate<T>): Boolean {
        if (first.sourcePackage.isBlank() || second.sourcePackage.isBlank()) return false
        if (first.sourcePackage == second.sourcePackage) return false
        if (first.exerciseType != second.exerciseType) return false

        val sharedStart = maxOf(first.startTime, second.startTime)
        val sharedEnd = minOf(first.endTime, second.endTime)
        val shared = Duration.between(sharedStart, sharedEnd).toMillis()
        val shorter = minOf(
            Duration.between(first.startTime, first.endTime).toMillis(),
            Duration.between(second.startTime, second.endTime).toMillis()
        )
        return shared > 0 && shorter > 0 && shared.toDouble() / shorter >= MINIMUM_SHARED_DURATION
    }

    private fun <T> canonicalComparator(): Comparator<Candidate<T>> = compareBy<Candidate<T>> {
        it.sourcePackage != ZEPPA_AMAZFIT_PACKAGE
    }.thenByDescending {
        Duration.between(it.startTime, it.endTime).toMillis()
    }.thenBy { it.startTime }.thenBy { it.recordId }

    data class Candidate<T>(
        val value: T,
        val recordId: String,
        val exerciseType: Int,
        val startTime: Instant,
        val endTime: Instant,
        val sourcePackage: String
    )

    data class Reconciled<T>(
        val candidate: Candidate<T>,
        val excludedDuplicateSources: Set<String>
    )
}
