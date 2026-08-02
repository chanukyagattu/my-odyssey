package app.odyssey.engine

/**
 * The on-device blob store. Content-addressed: `put` returns the sha256 of the
 * bytes and that hash is the filename, so writing the same photo twice costs
 * one copy and an identifier can never drift from its content.
 *
 * There is no cloud counterpart and no upload path anywhere in this interface.
 * That is the product decision made structural: nothing above this layer has an
 * API it could use to send a photo off the device.
 */
expect class MediaStore() {

    /** Writes the bytes if absent and returns their content address. */
    fun put(bytes: ByteArray): String

    fun exists(mediaId: String): Boolean

    fun read(mediaId: String): ByteArray?

    /** Reclaims disk. The ledger event stays; the memory renders as a tombstone. */
    fun delete(mediaId: String): Boolean

    fun totalBytes(): Long

    fun count(): Int

    /**
     * The container is included in the user's own iCloud device backup by
     * default — their storage, never ours. Users on a small plan can opt out
     * and accept that a lost phone loses the library.
     */
    fun setExcludedFromBackup(excluded: Boolean)

    fun isExcludedFromBackup(): Boolean
}
