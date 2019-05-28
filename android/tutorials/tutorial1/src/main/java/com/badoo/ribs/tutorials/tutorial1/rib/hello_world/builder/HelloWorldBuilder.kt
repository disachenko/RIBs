package com.badoo.ribs.tutorials.tutorial1.rib.hello_world.builder

import com.badoo.ribs.core.Builder
import com.badoo.ribs.core.Node
import com.badoo.ribs.tutorials.tutorial1.rib.hello_world.HelloWorld
import com.badoo.ribs.tutorials.tutorial1.rib.hello_world.HelloWorldView

class HelloWorldBuilder(dependency: HelloWorld.Dependency) :
    Builder<HelloWorld.Dependency>(dependency) {

    fun build(): Node<HelloWorldView> {
        val customisation = HelloWorld.Customisation()
        val component = DaggerHelloWorldComponent.builder()
            .dependency(dependency)
            .customisation(customisation)
            .build()

        return component.node()
    }
}
