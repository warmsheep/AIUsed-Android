package com.aiusage.app.widget

/**
 * Pure helpers for the widget layout. Row/cell sizing lives in [WidgetGrid]
 * (classic launcher grid, no header, no paging); this object only handles
 * capping the visible providers and the three-column chunking of a page.
 */
internal object WidgetPaging {
    const val COLUMNS = 3
    const val MAX_VISIBLE_PROVIDERS = 15

    /** Grid mode shows the first [pageSize] providers (no pager). */
    fun <T> visibleProviders(providers: List<T>, pageSize: Int): List<T> =
        providers.take(pageSize.coerceAtLeast(1))

    /** Chunk providers into rows and pad the last row to [columns] slots. */
    fun <T> gridRows(providers: List<T>, columns: Int = COLUMNS): List<List<T?>> {
        if (providers.isEmpty()) return emptyList()
        val width = columns.coerceAtLeast(1)
        return providers.chunked(width).map { row ->
            row + List(width - row.size) { null }
        }
    }
}
