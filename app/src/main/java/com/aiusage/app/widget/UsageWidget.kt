package com.aiusage.app.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Shader
import android.content.res.Configuration
import kotlin.math.abs
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.compose.runtime.remember
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.aiusage.app.MainActivity
import com.aiusage.app.R
import com.aiusage.app.data.model.ThemeMode
import com.aiusage.app.data.store.WidgetCache
import com.aiusage.app.data.store.WidgetCacheStore
import com.aiusage.app.data.store.WidgetProvider
import com.aiusage.app.data.work.RefreshScheduler
import com.aiusage.app.ui.components.quotaColorStops
import com.aiusage.app.ui.components.remainingTextColor
import com.aiusage.app.ui.theme.Amber
import com.aiusage.app.ui.theme.Coral
import com.aiusage.app.ui.theme.Mint
import com.aiusage.app.ui.theme.TextHi
import com.aiusage.app.ui.theme.TextLow
import com.aiusage.app.util.formatPercent
import com.aiusage.app.util.formatUpdatedAt

class UsageWidget : GlanceAppWidget() {
    // Exact lets LocalSize expose the launcher's real cell height, so the
    // flow list can divide the true height precisely on every phone.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // currentState() subscribes this composition to Glance state, so
            // updateAppWidgetState() elsewhere (cache refresh / theme change)
            // triggers a re-render.
            currentState<androidx.datastore.preferences.core.Preferences>()
            // Read the cache inside the composition too: an update() reuses
            // the running Glance session, so values captured in provideGlance
            // would otherwise stay stale after a refresh/theme change.
            val cache = WidgetCacheStore.read(context)
            val colors = WidgetColors.resolve(context, cache.themeMode)
            GlanceTheme {
                WidgetRoot(cache, colors)
            }
        }
    }
}

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()
}

/**
 * Bump a Glance-state key on every placed widget before redrawing. Glance
 * reuses a live session for update(), so without an observed state change the
 * composition can keep rendering values captured from an older cache.
 */
suspend fun refreshWidgets(context: Context) {
    val appContext = context.applicationContext
    val ids = runCatching {
        GlanceAppWidgetManager(appContext).getGlanceIds(UsageWidget::class.java)
    }.getOrDefault(emptyList())
    val version = System.currentTimeMillis().toInt()
    ids.forEach { glanceId ->
        runCatching {
            updateAppWidgetState(appContext, glanceId) { prefs ->
                prefs[WIDGET_VERSION_KEY] = version
            }
        }
    }
    UsageWidget().updateAll(appContext)
}

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        // Immediate feedback: mark the cache as refreshing and repaint the
        // widget (header shows "刷新中…").
        WidgetCacheStore.markRefreshing(context)
        runCatching { refreshWidgets(context) }
        // Fetch directly through the SAME repository the app uses, so one tap
        // always refreshes — WorkManager alone can sit in the job queue for
        // minutes waiting on constraints (battery/network policy).
        val app = context.applicationContext as? com.aiusage.app.AiUsageApp
        val fetched = app != null && runCatching {
            app.container.repository.refreshAll()
        }.isSuccess
        if (!fetched) {
            RefreshScheduler.refreshNow(context)
        }
        runCatching { refreshWidgets(context) }
    }
}

/**
 * Widget color palette, resolved from the app's theme setting (falls back to
 * the system dark mode so freshly placed widgets match immediately).
 */
