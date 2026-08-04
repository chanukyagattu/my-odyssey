package app.odyssey

import app.odyssey.engine.LatLng
import app.odyssey.engine.MediaKind
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.PhotosUI.imagesFilter
import platform.PhotosUI.videosFilter
import platform.UIKit.UIApplication
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * The system photo picker.
 *
 * `PHPickerViewController` runs **outside this app's process**: the user
 * chooses photos and iOS hands over only those. It requires no photo-library
 * permission at all, which is why `NSPhotoLibraryUsageDescription` is absent
 * from Info.plist — the app genuinely cannot read your library, rather than
 * promising not to.
 *
 * It asks for `public.jpeg` rather than the original asset. iOS transcodes HEIC
 * on the way out, and the EXIF reader in `engine/Exif.kt` speaks JPEG APP1;
 * HEIC keeps its metadata in an ISO-BMFF box the parser does not understand.
 */
class IosPhotoPicker(private val selectionLimit: Long = 10) : MediaSource() {

    override val label: String get() = "photos from your library"

    override val isSynthetic: Boolean get() = false

    private var pending: ((List<ByteArray>) -> Unit)? = null
    private val delegate = Delegate()

    override fun pick(
        kind: MediaKind,
        near: LatLng?,
        atEpochSeconds: Long,
        onResult: (List<ByteArray>) -> Unit,
    ) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (root == null) {
            onResult(emptyList())
            return
        }
        pending = onResult

        val config = PHPickerConfiguration()
        config.selectionLimit = selectionLimit
        config.filter = if (kind == MediaKind.VIDEO) {
            PHPickerFilter.videosFilter()
        } else {
            PHPickerFilter.imagesFilter()
        }

        val picker = PHPickerViewController(configuration = config)
        picker.delegate = delegate
        root.presentViewController(picker, animated = true, completion = null)
    }

    private inner class Delegate : NSObject(), PHPickerViewControllerDelegateProtocol {

        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)

            val results = didFinishPicking.filterIsInstance<PHPickerResult>()
            if (results.isEmpty()) {
                deliver(emptyList())
                return
            }

            // Each item loads independently and out of order. Collect into a
            // fixed-size slot array so the user's chosen order survives, and
            // deliver once when the last one lands.
            val slots = arrayOfNulls<ByteArray>(results.size)
            var outstanding = results.size

            results.forEachIndexed { index, result ->
                result.itemProvider.loadDataRepresentationForTypeIdentifier(
                    typeIdentifier = "public.jpeg",
                ) { data: NSData?, _: NSError? ->
                    dispatch_async(dispatch_get_main_queue()) {
                        slots[index] = data?.toByteArray()
                        outstanding -= 1
                        if (outstanding == 0) deliver(slots.filterNotNull())
                    }
                }
            }
        }

        private fun deliver(bytes: List<ByteArray>) {
            val callback = pending
            pending = null
            callback?.invoke(bytes)
        }
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
