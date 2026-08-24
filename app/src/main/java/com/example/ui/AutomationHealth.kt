package com.example.ui

enum class AutomationHealthState {
    READY,
    ATTENTION_REQUIRED,
    PAUSED
}

object AutomationHealth {
    fun evaluate(
        automaticExportEnabled: Boolean,
        nightlyReviewEnabled: Boolean,
        folderConfigured: Boolean,
        backgroundReadAvailable: Boolean,
        backgroundReadGranted: Boolean,
        automaticStatus: String?,
        nightlyStatus: String?
    ): AutomationHealthState {
        if (!automaticExportEnabled && !nightlyReviewEnabled) return AutomationHealthState.PAUSED
        val technicalFailure = listOfNotNull(automaticStatus, nightlyStatus).any { status ->
            status.contains("fall", ignoreCase = true) || status.contains("deten", ignoreCase = true)
        }
        return if (
            !automaticExportEnabled || !nightlyReviewEnabled || !folderConfigured ||
            !backgroundReadAvailable || !backgroundReadGranted || technicalFailure
        ) {
            AutomationHealthState.ATTENTION_REQUIRED
        } else {
            AutomationHealthState.READY
        }
    }
}
