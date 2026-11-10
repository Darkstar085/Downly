package com.sipun.downly.presentation

import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.DuplicateDownloadPrompt
import com.sipun.downly.data.MediaMode
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicateReplacementTest {
    @Test
    fun duplicateChoiceIsAvailableOnlyForTheWaitingRecord() {
        val prompt = DuplicateDownloadPrompt(
            "waiting",
            "/tmp/source",
            "content://existing",
            "Download/Downly/Videos/item.mp4",
            MediaMode.VIDEO,
            "item.mp4"
        )
        val waiting = DownloadRecord(
            id = "waiting",
            url = "url",
            title = "Waiting",
            status = DownloadStatus.FAILED
        )
        val other = DownloadRecord(
            id = "other",
            url = "url",
            title = "Other",
            status = DownloadStatus.FAILED
        )

        assertEquals(prompt, duplicateChoiceFor(waiting, prompt))
        assertEquals(null, duplicateChoiceFor(other, prompt))
    }

    @Test
    fun workerRefreshRestoresPromptOnlyWhenItsRecordIsStillInHistory() {
        val prompt = DuplicateDownloadPrompt(
            "waiting",
            "/tmp/source",
            "content://existing",
            "Download/Downly/Videos/item.mp4",
            MediaMode.VIDEO,
            "item.mp4"
        )
        val waiting = DownloadRecord(
            id = "waiting",
            url = "url",
            title = "Waiting",
            status = DownloadStatus.FAILED
        )

        assertEquals(prompt, duplicatePromptForHistory(listOf(waiting), prompt))
        assertEquals(null, duplicatePromptForHistory(emptyList(), prompt))
    }

    @Test
    fun replacementRemovesOlderHistoryEntryForTheSamePublishedPath() {
        val old = DownloadRecord(
            id = "old",
            url = "old",
            title = "Old",
            status = DownloadStatus.COMPLETE,
            filePath = "Download/Downly/Videos/item.mp4"
        )
        val current = DownloadRecord(
            id = "current",
            url = "new",
            title = "New",
            status = DownloadStatus.COMPLETE,
            filePath = "Download/Downly/Videos/item.mp4"
        )

        val result = reconcileReplacedHistory(
            listOf(current, old),
            current,
            "Download/Downly/Videos/item.mp4"
        )

        assertEquals(listOf(current), result)
    }

}
