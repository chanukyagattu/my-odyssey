# My Odyssey — flow spec

Living document. Updated as decisions land, not rewritten. Page numbers match
the wireframe.

---

## 1. Pages

| Page | Purpose | Built |
|---|---|---|
| P1 | Login / sign up | no |
| P2 | Registration | no |
| P3 | Home — W/C/S selection context, tracker + timeline entry | yes (Odyssey tab) |
| P4 | World tracker | yes (scope = W) |
| P5 | Country tracker | yes (scope = C) |
| P6 | State tracker — must-go places, capture | yes (Tracker tab) |
| P7 | Timeline @ world — Memories · Explore | yes (Timeline tab, scope = W) |
| P8 | Timeline @ country — Memories · Explore | yes (Timeline tab, scope = C) |
| P9 | Timeline @ state — Memories · Explore | yes (Timeline tab, scope = S) |
| P10 | Forgot password — request link | no |
| P11 | Forgot password — set new | no |

---

## 2. Selection context (W / C / S)

- **P3 is the sole owner.** Every other page reads the selection and none of
  them write it. Enforced in code: `OdysseyRepository.select` / `selectState`
  are the only mutators, and nothing in Explore calls them.
- The C→S cascade is one-way. Changing country resets state; changing state
  never changes country.
- Default state is **derived, not remembered**: the state of the most recent
  visit, alphabetical fallback. A fresh install and a restored install agree.

---

## 3. P7 / P8 / P9 — Memories and Explore

Two tabs that **partition the canon**: what you did, and what is left.

### Memories

Flat at every level — your visits, newest first. Only the scope filter changes
(world = all, country = that country, state = that state). Uncredited visits
appear here too; they are real memories that do not move a percentage. Revoked
visits appear in neither tab.

### Explore

A **reference list of what remains**, not a navigation surface. Nothing in it is
tappable, because tapping would mean writing selection and selection has one
owner. Granularity follows the scope:

| Page | Scope | Shows | Grouped by |
|---|---|---|---|
| P7 | World | remaining places worldwide | country |
| P8 | Country | remaining places in the country | state |
| P9 | State | remaining places in the state | (single group) |

**Why country is a group header and not a card.** If P7 listed places flat it
would be an unbounded wall; if it listed tappable country cards it would be a
navigation surface, which contradicts "reference list". A group header gives
world scope its own granularity without either problem.

**Ordering — nearest first.** With a GPS fix, items sort by distance and each
group sorts by its *nearest remaining member*. Sorting groups by a country or
state centroid would rank Brazil above Canada from Miami, which is the wrong
answer to "what could I actually go do".

**Ordering — no fix.** Fresh install or location denied: fall back to
most-complete first, alphabetical within ties. Surfaces the user's own momentum
instead of opening on Alabama.

**"Completes state" badge.** A row is flagged when it is the last uncredited
active place in its state. Computed against the whole canon, never the current
filter — otherwise P9 would claim every row completes a state. This is the
cheapest-next-increment idea kept as an annotation rather than a sort order, so
ordering stays predictable while the two-metric tension stays visible.

**Countries not in canon.** One honest line — "N more countries arrive with
future canon releases" — not N greyed rows. They are in no denominator today,
so they cannot quietly dilute a percentage.

### Footer

FEED and MESSAGE render but are inert; both are open decisions (§8).

---

## 4. Canon lifecycle

`proposed → active ↔ suspended → retired`. A new release is a full immutable
snapshot, never a patch. Completion is recomputed as a re-fold against the
release — there is no derived state to migrate.

- **Proposed** places are outside the denominator and earn nothing yet; a visit
  recorded before activation is credited at activation with zero migration.
- **Suspended** places leave the denominator uniformly for everyone.
- A state with zero active entries is **frozen** and leaves *both* sides of the
  state-coverage ratio, so nothing completes for free.

---

## 4a. Media — on device, never in our cloud

Anything the user uploads — photos, videos — is copied into **this app's own
container on the phone**. There is no company cloud storage and no upload path
anywhere in the codebase. `MediaStore` has no method that could send bytes to a
network, which makes the policy structural rather than a promise.

