package com.leftovers.app.data

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

/** Keeps the app's main data streams running for the whole process, so every screen reads one shared copy. */
private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })

/**
 * One live copy of this stream, started straight away and kept up to date, with the latest value ready for
 * each new reader. A tab opened for the first time then draws with its data at once instead of an empty frame
 * while its own query runs.
 */
internal fun <T> Flow<T>.sharedLive(): Flow<T> = shareIn(sharedScope, SharingStarted.Eagerly, replay = 1)
