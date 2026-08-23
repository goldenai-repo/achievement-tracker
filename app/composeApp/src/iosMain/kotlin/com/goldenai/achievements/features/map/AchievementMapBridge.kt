package com.goldenai.achievements.features.map

import platform.UIKit.UIView

/**
 * Host-app MapLibre surface. Implemented in Swift (`AchievementMapHandleImpl`)
 * and registered from [iOSApp] before Compose starts.
 *
 * Callbacks use a listener + JSON strings so Swift/Kotlin interop stays
 * ObjC-friendly (no function-type / KotlinDouble bridging issues).
 */
interface AchievementMapListener {
    fun onPointClick(pointId: String)
    fun onBoundaryClick(catalogId: String)
    fun onViewportChanged(viewportJson: String)
}

interface AchievementMapHandle {
    fun view(): UIView
    fun setListener(listener: AchievementMapListener?)
    fun bind(
        styleUrl: String,
        pointsJson: String,
        boundariesJson: String,
        viewportJson: String?,
        cameraResetKey: Long,
    )
}

fun interface AchievementMapHandleFactory {
    fun create(): AchievementMapHandle
}

object AchievementMapBridge {
    var factory: AchievementMapHandleFactory? = null
}
