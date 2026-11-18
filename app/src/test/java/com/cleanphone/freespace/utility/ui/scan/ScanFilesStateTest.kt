package com.cleanphone.freespace.utility.ui.scan

import com.cleanphone.freespace.utility.bean.JunkChildBean
import com.cleanphone.freespace.utility.bean.JunkMotherBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFilesStateTest {

    @Test
    fun deepCopyCategories_isUnchangedWhenOriginalIsMutated() {
        val originalChild = JunkChildBean("Cache", "/cache", 128L, isCheck = true)
        val originalGroup = JunkMotherBean(
            name = "Junk",
            size = 128L,
            junkChildrenItems = mutableListOf(originalChild),
            isChecked = true,
            isShowCheck = false,
        )

        val copy = listOf(originalGroup).deepCopyCategories(showChecks = true)

        originalGroup.name = "Changed"
        originalGroup.size = 256L
        originalGroup.isChecked = false
        originalGroup.isShowCheck = false
        originalGroup.junkChildrenItems.clear()
        originalChild.name = "Changed child"
        originalChild.path = "/changed"
        originalChild.size = 256L
        originalChild.isCheck = false

        val copiedGroup = copy.single()
        val copiedChild = copiedGroup.junkChildrenItems.single()
        assertNotSame(originalGroup, copiedGroup)
        assertNotSame(originalGroup.junkChildrenItems, copiedGroup.junkChildrenItems)
        assertNotSame(originalChild, copiedChild)
        assertEquals("Junk", copiedGroup.name)
        assertEquals(128L, copiedGroup.size)
        assertTrue(copiedGroup.isChecked)
        assertTrue(copiedGroup.isShowCheck)
        assertEquals("Cache", copiedChild.name)
        assertEquals("/cache", copiedChild.path)
        assertEquals(128L, copiedChild.size)
        assertTrue(copiedChild.isCheck)
    }

    @Test
    fun deepCopyCategories_originalIsUnchangedWhenCopyIsMutated() {
        val originalChild = JunkChildBean("Log", "/log", 64L, isCheck = false)
        val originalGroup = JunkMotherBean(
            name = "Residuals",
            size = 64L,
            junkChildrenItems = mutableListOf(originalChild),
            isChecked = false,
            isShowCheck = true,
        )

        val copiedGroup = listOf(originalGroup).deepCopyCategories().single()
        val copiedChild = copiedGroup.junkChildrenItems.single()
        copiedGroup.name = "Changed"
        copiedGroup.size = 512L
        copiedGroup.isChecked = true
        copiedGroup.isShowCheck = false
        copiedGroup.junkChildrenItems.clear()
        copiedChild.name = "Changed child"
        copiedChild.path = "/changed"
        copiedChild.size = 512L
        copiedChild.isCheck = true

        assertEquals("Residuals", originalGroup.name)
        assertEquals(64L, originalGroup.size)
        assertFalse(originalGroup.isChecked)
        assertTrue(originalGroup.isShowCheck)
        assertEquals(1, originalGroup.junkChildrenItems.size)
        assertEquals("Log", originalChild.name)
        assertEquals("/log", originalChild.path)
        assertEquals(64L, originalChild.size)
        assertFalse(originalChild.isCheck)
    }
}
