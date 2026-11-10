package com.sipun.downly.presentation

import com.sipun.downly.data.DownloadRecord

internal fun enqueueDownloadRecord(
    history: List<DownloadRecord>,
    record: DownloadRecord
): List<DownloadRecord> = listOf(record) + history
