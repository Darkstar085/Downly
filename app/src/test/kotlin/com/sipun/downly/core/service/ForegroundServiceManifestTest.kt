package com.sipun.downly.core.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForegroundServiceManifestTest {
    @Test
    fun workManagerForegroundServiceSurvivesTaskRemoval() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val serviceDeclaration = manifest.substringAfter(
            "android:name=\"androidx.work.impl.foreground.SystemForegroundService\""
        ).substringBefore("/>")

        assertTrue(serviceDeclaration.contains("android:stopWithTask=\"false\""))
        assertTrue(serviceDeclaration.contains("android:foregroundServiceType=\"dataSync\""))
    }
}
