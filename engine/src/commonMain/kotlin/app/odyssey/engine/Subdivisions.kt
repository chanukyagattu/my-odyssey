package app.odyssey.engine

/**
 * How many top-level administrative divisions each country actually has, and
 * what they are called there.
 *
 * The country dial counts against the *real* total, not against how much of it
 * the canon happens to cover — the same honesty as the world dial reading
 * 1/195 rather than 1/1. Coverage is shown separately so the gap is visible
 * rather than disguised.
 *
 * Counts follow ISO 3166-2 top-level entries. Where inclusion of overseas or
 * autonomous territories is a judgement call, the choice is noted in
 * `data/gen_subdivisions.py`.
 *
 * Generated; do not hand-edit.
 */
data class Subdivision(
    val total: Int,
    /** "prefectures", "régions", "provinces and territories". */
    val plural: String,
    val singular: String,
)

object Subdivisions {

    private val byCountry: Map<String, Subdivision> = mapOf(
        "AE" to Subdivision(7, "emirates", "emirate"),
        "AR" to Subdivision(24, "provinces", "province"),
        "AT" to Subdivision(9, "states", "state"),
        "AU" to Subdivision(8, "states and territories", "state"),
        "BE" to Subdivision(3, "regions", "region"),
        "BR" to Subdivision(27, "states", "state"),
        "CA" to Subdivision(13, "provinces and territories", "province"),
        "CH" to Subdivision(26, "cantons", "canton"),
        "CL" to Subdivision(16, "regions", "region"),
        "CN" to Subdivision(34, "provinces", "province"),
        "CZ" to Subdivision(14, "regions", "region"),
        "DE" to Subdivision(16, "states", "state"),
        "EG" to Subdivision(27, "governorates", "governorate"),
        "ES" to Subdivision(19, "autonomous communities", "community"),
        "FR" to Subdivision(18, "régions", "région"),
        "GB" to Subdivision(4, "nations", "nation"),
        "GR" to Subdivision(13, "regions", "region"),
        "HR" to Subdivision(21, "counties", "county"),
        "HU" to Subdivision(20, "counties", "county"),
        "ID" to Subdivision(38, "provinces", "province"),
        "IE" to Subdivision(26, "counties", "county"),
        "IN" to Subdivision(36, "states and union territories", "state"),
        "IS" to Subdivision(8, "regions", "region"),
        "IT" to Subdivision(20, "regions", "region"),
        "JO" to Subdivision(12, "governorates", "governorate"),
        "JP" to Subdivision(47, "prefectures", "prefecture"),
        "KE" to Subdivision(47, "counties", "county"),
        "KH" to Subdivision(25, "provinces", "province"),
        "KR" to Subdivision(17, "provinces and cities", "province"),
        "MA" to Subdivision(12, "regions", "region"),
        "MX" to Subdivision(32, "states", "state"),
        "NL" to Subdivision(12, "provinces", "province"),
        "NO" to Subdivision(15, "counties", "county"),
        "NP" to Subdivision(7, "provinces", "province"),
        "NZ" to Subdivision(16, "regions", "region"),
        "PE" to Subdivision(25, "regions", "region"),
        "PL" to Subdivision(16, "voivodeships", "voivodeship"),
        "PT" to Subdivision(20, "districts", "district"),
        "RU" to Subdivision(83, "federal subjects", "federal subject"),
        "SE" to Subdivision(21, "counties", "county"),
        "TH" to Subdivision(77, "provinces", "province"),
        "TR" to Subdivision(81, "provinces", "province"),
        "TZ" to Subdivision(31, "regions", "region"),
        "US" to Subdivision(50, "states", "state"),
        "VN" to Subdivision(63, "provinces", "province"),
        "ZA" to Subdivision(9, "provinces", "province"),
    )

    operator fun get(country: String): Subdivision? = byCountry[country]

    /** Total for [country], or the canon's own coverage when we have no entry. */
    fun total(country: String, fallback: Int): Int = byCountry[country]?.total ?: fallback

    fun plural(country: String): String = byCountry[country]?.plural ?: "regions"

    fun singular(country: String): String = byCountry[country]?.singular ?: "region"

    val countries: Set<String> get() = byCountry.keys
}
