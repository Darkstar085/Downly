package com.sipun.downly.presentation

import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DuplicateDownloadPrompt

internal fun duplicateChoiceFor(
    record: DownloadRecord,
    prompt: DuplicateDownloadPrompt?
): DuplicateDownloadPrompt? = prompt?.takeIf { it.recordId == record.id }

internal fun duplicatePromptForHistory(
    history: List<DownloadRecord>,
    prompt: DuplicateDownloadPrompt?
): DuplicateDownloadPrompt? =
    prompt?.takeIf { candidate -> history.any { it.id == candidate.recordId } }
