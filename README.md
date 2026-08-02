# My Odyssey

A GPS-verified US travel completion tracker for iPhone. The differentiator is
not the map — it is that **only evidenced visits count**. Every percentage in
the app is a pure fold over an append-only visit ledger against a versioned
canon of must-go places. There is no boolean anywhere in this system that says
`Utah: complete`.

Milestone 0 shipped the engine. This is milestone 0.5: the same engine, running
on a phone, with the vertical slice that proves the thesis end to end —
selection context → state tracker → GPS capture → recomputed percentages.

---

## Build and run (macOS, iOS Simulator)

**Prerequisites:** Xcode 15+ and a JDK 17+. No Android SDK is required — the
build declares only iOS and JVM targets and applies no Android Gradle Plugin,
so there is no `ANDROID_HOME` and no `local.properties`. (Google's Maven
repository *is* declared: Compose Multiplatform's `androidx.annotation`,
`androidx.collection` and `androidx.lifecycle` dependencies are published there
for every target including iOS. A Maven repository is not an SDK.)

```bash
cd my-odyssey

# 1. Run the invariant suite first. This is the fast feedback loop and it
#    needs neither Xcode nor a simulator.
./gradlew :engine:jvmTest

# 2. Link the Kotlin framework for the simulator (optional — Xcode does this
#    for you, but running it once surfaces Kotlin errors without Xcode noise).
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# 3. Open the app and hit Run.
open iosApp/iosApp.xcodeproj
```

In Xcode: select the **iosApp** scheme, pick any iPhone simulator, ⌘R.

The first build downloads the Kotlin/Native toolchain (~1 GB) and the Compose
Multiplatform artifacts. Expect several minutes once, then seconds.

### Faking a location in the Simulator

The Simulator has no GPS. Two options:

- **Xcode:** Debug → Simulate Location, or the scheme's *Allow Location
  Simulation* (already enabled) plus Features → Location → Custom Location.
- **In-app:** flip `USE_SIMULATED_LOCATION` to `true` in
  `composeApp/src/iosMain/kotlin/app/odyssey/MainViewController.kt`. The capture
  screen then grows "Stand at the place" / "Stand 40 km away" buttons, which is
  the fastest way to demo the geofence rule and watch a capture get denied.

Simulated fixes get no special treatment — they run through the identical
evidence rules.

### Command line, no Xcode UI

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15' build
```

---

## Layout

```
engine/          Kotlin Multiplatform, no UI, no framework dependencies
  Types.kt         canon entries, lifecycle, ledger events, evidence tiers
  Ledger.kt        append-only store: idempotency, anti-cheat, plausibility
  Fold.kt          the derivation — the whole app is a view over this function
  Codec.kt         line-oriented on-disk format for the log
  Repository.kt    the only write path; append, persist, re-fold
  Explore.kt       P7/P8/P9 grouping, distance ordering, completes-state flag
  Dates.kt         civil-from-days, so a memory label needs no date library
  Sha256.kt        content addressing; one implementation, every platform
  Exif.kt          JPEG APP1 reader — what makes PHOTO_VERIFIED evidence
  Media.kt         attach/detach events, the photo corroboration rule
  MediaStore.kt    expect: the on-device blob store. No upload method exists.
  CanonV1.kt       100 places, 2 per state, all 50 states
  Platform.kt      expect: wall clock + a two-method persistence surface
  InvariantsTest   the properties the product promises, as executable statements
  CodecTest        the on-disk format is the system of record; it must be exact
  ExploreTest      the two tabs partition the canon; ordering contracts
  DatesTest        checked against a decade of consecutive days

composeApp/      Compose Multiplatform UI, iOS targets only
  AppModel.kt      plain state holder — no framework ViewModel, no async
  Location.kt      LocationSource abstraction + a simulated implementation
  ui/              Home (P3/P4/P5), Tracker (P6), Timeline (P7/P8/P9),
                   Capture, Ledger inspector
  iosMain/         CoreLocation delegate + the single Swift-facing entry point

