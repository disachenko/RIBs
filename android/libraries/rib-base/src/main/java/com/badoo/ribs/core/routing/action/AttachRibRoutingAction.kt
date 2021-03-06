package com.badoo.ribs.core.routing.action

import android.os.Bundle
import com.badoo.ribs.core.Node
import com.badoo.ribs.core.view.RibView

open class AttachRibRoutingAction<V : RibView>(
    private val builder: (savedInstanceState: Bundle?) -> Node<*>
) : RoutingAction<V> {

    override fun buildNodes(bundles: List<Bundle?>): List<Node.Descriptor> =
        listOf(
            Node.Descriptor(
                node = builder.invoke(bundles.firstOrNull()),
                viewAttachMode = Node.AttachMode.PARENT
            )
        )

    companion object {
        fun <V : RibView> attach(builder: (savedInstanceState: Bundle?) -> Node<*>): RoutingAction<V> =
            AttachRibRoutingAction(builder)
    }
}
