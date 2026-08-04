package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {

    @Test
    fun theDefaultIsTheSystemSetting() {
        KeyValueStore().remove("odyssey.settings.theme")
        assertEquals(ThemeMode.SYSTEM, SettingsStore().themeMode())
    }

    @Test
    fun theChoiceSurvivesARestart() {
        SettingsStore().setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, SettingsStore().themeMode(), "a fresh store should read it back")
        SettingsStore().setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, SettingsStore().themeMode())
    }

    @Test
    fun aCorruptValueFallsBackInsteadOfCrashing() {
        KeyValueStore().write("odyssey.settings.theme", "NEON")
        assertEquals(ThemeMode.SYSTEM, SettingsStore().themeMode())
    }

    @Test
    fun everyModeHasALabel() {
        for (mode in ThemeMode.entries) {
            assertEquals(true, mode.label.isNotBlank(), "$mode has no label")
        }
        assertEquals("Phone setting", ThemeMode.SYSTEM.label)
    }

    @Test
    fun themePreferenceIsNotPartOfTheAccount() {
        // It belongs to the phone, so signing out must not reset it.
        SettingsStore().setThemeMode(ThemeMode.LIGHT)
        AccountStore().signOut()
        assertEquals(ThemeMode.LIGHT, SettingsStore().themeMode())
    }
}
