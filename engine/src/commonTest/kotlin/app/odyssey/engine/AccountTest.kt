package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountTest {

    private var counter = 0

    /** Unique per test so the shared on-disk KeyValueStore cannot leak between them. */
    private fun user() = "traveller${counter++}_${kotlin.random.Random.nextInt(100_000)}"

    private fun store() = AccountStore()

    private fun register(
        s: AccountStore,
        username: String,
        password: String = "odyssey123",
        confirm: String = password,
        fullName: String = "Chanukya Gattu",
        email: String = "$username@example.com",
        phone: String = "",
    ) = s.register(fullName, phone, username, email, password, confirm)

    // ---------- happy path ----------

    @Test
    fun registerThenSignIn() {
        val s = store()
        val u = user()
        val account = assertNotNull(register(s, u).accountOrNull)
        assertEquals(u, account.username)
        assertEquals("Chanukya Gattu", account.fullName)

        s.signOut()
        assertNull(s.currentAccount())

        val back = assertNotNull(s.signIn(u, "odyssey123").accountOrNull)
        assertEquals(u, back.username)
        assertEquals(u, s.currentAccount()?.username)
    }

    @Test
    fun registrationStartsASessionImmediately() {
        val s = store()
        val u = user()
        register(s, u)
        assertEquals(u, s.currentAccount()?.username, "P2 should land you signed in, not back at P1")
    }

    @Test
    fun usernamesAreCaseInsensitive() {
        val s = store()
        val u = user()
        register(s, u.uppercase())
        assertNotNull(s.signIn(u.lowercase(), "odyssey123").accountOrNull)
    }

    // ---------- passwords ----------

    @Test
    fun thePasswordIsNeverStored() {
        val s = store()
        val u = user()
        val secret = "notmyrealpw9"
        register(s, u, password = secret)

        val raw = KeyValueStore()
        val hash = assertNotNull(raw.read("odyssey.account.$u.hash"))
        val salt = assertNotNull(raw.read("odyssey.account.$u.salt"))
        assertFalse(hash.contains(secret), "the password leaked into storage")
        assertEquals(64, hash.length, "expected a sha256 hex digest")
        assertEquals(32, salt.length)
        assertEquals(sha256((salt + secret).encodeToByteArray()).toHex(), hash)
    }

    @Test
    fun twoAccountsWithTheSamePasswordGetDifferentHashes() {
        val s = store()
        val a = user()
        val b = user()
        register(s, a, password = "identical1")
        register(s, b, password = "identical1")
        val raw = KeyValueStore()
        assertTrue(
            raw.read("odyssey.account.$a.hash") != raw.read("odyssey.account.$b.hash"),
            "salts must make identical passwords hash differently",
        )
    }

    @Test
    fun wrongPasswordIsRejected() {
        val s = store()
        val u = user()
        register(s, u)
        s.signOut()
        assertNull(s.signIn(u, "wrongpass1").accountOrNull)
        assertNull(s.currentAccount(), "a failed sign-in must not start a session")
    }

    @Test
    fun unknownAndWrongPasswordAreIndistinguishable() {
        val s = store()
        val u = user()
        register(s, u)
        s.signOut()
        assertEquals(
            s.signIn("nobody-here-at-all", "whatever1").error,
            s.signIn(u, "wrongpass1").error,
            "the error must not reveal which usernames exist",
        )
    }

    // ---------- validation ----------

    @Test
    fun weakPasswordsAreRefused() {
        val s = store()
        assertEquals("password", (register(s, user(), password = "short1") as AuthResult.Invalid).field)
        assertEquals("password", (register(s, user(), password = "alllettersonly") as AuthResult.Invalid).field)
        assertEquals("password", (register(s, user(), password = "12345678") as AuthResult.Invalid).field)
    }

    @Test
    fun mismatchedConfirmationIsRefused() {
        val s = store()
        val r = register(s, user(), password = "odyssey123", confirm = "odyssey124")
        assertEquals("confirmPassword", (r as AuthResult.Invalid).field)
    }

    @Test
    fun badUsernamesAreRefused() {
        val s = store()
        assertEquals("username", (register(s, "ab") as AuthResult.Invalid).field)
        assertEquals("username", (register(s, "has spaces") as AuthResult.Invalid).field)
        assertEquals("username", (register(s, "a".repeat(21)) as AuthResult.Invalid).field)
    }

    @Test
    fun badEmailsAreRefused() {
        val s = store()
        for (bad in listOf("nope", "no@dot", "@example.com", "a b@example.com", "trailing@dot.")) {
            val r = register(s, user(), email = bad)
            assertEquals("email", (r as AuthResult.Invalid).field, "accepted bad email: $bad")
        }
    }

    @Test
    fun phoneIsOptionalButValidatedWhenGiven() {
        val s = store()
        assertNotNull(register(s, user(), phone = "").accountOrNull, "blank phone is allowed")
        assertNotNull(register(s, user(), phone = "+1 (415) 555-0172").accountOrNull)
        assertEquals("phone", (register(s, user(), phone = "12") as AuthResult.Invalid).field)
        assertEquals("phone", (register(s, user(), phone = "not-a-number") as AuthResult.Invalid).field)
    }

    @Test
    fun duplicateUsernamesAreRefused() {
        val s = store()
        val u = user()
        register(s, u)
        assertEquals("username", (register(s, u) as AuthResult.Invalid).field)
    }

    // ---------- session and the ledger ----------

    @Test
    fun signOutLeavesTheLedgerAlone() {
        val s = store()
        val u = user()
        register(s, u)

        val repo = OdysseyRepository(userId = u)
        val zion = CanonV1.release.byId.getValue("us-ut-zion")
        val now = 1_785_628_800L
        repo.recordVisit(zion.placeId, now - zion.minDwellSeconds - 60, now, Evidence.GPS_VERIFIED)
        val before = repo.snapshot().result.placesCredited

        s.signOut()
        assertNull(s.currentAccount())

        assertEquals(before, OdysseyRepository(userId = u).snapshot().result.placesCredited)
        assertTrue(before.isNotEmpty(), "the visit should have been credited in the first place")
    }

    @Test
    fun oneUsersHistoryIsInvisibleToAnother() {
        val a = user()
        val b = user()
        val repoA = OdysseyRepository(userId = a)
        val zion = CanonV1.release.byId.getValue("us-ut-zion")
        val now = 1_785_628_800L
        repoA.recordVisit(zion.placeId, now - zion.minDwellSeconds - 60, now, Evidence.GPS_VERIFIED)

        assertTrue(OdysseyRepository(userId = b).snapshot().result.placesCredited.isEmpty())
    }
}
