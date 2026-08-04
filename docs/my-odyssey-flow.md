# My Odyssey — flow spec

Living document. Updated as decisions land, not rewritten. Page numbers match
the wireframe.

---

## 1. Pages

| Page | Purpose | Built |
|---|---|---|
| P1 | Login / sign up | yes — local accounts |
| P2 | Registration | yes — local accounts |
| P3 | Home — three tracker dials, TRACKER/TIMELINE tabs, hamburger, share | yes |
| P4 | World tracker — countries | yes |
| P5 | Country tracker — states, drills into P6 | yes |
| P6 | State tracker — must-go places, capture | yes |
| P7 | Timeline @ world — Memories · Explore | yes |
| P8 | Timeline @ country — Memories · Explore | yes |
| P9 | Timeline @ state — Memories · Explore | yes |
| P10 | Forgot password — request link | no — needs a backend |
| P11 | Forgot password — set new | no — needs a backend |
| — | Hamburger menu: appearance, account, sign out, ledger | yes (not in wireframe) |

### Navigation

```
P1 ⇄ P2          register lands you signed in, straight to P3
P3 → P4 | P5 | P6    tap a tracker dial
P5 → P6              tap a state row (selects it, then drills in)
P4/P5/P6 → P3        back arrow
TIMELINE tab:  P3 → P7 (W)   P4 → P7 (W)   P5 → P8 (C)   P6 → P9 (S)
```

The TIMELINE tab carries the page's scope with it, which is what the
wireframe's "default: W / C / S" annotations specify.

---

## 2. Selection context (W / C / S)

- **One writer.** `OdysseyRepository.select` / `selectState` are the only
  mutators in the system, reached through `AppModel`. Nothing in Explore calls
  them — Explore is a reference list and stays read-only.
- **Refinement.** Tapping a state row on P5 both selects that state and drills
  into P6, because that *is* the country tracker's job. The rule that matters —
  a single writer, never a screen mutating selection as a side effect of
  rendering — still holds.
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

### The W / C / S selector

Three small pies rather than tab buttons — the same fold the big dials show,
shrunk. W is places coverage, C is states complete, S is places within the
selected state.

Green is done, pale is still to visit. Pale is its own colour token, not the
amber used for *recorded but uncredited*: amber describes something that
happened, pale describes something that has not. Sharing one would make the pies
lie.

Pale rather than a second hue is deliberate. Done-versus-remaining then
separates by **luminance**, which survives greyscale and every form of colour
blindness — a green/orange pairing is the classic red-green confusion case.
Selection is carried by a ring and a filled letter, never by colour alone.

### Footer — FEED and MESSAGE

Placeholders. Their original jobs — seeing other people's travels, messaging
them — need a server that does not exist, so neither claims to work. Open
decision, revisited when there is a backend.

The activity feed they would eventually surface is already built and tested:
`activityFeed` reads your ledger back as sentences, ordered by **log position
rather than timestamp**, because only `VisitRecorded` carries a wall clock —
compensating events record that they happened, not when. It is reachable today
under the hamburger as "Your activity".

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

## 6a. Sharing — the card is the social feature

There is no feed and no cloud photo library. What travels instead is a rendered
**1080×1920 story card** of your progress, which you hand to the system share
sheet yourself. Every dial on P3–P6 has its own "Share card" action, scoped to
what that dial counts.

This replaces an in-app feed on purpose. Distribution happens on networks that
already hold your users' friends; it costs one PNG per share; and it takes on
none of the obligations that hosting user images does — no CSAM detection, no
DMCA agent, no moderation, no storage bill that grows forever against a
seasonal product.

It is also the growth loop. Anyone can post a travel photo. "38/100 places,
every one GPS-verified" is a claim only this app can make, and it is the thing
that makes a stranger ask which app that is.

**The card is the entire privacy surface.** It is the only artefact that ever
leaves the device, so its content is aggregate by construction: a score, never
an itinerary. No place names, no state names, no dates, no coordinates. The
state card deliberately does not name the state — naming it converts a score
into a location, and the post is public forever. `ShareCardTest` asserts all of
this against the real canon rather than trusting the copy to stay careful.

Card copy and numbers are computed in `engine/ShareCard.kt` from the fold and
unit-tested; only the rasterisation is platform code.

---

## 6b. Appearance

Three modes under the hamburger: **Phone setting** (default), **Light**, **Dark**.
Stored by `SettingsStore`, deliberately separate from the account — appearance
belongs to the device, so it survives sign-out.

Colour carries meaning in both schemes and the roles never move: verified /
pending / danger / muted mean the same thing whichever way the phone is set.
Only surfaces flip. The light scheme darkens the accents rather than reusing
them — the dark-scheme mint fails contrast as text on white, and these
percentages have to be readable in direct sun at a trailhead.

Themes resolve through a composition local, so `Palette.Verified` at every call
site re-themes for free. The one constraint: a `DrawScope` lambda is not a
composable, so ring colours are hoisted before the `Canvas` block.

---

## 7. Account

- Account actions live in the hamburger menu.
- Forgot password is email-only, time-limited single-use links (P10 → P11).
- Sign-out guards against unsynced GPS traces and queued uploads before
  clearing the session.

---

## 8. Open decisions

1. Phone number optional at registration?
2. FEED vs. notifications behaviour for the MESSAGE button. Still open — both
   render as placeholders. The activity feed exists under the hamburger in the
   meantime.
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
