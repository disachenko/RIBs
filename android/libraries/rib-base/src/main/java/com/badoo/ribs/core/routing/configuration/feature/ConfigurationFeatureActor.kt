package com.badoo.ribs.core.routing.configuration.feature

import android.os.Handler
import android.os.Parcelable
import android.view.View
import com.badoo.mvicore.element.Actor
import com.badoo.ribs.core.Node
import com.badoo.ribs.core.routing.action.RoutingAction
import com.badoo.ribs.core.routing.configuration.ConfigurationCommand
import com.badoo.ribs.core.routing.configuration.ConfigurationContext
import com.badoo.ribs.core.routing.configuration.ConfigurationContext.ActivationState.SLEEPING
import com.badoo.ribs.core.routing.configuration.ConfigurationKey
import com.badoo.ribs.core.routing.configuration.Transaction
import com.badoo.ribs.core.routing.configuration.Transaction.MultiConfigurationCommand
import com.badoo.ribs.core.routing.configuration.action.ActionExecutionParams
import com.badoo.ribs.core.routing.configuration.action.single.Action
import com.badoo.ribs.core.routing.configuration.action.single.AddAction
import com.badoo.ribs.core.routing.configuration.isBackStackOperation
import com.badoo.ribs.core.routing.transition.TransitionDirection
import com.badoo.ribs.core.routing.transition.TransitionElement
import com.badoo.ribs.core.routing.transition.handler.TransitionHandler
import io.reactivex.Observable
import io.reactivex.Observable.fromCallable

/**
 * Executes [MultiConfigurationAction] / [SingleConfigurationAction] associated with the incoming
 * [ConfigurationCommand]. The actions will take care of [RoutingAction] invocations and [Node]
 * manipulations, and are expected to return updated elements.
 *
 * Updated elements are then passed on to the [ReducerImpl] in the respective [Effect]s
 */
