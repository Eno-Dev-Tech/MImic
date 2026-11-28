package com.example.mimic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mimic.ui.designsystem.ThemeRegistry
import com.example.mimic.ui.designsystem.toColorScheme
import com.example.mimic.ui.navigation.AnimatedMimicNavigation
import com.example.mimic.ui.navigation.MotionPalette
import com.example.mimic.ui.navigation.NavigationMotion
import com.example.mimic.ui.navigation.TopLevel
import com.example.mimic.ui.navigation.mimicMotionTabs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real rendering and tab selection on both Felt palettes for every motion. */
@RunWith(AndroidJUnit4::class)
class FeltNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun all_six_styles_select_every_destination_in_both_felt_themes() {
        var themeId by mutableStateOf(ThemeRegistry.FELT_CLASSIC_ID)
        var motion by mutableStateOf(NavigationMotion.SLIDING_PILL)
        var selected by mutableStateOf(TopLevel.HOME.route)
        compose.setContent {
            MaterialTheme(colorScheme = ThemeRegistry.byId(themeId).colors.toColorScheme()) {
                AnimatedMimicNavigation(
                    tabs = mimicMotionTabs, selectedKey = selected, onSelect = { selected = it },
                    motion = motion,
                    palette = if (themeId == ThemeRegistry.FELT_CLASSIC_ID)
                        MotionPalette.ClassicFelt else MotionPalette.OrangeFelt,
                )
            }
        }
        listOf(ThemeRegistry.FELT_CLASSIC_ID, ThemeRegistry.FELT_ORANGE_ID).forEach { theme ->
            NavigationMotion.entries.forEach { style ->
                compose.runOnIdle { themeId = theme; motion = style }
                TopLevel.visible.forEach { tab ->
                    compose.onNodeWithContentDescription(tab.label).performClick().assertIsSelected()
                }
            }
        }
    }
}
