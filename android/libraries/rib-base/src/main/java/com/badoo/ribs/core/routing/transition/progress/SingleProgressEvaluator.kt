package com.badoo.ribs.core.routing.transition.progress


class SingleProgressEvaluator : ProgressEvaluator {

    private sealed class Progress {
        object Initialised : Progress()
        object Reset : Progress()
        class InProgress : Progress() {
            var progress: Float = 0f
        }
        object Finished : Progress()
    }

    private var state: Progress =
        Progress.Initialised

    override val progress: Float =
        when (val state = state) {
            is Progress.Initialised -> 0f
            is Progress.Reset -> 0f
            is Progress.InProgress -> state.progress
            is Progress.Finished -> 1f
        }

    fun start() {
        state = Progress.InProgress()
    }

    fun updateProgress(progress: Float) {
        when (val state = state) {
            is Progress.InProgress -> state.progress = progress
            else -> if (progress != 1f && progress != 0f) {
                throw IllegalStateException("Not in progress anymore")
            }
        }
    }

    fun reset() {
        state = Progress.Reset
    }

    fun markFinished() {
        state = Progress.Finished
    }

    override fun isPending(): Boolean =
        isInProgress() || isInitialised()

    private fun isInProgress(): Boolean =
        state is Progress.InProgress

    private fun isInitialised(): Boolean =
        state is Progress.Initialised
}
