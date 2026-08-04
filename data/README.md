# Canon data

`canon-world.tsv` is the source of truth for the canon. `CanonWorld.kt` is
generated from it and must not be hand-edited.

```
python3 data/gen_canon_world.py     # validates, then rewrites the TSV
```

228 places · 46 countries · 153 regions.

## Why a TSV and not a database

A canon release is an immutable snapshot, and completion is a re-fold against
it rather than a stored value. That makes the canon *reference data*, not
application state — it wants versioning and review, which a text file in git
gives you for free.

It is generated into Kotlin rather than parsed at runtime for a narrower
reason: loading a bundled resource in Kotlin Multiplatform needs
`compose-resources` plumbing on every target. Generating source keeps the
property that matters — the canon is data you regenerate, not code you edit by
hand — without the platform surface.

## Validation

The generator refuses to write a file that fails its checks. Each row must
have a unique id that encodes its own country, a region belonging to that
country, a plausible dwell floor and geofence, and **coordinates inside that
country's bounding box**.

That last one is not decoration. It caught Rapa Nui on the first run: Easter
Island is Chilean but sits 3,500 km into the Pacific, well outside a mainland
bounding box. A transposed latitude and longitude, or a dropped minus sign,
fails the build rather than shipping as a place nobody can reach.

## Fields

| Column | Notes |
|---|---|
| `place_id` | Primary key. Stable forever, never reused, prefixed with its country. |
| `country` | ISO 3166-1 alpha-2. |
| `region_code` | ISO 3166-2 shaped: `US-UT`, `FR-IDF`, `JP-26`. |
| `region_name` | Display name. |
| `name`, `city` | `city` is blank for places that are not in one. |
| `postal_code` | **Deliberately empty.** See below. |
| `lat`, `lng` | Centroid. |
| `dwell_seconds` | How long a visit must last to count. |
| `geofence_meters` | Radius that stands in for a boundary polygon. |
| `tags` | Pipe-separated. |

## What is deliberately missing

**Postal codes are blank.** Many of these are national parks, deserts and
reefs where a postcode is meaningless, and filling the rest from memory would
mean inventing addresses. A fabricated postcode is worse than an empty one,
because nobody can tell it is wrong by looking at it. The column exists so a
gazetteer — Wikidata or OpenStreetMap — can populate it later without a
schema change.

**Centroids, not polygons.** Accurate to a few kilometres, which is ample
against geofences measured in kilometres. Real boundaries land with the
segmentation engine, when attribution needs point-in-polygon.

## Coverage

The United States keeps its deliberate structure — exactly two places in each
of the 50 states — because state completion is a mechanic. Other countries
have between two and seven places, weighted toward how much there is worth
seeing rather than toward a uniform count.