- **Content-addressed.** `put(bytes)` returns the SHA-256 of the bytes and that
  hash is the filename. Free deduplication, immutable identifiers, and a blob
  can always be checked against its own name.
- **Bytes are not in the ledger.** `MediaAttached` carries a `mediaId`, a size
  and any EXIF, never the image. The log stays small and replayable; the blobs
  are a sidecar.
- **Two different durability rules.** The ledger is append-only and permanent.
  The blob store is reclaimable — `MediaDetached` frees the file while the
  attachment event stays in the log, so a memory whose bytes were evicted
  renders as a tombstone and the fold is untouched.
- **Backup.** The container is included in the user's *own* iCloud device backup
  by default — their storage, never ours — with an opt-out for users on a small
  plan who accept that a lost phone loses the library.
- **Coordinates persist as E7 integers** (degrees × 10⁷), not doubles, so the
  on-disk log round-trips identically on every platform.

### Photo evidence

EXIF turns `PHOTO_VERIFIED` from a label into evidence. A photo corroborates a
visit when its embedded coordinates fall inside the place's geofence *and* its
timestamp falls inside the visit window (±15 min).

Only the **GPS** timestamp counts. `DateTimeOriginal` has no time zone and
cannot be compared to a window without guessing an offset; `GPSDateStamp` +
`GPSTimeStamp` are UTC by specification. Missing either means no upgrade —
verification fails closed, because a photo with no metadata is
indistinguishable from a photo taken off the internet.

EXIF is user-writable. That is precisely why photo evidence ranks *below* a live
GPS fix rather than beside it.

A corroborating photo causes an `EvidenceUpgraded` to be appended right after
the `MediaAttached` — two events, so the ledger screen shows exactly why a
percentage moved. Attaching media never moves a number by itself.

---

## 5. Evidence

`self_reported < import_verified < photo_verified < gps_verified`. Only the top
three count. Evidence is upgrade-only, enforced at ingest. GPS credit requires a
fix inside the place's geofence and a dwell at or above the place's floor.

---

## 6. Ledger

Append-only. Corrections are compensating events (`VisitRevoked`,
`EvidenceUpgraded`); nothing is mutated or deleted. Idempotent on `eventId` and
on `(userId, deviceId, sourceSeq)` for at-least-once producers. Teleports and
overlapping visits are rejected at ingest, so the log never contains a
physically impossible history.

---

## 7. Account

- Account actions live in the hamburger menu.
- Forgot password is email-only, time-limited single-use links (P10 → P11).
- Sign-out guards against unsynced GPS traces and queued uploads before
  clearing the session.

---

## 8. Open decisions

1. Phone number optional at registration?
2. FEED vs. notifications behaviour for the MESSAGE button.
3. ~~Place-list screen under P6~~ — **closed**: P6 lists the state's canon
   places inline with capture; P9 Explore is the read-only reference view.
4. Live vs. retrospective Timeline posting.
5. `upload_timestamp` vs. `captured_at` for default country/state selection.
   *(Currently `captured_at`, via visit start time.)*
6. No-data country fallback behaviour.
7. Account deletion ledger semantics.
8. ~~P7 Explore granularity~~ — **closed**: grouped by country, non-navigable.
9. ~~Explore contents~~ — **closed**: remainder only; credited places live in
   Memories.
10. ~~Explore ordering with no GPS fix~~ — **closed**: most-complete first.
11. ~~Where user media lives~~ — **closed**: copied into the app's own container
    on the device, content-addressed. No company cloud storage.
12. ~~Media backup posture~~ — **closed**: included in the user's own iCloud
    device backup, with an opt-out.
13. Real photo-library picker — written but not yet compiled; see
    `docs/IosPhotoPicker.kt.txt`.
14. Video evidence. Videos store and play back but never corroborate a visit;
    whether a video's metadata should count is undecided.
15. Storage ceiling and eviction policy once a library gets large.

### Future-MVP

New attractions entering the canon; optional / unscored places that sit outside
the denominator.
