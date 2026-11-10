package com.sipun.downly.presentation

import com.sipun.downly.data.DownloadRecord

internal fun reconcileReplacedHistory(
    history: List<DownloadRecord>,
    replacement: DownloadRecord,
    replacedPath: String
): List<DownloadRecord> = history.filterNot {
    it.id != replacement.id && it.filePath == replacedPath
}
