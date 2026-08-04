package app.odyssey.engine

/**
 * The share card — the only artefact this app ever emits.
 *
 * Photos stay on the device, so there is no feed and no cloud library. What
 * travels instead is a rendered image of your progress, handed to the system
 * share sheet by you. That makes this the app's entire privacy surface, and it
 * is why the content is deliberately aggregate: a score, never an itinerary.
 *
 * A percentage says nothing about where you live, when your house was empty, or
 * where you are standing right now. A list of places and dates says all three.
 * [ShareCardTest] asserts that no canon place name, state name or date can ever
 * appear on a card.
 *
 * Content lives here rather than in the renderer so the numbers come straight
 * from the fold and can be tested without a simulator.
 */
data class CardStat(val value: String, val label: String)

data class ShareCard(
    /** Big centre number, e.g. "48%" or "12/50". */
    val bigValue: String,
    val bigCaption: String,
    /** Ring fill, 0..1. */
    val fraction: Float,
    /** The scope this card is about, e.g. "UNITED STATES". */
    val scopeLabel: String,
    val stats: List<CardStat>,
    /** The claim no competitor can make. */
    val verifiedLine: String,
    val canonLine: String,
    val handle: String,
    /** Text that accompanies the image in the share sheet. */
    val caption: String,
) {
    /** Everything the renderer will draw. Used by the privacy test. */
    val allText: List<String>
        get() = listOf(bigValue, bigCaption, scopeLabel, verifiedLine, canonLine, handle, caption) +
            stats.flatMap { listOf(it.value, it.label) }
}

private fun pct(value: Double): String {
    val rounded = (value * 10).toLong()
    return if (rounded % 10 == 0L) "${rounded / 10}%" else "${rounded / 10}.${rounded % 10}%"
}

fun shareCardFor(
    snapshot: AppSnapshot,
    scope: Scope,
    username: String?,
): ShareCard {
    val r = snapshot.result
    val handle = username?.let { "@$it" } ?: "my odyssey"
    val canonLine = "canon v${r.canonVersion} · ${r.placesDenominator} places · ${r.stateDenominator} states"

    return when (scope) {
        Scope.WORLD -> ShareCard(
            bigValue = "${r.placesCredited.size}/${r.placesDenominator}",
            bigCaption = "must-go places verified",
            fraction = (r.placesCoveragePct / 100.0).toFloat(),
            scopeLabel = "THE WORLD",
            stats = listOf(
                CardStat("${r.statesComplete.size}/${r.stateDenominator}", "states complete"),
                CardStat(pct(r.placesCoveragePct), "of the canon"),
            ),
            verifiedLine = "Every visit GPS-verified",
            canonLine = canonLine,
            handle = handle,
            caption = "${r.placesCredited.size} of ${r.placesDenominator} must-go places — " +
                "every one of them GPS-verified. #MyOdyssey",
        )

        Scope.COUNTRY -> ShareCard(
            bigValue = pct(r.stateCoveragePct),
            bigCaption = "of the United States",
            fraction = (r.stateCoveragePct / 100.0).toFloat(),
            scopeLabel = "UNITED STATES",
            stats = listOf(
                CardStat("${r.statesComplete.size}/${r.stateDenominator}", "states complete"),
                CardStat("${r.placesCredited.size}/${r.placesDenominator}", "places verified"),
            ),
            verifiedLine = "No self-reported visits counted",
            canonLine = canonLine,
            handle = handle,
            caption = "I'm ${pct(r.stateCoveragePct)} through the United States — " +
                "${r.statesComplete.size}/${r.stateDenominator} states complete, all GPS-verified. #MyOdyssey",
        )

        Scope.STATE -> {
            // Note the state is NOT named. Naming it turns a score into a
            // location, and the card is public forever.
            val (done, total) = r.stateProgress(snapshot.selection.usState)
            ShareCard(
                bigValue = "$done/$total",
                bigCaption = "places verified in this state",
                fraction = if (total == 0) 0f else done.toFloat() / total,
                scopeLabel = "ONE STATE DOWN",
                stats = listOf(
                    CardStat("${r.statesComplete.size}/${r.stateDenominator}", "states complete"),
                    CardStat("${r.placesCredited.size}/${r.placesDenominator}", "places verified"),
                ),
                verifiedLine = if (done == total && total > 0) {
                    "State complete — every place GPS-verified"
                } else {
                    "Every visit GPS-verified"
                },
                canonLine = canonLine,
                handle = handle,
                caption = "$done of $total must-go places verified. " +
                    "${r.statesComplete.size}/${r.stateDenominator} states complete. #MyOdyssey",
            )
        }
    }
}
