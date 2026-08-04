package app.odyssey.engine

import kotlin.random.Random

/**
 * P1 / P2 — local accounts.
 *
 * There is no backend yet, so this is deliberately a *complete* auth surface
 * with a local implementation rather than a fake one: real validation, real
 * salted hashing, a real session. Swapping in a server means replacing this
 * class and nothing else, because the ledger only ever sees a `userId`.
 *
 * Passwords are never stored. What lands on disk is `sha256(salt + password)`
 * with a per-account random salt — the same discipline a server would use, so
 * the local build never teaches the wrong habit.
 */
data class Account(
    val username: String,
    val fullName: String,
    val email: String,
    val phone: String?,
)

sealed interface AuthResult {
    data class Success(val account: Account) : AuthResult
    data class Invalid(val field: String, val message: String) : AuthResult

    // Named to avoid colliding with Success.account, which would be an
    // accidental override of a nullable member by a non-null one.
    val accountOrNull: Account? get() = (this as? Success)?.account
    val error: String? get() = (this as? Invalid)?.message
}

class AccountStore(private val store: KeyValueStore = KeyValueStore()) {

    fun register(
        fullName: String,
        phone: String,
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): AuthResult {
        val name = fullName.trim()
        val user = username.trim().lowercase()
        val mail = email.trim().lowercase()
        val tel = phone.trim()

        validateFullName(name)?.let { return it }
        validateUsername(user)?.let { return it }
        validateEmail(mail)?.let { return it }
        validatePhone(tel)?.let { return it }
        validatePassword(password)?.let { return it }

        if (password != confirmPassword) {
            return AuthResult.Invalid("confirmPassword", "Passwords do not match.")
        }
        if (store.read(key(user, "hash")) != null) {
            return AuthResult.Invalid("username", "That username is taken.")
        }

        val salt = newSalt()
        store.write(key(user, "salt"), salt)
        store.write(key(user, "hash"), hash(salt, password))
        store.write(key(user, "fullName"), name)
        store.write(key(user, "email"), mail)
        store.write(key(user, "phone"), tel)

        val account = Account(user, name, mail, tel.ifEmpty { null })
        startSession(account)
        return AuthResult.Success(account)
    }

    fun signIn(username: String, password: String): AuthResult {
        val user = username.trim().lowercase()
        if (user.isEmpty()) return AuthResult.Invalid("username", "Enter your username.")
        if (password.isEmpty()) return AuthResult.Invalid("password", "Enter your password.")

        val salt = store.read(key(user, "salt"))
        val stored = store.read(key(user, "hash"))
        // One message for both cases: never reveal which usernames exist.
        val wrong = AuthResult.Invalid("password", "Username or password is incorrect.")
        if (salt == null || stored == null) return wrong
        if (hash(salt, password) != stored) return wrong

        val account = load(user) ?: return wrong
        startSession(account)
        return AuthResult.Success(account)
    }

    fun currentAccount(): Account? = store.read(SESSION_KEY)?.let { load(it) }

    /**
     * Sign-out clears the session only. The ledger is untouched — events are
     * facts about the world, not session state, and the same device may hold
     * more than one account's history.
     */
    fun signOut() {
        store.remove(SESSION_KEY)
    }

    fun exists(username: String): Boolean = store.read(key(username.trim().lowercase(), "hash")) != null

    // ---------- validation ----------

    private fun validateFullName(v: String): AuthResult.Invalid? = when {
        v.isEmpty() -> AuthResult.Invalid("fullName", "Enter your full name.")
        v.length < 2 -> AuthResult.Invalid("fullName", "That name looks too short.")
        else -> null
    }

    private fun validateUsername(v: String): AuthResult.Invalid? = when {
        v.isEmpty() -> AuthResult.Invalid("username", "Choose a username.")
        v.length < 3 -> AuthResult.Invalid("username", "Usernames are at least 3 characters.")
        v.length > 20 -> AuthResult.Invalid("username", "Usernames are at most 20 characters.")
        !v.all { it.isLetterOrDigit() || it == '_' || it == '.' } ->
            AuthResult.Invalid("username", "Letters, digits, dot and underscore only.")
        else -> null
    }

    private fun validateEmail(v: String): AuthResult.Invalid? {
        if (v.isEmpty()) return AuthResult.Invalid("email", "Enter your email.")
        val at = v.indexOf('@')
        val dot = v.lastIndexOf('.')
        val ok = at > 0 && dot > at + 1 && dot < v.length - 1 && !v.contains(' ')
        return if (ok) null else AuthResult.Invalid("email", "That does not look like an email address.")
    }

    /** Phone stays optional — open decision §8.1. Validated only if supplied. */
    private fun validatePhone(v: String): AuthResult.Invalid? {
        if (v.isEmpty()) return null
        val digits = v.count { it.isDigit() }
        val allowed = v.all { it.isDigit() || it in " +-()" }
        return if (digits in 7..15 && allowed) {
            null
        } else {
            AuthResult.Invalid("phone", "Enter a valid phone number, or leave it blank.")
        }
    }

    private fun validatePassword(v: String): AuthResult.Invalid? = when {
        v.length < 8 -> AuthResult.Invalid("password", "Use at least 8 characters.")
        v.none { it.isDigit() } -> AuthResult.Invalid("password", "Include at least one digit.")
        v.none { it.isLetter() } -> AuthResult.Invalid("password", "Include at least one letter.")
        else -> null
    }

    // ---------- internals ----------

    private fun load(username: String): Account? {
        val full = store.read(key(username, "fullName")) ?: return null
        val mail = store.read(key(username, "email")) ?: return null
        val tel = store.read(key(username, "phone"))
        return Account(username, full, mail, tel?.ifEmpty { null })
    }

    private fun startSession(account: Account) {
        store.write(SESSION_KEY, account.username)
    }

    private fun newSalt(): String {
        val sb = StringBuilder(32)
        repeat(32) { sb.append(HEX[Random.nextInt(16)]) }
        return sb.toString()
    }

    private fun hash(salt: String, password: String): String =
        sha256((salt + password).encodeToByteArray()).toHex()

    private fun key(username: String, field: String) = "odyssey.account.$username.$field"

    private companion object {
        const val SESSION_KEY = "odyssey.session"
        const val HEX = "0123456789abcdef"
    }
}
