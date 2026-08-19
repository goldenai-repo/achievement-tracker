package com.goldenai.achievements.core.db

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for all SQLDelight reads and writes. Android uses [Dispatchers.IO];
 * iOS uses a single thread because the native SQLite driver is not safe on a
 * thread pool.
 */
expect val databaseDispatcher: CoroutineDispatcher
