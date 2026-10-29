package com.aiusage.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPagingTest {

    // Classic launcher grid + flowing list: list line count follows the
    // launcher cells with the community rhythm 1 cell → 1 provider,
    // 2 cells → 3, 3 cells → 5, 4 cells → 7 (2n − 1), capped at 15.

    @Test
    fun flowCountMatchesTheSpokenRhythm() {
        assertEquals(1, WidgetGrid.flowCount(1))
        assertEquals(3, WidgetGrid.flowCount(2))
        assertEquals(5, WidgetGrid.flowCount(3))
        assertEquals(7, WidgetGrid.flowCount(4))
        assertEquals(9, WidgetGrid.flowCount(5))
        assertEquals(11, WidgetGrid.flowCount(6))
    }

    @Test
    fun flowCountCapsAtFifteenProviders() {
        assertEquals(15, WidgetGrid.flowCount(9))
        assertEquals(15, WidgetGrid.flowCount(100))
        assertEquals(1, WidgetGrid.flowCount(0))
    }

    @Test
    fun calibratedCellHeightGivesTheExpectedCellCount() {
        // Pixel launcher: cell ≈ 126.5dp → 237dp widget spans 2 cells.
        assertEquals(2, WidgetGrid.cells(236.95f, 124.5f))
        assertEquals(3, WidgetGrid.cells(363.43f, 124.5f))
        assertEquals(4, WidgetGrid.cells(489.9f, 124.5f))
        assertEquals(5, WidgetGrid.cells(616.38f, 124.5f))
        // ~99dp cells (HyperOS/MIUI): 190dp spans 2 cells.
        assertEquals(2, WidgetGrid.cells(190f, 99f))
        assertEquals(3, WidgetGrid.cells(285f, 99f))
    }

    @Test
    fun fallbackCellCountStaysSaneBeforeCalibration() {
        assertEquals(3, WidgetGrid.stretchRows(237f))
        assertEquals(2, WidgetGrid.stretchRows(174f))
        assertEquals(6, WidgetGrid.stretchRows(700f))
        assertTrue(WidgetGrid.stretchRows(88f) in 1..WidgetGrid.MAX_ROWS)
    }

    @Test
    fun visibleProvidersCapAtSlotCount() {
        val providers = (1..20).map { "p$it" }
        assertEquals(9, WidgetPaging.visibleProviders(providers, 9).size)
        assertEquals(providers.take(9), WidgetPaging.visibleProviders(providers, 9))
        assertEquals((1..15).map { "p$it" }, WidgetPaging.visibleProviders(providers, WidgetGrid.MAX_FLOW_ITEMS))
    }

    // ---- pitch recovery (launcher cell height from committed sizes) -------

    private fun sample(h: Float, seconds: Long) = WidgetGrid.Sample(h, seconds * 1_000L)

    @Test
    fun singleSampleCannotRecoverPitch() {
        assertNull(WidgetGrid.recoverPitch(listOf(WidgetGrid.Sample(237f, 0L))))
        assertNull(WidgetGrid.recoverPitch(emptyList()))
    }

    @Test
    fun pixelEmulatorSizesRecoverItsBigCells() {
        val pitch = WidgetGrid.recoverPitch(listOf(sample(237f, 0), sample(363.43f, 90)))!!
        assertTrue("pitch $pitch", pitch in 118f..131f)
        val wider = WidgetGrid.recoverPitch(listOf(sample(237f, 0), sample(616.38f, 90)))!!
        assertTrue("wider $wider", wider in 116f..133f)
    }

    @Test
    fun narrowCellLaunchersRecoverTheirSmallerCells() {
        val pitch = WidgetGrid.recoverPitch(listOf(sample(190f, 0), sample(479f, 90)))!!
        assertTrue("pitch $pitch", pitch in 91f..107f)
        val near = WidgetGrid.recoverPitch(listOf(sample(190f, 0), sample(285f, 90)))!!
        assertTrue("near $near", near in 91f..107f)
    }

    @Test
    fun transientSamplesWithinSecondsFailRecovery() {
        assertNull(
            WidgetGrid.recoverPitch(
                listOf(sample(237f, 0), sample(305f, 3), sample(363.43f, 5)),
            ),
        )
        assertNull(WidgetGrid.recoverPitch(listOf(sample(237f, 0), sample(305f, 1))))
    }

    @Test
    fun recoveryPicksTheWidestConsistentPairIgnoringJunk() {
        val pitch = WidgetGrid.recoverPitch(
            listOf(sample(237f, 0), sample(305f, 60), sample(363.43f, 120)),
        )!!
        assertTrue("pitch $pitch", pitch in 118f..131f)
        val junkPair = WidgetGrid.recoverPitch(listOf(sample(237f, 0), sample(305f, 90)))!!
        assertTrue("junkPair $junkPair", junkPair in 75f..86f)
    }
}
