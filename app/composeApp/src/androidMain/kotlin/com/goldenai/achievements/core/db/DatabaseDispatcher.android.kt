package com.goldenai.achievements.core.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val databaseDispatcher: CoroutineDispatcher = Dispatchers.IO
