package com.example.mimic

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mimic.ui.ThemeRemovalButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeRemovalControlTest {
    @get:Rule val compose = createComposeRule()
    @Test fun navigationPickerExposesSelectedStateForAccessibility() {
        val selected = mutableStateOf(com.example.mimic.data.NavStyle.STANDARD)
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    com.example.mimic.ui.NavigationStylePicker(selected.value,
                        com.example.mimic.ui.designsystem.ThemeRegistry.FELT_ORANGE_ID) { selected.value = it }
                }
            }
        }
        compose.onNodeWithText("Standard").assertIsSelected()
        compose.onNodeWithText("Bubble Float").performScrollTo().performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(com.example.mimic.data.NavStyle.BUBBLE_FLOAT, selected.value) }
    }

    @Test fun minusHasAccessibleTargetAndLastThemeIsDisabled() {
        val enabled = mutableStateOf(true)
        var clicks = 0
        compose.setContent { MaterialTheme { ThemeRemovalButton("Felt Orange", enabled.value, { clicks++ }) } }
        val node = compose.onNodeWithContentDescription("Remove Felt Orange")
        node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
        compose.runOnIdle { assertEquals(1, clicks); enabled.value = false }
        node.assertIsNotEnabled()
    }
}
