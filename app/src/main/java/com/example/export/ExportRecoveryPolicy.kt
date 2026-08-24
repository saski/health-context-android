package com.example.export

import java.time.LocalDate

object ExportRecoveryPolicy {
    private const val RECOVERY_DAYS = 7

    fun datesToExport(
        today: LocalDate,
        existingArchiveDates: Set<LocalDate>,
        recoveryDays: Int = RECOVERY_DAYS
    ): List<LocalDate> {
        require(recoveryDays > 0) { "recoveryDays must be positive" }
        val yesterday = today.minusDays(1)
        val missing = (recoveryDays.toLong() downTo 1L)
            .map(today::minusDays)
            .filterNot(existingArchiveDates::contains)
            .filterNot { it == yesterday }
        return missing + yesterday
    }
}

object DailyContextArchiveDates {
    private val canonicalName = Regex("^health-context-(\\d{4}-\\d{2}-\\d{2})\\.md$")

    fun parse(fileNames: Iterable<String>): Set<LocalDate> = fileNames.mapNotNull { fileName ->
        canonicalName.matchEntire(fileName)
            ?.groupValues
            ?.get(1)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }.toSet()
}