private data class WidgetColors(
    val isDark: Boolean,
    val backgroundRes: Int,
    val logoRes: Int,
    val refreshRes: Int,
    val textPrimary: Color,
    val textLow: Color,
    val track: Color,
    val accent: Color,
    val amber: Color,
    val coral: Color,
    val divider: Color,
) {
    companion object {
        fun resolve(context: Context, mode: ThemeMode): WidgetColors {
            val systemDark = (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (mode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            return if (dark) {
                WidgetColors(
                    isDark = true,
                    backgroundRes = R.drawable.widget_bg,
                    logoRes = R.drawable.ic_widget_logo,
                    refreshRes = R.drawable.ic_widget_refresh,
                    textPrimary = TextHi,
                    textLow = TextLow,
                    track = Color(0xFF232B3D),
                    accent = Mint,
                    amber = Amber,
                    coral = Coral,
                    divider = Color(0x14FFFFFF),
                )
            } else {
                WidgetColors(
                    isDark = false,
                    backgroundRes = R.drawable.widget_bg_light,
                    logoRes = R.drawable.ic_widget_logo_light,
                    refreshRes = R.drawable.ic_widget_refresh_light,
                    textPrimary = Color(0xFF141925),
                    textLow = Color(0xFF6F788B),
                    track = Color(0xFFE3E7F0),
                    accent = Color(0xFF0C9F78),
                    amber = Color(0xFFB86900),
                    coral = Color(0xFFD92D4A),
                    divider = Color(0x0D141925),
                )
            }
        }
    }
}

/** Persisted launcher-grid calibration (cell height + uncalibrated samples). */
private const val METRICS_PREFS = "aiusage_widget_metrics"
private const val KEY_PITCH = "launcher_cell_dp"
private const val KEY_SAMPLES = "launcher_cell_samples"
private const val KEY_PENDING = "launcher_cell_pending"
private const val KEY_FORMAT = "metrics_format"
private const val METRICS_FORMAT = 2

/** Seconds on screen before a size is treated as a committed (real) size. */
private const val STABLE_MS = 3_000L

private fun readSamples(prefs: android.content.SharedPreferences, key: String): List<WidgetGrid.Sample> =
    prefs.getString(key, "")
        ?.split(';')
        ?.mapNotNull { part ->
            val fields = part.split('@')
            val h = fields.getOrNull(0)?.toFloatOrNull() ?: return@mapNotNull null
            WidgetGrid.Sample(h, fields.getOrNull(1)?.toLongOrNull() ?: 0L)
        }
        ?: emptyList()

private val WIDGET_VERSION_KEY = intPreferencesKey("widget_version")

@androidx.compose.runtime.Composable
private fun WidgetRoot(cache: WidgetCache, colors: WidgetColors) {
    val size = LocalSize.current
    val context = LocalContext.current

    // Flowing list layout (the classic pattern in community quota widgets):
    // a two-line header, then one provider per line, as many as the height
    // allows. The line count follows the launcher grid on purpose:
    // 1 cell → 1 provider, 2 cells → 3, 3 cells → 5, 4 cells → 7, …
    // The body divides the space below the header evenly over the items, so
    // nothing can ever clip on any device. Within each item the three quota
    // types sit side by side, every column showing its type text and
    // remaining percent above its own bar.
    val heightDp = size.height.value
    val widthDp = size.width.value
    val prefs = context.getSharedPreferences(METRICS_PREFS, Context.MODE_PRIVATE)
    if (prefs.getInt(KEY_FORMAT, 0) != METRICS_FORMAT) {
        prefs.edit().clear().putInt(KEY_FORMAT, METRICS_FORMAT).apply()
    }
    var pitch = prefs.getFloat(KEY_PITCH, 0f)
    if (pitch !in WidgetGrid.MIN_CELL_DP..WidgetGrid.MAX_CELL_DP) pitch = 0f
    val cells: Int
    if (pitch > 0f) {
        cells = WidgetGrid.cells(heightDp, pitch)
    } else {
        // No calibrated pitch yet: record this size and try to recover the
        // lattice; until then use the generic fallback count.
        val now = System.currentTimeMillis()
        var confirmed = readSamples(prefs, KEY_SAMPLES)
        var pending = readSamples(prefs, KEY_PENDING)
        // A height counts as "committed" only when the exact same size was
        // re-rendered at least STABLE_MS later; transient mid-drag sizes are
        // rendered exactly once, so they never become calibratable samples.
        val seen = pending.firstOrNull { abs(it.heightDp - heightDp) < 10f }
        pending = when {
            seen == null ->
                if (widthDp in 150f..480f && heightDp in 60f..900f) {
                    (pending + WidgetGrid.Sample(heightDp, now)).takeLast(8)
                } else {
                    pending
                }
            now - seen.atMs >= STABLE_MS -> {
                confirmed = (confirmed + seen)
                    .distinctBy { Math.round(it.heightDp / 10f) }
                    .takeLast(6)
                pending.filterNot { abs(it.heightDp - heightDp) < 10f && it.atMs == seen.atMs }
            }
            else -> pending
        }
        pending = pending.filter { now - it.atMs < 300_000L }
        val recovered = WidgetGrid.recoverPitch(confirmed)
        if (recovered != null) {
            pitch = recovered
            prefs.edit()
                .putFloat(KEY_PITCH, pitch)
                .putString(KEY_SAMPLES, "")
                .putString(KEY_PENDING, "")
                .apply()
        } else {
            prefs.edit()
                .putString(KEY_SAMPLES, confirmed.joinToString(";") { "${it.heightDp}@${it.atMs}" })
                .putString(KEY_PENDING, pending.joinToString(";") { "${it.heightDp}@${it.atMs}" })
                .apply()
        }
        // Recovered pitch (or the safe 96dp fallback reference) decides THIS
        // render's cell count, so a freshly calibrated widget never mixes
        // formulas — and a pitch of 0 (fallback) must never divide by zero.
        cells = if (pitch > 0f) WidgetGrid.cells(heightDp, pitch)
        else WidgetGrid.stretchRows(heightDp)
    }
    var itemCount = WidgetGrid.flowCount(cells)
    // Two-line header (title over the update stamp) as in the design mock.
    val headerZone = 42f
    val bodyTop = 2f
    var itemHeight = ((heightDp - headerZone - bodyTop) / itemCount)
        .coerceAtLeast(24f)
    val itemChrome = 3f // top breathing inside every list item
    var itemContentHeight = (itemHeight - itemChrome).coerceAtLeast(20f)
    var itemMetrics = WidgetMetrics.fromItem(itemContentHeight.dp, context)
    // The grid rhythm (2n − 1) wins, but content never clips: if not even
    // the tightest typographic level fits a line, show fewer (taller) lines.
    while (!itemMetrics.flowsFit && itemCount > 1) {
        itemCount -= 1
        itemHeight = ((heightDp - headerZone - bodyTop) / itemCount)
            .coerceAtLeast(24f)
        itemContentHeight = (itemHeight - itemChrome).coerceAtLeast(20f)
        itemMetrics = WidgetMetrics.fromItem(itemContentHeight.dp, context)
    }
    val providers = WidgetPaging.visibleProviders(cache.providers, itemCount)
    // Pad to the full slot count so empty capacity renders as a clickable
    // "add supplier" line instead of unexplained blank space.
    val slots = providers + List((itemCount - providers.size).coerceAtLeast(0)) { null }
    if (com.aiusage.app.BuildConfig.DEBUG) {
        Log.d(
            "AIUsageWidget",
            "flow size=$size pitch=$pitch calibrated=${pitch > 0f} cells=$cells " +
                "items=$itemCount itemHeight=$itemHeight providers=${cache.providers.size}",
        )
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(colors.backgroundRes))
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(GlanceModifier.fillMaxSize()) {
            WidgetHeader(cache, colors)
            Spacer(GlanceModifier.height(bodyTop.dp))
            when {
                providers.isEmpty() -> EmptyWidget(colors)
                else -> {
                    // RemoteViews Columns cap at 10 children; chunk the flow
                    // rows into nested columns so 15 rows stay safe. With a
                    // single provider the one item fills the body height.
                    val chunkCount = ((itemCount + 9) / 10)
                    val perChunk = ((itemCount + chunkCount - 1) / chunkCount)
                    slots.chunked(perChunk).forEach { chunk ->
                        Column(GlanceModifier.fillMaxWidth()) {
                            chunk.forEach { provider ->
                                ProviderFlowRow(
                                    provider,
                                    itemContentHeight.dp,
                                    itemMetrics,
                                    colors,
                                    single = itemCount == 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Layout metrics for one flowing list item. Each item = name row (status
 * square + name) above a single band of three quota columns; every column is
 * "type label … remaining percent" above its own bar. Levels shrink fonts
 * for smaller line heights so every item fits without clipping; TextView
 * line heights (fontPadding) are used so the plan matches the render.
 */
private data class WidgetMetrics(
    val quotaGap: Dp,
    val nameSize: TextUnit,
    val secondarySize: TextUnit,
    val labelSize: TextUnit,
    val valueSize: TextUnit,
    val barHeight: Dp,
    /** True when this level's content really fits the line height. */
    val flowsFit: Boolean,
) {
    companion object {
        fun fromItem(unit: Dp, context: Context): WidgetMetrics {
            fun textLine(lab: Float, value: Float) =
                maxOf(line(lab, context), line(value, context, bold = true))

            data class Level(
                val name: Float,
                val lab: Float,
                val value: Float,
                val bar: Float,
                val pad: Float,
            )

            val levels = listOf(
                Level(12f, 8f, 9f, 7f, 3f),
                Level(11f, 7.5f, 8.5f, 6f, 3f),
                Level(10f, 7f, 8f, 6f, 2f),
                Level(9.5f, 6.5f, 7.5f, 5f, 2f),
                Level(9f, 6f, 7f, 5f, 2f),
                Level(8.5f, 5.5f, 6.5f, 4f, 2f),
            )
            fun contentHeight(l: Level): Float {
                val nameLine = line(l.name, context, bold = true) + 1f
                val labelRow = textLine(l.lab, l.value) + 2f // gap to the bar
                val bar = l.bar
                return l.pad + nameLine + (labelRow + bar).coerceAtLeast(0f)
            }
            // Per-level bottom reserve: roomy levels keep 3dp, dense trade to 2dp.
            fun bottomReserve(l: Level): Float = if (l.pad <= 2f) 2f else 3f
            val chosen = levels.firstOrNull {
                contentHeight(it) + 3.6f <= unit.value - bottomReserve(it)
            } ?: levels.last()
            val chosenReserve = bottomReserve(chosen)
            val flowsFit = contentHeight(chosen) + 3f * 1.2f <= unit.value - chosenReserve
            // Leftover spreads between the name row and the quota band.
            val gap = ((unit.value - chosenReserve - contentHeight(chosen)) / 3f)
                .coerceIn(1.2f, 12f)
            return WidgetMetrics(
                quotaGap = gap.dp,
                nameSize = chosen.name.sp,
                secondarySize = chosen.lab.sp,
                labelSize = chosen.lab.sp,
                valueSize = chosen.value.sp,
                barHeight = chosen.bar.dp,
                flowsFit = flowsFit,
            )
        }

        /** Real text line height on the current device, including font scale. */
        private fun line(sp: Float, context: Context, bold: Boolean = false): Float {
            val dm = context.resources.displayMetrics
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = sp * dm.scaledDensity
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val fm = paint.fontMetrics
            // TextView (includeFontPadding) measures bottom − top, which is
            // taller than descent − ascent; use it or the plan under-fits.
            return (fm.bottom - fm.top) / dm.density
        }
    }
}

/** Header: big title over the update stamp; refresh on the right. */
@androidx.compose.runtime.Composable
private fun WidgetHeader(cache: WidgetCache, colors: WidgetColors) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(colors.logoRes),
            contentDescription = null,
            modifier = GlanceModifier
                .size(24.dp)
                .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
        )
        Spacer(GlanceModifier.width(8.dp))
        // Tapping the header text zone opens the main app, like the logo.
        Column(
            GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
        ) {
            Text(
                text = "AI 用量",
                style = TextStyle(
                    color = ColorProvider(colors.textPrimary),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(1.dp))
            val refreshing = cache.refreshingAt > 0 &&
                System.currentTimeMillis() - cache.refreshingAt < 120_000L
            val stamp = when {
                cache.updatedAt > 0 -> formatUpdatedAt(cache.updatedAt).removeSuffix("更新")
                else -> "尚未更新"
            }
            val count = if (cache.providers.size > 0) " · ${cache.providers.size} 个" else ""
            Text(
                text = if (refreshing) "刷新中…" else "$stamp$count",
                style = TextStyle(color = ColorProvider(colors.textLow), fontSize = 8.sp),
                maxLines = 1,
            )
        }
        Box(
            modifier = GlanceModifier
                .width(34.dp)
                .height(34.dp)
                .clickable(actionRunCallback<RefreshWidgetAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(colors.refreshRes),
                contentDescription = "刷新额度",
                modifier = GlanceModifier.size(20.dp),
            )
        }
    }
}

/**
 * One provider per line. Each item = status-colored square + name above one
 * band of three quota columns (5H / 周 / 月), every column showing its label
 * and remaining percent above its own bar. Single-provider widgets fill the
 * body height and center the band. null renders an "add supplier" line.
 */
@androidx.compose.runtime.Composable
private fun ProviderFlowRow(
    provider: WidgetProvider?,
    contentHeight: Dp,
    metrics: WidgetMetrics,
    colors: WidgetColors,
    single: Boolean,
) {
    if (provider == null) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(contentHeight)
                .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "＋ 添加供应商",
                style = TextStyle(
                    color = ColorProvider(colors.textLow),
                    fontSize = (metrics.secondarySize.value + 1f).sp,
                ),
                maxLines = 1,
            )
        }
        return
    }
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(contentHeight)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(provider, colors, (metrics.nameSize.value * 0.72f).dp)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = provider.name,
                style = TextStyle(
                    color = ColorProvider(colors.textPrimary),
                    fontSize = metrics.nameSize,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            // Plan name (dim) then balance (accent bold) both sit at the
            // right edge of the name row; "过期" only fills the gap when the
            // snapshot has neither (the dot already carries the error color).
            if (provider.planName != null) {
                Text(
                    text = provider.planName ?: "",
                    style = TextStyle(
                        color = ColorProvider(colors.textLow),
                        fontSize = metrics.secondarySize,
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(4.dp))
            }
            when {
                provider.balance != null -> Text(
                    text = provider.balance ?: "",
                    style = TextStyle(
                        color = ColorProvider(colors.accent),
                        fontSize = metrics.secondarySize,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                provider.hasError -> Text(
                    text = "过期",
                    style = TextStyle(color = ColorProvider(colors.amber), fontSize = metrics.secondarySize),
                    maxLines = 1,
                )
            }
        }
        if (single) Spacer(GlanceModifier.defaultWeight()) else Spacer(GlanceModifier.height(metrics.quotaGap))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            QuotaStat("5H", provider.fiveHour, metrics, colors)
            Spacer(GlanceModifier.width(10.dp))
            QuotaStat("周", provider.weekly, metrics, colors)
            Spacer(GlanceModifier.width(10.dp))
            QuotaStat("月", provider.monthly, metrics, colors)
        }
        if (single) Spacer(GlanceModifier.defaultWeight())
    }
}

/** One quota column: "5H … 96%" line above a full-width bar. */
@androidx.compose.runtime.Composable
private fun androidx.glance.layout.RowScope.QuotaStat(
    label: String,
    percent: Double?,
    metrics: WidgetMetrics,
    colors: WidgetColors,
) {
    Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(colors.textLow),
                    fontSize = metrics.labelSize,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = formatPercent(percent),
                style = TextStyle(
                    color = ColorProvider(
                        if (percent == null) colors.textLow else remainingTextColor(percent, colors.isDark),
                    ),
                    fontSize = metrics.valueSize,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        GradientMiniBar(percent, metrics.barHeight, colors)
    }
}

@androidx.compose.runtime.Composable
private fun StatusDot(provider: WidgetProvider?, colors: WidgetColors, dotSize: Dp) {
    val color = when {
        provider == null -> colors.accent
        provider.hasError -> colors.coral
        provider.fiveHour != null -> remainingTextColor(provider.fiveHour, colors.isDark)
        else -> colors.accent
    }
    Box(
        modifier = GlanceModifier
            .size(dotSize)
            .background(ColorProvider(color)),
    ) {}
}

@androidx.compose.runtime.Composable
private fun EmptyWidget(colors: WidgetColors) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))),
    ) {
        Text(
            text = "还没有供应商",
            style = TextStyle(
                color = ColorProvider(colors.textPrimary),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = "点按打开 AI用量，添加第一个 Key",
            style = TextStyle(color = ColorProvider(colors.textLow), fontSize = 9.sp),
            maxLines = 1,
        )
    }
}

@androidx.compose.runtime.Composable
private fun GradientMiniBar(percent: Double?, barHeight: Dp, colors: WidgetColors) {
    val p = (percent ?: 0.0).coerceIn(0.0, 100.0)
    val track = remember(colors.track) { roundedBarBitmap(colors.track) }
    val fill = remember(p) { quotaBarBitmap(p) }
    Box(
        modifier = GlanceModifier.fillMaxWidth().height(barHeight),
    ) {
        Image(
            provider = ImageProvider(track),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxWidth().height(barHeight),
        )
        if (p > 0.0) {
            Image(
                provider = ImageProvider(fill),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxWidth().height(barHeight),
            )
        }
    }
}

private fun quotaBarBitmap(percent: Double): Bitmap {
    val width = 256
    val height = 16
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val fillWidth = (percent.coerceIn(0.0, 100.0) / 100.0 * width).toFloat()
    if (fillWidth <= 0f) return bitmap
    val canvas = Canvas(bitmap)
    val stops = quotaColorStops()
    val shader = LinearGradient(
        0f, 0f, width.toFloat(), 0f,
        stops.map { it.second.toArgb() }.toIntArray(),
        stops.map { it.first }.toFloatArray(),
        Shader.TileMode.CLAMP,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
    val radius = minOf(height / 2f, fillWidth / 2f)
    canvas.drawRoundRect(0f, 0f, fillWidth, height.toFloat(), radius, radius, paint)
    return bitmap
}

private fun roundedBarBitmap(color: Color): Bitmap {
    val width = 256
    val height = 16
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
    Canvas(bitmap).drawRoundRect(
        0f, 0f, width.toFloat(), height.toFloat(),
        height / 2f, height / 2f, paint,
    )
    return bitmap
}
