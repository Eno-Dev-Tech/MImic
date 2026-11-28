package com.example.mimic

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mimic.data.SettingsRepository
import com.example.mimic.ui.designsystem.ThemeRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Each store has its own temporary file. Never changes the owner's production datastore. */
@RunWith(AndroidJUnit4::class)
class ThemeRecoveryTest {
    @Test fun appearanceResetRestoresHiddenDefaultAndKeepsOtherPreferences() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "appearance-test-${UUID.randomUUID()}.preferences_pb")
        val owner = SupervisorJob()
        val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + owner), produceFile = { file })
        val repository = SettingsRepository(context, data)
        val retained = stringPreferencesKey("future_personal_account_key")
        try {
            data.edit {
                it[retained] = "retained-fixture"
                it[stringPreferencesKey("recent_searches")] = "Artwork Red"
            }
            repository.setAudio(com.example.mimic.data.AudioPrefs(speed = 1.25f))
            repository.setHomeConfig(com.example.mimic.data.HomeConfig(
                hidden = setOf(com.example.mimic.data.HomeSection.ALBUMS),
                navStyle = com.example.mimic.data.NavStyle.BUBBLE_FLOAT))
            val ids = listOf(ThemeRegistry.DEFAULT_ID, ThemeRegistry.FELT_ORANGE_ID)
            assertTrue(repository.removeTheme(ThemeRegistry.DEFAULT_ID, ids))
            repository.resetAppearancePreferences()
            val settings = repository.settings.first()
            assertEquals(ThemeRegistry.DEFAULT_ID, settings.themeId)
            assertTrue(settings.removedThemeIds.isEmpty())
            assertEquals(com.example.mimic.data.NavStyle.AUTO, settings.homeConfig.navStyle)
            assertEquals(setOf(com.example.mimic.data.HomeSection.ALBUMS), settings.homeConfig.hidden)
            assertEquals(1.25f, settings.audio.speed)
            assertEquals(listOf("Artwork Red"), settings.recentSearches)
            assertEquals("retained-fixture", data.data.first()[retained])
        } finally {
            owner.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun removalPersistsAcrossStoreRestartAndResetPreservesPersonalKeys() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "theme-test-${UUID.randomUUID()}.preferences_pb")
        var owner = SupervisorJob()
        fun store() = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + owner), produceFile = { file })
        var data = store()
        var repository = SettingsRepository(context, data)
        val ids = listOf(ThemeRegistry.DEFAULT_ID, ThemeRegistry.FELT_CLASSIC_ID, ThemeRegistry.FELT_ORANGE_ID)
        val retained = stringPreferencesKey("future_personal_account_key")
        try {
            data.edit { it[retained] = "retained-fixture"; it[stringPreferencesKey("recent_searches")] = "Artwork Red" }
            repository.setThemeId(ThemeRegistry.FELT_ORANGE_ID)
            assertTrue(repository.removeTheme(ThemeRegistry.FELT_ORANGE_ID, ids))
            assertEquals(ThemeRegistry.DEFAULT_ID, repository.settings.first().themeId)
            owner.cancelAndJoin()
            owner = SupervisorJob()
            data = store()
            repository = SettingsRepository(context, data)
            assertEquals(setOf(ThemeRegistry.FELT_ORANGE_ID), repository.settings.first().removedThemeIds)
            assertTrue(repository.removeTheme(ThemeRegistry.DEFAULT_ID, ids))
            assertFalse(repository.removeTheme(ThemeRegistry.FELT_CLASSIC_ID, ids))
            repository.restoreRemovedThemes()
            assertEquals(ThemeRegistry.FELT_CLASSIC_ID, repository.settings.first().themeId)
            assertTrue(repository.settings.first().removedThemeIds.isEmpty())
            repository.removeTheme(ThemeRegistry.FELT_ORANGE_ID, ids)
            repository.resetConfigurablePreferences()
            val defaults = repository.settings.first()
            assertEquals(ThemeRegistry.DEFAULT_ID, defaults.themeId)
            assertTrue(defaults.removedThemeIds.isEmpty())
            assertEquals(listOf("Artwork Red"), defaults.recentSearches)
            assertEquals("retained-fixture", data.data.first()[retained])
        } finally {
            owner.cancelAndJoin()
            file.delete()
        }
    }
}
