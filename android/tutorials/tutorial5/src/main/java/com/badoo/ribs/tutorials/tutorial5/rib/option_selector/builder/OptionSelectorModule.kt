package com.badoo.ribs.tutorials.tutorial5.rib.option_selector.builder

import android.os.Bundle
import com.badoo.ribs.core.Node
import com.badoo.ribs.tutorials.tutorial5.rib.option_selector.OptionSelector
import com.badoo.ribs.tutorials.tutorial5.rib.option_selector.OptionSelector.Output
import com.badoo.ribs.tutorials.tutorial5.rib.option_selector.OptionSelectorInteractor
import com.badoo.ribs.tutorials.tutorial5.rib.option_selector.OptionSelectorView
import dagger.Provides
import io.reactivex.functions.Consumer

@dagger.Module
internal object OptionSelectorModule {

    @OptionSelectorScope
    @Provides
    @JvmStatic
    internal fun interactor(
        savedInstanceState: Bundle?,
        output: Consumer<Output>,
        config: OptionSelector.Config
    ): OptionSelectorInteractor =
        OptionSelectorInteractor(
            savedInstanceState = savedInstanceState,
            output = output,
            options = config.options
        )

    @OptionSelectorScope
    @Provides
    @JvmStatic
    internal fun node(
        savedInstanceState: Bundle?,
        customisation: OptionSelector.Customisation,
        interactor: OptionSelectorInteractor
    ) : Node<OptionSelectorView> = Node(
        savedInstanceState = savedInstanceState,
        identifier = object : OptionSelector {},
        viewFactory = customisation.viewFactory.invoke(null),
        router = null,
        interactor = interactor
    )
}