iosApp/          SwiftUI host: ~30 lines of Swift and an Xcode project
```

The split is deliberate. `engine` has no Compose and no iOS dependency, so the
invariants run on the JVM in about a second. Adding Android later is one line
(`androidTarget()`) in each `build.gradle.kts` and zero changes to any logic.

---

## What the app demonstrates

**Home** — the two headline metrics as rings, driven by the scope control
(World / United States / State). Selection context is owned here and read
everywhere else, never written elsewhere.

**Tracker** — the selected state's canon places with their dwell floors and
geofence radii, live distance from your current fix, and their credit status.

**Capture** — before you record anything it tells you exactly what will be
appended: the event type, the evidence tier you have earned, and whether it
will move a percentage. Standing outside the geofence still writes a visit; it
just writes a self-reported one, which renders in your history and stays out of
the numbers.

**Timeline (P7/P8/P9)** — the W/C/S pills switch between the three pages;
Memories and Explore partition the canon. Memories is flat at every level, your
visits newest first, including the uncredited ones. Explore is a read-only
reference list of what remains, grouped by country at world scope, by state at
country scope, flat at state scope, ordered nearest-first from your fix. Nothing
in Explore is tappable — tapping would mean writing selection, and selection has
exactly one owner. See `docs/my-odyssey-flow.md`.

**Ledger** — the raw encoded log, one line per event, with the fold output
beside it. Revoke a visit and watch a compensating event get appended below the
original rather than replacing it. Upgrade evidence and watch a previously
uncredited visit start counting. This screen exists to make the architecture
falsifiable by hand.

---

## Design decisions worth defending

**Completion is derived, never stored.** `fold(events, canon, user)` is the
only source of any number. The suite proves it: take a ledger where Utah is
complete, publish a canon release that adds a third Utah place, and the same
untouched ledger now reports Utah incomplete. No migration, because there is no
derived state to migrate.

**Canon versioning over mutation.** Closures, seasonal shutdowns, and new
additions are lifecycle transitions in a new immutable release. A fully
suspended state is *frozen*: removed from both sides of the state-coverage
ratio, so nothing completes for free. Reactivation restores the fold exactly —
that round trip is a test.

**Evidence-gated metrics.** `SELF_REPORTED < IMPORT_VERIFIED < PHOTO_VERIFIED
< GPS_VERIFIED`. Only the top three count. Evidence is upgrade-only, enforced at
ingest, so a user's trust score can never silently regress.

**Plausibility at the door.** Teleports (faster than Mach 1 between two
centroids) and overlapping visits to different places are rejected on ingest, so
the ledger never contains a physically impossible history. A 300-event fuzz test
asserts this over whatever survives.

**Idempotency for real producers.** Offline dumps replay at-least-once, so
`(userId, deviceId, sourceSeq)` is an idempotency key alongside `eventId`. A
retried batch with fresh event ids is still a no-op.

**Media never leaves the device.** Photos and videos are copied into this app's
own container, content-addressed by SHA-256. `MediaStore` has no method that
could send bytes to a network — the privacy position is structural, not a
promise in a policy document. The ledger stores only the content address, so the
log stays small and replayable while blobs remain reclaimable: detaching frees
the file and leaves the event, and a memory whose bytes were evicted renders as
a tombstone without moving a percentage.

**EXIF makes the second evidence tier real.** A photo corroborates a visit when
its embedded coordinates land inside the geofence and its GPS timestamp lands
inside the visit window. Only the GPS timestamp counts — `DateTimeOriginal` is
zoneless and cannot be compared to a window without guessing. Missing metadata
means no upgrade: verification fails closed. EXIF is user-writable, which is
exactly why photo evidence ranks below a live GPS fix rather than beside it.

**A line-oriented log instead of a serialization framework.** The on-disk
format is the system of record, so it is readable by eye, appendable without
rewriting, and free of any library that could change its encoding between
releases. A torn tail from a crash mid-write drops one line and folds the rest.

---

## Where this goes next

- **M1** — segmentation: raw GPS streams → stay-points → attributed visits, with
  event-time watermarks and late offline dumps.
- **M2** — deterministic simulation harness: trace replay with injected clock
  skew, duplication floods, and spoofed teleports; byte-identical folds.
- **M3** — real canon tooling: quarterly releases with index-rebalance
  discipline, and place boundaries as polygons instead of centroid + radius.
- **M4** — planner: cheapest next increment of completion under seasonal windows.
- **M5** — sync: the ledger is already the wire format, so the server is a
  merge of two append-only logs.

### Known scaffolding

The real photo-library picker (`PHPickerViewController`) is written but ships
uncompiled at `docs/IosPhotoPicker.kt.txt`. It is the one file whose Kotlin/ObjC
interop signatures could not be verified without a macOS toolchain, and an
unreferenced Kotlin file still breaks the build if it does not compile — so it
waits until the build is green rather than risking it. Until then the capture
screen stages photos from `SyntheticMediaSource`, which fabricates a real EXIF
container around the current fix and then goes through the identical hash,
parser and evidence rules. It gets no shortcuts.


`KeyValueStore` on iOS is backed by `NSUserDefaults`. That is correct for a log
measured in kilobytes and deliberately wrong at scale — it is a two-method
interface precisely so SQLDelight can replace it without touching a caller. The
canon uses centroids with a radius rather than boundary polygons; that lands
with M1. Auth, sync, and the social surface (P7–P11 in the flow spec) are not in
this slice.
