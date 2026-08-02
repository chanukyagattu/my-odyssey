package app.odyssey.engine

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.posix.memcpy

/**
 * The library lives in Application Support inside the app's own container —
 * private to this app, invisible in Files, wiped when the app is deleted, and
 * included in the *user's* iCloud device backup unless they opt out.
 *
 * This file is the only place in the project that touches raw pointers. Every
 * decision above it (hashing, evidence, accounting) is plain Kotlin verified on
 * the JVM; this is plumbing.
 */
@OptIn(ExperimentalForeignApi::class)
actual class MediaStore actual constructor() {

    private val fm = NSFileManager.defaultManager

    private val dir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomainsCompat()
        val path = "$base/MyOdyssey/media"
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        }
        path
    }

    private fun pathFor(mediaId: String) = "$dir/$mediaId.blob"

    actual fun put(bytes: ByteArray): String {
        val id = mediaIdOf(bytes)
        val path = pathFor(id)
        if (!fm.fileExistsAtPath(path)) {
            bytes.toNSData().writeToFile(path, atomically = true)
        }
        return id
    }

    actual fun exists(mediaId: String): Boolean = fm.fileExistsAtPath(pathFor(mediaId))

    actual fun read(mediaId: String): ByteArray? {
        val path = pathFor(mediaId)
        if (!fm.fileExistsAtPath(path)) return null
        // NSFileManager.contentsAtPath is a plain instance method. NSData's
        // dataWithContentsOfFile: is an ObjC class factory, which Kotlin exposes
        // as a companion extension needing its own import — avoided here.
        val data = fm.contentsAtPath(path) ?: return null
        return data.toByteArray()
    }

    actual fun delete(mediaId: String): Boolean {
        val path = pathFor(mediaId)
        if (!fm.fileExistsAtPath(path)) return false
        return fm.removeItemAtPath(path, null)
    }

    actual fun totalBytes(): Long {
        val names = fm.contentsOfDirectoryAtPath(dir, null) ?: return 0L
        var total = 0L
        for (name in names) {
            val attrs = fm.attributesOfItemAtPath("$dir/$name", null) ?: continue
            val size = attrs["NSFileSize"] as? NSNumberCompat
            total += size?.longLongValue ?: 0L
        }
        return total
    }

    actual fun count(): Int = fm.contentsOfDirectoryAtPath(dir, null)?.size ?: 0

    actual fun setExcludedFromBackup(excluded: Boolean) {
        val url = NSURL.fileURLWithPath(dir)
        url.setResourceValue(excluded, forKey = NSURLIsExcludedFromBackupKey, error = null)
        NSUserDefaults.standardUserDefaults.setBool(excluded, BACKUP_KEY)
    }

    actual fun isExcludedFromBackup(): Boolean =
        NSUserDefaults.standardUserDefaults.boolForKey(BACKUP_KEY)

    private companion object {
        const val BACKUP_KEY = "odyssey.media.excludedFromBackup"
    }
}

private typealias NSNumberCompat = platform.Foundation.NSNumber

@OptIn(ExperimentalForeignApi::class)
private fun NSSearchPathForDirectoriesInDomainsCompat(): String {
    val paths = platform.Foundation.NSSearchPathForDirectoriesInDomains(
        directory = platform.Foundation.NSApplicationSupportDirectory,
        domainMask = platform.Foundation.NSUserDomainMask,
        expandTilde = true,
    )
    return paths.firstOrNull() as? String ?: platform.Foundation.NSTemporaryDirectory()
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return out
}
