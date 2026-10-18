package com.centraldocasal.app

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable

/**
 * Default motion timings for the Central do Casal.
 *
 * The effect never communicates a state that cannot be understood without animation.
 * A user preference OR Android's disabled system animators results in instant transitions.
 */
internal enum class MotionKind(val durationMillis: Int) {
    FEEDBACK(140),
    NAVIGATION(180),
    LAYOUT(220),
    PROGRESS(400)
}

/** Pure and testable: avoid duplicating preference checks in each screen. */
internal fun motionDurationMillis(kind: MotionKind, userAllowsMotion: Boolean, systemAllowsMotion: Boolean): Int =
    if (userAllowsMotion && systemAllowsMotion) kind.durationMillis else 0

@Composable
internal fun centralMotionDurationMillis(kind: MotionKind): Int =
    motionDurationMillis(
        kind,
        userAllowsMotion = LocalExperience.current.motion,
        systemAllowsMotion = ValueAnimator.areAnimatorsEnabled()
    )
