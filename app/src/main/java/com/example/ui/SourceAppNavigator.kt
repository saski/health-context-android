package com.example.ui

import android.content.Context
import android.content.Intent

object SourcePackagePolicy {
    private val technicalPackages = setOf(
        "com.android.healthconnect.controller",
        "com.google.android.apps.healthdata"
    )

    fun actionable(sourcePackages: Set<String>, installedPackages: Set<String>): List<String> {
        val installed = sourcePackages.intersect(installedPackages).sorted()
        val userFacing = installed.filterNot(technicalPackages::contains)
        return userFacing.ifEmpty { installed }
    }
}

class SourceAppNavigator(private val context: Context) {
    fun open(sourcePackages: Set<String>): Boolean {
        val launchIntents = sourcePackages.mapNotNull { packageName ->
            context.packageManager.getLaunchIntentForPackage(packageName)?.let { packageName to it }
        }
        val allowedPackages = SourcePackagePolicy.actionable(
            sourcePackages,
            launchIntents.map { it.first }.toSet()
        )
        val intents = launchIntents
            .filter { it.first in allowedPackages }
            .map { it.second.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intents.isEmpty()) return false

        val intent = if (intents.size == 1) {
            intents.first()
        } else {
            Intent.createChooser(intents.first(), "Abrir fuente de datos").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        return true
    }
}
