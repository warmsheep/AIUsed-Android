package com.aiusage.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiusage.app.data.model.ProviderConfig
import com.aiusage.app.data.model.ProviderKind
import com.aiusage.app.data.model.QuotaSlot
import com.aiusage.app.data.model.Region
import com.aiusage.app.data.model.ThemeMode
import com.aiusage.app.data.model.UsageSnapshot
import com.aiusage.app.ui.add.AddProviderSheet
import com.aiusage.app.ui.components.ProviderCard
import com.aiusage.app.ui.home.EmptyState
import com.aiusage.app.ui.theme.AiUsageTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyStateShowsPrimaryAction() {
        compose.setContent {
            AiUsageTheme(mode = ThemeMode.DARK) {
                EmptyState(onAdd = {})
            }
        }
        compose.onNodeWithText("把 AI 额度，装进口袋").assertIsDisplayed()
        compose.onNodeWithText("添加第一个供应商").assertIsDisplayed()
    }

    @Test
    fun unsupportedSlotsKeepTheirPlaceholders() {
        val provider = ProviderConfig(
            id = "p1",
            kind = ProviderKind.ZHIPU,
            region = Region.CN,
            encryptedKey = "not-a-real-key",
            createdAt = 0,
        )
        val snapshot = UsageSnapshot(
            providerId = "p1",
            planName = "GLM Coding Plan",
            fiveHour = QuotaSlot.of(82.0),
            weekly = QuotaSlot.of(64.0),
            fetchedAt = System.currentTimeMillis(),
            attemptedAt = System.currentTimeMillis(),
        )
        compose.setContent {
            AiUsageTheme(mode = ThemeMode.DARK) {
                ProviderCard(
                    provider = provider,
                    snapshot = snapshot,
                    isRefreshing = false,
                    onRefresh = {},
                    onEdit = {},
                    onRemove = {},
                )
            }
        }
        compose.onNodeWithText("5小时余量").assertIsDisplayed()
        compose.onNodeWithText("本周余量").assertIsDisplayed()
        compose.onNodeWithText("本月余量").assertIsDisplayed()
        compose.onNodeWithText("余额").assertIsDisplayed()
        compose.onAllNodesWithText("未提供此额度").assertCountEquals(1)
        compose.onAllNodesWithText("未提供余额").assertCountEquals(1)
        compose.onNodeWithText("82%").assertIsDisplayed()
        compose.onNodeWithText("64%").assertIsDisplayed()
    }

    @Test
    fun addSheetOffersAllProviders() {
        compose.setContent {
            AiUsageTheme(mode = ThemeMode.DARK) {
                AddProviderSheet(
                    existingKinds = emptySet(),
                    onSubmit = { _, _, _ -> Result.success(Unit) },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("添加供应商").assertIsDisplayed()
        compose.onNodeWithText("智谱 GLM").assertIsDisplayed()
        compose.onNodeWithText("MiniMax").assertIsDisplayed()
        compose.onNodeWithText("OpenCode Go").assertIsDisplayed()
        compose.onNodeWithText("DeepSeek").assertIsDisplayed()
        compose.onNodeWithText("Kimi").assertIsDisplayed()
    }
}
