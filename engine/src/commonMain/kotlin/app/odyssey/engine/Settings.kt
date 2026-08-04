package app.odyssey.engine

/**
 * Appearance. [SYSTEM] follows the phone's own setting, which is the default
 * because the OS already knows whether the user is in bright sun or a tent.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    val label: String
        get() = when (this) {
            SYSTEM -> "Phone setting"
            LIGHT -> "Light"
            DARK -> "Dark"
        }
}

/**
 * Device preferences. Deliberately separate from [AccountStore]: these survive
 * sign-out, because they belong to the phone rather than to the person.
 */
class SettingsStore(private val store: KeyValueStore = KeyValueStore()) {

    fun themeMode(): ThemeMode {
        val raw = store.read(THEME_KEY) ?: return ThemeMode.SYSTEM
        // An unknown value means a downgrade or a corrupt write; fall back
        // rather than crash on launch.
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(mode: ThemeMode) {
        store.write(THEME_KEY, mode.name)
    }

    private companion object {
        const val THEME_KEY = "odyssey.settings.theme"
    }
}
