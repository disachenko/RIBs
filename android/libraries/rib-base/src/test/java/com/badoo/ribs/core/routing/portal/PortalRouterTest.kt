package com.badoo.ribs.core.routing.portal

import android.os.Parcelable
import com.badoo.ribs.core.helper.TestNode
import com.badoo.ribs.core.helper.TestRouter
import com.badoo.ribs.core.Node
import com.badoo.ribs.core.routing.action.AttachRibRoutingAction
import com.badoo.ribs.core.routing.action.AttachRibRoutingAction.Companion.attach
import com.badoo.ribs.core.routing.portal.PortalRouter.Configuration.Content.Portal
import com.badoo.ribs.core.routing.portal.PortalRouter.Configuration.Content.Default
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PortalRouterTest {

    lateinit var router: PortalRouter
    lateinit var node1: Node<*>
    lateinit var node2: Node<*>
    lateinit var node3: Node<*>


    @Before
    fun setUp() {
        node3 = TestNode()
        node2 = TestNode(router = TestRouter(routingActionForC3 = attach { node3 }))
        node1 = TestNode(router = TestRouter(routingActionForC2 = attach { node2 }))



        router = PortalRouter(
            savedInstanceState = null
        ).apply {
            defaultRoutingAction = attach { node1 }
        }
    }

    @Test
    fun `Resolving Portal configuration builds expected remote Node`() {
        val remoteRoutingAction = router.resolveConfiguration(
            Portal(
                listOf(
                    Default,
                    TestRouter.Configuration.C2,
                    TestRouter.Configuration.C3
                )
            )
        )

        val builtNodes = remoteRoutingAction.buildNodes(emptyList()).map { it.node }
        assertEquals(listOf(node3), builtNodes)
    }
}
