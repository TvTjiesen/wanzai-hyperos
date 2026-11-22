package com.muyao.wanzaihyperos.data

import com.muyao.wanzaihyperos.data.repo.AccountEpoch
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test

class AccountEpochGateTest {
    @Test fun pendingOwnerAllowsCasPublicationButBlocksDataAndIdentityUntilCommit() {
        val epoch = AccountEpoch()
        epoch.activate("synthetic-account-a", allowData = false)
        val token = epoch.token()
        var credentialsPublished = false
        var dataPublished = false
        assertTrue(epoch.isCurrent(token))
        assertFalse(epoch.isDataCurrent(token))
        assertNull(epoch.identity())
        assertTrue(epoch.publish(token) { credentialsPublished = true })
        assertTrue(credentialsPublished)
        assertFalse(epoch.publishData(token) { dataPublished = true })
        assertFalse(dataPublished)
        val blocked = Job()
        assertFalse(epoch.track(token, blocked))
        assertTrue(blocked.isCancelled)
        assertTrue(epoch.allowData(token))
        assertTrue(epoch.isDataCurrent(token))
        assertTrue(epoch.publishData(token) { dataPublished = true })
        assertTrue(dataPublished)
        val identity = epoch.identity()!!
        assertEquals(token, identity.generation)
        assertNotEquals("synthetic-account-a", identity.ownerKey)
        val allowed = Job()
        assertTrue(epoch.track(token, allowed))
        epoch.untrack(allowed)
        allowed.cancel()
    }

    @Test fun previousGenerationCanNeverOpenOrPublishIntoPendingReplacement() {
        val epoch = AccountEpoch()
        epoch.activate("synthetic-account-a")
        val old = epoch.token()
        val oldIdentity = epoch.identity()!!
        epoch.suspendOwner()
        epoch.activate("synthetic-account-b", allowData = false)
        val pending = epoch.token()
        assertNotEquals(old, pending)
        assertFalse(epoch.allowData(old))
        assertFalse(epoch.isCurrent(old))
        assertFalse(epoch.isDataCurrent(old))
        var oldActionRan = false
        assertFalse(epoch.publish(old) { oldActionRan = true })
        assertFalse(epoch.publishData(old) { oldActionRan = true })
        assertFalse(oldActionRan)
        assertNull(epoch.identity())
        assertFalse(epoch.isDataCurrent(pending))
        assertTrue(epoch.publish(pending) {})
        assertTrue(epoch.allowData(pending))
        assertNotEquals(oldIdentity.ownerKey, epoch.identity()!!.ownerKey)
        assertEquals(pending, epoch.identity()!!.generation)
    }

    @Test fun closingOwnerCancelsTrackedReadsResetsDataAndCannotBeReopenedByOldToken() {
        val epoch = AccountEpoch()
        var resets = 0
        epoch.registerReset { resets++ }
        epoch.activate("synthetic-account-a")
        val token = epoch.token()
        val tracked = Job()
        val removed = Job()
        assertTrue(epoch.track(token, tracked))
        assertTrue(epoch.track(token, removed))
        epoch.untrack(removed)
        val before = resets
        epoch.suspendOwner()
        assertTrue(tracked.isCancelled)
        assertFalse(removed.isCancelled)
        assertEquals(before + 1, resets)
        assertNull(epoch.identity())
        assertFalse(epoch.isCurrent(token))
        assertFalse(epoch.isDataCurrent(token))
        assertFalse(epoch.allowData(token))
        assertFalse(epoch.allowData(epoch.token()))
        assertFalse(epoch.publish(token) { fail("Closed owner must not publish credentials") })
        assertFalse(epoch.publishData(token) { fail("Closed owner must not publish data") })
        val late = Job()
        assertFalse(epoch.track(token, late))
        assertTrue(late.isCancelled)
        removed.cancel()
    }

    @Test fun failedReplacementBoundaryRemainsClosedEvenWithoutANewOwner() {
        val epoch = AccountEpoch()
        epoch.activate("synthetic-account-a")
        val previous = epoch.token()
        epoch.suspendOwner()
        val closed = epoch.token()
        assertNotEquals(previous, closed)
        assertNull(epoch.identity())
        assertFalse(epoch.publish(closed) { fail("Disk failure must not imply a successful login") })
        assertFalse(epoch.publishData(closed) { fail("Disk failure must not restore old data") })
        assertFalse(epoch.allowData(closed))
        assertFalse(epoch.isCurrent(closed))
    }

    @Test fun sameOwnerRenewalKeepsGenerationWhileExplicitInvalidationRejectsOldCallbacks() {
        val epoch = AccountEpoch()
        epoch.activate("synthetic-account-a")
        val token = epoch.token()
        val identity = epoch.identity()
        epoch.activate("synthetic-account-a")
        assertEquals(token, epoch.token())
        assertEquals(identity, epoch.identity())
        val tracked = Job()
        assertTrue(epoch.track(token, tracked))
        epoch.invalidateCurrent()
        assertTrue(tracked.isCancelled)
        assertNotEquals(token, epoch.token())
        assertFalse(epoch.publishData(token) { fail("Invalidated callback must not publish") })
        assertFalse(epoch.allowData(token))
        assertTrue(epoch.isDataCurrent(epoch.token()))
    }
}
