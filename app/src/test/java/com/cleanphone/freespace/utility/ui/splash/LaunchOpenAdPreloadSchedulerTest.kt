package com.cleanphone.freespace.utility.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchOpenAdPreloadSchedulerTest {
    private val scheduler = LaunchOpenAdPreloadScheduler(retryIntervalMs = 500L)

    @Test
    fun `initial cache miss permits preload immediately`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertEquals(1, scheduler.attemptCount)
    }

    @Test
    fun `failed initial attempt can retry after interval`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertFalse(scheduler.tryAcquire(nowMs = 599L, hasCachedAd = false, isLoading = false))
        assertTrue(scheduler.tryAcquire(nowMs = 600L, hasCachedAd = false, isLoading = false))
        assertEquals(2, scheduler.attemptCount)
    }

    @Test
    fun `loading request suppresses another attempt without consuming interval`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertFalse(scheduler.tryAcquire(nowMs = 600L, hasCachedAd = false, isLoading = true))
        assertTrue(scheduler.tryAcquire(nowMs = 700L, hasCachedAd = false, isLoading = false))
        assertEquals(2, scheduler.attemptCount)
    }

    @Test
    fun `cached ad suppresses further attempts`() {
        assertTrue(scheduler.tryAcquire(nowMs = 100L, hasCachedAd = false, isLoading = false))
        assertFalse(scheduler.tryAcquire(nowMs = 600L, hasCachedAd = true, isLoading = false))
        assertEquals(1, scheduler.attemptCount)
    }
}
