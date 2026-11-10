package com.sipun.downly.utils

import com.sipun.downly.data.MediaMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaOptionNormalizationTest {
    @Test
    fun genericVideoQualitiesUseStandardCapsAndPreserveNonStandardMaximum() {
        assertEquals(
            listOf(2160, 1440, 1080, 720, 480, 360, 240, 144),
            genericVideoQualityHeights(listOf(2160, 1080, 720, 480, 360, 240, 144))
        )
        assertEquals(
            listOf(900, 720, 480, 360, 240, 144),
            genericVideoQualityHeights(listOf(900, 480, 360, 240, 144))
        )
        assertEquals(
            listOf(480, 360, 240, 144),
            genericVideoQualityHeights(listOf(480, 360, 240, 144))
        )
        assertEquals(emptyList<Int>(), genericVideoQualityHeights(emptyList()))
    }

    @Test
    fun videoFormatSelectorChoosesBestAvailableFormatWithinSelectedResolutionCap() {
        assertEquals(
            "bv*[height<=2160]+ba/b[height<=2160]/bv*+ba/b",
            genericVideoFormatSelector(2160)
        )
        assertEquals(
            "bv*[height<=720]+ba/b[height<=720]/bv*+ba/b",
            genericVideoFormatSelector(720)
        )
    }

    @Test
    fun videoFilenameUsesHumanReadableResolutionWithoutFormatSelector() {
        assertEquals(
            "2160p",
            downloadFilenameQuality(
                MediaMode.VIDEO,
                "video:bv*[height<=2160]+ba/b[height<=2160]/bv*+ba/b"
            )
        )
        assertEquals("720p", downloadFilenameQuality(MediaMode.VIDEO, "720p"))
        assertEquals("audio-251", downloadFilenameQuality(MediaMode.AUDIO, "audio:251"))
    }

    @Test
    fun genericVideoSizeEstimatePrefersFormatBytesOverBitrateCalculation() {
        assertEquals(
            4_600_000_000L,
            estimateFormatBytes(ResolvedFormatSize(4_600_000_000L, true), 5000.0, 2028)
        )
        assertEquals(
            900_000_000L,
            estimateFormatBytes(ResolvedFormatSize(900_000_000L, false), 5000.0, 2028)
        )
        assertEquals(
            1_267_500_000L,
            estimateFormatBytes(null, 5000.0, 2028)
        )
    }

    @Test
    fun activeDownloadTotalSizeIsShownFromYtDlpProgressDetail() {
        assertEquals(
            "2.57 GiB",
            downloadTotalSize("[download] 4.9% of 2.57GiB at 7.13MiB/s ETA 05:51")
        )
        assertEquals("1.2 GB", downloadTotalSize("[download] 10% of 1.2 GB at 5MiB/s ETA 01:00"))
        assertNull(downloadTotalSize("[download] 42.0% at 5MiB/s ETA 01:00"))
    }

    @Test
    fun qualityIdentitySeparatesResolutionsAndFormats() {
        assertEquals("video-1080p", downloadQualityTag(MediaMode.VIDEO, "1080p"))
        assertEquals(
            "video-137-bestaudio-137",
            downloadQualityTag(MediaMode.VIDEO, "video:137+bestaudio/137")
        )
        assertEquals("audio-251", downloadQualityTag(MediaMode.AUDIO, "audio:251"))
        assertEquals(
            false,
            mediaIdentityKey("video123", MediaMode.VIDEO, "1080p") ==
                    mediaIdentityKey("video123", MediaMode.VIDEO, "360p")
        )
    }

    @Test
    fun bitrateEstimateScalesWithBitrateAndDuration() {
        assertEquals(10_688_000L, estimateBytesFromBitrate(128.0, 668))
        assertEquals(21_376_000L, estimateBytesFromBitrate(256.0, 668))
        assertEquals(21_376_000L, estimateBytesFromBitrate(128.0, 1336))
        assertNull(estimateBytesFromBitrate(0.0, 668))
        assertNull(estimateBytesFromBitrate(128.0, 0))
    }

    @Test
    fun sizeResolutionPrefersExactThenRemoteThenApproximateThenEstimate() {
        assertEquals(1000L, resolveSizeValue(1000L, 2000L, 3000L, 128.0, 60).bytes)
        assertEquals(false, resolveSizeValue(1000L, 2000L, 3000L, 128.0, 60).approximate)
        assertEquals(2000L, resolveSizeValue(0L, 2000L, 3000L, 128.0, 60).bytes)
        assertEquals(false, resolveSizeValue(0L, 2000L, 3000L, 128.0, 60).approximate)
        assertEquals(3000L, resolveSizeValue(0L, null, 3000L, 128.0, 60).bytes)
        assertEquals(true, resolveSizeValue(0L, null, 3000L, 128.0, 60).approximate)
        assertEquals(960000L, resolveSizeValue(0L, null, 0L, 128.0, 60).bytes)
        assertEquals(true, resolveSizeValue(0L, null, 0L, 128.0, 60).approximate)
    }

    @Test
    fun audioBitrateMapsToNearestCommonLevel() {
        assertEquals(128, commonAudioBitrate(129))
        assertEquals(128, commonAudioBitrate(122))
        assertEquals(64, commonAudioBitrate(70))
        assertEquals(48, commonAudioBitrate(52))
        assertEquals(320, commonAudioBitrate(320))
        assertNull(commonAudioBitrate(0))
    }

    @Test
    fun vcodecRankRanksAV1HigherThanVP9AndH264() {
        assertEquals(4, vcodecRank("av01.0.12M.08"))
        assertEquals(4, vcodecRank("av1"))
        assertEquals(3, vcodecRank("vp09.00.51.08"))
        assertEquals(3, vcodecRank("vp9"))
        assertEquals(2, vcodecRank("avc1.64002a"))
        assertEquals(2, vcodecRank("h264"))
        assertEquals(1, vcodecRank("mp4v.20.3"))
        assertEquals(0, vcodecRank("none"))
        assertEquals(0, vcodecRank(null))
    }
}