internal class ConfigurationFeatureActor<C : Parcelable>(
    private val configurationResolver: (C) -> RoutingAction<*>,
    private val parentNode: Node<*>,
    private val transitionHandler: TransitionHandler<C>?
) : Actor<WorkingState<C>, Transaction<C>, ConfigurationFeature.Effect<C>> {

    private val handler = Handler()
    private val configurationKeyResolver = ConfigurationKeyResolver(configurationResolver, parentNode)

    override fun invoke(
        state: WorkingState<C>,
        transaction: Transaction<C>
    ): Observable<ConfigurationFeature.Effect<C>> =
        when (transaction) {
            is MultiConfigurationCommand -> processMultiConfigurationCommand(transaction, state)
            is Transaction.ListOfCommands -> processTransaction(state, transaction)
        }

    private fun processMultiConfigurationCommand(
        transaction: MultiConfigurationCommand<C>,
        state: WorkingState<C>
    ): Observable<ConfigurationFeature.Effect<C>> =
        fromCallable {
            transaction.action.execute(state, createParams(state, emptyMap(), transaction))
        }.map { updated ->
            ConfigurationFeature.Effect.Global(
                transaction,
                updated
            )
        }

    private enum class NewTransitionsExecution {
        ABORT, CONTINUE
    }

    private fun processTransaction(
        state: WorkingState<C>,
        transaction: Transaction.ListOfCommands<C>
    ): Observable<ConfigurationFeature.Effect<C>> =
        Observable.create<ConfigurationFeature.Effect<C>> { emitter ->
            val commands = transaction.commands
            val defaultElements = createDefaultElements(state, commands)
            val params = createParams(
                state = state.withDefaults(defaultElements),
                defaultElements = defaultElements,
                command = transaction
            )

            val actions = createActions(commands, params)
            val effects = createEffects(commands, actions)

            // Effects always need to be emitted, even if we abort afterwards. This is to ensure
            // State reflects latest Configurations.
            effects.forEach { emitter.onNext(it) }
            if (checkOngoingTransitions(state, transaction) == NewTransitionsExecution.ABORT) {
                emitter.onComplete()
                return@create
            }

            actions.forEach { it.onBeforeTransition() }
            val transitionElements = actions.flatMap { it.transitionElements }

            if (params.globalActivationLevel == SLEEPING || transitionHandler == null) {
                actions.forEach { it.onTransition() }
                actions.forEach { it.onFinish() }
                emitter.onComplete()
            } else {
                beginTransitions(transaction.descriptor, transitionElements, emitter, actions)
            }
        }

    private fun checkOngoingTransitions(
        state: WorkingState<C>,
        transaction: Transaction.ListOfCommands<C>
    ): NewTransitionsExecution {
        state.ongoingTransitions.forEach {
            when {
                transaction.descriptor.isReverseOf(it.descriptor) -> {
                    it.reverse()
                    return NewTransitionsExecution.ABORT
                }
                transaction.descriptor.isContinuationOf(it.descriptor) -> {
                    it.jumpToEnd()
                }
            }
        }

        return NewTransitionsExecution.CONTINUE
    }

    private fun beginTransitions(
        descriptor: TransitionDescriptor,
        transitionElements: List<TransitionElement<C>>,
        emitter: EffectEmitter<C>,
        actions: List<Action<C>>
    ) {
        requireNotNull(transitionHandler)
        val enteringElements = transitionElements.filter { it.direction == TransitionDirection.ENTER }

        /**
         * Entering views at this point are created but will be measured / laid out the next frame.
         * We need to base calculations in transition implementations based on their actual measurements,
         * but without them appearing just yet to avoid flickering.
         * Making them invisible, starting the transitions then making them visible achieves the above.
         */
        enteringElements.visibility(View.INVISIBLE)
        handler.post {
            val transitionPair = transitionHandler.onTransition(transitionElements)
            enteringElements.visibility(View.VISIBLE)

            // TODO consider whether splitting this two two instances (one per direction, so that
            //  enter and exit can be controlled separately) is better
            OngoingTransition(
                descriptor = descriptor,
                direction = TransitionDirection.EXIT,
                transitionPair = transitionPair,
                actions = actions,
                transitionElements = transitionElements,
                emitter = emitter
            ).start()
        }
    }

    private fun List<TransitionElement<C>>.visibility(visibility: Int) {
        forEach {
            it.view.visibility = visibility
        }
    }

    /**
     * Since the state doesn't yet reflect elements we're just about to add, we'll create them ahead
     * so that other [ConfigurationCommand]s that rely on their existence can function properly.
     */
    private fun createDefaultElements(
        state: WorkingState<C>,
        commands: List<ConfigurationCommand<C>>
    ): Map<ConfigurationKey, ConfigurationContext<C>> {
        val defaultElements: MutableMap<ConfigurationKey, ConfigurationContext<C>> = mutableMapOf()

        commands.forEach { command ->
            // TODO unify this with resolution for all other types if possible
            if (command is ConfigurationCommand.Add<C> && !state.pool.containsKey(command.key) && !defaultElements.containsKey(command.key)) {
                defaultElements[command.key] = ConfigurationContext.Unresolved(
                    ConfigurationContext.ActivationState.INACTIVE,
                    command.configuration
                ).resolve(configurationResolver, parentNode) {
                    val action = AddAction(it, parentNode)
                    action.onTransition()
                    action.result
                }
            }
        }

        return defaultElements
    }

    private fun createParams(
        state: WorkingState<C>,
        defaultElements: Map<ConfigurationKey, ConfigurationContext<C>>,
        command: Transaction<C>? = null
    ): ActionExecutionParams<C> =
        ActionExecutionParams(
            resolver = { key -> configurationKeyResolver.resolve(state, key, defaultElements) },
            parentNode = parentNode,
            globalActivationLevel = when (command) {
                is MultiConfigurationCommand.Sleep -> SLEEPING
                is MultiConfigurationCommand.WakeUp -> ConfigurationContext.ActivationState.ACTIVE
                else -> state.activationLevel
            }
        )

    private fun createActions(
        commands: List<ConfigurationCommand<C>>,
        params: ActionExecutionParams<C>
    ): List<Action<C>> = commands.map { command ->
        command.actionFactory.create(command.key, params, commands.isBackStackOperation(command.key))
    }

    private fun createEffects(
        commands: List<ConfigurationCommand<C>>,
        actions: List<Action<C>>
    ): List<ConfigurationFeature.Effect<C>> =
        commands.mapIndexed { index, command ->
            ConfigurationFeature.Effect.Individual(command, actions[index].result) as ConfigurationFeature.Effect<C>
        }
}
