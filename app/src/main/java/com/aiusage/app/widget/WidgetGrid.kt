package com.aiusage.app.widget

import kotlin.math.abs

/**
 * Classic launcher grid model for the widget.
 *
 * A launcher lays widgets out on device grid cells (one cell is roughly
 * 95-130dp tall). The provider card occupies EXACTLY one cell: its height is
 * a per-device constant, never re-derived per size. Resizing the widget only
 * changes how many rows are shown (1 cell → 1 row, 2 cells → 2 rows, …);
 * existing rows keep their height and position.
 *
 * The launcher does not expose its cell height, so the widget recovers it:
 * committed sizes on one device all satisfy `height = cells * cell - bias`
 * (intercell gaps). [recoverPitch] finds the candidate cell height that lets
 * two observed sizes land on whole, distinct row counts; the caller persists
 * the recovered value and reuses it forever. Until then the layout falls
 * back to the safe even-division estimate (never clips).
 */
internal object WidgetGrid {

    /** One committed-size observation. */
    data class Sample(val heightDp: Float, val atMs: Long)

    /**
     * Two samples pair up for calibration only when their renders are this
     * far apart in time: launcher resizes emit transient mid-drag sizes for
     * a couple of seconds only, while committed sizes re-render for minutes,
     * so the gap rule rejects junk pairs by construction.
     */
    const val PAIR_MIN_GAP_MS = 45_000L

    /** Heights are reported as cells * cell - bias (intercell gaps). */
    const val CELL_BIAS_DP = 8F

    /** Generic reference used only before a pitch is recovered (fallback). */
    const val REF_CELL_DP = 96F

    /** Recovered launcher cell heights must fall inside this band. */
    const val MIN_CELL_DP = 70F
    const val MAX_CELL_DP = 220F

    /** Fraction tolerance when snapping a height onto a whole row count. */
    const val LATTICE_TOLERANCE = 0.12F

    const val COLUMNS = 3

    /** Up to [MAX_ROWS] grid cells are counted for a placed widget. */
    const val MAX_ROWS = 8

    /** Flow list renders at most this many providers (matches App cap). */
    const val MAX_FLOW_ITEMS = 15

    /** Sanity bound for pitch recovery row counts. */
    const val MAX_LATTICE_CELLS = 6

    /** Fallback before calibration: rows counted from the generic reference. */
    /** Fallback before calibration: generic 96dp reference cell height. */
    /** Fallback before calibration: generic 96dp reference cell height. */
    fun stretchRows(heightDp: Float): Int =
        Math.round((heightDp + CELL_BIAS_DP) / REF_CELL_DP).coerceIn(1, 6)

    /**
     * Flowing-list item count, the classic community-widget rhythm:
     * 1 cell → 1 provider, 2 cells → 3, 3 cells → 5, 4 cells → 7, …
     * Capped at [MAX_FLOW_ITEMS] visible providers (15).
     */
    fun flowCount(cells: Int): Int =
        (2 * cells.coerceAtLeast(1) - 1).coerceIn(1, MAX_FLOW_ITEMS)

    /** Fixed 1x: grid-cell count for a calibrated pitch. */
    fun cells(heightDp: Float, cellDp: Float): Int =
        Math.round((heightDp + CELL_BIAS_DP) / cellDp).coerceIn(1, MAX_ROWS)

    /**
     * Recover the launcher cell height from committed widget heights. A pair
     * of sizes is "consistent" when one candidate cell height makes BOTH land
     * on whole distinct row counts; the widest consistent pair wins and the
     * median of its candidate band is returned (robust to stray fits).
     * Returns null when fewer than two sizes are known or they are
     * inconsistent (e.g. transient mid-drag values) — the caller then keeps
     * using the even-division fallback and retries on the next update.
     */
    fun recoverPitch(samples: List<Sample>): Float? {
        val sizes = samples
            .filter { it.heightDp in 60f..900f }
            .distinctBy { Math.round(it.heightDp / 10f) }
        if (sizes.size < 2) return null

        var bestPair: Pair<Sample, Sample>? = null
        var bestSpan = -1f
        for (i in sizes.indices) {
            for (j in i + 1 until sizes.size) {
                val a = sizes[i]
                val b = sizes[j]
                val gap = abs(b.atMs - a.atMs)
                if (gap < PAIR_MIN_GAP_MS) continue
                val span = abs(b.heightDp - a.heightDp)
                if (span > bestSpan && pairPitch(a.heightDp, b.heightDp) > 0f) {
                    bestSpan = span
                    bestPair = a to b
                }
            }
        }
        val pair = bestPair ?: return null
        val pitch = pairPitch(pair.first.heightDp, pair.second.heightDp)
        return if (pitch > 0f) pitch else null
    }

    /** Cell height implied by two sizes landing on distinct whole rows. */
    private fun pairPitch(h1: Float, h2: Float): Float {
        val candidates = mutableListOf<Float>()
        var a = MIN_CELL_DP
        while (a <= MAX_CELL_DP) {
            val k1 = (h1 + CELL_BIAS_DP) / a
            val k2 = (h2 + CELL_BIAS_DP) / a
            val r1 = Math.round(k1)
            val r2 = Math.round(k2)
            val latticeOk = r1 in 1..MAX_LATTICE_CELLS && r2 in 1..MAX_LATTICE_CELLS &&
                r1 != r2 &&
                abs(k1 - r1) <= LATTICE_TOLERANCE &&
                abs(k2 - r2) <= LATTICE_TOLERANCE
            if (latticeOk) candidates += a
            a += 0.5F
        }
        if (candidates.isEmpty()) return 0f
        candidates.sort()
        return candidates[candidates.size / 2]
    }
}
