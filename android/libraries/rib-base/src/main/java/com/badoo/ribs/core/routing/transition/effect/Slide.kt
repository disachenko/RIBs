@file:SuppressWarnings("LongParameterList")
package com.badoo.ribs.core.routing.transition.effect

import android.view.animation.Interpolator
import androidx.annotation.CheckResult
import com.badoo.ribs.core.routing.transition.Transition
import com.badoo.ribs.core.routing.transition.TransitionDirection
import com.badoo.ribs.core.routing.transition.TransitionElement
import com.badoo.ribs.core.routing.transition.effect.AnimationContainer.Parent
import com.badoo.ribs.core.routing.transition.handler.defaultDuration
import com.badoo.ribs.core.routing.transition.handler.defaultInterpolator
import com.badoo.ribs.core.routing.transition.progress.SingleProgressEvaluator


@CheckResult
fun <T> TransitionElement<out T>.slide(
    gravity: Gravity,
    animationContainer: AnimationContainer = Parent,
    duration: Long = defaultDuration,
    interpolator: Interpolator = defaultInterpolator,
    reverseOnBackStack: Boolean = true
) : Transition {
    val evaluator = SingleProgressEvaluator().also { progressEvaluator.add(it) }
    val actualGravity = if (reverseOnBackStack && (isBackStackOperation xor (direction == TransitionDirection.EXIT))) gravity.reverse() else gravity
    val endValues = slideEndValues(animationContainer, actualGravity).let {
        when (direction) {
            TransitionDirection.EXIT -> it
            TransitionDirection.ENTER -> it.reverse()
        }
    }

    return Transition.from(
        slideValueAnimator(actualGravity, endValues, interpolator, duration, evaluator)
    )
}
