package app.odyssey.engine

import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual fun nowEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()

/**
 * NSUserDefaults is plenty for milestone 0: the log is a few KB and this keeps
 * the iOS surface to two stable Foundation calls. The interface is the point —
 * swapping in SQLDelight later touches this file and nothing else.
 */
actual class KeyValueStore actual constructor() {

    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun read(key: String): String? = defaults.stringForKey(key)

    actual fun write(key: String, value: String) {
        defaults.setObject(value, key)
        defaults.synchronize()
    }

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }
}
