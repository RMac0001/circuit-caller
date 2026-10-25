package com.roger.circuitcaller.update

enum class UpdatePhase { IDLE, DOWNLOADING, INSTALLING }

/** What the library screen's update card shows. */
data class UpdateState(
    /** A strictly newer release to offer, or null for no card. */
    val available: ReleaseInfo? = null,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    /** Download progress 0–100, or null when the size is unknown. */
    val percent: Int? = null,
    /** Show the "allow installing updates" explanation before opening Settings. */
    val needsInstallPermission: Boolean = false
)
