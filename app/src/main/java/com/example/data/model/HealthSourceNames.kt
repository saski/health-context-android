package com.example.data.model

/** Human-readable source names while retaining package IDs separately for provenance. */
object HealthSourceNames {
    private val packagePattern = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")

    fun display(rawSources: String): String = rawSources
        .split(',')
        .map(String::trim)
        .filter(String::isNotBlank)
        .map(::displaySingle)
        .distinct()
        .joinToString(", ")
        .ifBlank { rawSources }

    fun packageIds(rawSources: String): List<String> = rawSources
        .split(',')
        .map(String::trim)
        .filter { it.matches(packagePattern) }
        .distinct()

    fun replacePackageIds(text: String): String = packageNames.entries.fold(text) { result, (packageName, label) ->
        result.replace(packageName, label)
    }

    private fun displaySingle(source: String): String = when {
        source == "com.huami.watch.hmwatchmanager" -> "Zepp / Amazfit"
        source == "com.google.android.apps.fitness" -> "Google Fit"
        source == "com.fiton.android" -> "FitOn"
        source == "com.nothing.smartcenter" -> "Nothing X"
        source.startsWith("com.android.healthconnect") -> "Health Connect"
        else -> packageNames[source] ?: source
    }

    private val packageNames = linkedMapOf(
        "com.huami.watch.hmwatchmanager" to "Zepp / Amazfit",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.fiton.android" to "FitOn",
        "com.nothing.smartcenter" to "Nothing X"
    )
}
