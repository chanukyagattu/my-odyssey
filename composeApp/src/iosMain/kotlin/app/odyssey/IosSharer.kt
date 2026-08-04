package app.odyssey

import app.odyssey.engine.ShareCard
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGFloat
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSString
// NSMutableParagraphStyle and the NSStringDrawing category (drawInRect) are
// declared in UIKit on iOS, not Foundation.
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSMutableParagraphStyle
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSParagraphStyleAttributeName
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIActivityItemSourceProtocol
import platform.UIKit.UIActivityType
import platform.UIKit.UIActivityTypeAssignToContact
import platform.UIKit.UIActivityTypeSaveToCameraRoll
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIRectFill
import platform.UIKit.drawInRect
import platform.darwin.NSObject
import kotlin.math.PI

/**
 * The system share sheet decides which apps appear — X, Instagram, WhatsApp,
 * Messages, whatever the user actually has. That is why the wireframe's fixed
 * icon row routes to one action: the OS already knows the answer, and this app
 * does not need to integrate with a single network.
 *
 * The card is drawn here rather than in Compose because a 1080x1920 PNG is a
 * platform artefact, not a UI. What it *says* is computed in common Kotlin by
 * `shareCardFor` and tested there, including the privacy invariants.
 */
@OptIn(ExperimentalForeignApi::class)
class IosSharer : Sharer {

    override fun share(text: String) = present(listOf(text))

    override fun shareCard(card: ShareCard) {
        val image = render(card)
        if (image == null) {
            present(listOf(card.caption))
        } else {
            // Two item sources rather than [image, caption].
            //
            // Save to Photos cannot handle text, so a mixed array makes iOS drop
            // the activity entirely — which is why "Save Image" never appeared.
            // An item source lets each item answer per activity: the image goes
            // everywhere, the caption withholds itself from Save and Assign to
            // Contact.
            present(listOf(ImageItem(image), CaptionItem(card.caption)))
        }
    }

    // ---------- presentation ----------

    private fun present(items: List<Any>) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        root.presentViewController(controller, animated = true, completion = null)
    }

    // ---------- drawing ----------

    private fun render(card: ShareCard): UIImage? {
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(W, H))
        return renderer.imageWithActions {
            UIColor(red = 0.043, green = 0.063, blue = 0.125, alpha = 1.0).setFill()
            UIRectFill(CGRectMake(0.0, 0.0, W, H))

            text("MY ODYSSEY", 0.0, 150.0, W, 60.0, 42.0, TEXT, bold = true)
            text(card.scopeLabel, 0.0, 222.0, W, 50.0, 30.0, MUTED)

            ring(card.fraction.toDouble())

            // Percentage alone inside the ring; the fraction reads underneath.
            text(card.bigValue, 0.0, 680.0, W, 200.0, 160.0, TEXT, bold = true)
            text(card.countLine, 0.0, 1130.0, W, 60.0, 38.0, TEXT)

            var x = 140.0
            val slot = (W - 280.0) / card.stats.size.coerceAtLeast(1)
            for (stat in card.stats) {
                text(stat.value, x, 1270.0, slot, 90.0, 62.0, VERIFIED, bold = true)
                text(stat.label, x, 1356.0, slot, 50.0, 28.0, MUTED)
                x += slot
            }

            text("✓  ${card.verifiedLine}", 0.0, 1500.0, W, 60.0, 34.0, VERIFIED, bold = true)
            text(card.canonLine, 0.0, 1568.0, W, 50.0, 26.0, MUTED)
            text(card.handle, 0.0, 1760.0, W, 60.0, 32.0, TEXT)
        }
    }

    private fun ring(fraction: Double) {
        val centre = CGPointMake(W / 2, 760.0)
        val radius = 300.0
        val start = -PI / 2

        val track = UIBezierPath()
        track.addArcWithCenter(centre, radius, start, start + 2 * PI, true)
        track.lineWidth = 46.0
        UIColor(red = 0.114, green = 0.153, blue = 0.251, alpha = 1.0).setStroke()
        track.stroke()

        if (fraction > 0.0) {
            val progress = UIBezierPath()
            progress.addArcWithCenter(centre, radius, start, start + 2 * PI * fraction.coerceIn(0.0, 1.0), true)
            progress.lineWidth = 46.0
            UIColor(red = 0.239, green = 0.863, blue = 0.592, alpha = 1.0).setStroke()
            progress.stroke()
        }
    }

    private fun text(
        value: String,
        x: CGFloat,
        y: CGFloat,
        width: CGFloat,
        height: CGFloat,
        size: CGFloat,
        color: UIColor,
        bold: Boolean = false,
    ) {
        val paragraph = NSMutableParagraphStyle()
        paragraph.setAlignment(NSTextAlignmentCenter)
        val attributes = mapOf<Any?, Any?>(
            NSFontAttributeName to if (bold) UIFont.boldSystemFontOfSize(size) else UIFont.systemFontOfSize(size),
            NSForegroundColorAttributeName to color,
            NSParagraphStyleAttributeName to paragraph,
        )
        (value as NSString).drawInRect(CGRectMake(x, y, width, height), withAttributes = attributes)
    }

    /** Offers the card image to every activity, including Save to Photos. */
    private class ImageItem(private val image: UIImage) : NSObject(), UIActivityItemSourceProtocol {

        override fun activityViewControllerPlaceholderItem(
            activityViewController: UIActivityViewController,
        ): Any = image

        override fun activityViewController(
            activityViewController: UIActivityViewController,
            itemForActivityType: UIActivityType?,
        ): Any = image
    }

    /** Offers the caption to everything except activities that only take an image. */
    private class CaptionItem(private val caption: String) : NSObject(), UIActivityItemSourceProtocol {

        override fun activityViewControllerPlaceholderItem(
            activityViewController: UIActivityViewController,
        ): Any = caption

        override fun activityViewController(
            activityViewController: UIActivityViewController,
            itemForActivityType: UIActivityType?,
        ): Any? = when (itemForActivityType) {
            UIActivityTypeSaveToCameraRoll, UIActivityTypeAssignToContact -> null
            else -> caption
        }
    }

    private companion object {
        const val W: CGFloat = 1080.0
        const val H: CGFloat = 1920.0
        val TEXT = UIColor(red = 0.929, green = 0.945, blue = 0.980, alpha = 1.0)
        val MUTED = UIColor(red = 0.541, green = 0.592, blue = 0.722, alpha = 1.0)
        val VERIFIED = UIColor(red = 0.239, green = 0.863, blue = 0.592, alpha = 1.0)
    }
}
