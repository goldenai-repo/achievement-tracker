package com.goldenai.achievements.core.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext

@OptIn(DelicateCoroutinesApi::class)
actual val databaseDispatcher: CoroutineDispatcher =
    newSingleThreadContext("AchievementDatabase")
