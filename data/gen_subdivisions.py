#!/usr/bin/env python3
"""Generates Subdivisions.kt — how many top-level administrative divisions each
country actually has, and what they are called there.

Counts follow ISO 3166-2 top-level entries. A few are genuinely contested or
depend on whether overseas/autonomous territories are included; where that is
true the convention chosen is noted inline. The generator refuses to emit a
country whose canon coverage exceeds its stated total, which is the error worth
catching automatically.
"""
import sys

# country: (total, plural noun, singular noun)
SUB = {
    "AE": (7,  "emirates", "emirate"),
    "AR": (24, "provinces", "province"),          # 23 provinces + Buenos Aires city
    "AT": (9,  "states", "state"),                # Bundesländer
    "AU": (8,  "states and territories", "state"),
    "BE": (3,  "regions", "region"),
    "BR": (27, "states", "state"),                # 26 states + Federal District
    "CA": (13, "provinces and territories", "province"),
    "CH": (26, "cantons", "canton"),
    "CL": (16, "regions", "region"),
    "CN": (34, "provinces", "province"),          # province-level incl. municipalities and SARs
    "CZ": (14, "regions", "region"),
    "DE": (16, "states", "state"),
    "EG": (27, "governorates", "governorate"),
    "ES": (19, "autonomous communities", "community"),  # 17 + Ceuta and Melilla
    "FR": (18, "régions", "région"),              # 13 metropolitan + 5 overseas
    "GB": (4,  "nations", "nation"),
    "GR": (13, "regions", "region"),
    "HR": (21, "counties", "county"),
    "HU": (20, "counties", "county"),
    "ID": (38, "provinces", "province"),
    "IE": (26, "counties", "county"),
    "IN": (36, "states and union territories", "state"),
    "IS": (8,  "regions", "region"),
    "IT": (20, "regions", "region"),
    "JO": (12, "governorates", "governorate"),
    "JP": (47, "prefectures", "prefecture"),
    "KE": (47, "counties", "county"),
    "KH": (25, "provinces", "province"),
    "KR": (17, "provinces and cities", "province"),
    "MA": (12, "regions", "region"),
    "MX": (32, "states", "state"),                # 31 states + Mexico City
    "NL": (12, "provinces", "province"),
    "NO": (15, "counties", "county"),             # post-2024 reorganisation
    "NP": (7,  "provinces", "province"),
    "NZ": (16, "regions", "region"),
    "PE": (25, "regions", "region"),
    "PL": (16, "voivodeships", "voivodeship"),
    "PT": (20, "districts", "district"),          # 18 districts + 2 autonomous regions
    "RU": (83, "federal subjects", "federal subject"),  # excludes disputed annexations
    "SE": (21, "counties", "county"),
    "TH": (77, "provinces", "province"),
    "TR": (81, "provinces", "province"),
    "TZ": (31, "regions", "region"),
    "US": (50, "states", "state"),
    "VN": (63, "provinces", "province"),
    "ZA": (9,  "provinces", "province"),
}

rows = [l.rstrip("\n").split("\t") for l in open("canon-world.tsv")][1:]
canon_countries = sorted({r[1] for r in rows})
covered = {}
for r in rows:
    covered.setdefault(r[1], set()).add(r[2])

errs = []
for c in canon_countries:
    if c not in SUB:
        errs.append(f"{c}: in the canon but has no subdivision entry")
        continue
    total, plural, singular = SUB[c]
    n = len(covered[c])
    if n > total:
        errs.append(f"{c}: canon covers {n} regions but the country only has {total}")
    if total < 1:
        errs.append(f"{c}: implausible total {total}")
    if not plural or not singular:
        errs.append(f"{c}: missing noun")
for c in SUB:
    if c not in canon_countries:
        errs.append(f"{c}: subdivision entry for a country not in the canon")

if errs:
    for e in errs:
        print("FAIL:", e)
    sys.exit(1)

L = ["package app.odyssey.engine", "",
"/**", " * How many top-level administrative divisions each country actually has, and",
" * what they are called there.", " *",
" * The country dial counts against the *real* total, not against how much of it",
" * the canon happens to cover — the same honesty as the world dial reading",
" * 1/195 rather than 1/1. Coverage is shown separately so the gap is visible",
" * rather than disguised.", " *",
" * Counts follow ISO 3166-2 top-level entries. Where inclusion of overseas or",
" * autonomous territories is a judgement call, the choice is noted in",
" * `data/gen_subdivisions.py`.", " *",
" * Generated; do not hand-edit.", " */",
"data class Subdivision(", "    val total: Int,",
"    /** \"prefectures\", \"régions\", \"provinces and territories\". */",
"    val plural: String,", "    val singular: String,", ")", "",
"object Subdivisions {", "",
"    private val byCountry: Map<String, Subdivision> = mapOf(",]
for c in sorted(SUB):
    total, plural, singular = SUB[c]
    L.append(f'        "{c}" to Subdivision({total}, "{plural}", "{singular}"),')
L += ["    )", "",
"    operator fun get(country: String): Subdivision? = byCountry[country]", "",
"    /** Total for [country], or the canon's own coverage when we have no entry. */",
"    fun total(country: String, fallback: Int): Int = byCountry[country]?.total ?: fallback", "",
"    fun plural(country: String): String = byCountry[country]?.plural ?: \"regions\"", "",
"    fun singular(country: String): String = byCountry[country]?.singular ?: \"region\"", "",
"    val countries: Set<String> get() = byCountry.keys", "}", ""]
open("/sessions/great-nice-gates/mnt/GitHub/my-odyssey/engine/src/commonMain/kotlin/app/odyssey/engine/Subdivisions.kt","w").write("\n".join(L))

print(f"OK  {len(SUB)} countries")
for c in ["US","BR","FR","JP","IN","GB","CA"]:
    t,p,_ = SUB[c]
    print(f"     {c}: canon covers {len(covered[c])} of {t} {p}")
