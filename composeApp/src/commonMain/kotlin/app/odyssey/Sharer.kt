package app.odyssey

import app.odyssey.engine.ShareCard

/**
 * The "share on…" row on P3–P6.
 *
 * Handing text to the OS share sheet is the only egress path in the app, and
 * it is one the user initiates and can see. Consistent with media staying on
 * device: the app never transmits anything on its own behalf.
 */
interface Sharer {
    fun share(text: String)

    /** Renders [card] as an image and hands image + caption to the share sheet. */
    fun shareCard(card: ShareCard)
}

class NoopSharer : Sharer {
    override fun share(text: String) = Unit
    override fun shareCard(card: ShareCard) = Unit
}
