package app.odyssey.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * NIST vectors plus the block-boundary cases where a hand-rolled padding
 * implementation goes wrong: exactly 55, 56, 63, 64 and 119 bytes.
 */
class Sha256Test {

    private fun hash(s: String) = sha256(s.encodeToByteArray()).toHex()

    @Test
    fun knownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash("abc"))
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash("hello world"))
    }

    @Test
    fun everyByteValue() {
        val all = ByteArray(256) { it.toByte() }
        assertEquals("40aff2e9d2d8922e47afd4648e6967497158785fbd1da870e7110266bf944880", sha256(all).toHex())
    }

    @Test
    fun paddingBoundaries() {
        // 55 fits in one block with the length; 56 forces a second block.
        assertEquals(
            "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            sha256(ByteArray(55) { 'a'.code.toByte() }).toHex(),
        )
        assertEquals(
            "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            sha256(ByteArray(56) { 'a'.code.toByte() }).toHex(),
        )
        assertEquals(
            "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
            sha256(ByteArray(64) { 'a'.code.toByte() }).toHex(),
        )
        assertEquals(
            "c2e686823489ced2017f6059b8b239318b6364f6dcd835d0a519105a1eadd6e4",
            sha256(ByteArray(1000) { 'A'.code.toByte() }).toHex(),
        )
    }

    @Test
    fun hexEncodingIsLowercaseAndZeroPadded() {
        assertEquals("000102fdfeff", byteArrayOf(0, 1, 2, -3, -2, -1).toHex())
        assertEquals(64, sha256(ByteArray(0)).toHex().length)
    }

    @Test
    fun contentAddressingDedupesAndDiscriminates() {
        val a = "a photo".encodeToByteArray()
        val b = "a photo".encodeToByteArray()
        val c = "a photo ".encodeToByteArray()
        assertEquals(mediaIdOf(a), mediaIdOf(b), "identical bytes must share an id")
        assertNotEquals(mediaIdOf(a), mediaIdOf(c), "one trailing space must change the id")
    }
}
