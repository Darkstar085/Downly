package com.sipun.downly

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testLottieJsonFiles() {
        val downloadFile = File("src/main/res/raw/about_download.json")
        assertTrue(
            "about_download.json should exist: ${downloadFile.absolutePath}",
            downloadFile.exists()
        )
        val downloadJson = downloadFile.readText()
        assertNotNull(JSONObject(downloadJson))

        val heartFile = File("src/main/res/raw/about_heart.json")
        assertTrue("about_heart.json should exist: ${heartFile.absolutePath}", heartFile.exists())
        val heartJson = heartFile.readText()
        assertNotNull(JSONObject(heartJson))
    }
}