package com.muyao.wanzaihyperos.net

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LiveOptInTest {
    @Test fun explicitTrueIsRequired() {
        assertFalse(LiveNetworkGate.optedIn(null, null))
        for (value in listOf("", "1", "yes", "false", "TRUE")) {
            assertFalse(LiveNetworkGate.optedIn(value, null))
            assertFalse(LiveNetworkGate.optedIn(null, value))
        }
        assertTrue(LiveNetworkGate.optedIn("true", null))
        assertTrue(LiveNetworkGate.optedIn(null, "true"))
    }

    @Test fun credentialsAndNetworkAreGatedBeforeAccess() {
        val root = sequenceOf(File("."), File(".."), File("../..")).first {
            File(it, "app/src/test/java/com/muyao/wanzaihyperos/net/LiveNetworkGate.kt").isFile
        }
        fun source(name: String) = File(root, "app/src/test/java/com/muyao/wanzaihyperos/net/$name.kt").readText()
        for (name in listOf("HzcuLiveLoginTest", "PortalTodoContractProbeTest")) {
            val body = source(name).substringAfter("fun readCredentials(): Pair<String, String>? {")
            assertTrue(name, body.trimStart().startsWith("LiveNetworkGate.requireOptIn()"))
        }
        val gate = source("LiveNetworkGate")
        for (name in listOf("requirePortal", "requireTronclass")) {
            assertTrue(gate.substringAfter("fun $name() {").trimStart().startsWith("requireOptIn()"))
        }
        assertTrue(source("PortalLiveContractTest").substringAfter("fun ensureLoggedIn(): PortalApi? {")
            .trimStart().startsWith("LiveNetworkGate.requireOptIn()"))
    }
}
