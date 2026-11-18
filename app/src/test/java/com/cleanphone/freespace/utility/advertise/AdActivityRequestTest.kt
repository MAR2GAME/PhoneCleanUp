package com.cleanphone.freespace.utility.advertise

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdActivityRequestTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun launchedAdWaitsForHostToPauseAndReturnBeforeContinuing() = runTest {
        val host = Host()
        val events = mutableListOf<String>()
        val job = launchAdActivityRequest(
            scope = this,
            lifecycle = host.lifecycle,
            canShow = { true },
            launchAd = { events += "launch"; true },
            onLaunched = { events += "launched" },
            onClosed = { events += "closed" },
        )

        // Adding an observer to an already resumed host delivers ON_RESUME immediately.
        assertEquals(listOf("launch", "launched"), events)
        assertTrue(job.isActive)

        host.pause()
        assertEquals(listOf("launch", "launched"), events)
        host.resume()
        runCurrent()

        assertEquals(listOf("launch", "launched", "closed"), events)
        assertTrue(job.isCompleted)
        assertEquals(0, host.lifecycle.observerCount)

        host.pause()
        host.resume()
        assertEquals(listOf("launch", "launched", "closed"), events)
    }

    @Test
    fun ineligibleRequestContinuesWithoutLaunchingAdActivity() = runTest {
        val host = Host()
        var launches = 0
        var completions = 0
        val job = launchAdActivityRequest(
            scope = this,
            lifecycle = host.lifecycle,
            canShow = { false },
            launchAd = { launches++; true },
            onClosed = { completions++ },
        )

        assertTrue(job.isCompleted)
        assertEquals(0, launches)
        assertEquals(1, completions)
        assertEquals(0, host.lifecycle.observerCount)
    }

    @Test
    fun rejectedLaunchContinuesImmediatelyWithoutMarkingAdAsLaunched() = runTest {
        val host = Host()
        var launches = 0
        var accepted = 0
        var completions = 0
        val job = launchAdActivityRequest(
            scope = this,
            lifecycle = host.lifecycle,
            canShow = { true },
            launchAd = { launches++; false },
            onLaunched = { accepted++ },
            onClosed = { completions++ },
        )

        assertTrue(job.isCompleted)
        assertEquals(1, launches)
        assertEquals(0, accepted)
        assertEquals(1, completions)
        assertEquals(0, host.lifecycle.observerCount)
        host.pause()
        host.resume()
        assertEquals(1, completions)
    }

    @Test
    fun launchExceptionContinuesOnceAndRemovesObserver() = runTest {
        val host = Host()
        var completions = 0
        val job = launchAdActivityRequest(
            scope = this,
            lifecycle = host.lifecycle,
            canShow = { true },
            launchAd = { throw IllegalStateException("Cannot launch ad activity") },
            onClosed = { completions++ },
        )

        assertTrue(job.isCompleted)
        assertEquals(1, completions)
        assertEquals(0, host.lifecycle.observerCount)
    }

    @Test
    fun cancellationStopsWaitingWithoutContinuingTheOldPage() = runTest {
        val host = Host()
        var completions = 0
        val job = launchAdActivityRequest(
            scope = this,
            lifecycle = host.lifecycle,
            canShow = { true },
            launchAd = { true },
            onClosed = { completions++ },
        )
        assertEquals(1, host.lifecycle.observerCount)

        job.cancel()
        runCurrent()
        host.pause()
        host.resume()

        assertTrue(job.isCancelled)
        assertEquals(0, completions)
        assertEquals(0, host.lifecycle.observerCount)
    }

    @Test
    fun hostDestructionCancelsPendingCompletion() = runTest {
        val host = Host()
        var completions = 0
        val job = launchAdActivityRequest(
            scope = this,
            lifecycle = host.lifecycle,
            canShow = { true },
            launchAd = { true },
            onClosed = { completions++ },
        )

        host.pause()
        host.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        host.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(0, completions)
        assertEquals(0, host.lifecycle.observerCount)
    }

    @Test
    fun cancelledScopeCannotLaunchOrContinue() = runTest {
        val host = Host()
        val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        requestScope.cancel()
        var launches = 0
        var completions = 0
        val job = launchAdActivityRequest(
            scope = requestScope,
            lifecycle = host.lifecycle,
            canShow = { true },
            launchAd = { launches++; true },
            onClosed = { completions++ },
        )
        runCurrent()

        assertTrue(job.isCancelled)
        assertEquals(0, launches)
        assertEquals(0, completions)
        assertEquals(0, host.lifecycle.observerCount)
    }

    @Test
    fun continuationFailureIsNotRetriedAsAnAdLaunchFailure() = runTest {
        val host = Host()
        val failure = IllegalStateException("Navigation failed")
        var caught: Throwable? = null
        var completions = 0
        val requestScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, error -> caught = error },
        )
        try {
            launchAdActivityRequest(
                scope = requestScope,
                lifecycle = host.lifecycle,
                canShow = { true },
                launchAd = { false },
                onClosed = { completions++; throw failure },
            )
            runCurrent()

            assertEquals(1, completions)
            assertSame(failure, caught)
            assertEquals(0, host.lifecycle.observerCount)
        } finally {
            requestScope.cancel()
        }
    }

    private class Host : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

        fun pause() = lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        fun resume() = lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}
