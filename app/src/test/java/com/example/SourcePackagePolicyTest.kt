package com.example

import com.example.ui.SourcePackagePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class SourcePackagePolicyTest {
    @Test
    fun `prefers installed user facing sources over Health Connect plumbing`() {
        val packages = setOf(
            "com.android.healthconnect.controller",
            "com.google.android.apps.fitness",
            "com.huami.watch.hmwatchmanager"
        )

        assertEquals(
            listOf("com.google.android.apps.fitness", "com.huami.watch.hmwatchmanager"),
            SourcePackagePolicy.actionable(packages, packages)
        )
    }

    @Test
    fun `keeps the technical package only when it is the sole launchable source`() {
        assertEquals(
            listOf("com.android.healthconnect.controller"),
            SourcePackagePolicy.actionable(
                setOf("com.android.healthconnect.controller", "com.missing.app"),
                setOf("com.android.healthconnect.controller")
            )
        )
    }
}
