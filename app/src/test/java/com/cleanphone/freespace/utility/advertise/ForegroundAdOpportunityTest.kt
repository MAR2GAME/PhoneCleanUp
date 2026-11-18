package com.cleanphone.freespace.utility.advertise

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundAdOpportunityTest {
    private val opportunity = ForegroundAdOpportunity()

    @Test
    fun coldStartDoesNotCreateAForegroundAd() {
        opportunity.onForeground(isShowingAd = false)
        assertFalse(opportunity.take(eligible = true))
    }

    @Test
    fun returnWaitsForAnEligiblePageAndRunsOnce() {
        opportunity.onBackground(isShowingAd = false)
        opportunity.onForeground(isShowingAd = false)
        assertFalse(opportunity.take(eligible = false))
        assertTrue(opportunity.take(eligible = true))
        assertFalse(opportunity.take(eligible = true))
    }

    @Test
    fun suppressionAppliesToOneActualReturn() {
        opportunity.suppressNextReturn()
        opportunity.onForeground(isShowingAd = false)
        opportunity.onBackground(isShowingAd = false)
        opportunity.onForeground(isShowingAd = false)
        assertFalse(opportunity.take(eligible = true))

        opportunity.onBackground(isShowingAd = false)
        opportunity.onForeground(isShowingAd = false)
        assertTrue(opportunity.take(eligible = true))
    }

    @Test
    fun disabledSplashDoesNotDeferAnAdIntoTheNextPage() {
        opportunity.setEnabled(false)
        opportunity.onBackground(isShowingAd = false)
        opportunity.onForeground(isShowingAd = false)
        opportunity.setEnabled(true)
        assertFalse(opportunity.take(eligible = true))
    }

    @Test
    fun adOwnedBackgroundDoesNotCreateAnotherAd() {
        opportunity.onBackground(isShowingAd = true)
        opportunity.onForeground(isShowingAd = false)
        assertFalse(opportunity.take(eligible = true))
    }
}
