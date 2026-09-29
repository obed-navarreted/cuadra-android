package com.cuadra.caja.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinGuardTest {
    private var clock = 0L
    private val guard = PinGuard { clock }

    @Test fun fourFailuresDoNotLockButTheFifthDoes() {
        repeat(4) { assertFalse(guard.recordFailure()) }
        assertFalse(guard.isLocked())
        assertTrue(guard.recordFailure())
        assertEquals(30_000L, guard.waitMillis())
    }

    @Test fun theLockExpiresAndEachNewFailureDoublesIt() {
        repeat(5) { guard.recordFailure() }
        clock += 30_000
        assertFalse(guard.isLocked())
        guard.recordFailure()
        assertEquals(60_000L, guard.waitMillis())
        clock += 60_000
        guard.recordFailure()
        assertEquals(120_000L, guard.waitMillis())
    }

    @Test fun theLockIsCappedAt15Minutes() {
        repeat(40) { guard.recordFailure() }
        assertEquals(15 * 60_000L, guard.waitMillis())
    }

    @Test fun aSuccessfulPinResetsEverything() {
        repeat(5) { guard.recordFailure() }
        clock += 30_000
        guard.recordSuccess()
        repeat(4) { assertFalse(guard.recordFailure()) }
        assertFalse(guard.isLocked())
    }
}
