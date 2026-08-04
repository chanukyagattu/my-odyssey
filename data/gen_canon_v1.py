#!/usr/bin/env python3
"""Generates CanonV1.kt — two canonical places per US state, 100 total."""

# (state, placeId, name, lat, lng, minDwellSeconds, geofenceMeters)
CANON = [
    ("AL", "us-al-uss-alabama",      "USS Alabama Battleship Park", 30.6819, -88.0147, 3600, 1500),
    ("AL", "us-al-civil-rights",     "Birmingham Civil Rights Institute", 33.5157, -86.8149, 2700, 800),
    ("AK", "us-ak-denali",           "Denali National Park", 63.1148, -151.1926, 7200, 25000),
    ("AK", "us-ak-glacier-bay",      "Glacier Bay National Park", 58.6658, -136.9002, 7200, 25000),
    ("AZ", "us-az-grand-canyon",     "Grand Canyon South Rim", 36.0544, -112.1401, 5400, 12000),
    ("AZ", "us-az-antelope-canyon",  "Antelope Canyon", 36.8619, -111.3743, 2700, 3000),
    ("AR", "us-ar-hot-springs",      "Hot Springs National Park", 34.5117, -93.0538, 3600, 6000),
    ("AR", "us-ar-crystal-bridges",  "Crystal Bridges Museum", 36.3808, -94.2043, 3600, 1200),
    ("CA", "us-ca-yosemite",         "Yosemite Valley", 37.7456, -119.5936, 7200, 15000),
    ("CA", "us-ca-golden-gate",      "Golden Gate Bridge", 37.8199, -122.4783, 1800, 2500),
    ("CO", "us-co-rocky-mountain",   "Rocky Mountain National Park", 40.3428, -105.6836, 7200, 20000),
    ("CO", "us-co-mesa-verde",       "Mesa Verde National Park", 37.2309, -108.4618, 5400, 15000),
    ("CT", "us-ct-mystic-seaport",   "Mystic Seaport Museum", 41.3626, -71.9662, 3600, 1500),
    ("CT", "us-ct-yale-art",         "Yale University Art Gallery", 41.3083, -72.9315, 2700, 800),
    ("DE", "us-de-winterthur",       "Winterthur Museum & Garden", 39.8043, -75.5985, 3600, 2500),
    ("DE", "us-de-cape-henlopen",    "Cape Henlopen State Park", 38.7862, -75.0930, 3600, 5000),
    ("FL", "us-fl-everglades",       "Everglades National Park", 25.2866, -80.8987, 5400, 25000),
    ("FL", "us-fl-kennedy-space",    "Kennedy Space Center", 28.5729, -80.6490, 5400, 6000),
    ("GA", "us-ga-savannah",         "Savannah Historic District", 32.0782, -81.0912, 5400, 3000),
    ("GA", "us-ga-mlk-historic",     "MLK Jr. National Historical Park", 33.7556, -84.3733, 2700, 1500),
    ("HI", "us-hi-volcanoes",        "Hawai'i Volcanoes National Park", 19.4194, -155.2885, 7200, 20000),
    ("HI", "us-hi-pearl-harbor",     "Pearl Harbor Memorial", 21.3649, -157.9399, 3600, 2500),
    ("ID", "us-id-craters-moon",     "Craters of the Moon", 43.4167, -113.5167, 3600, 15000),
    ("ID", "us-id-sawtooth",         "Sawtooth National Rec. Area", 44.1600, -114.9300, 7200, 25000),
    ("IL", "us-il-art-institute",    "Art Institute of Chicago", 41.8796, -87.6237, 5400, 800),
    ("IL", "us-il-cloud-gate",       "Millennium Park & Cloud Gate", 41.8826, -87.6226, 1800, 800),
    ("IN", "us-in-motor-speedway",   "Indianapolis Motor Speedway", 39.7950, -86.2347, 3600, 3000),
    ("IN", "us-in-indiana-dunes",    "Indiana Dunes National Park", 41.6533, -87.0524, 3600, 12000),
    ("IA", "us-ia-field-of-dreams",  "Field of Dreams", 42.4711, -90.9268, 2700, 1500),
    ("IA", "us-ia-effigy-mounds",    "Effigy Mounds National Monument", 43.0908, -91.1868, 3600, 6000),
    ("KS", "us-ks-tallgrass",        "Tallgrass Prairie Preserve", 38.4322, -96.5583, 3600, 12000),
    ("KS", "us-ks-nelson-atkins",    "Nelson-Atkins Museum", 39.0451, -94.5807, 3600, 800),
    ("KY", "us-ky-mammoth-cave",     "Mammoth Cave National Park", 37.1862, -86.1000, 5400, 12000),
    ("KY", "us-ky-churchill-downs",  "Churchill Downs", 38.2027, -85.7708, 3600, 1500),
    ("LA", "us-la-french-quarter",   "French Quarter", 29.9584, -90.0644, 5400, 2000),
    ("LA", "us-la-oak-alley",        "Oak Alley Plantation", 30.0055, -90.8817, 3600, 1500),
    ("ME", "us-me-acadia",           "Acadia National Park", 44.3386, -68.2733, 7200, 15000),
    ("ME", "us-me-portland-head",    "Portland Head Light", 43.6231, -70.2078, 1800, 1200),
    ("MD", "us-md-fort-mchenry",     "Fort McHenry", 39.2639, -76.5800, 2700, 1500),
    ("MD", "us-md-assateague",       "Assateague Island", 38.2497, -75.1519, 3600, 10000),
    ("MA", "us-ma-freedom-trail",    "Boston Freedom Trail", 42.3601, -71.0589, 5400, 3000),
    ("MA", "us-ma-cape-cod",         "Cape Cod National Seashore", 41.9250, -70.0200, 5400, 15000),
    ("MI", "us-mi-pictured-rocks",   "Pictured Rocks Lakeshore", 46.5522, -86.3400, 5400, 20000),
    ("MI", "us-mi-mackinac",         "Mackinac Island", 45.8492, -84.6189, 7200, 5000),
    ("MN", "us-mn-boundary-waters",  "Boundary Waters Canoe Area", 47.9500, -91.5000, 7200, 30000),
    ("MN", "us-mn-split-rock",       "Split Rock Lighthouse", 47.2003, -91.3672, 2700, 2000),
    ("MS", "us-ms-vicksburg",        "Vicksburg National Military Park", 32.3465, -90.8546, 3600, 6000),
    ("MS", "us-ms-natchez-trace",    "Natchez Trace Parkway", 32.2988, -90.9084, 3600, 15000),
    ("MO", "us-mo-gateway-arch",     "Gateway Arch", 38.6247, -90.1848, 2700, 1200),
    ("MO", "us-mo-ozarks",           "Ozark National Scenic Riverways", 37.1833, -91.2500, 5400, 20000),
    ("MT", "us-mt-glacier",          "Glacier National Park", 48.7596, -113.7870, 7200, 25000),
    ("MT", "us-mt-little-bighorn",   "Little Bighorn Battlefield", 45.5703, -107.4292, 2700, 5000),
    ("NE", "us-ne-chimney-rock",     "Chimney Rock", 41.7036, -103.3494, 1800, 3000),
    ("NE", "us-ne-henry-doorly",     "Henry Doorly Zoo", 41.2278, -95.9247, 5400, 1500),
    ("NV", "us-nv-las-vegas-strip",  "Las Vegas Strip", 36.1147, -115.1728, 5400, 3000),
    ("NV", "us-nv-valley-of-fire",   "Valley of Fire State Park", 36.4816, -114.5253, 3600, 12000),
    ("NH", "us-nh-mount-washington", "Mount Washington", 44.2705, -71.3033, 5400, 6000),
    ("NH", "us-nh-franconia-notch",  "Franconia Notch", 44.1500, -71.6833, 3600, 8000),
    ("NJ", "us-nj-liberty-park",     "Liberty State Park", 40.7050, -74.0553, 2700, 3000),
    ("NJ", "us-nj-cape-may",         "Cape May Historic District", 38.9351, -74.9060, 3600, 2500),
    ("NM", "us-nm-carlsbad",         "Carlsbad Caverns", 32.1479, -104.5567, 5400, 10000),
    ("NM", "us-nm-white-sands",      "White Sands National Park", 32.7791, -106.1717, 3600, 15000),
    ("NY", "us-ny-statue-liberty",   "Statue of Liberty", 40.6892, -74.0445, 3600, 2000),
    ("NY", "us-ny-niagara-falls",    "Niagara Falls State Park", 43.0828, -79.0742, 3600, 3000),
    ("NC", "us-nc-blue-ridge",       "Blue Ridge Parkway", 35.5951, -82.5515, 5400, 25000),
    ("NC", "us-nc-outer-banks",      "Cape Hatteras Lighthouse", 35.2508, -75.5288, 2700, 3000),
    ("ND", "us-nd-theodore-roosevelt", "Theodore Roosevelt National Park", 46.9790, -103.5387, 5400, 20000),
    ("ND", "us-nd-knife-river",      "Knife River Indian Villages", 47.3372, -101.3838, 2700, 5000),
    ("OH", "us-oh-rock-hall",        "Rock & Roll Hall of Fame", 41.5085, -81.6954, 3600, 1200),
    ("OH", "us-oh-cuyahoga",         "Cuyahoga Valley National Park", 41.2808, -81.5678, 5400, 15000),
    ("OK", "us-ok-omm",              "Oklahoma City National Memorial", 35.4728, -97.5170, 2700, 1200),
    ("OK", "us-ok-wichita-mtns",     "Wichita Mountains Refuge", 34.7300, -98.7000, 3600, 15000),
    ("OR", "us-or-crater-lake",      "Crater Lake National Park", 42.9446, -122.1090, 5400, 15000),
    ("OR", "us-or-columbia-gorge",   "Columbia River Gorge", 45.5762, -122.1158, 3600, 20000),
    ("PA", "us-pa-independence",     "Independence Hall", 39.9489, -75.1500, 2700, 1200),
    ("PA", "us-pa-gettysburg",       "Gettysburg National Military Park", 39.8117, -77.2311, 5400, 8000),
    ("RI", "us-ri-the-breakers",     "The Breakers, Newport", 41.4696, -71.2986, 2700, 1200),
    ("RI", "us-ri-block-island",     "Block Island", 41.1683, -71.5783, 5400, 6000),
    ("SC", "us-sc-charleston",       "Charleston Historic District", 32.7765, -79.9311, 5400, 3000),
    ("SC", "us-sc-fort-sumter",      "Fort Sumter", 32.7522, -79.8747, 2700, 1500),
    ("SD", "us-sd-mount-rushmore",   "Mount Rushmore", 43.8791, -103.4591, 2700, 3000),
    ("SD", "us-sd-badlands",         "Badlands National Park", 43.8554, -102.3397, 5400, 20000),
    ("TN", "us-tn-great-smoky",      "Great Smoky Mountains", 35.6532, -83.5070, 7200, 25000),
    ("TN", "us-tn-graceland",        "Graceland", 35.0478, -90.0260, 3600, 1500),
    ("TX", "us-tx-the-alamo",        "The Alamo", 29.4260, -98.4861, 2700, 1200),
    ("TX", "us-tx-big-bend",         "Big Bend National Park", 29.2498, -103.2502, 7200, 30000),
    ("UT", "us-ut-zion",             "Zion National Park", 37.2982, -113.0263, 5400, 15000),
    ("UT", "us-ut-arches",           "Arches National Park", 38.7331, -109.5925, 5400, 15000),
    ("VT", "us-vt-shelburne",        "Shelburne Museum", 44.3714, -73.2262, 3600, 1500),
    ("VT", "us-vt-stowe",            "Stowe & Mount Mansfield", 44.5438, -72.8143, 5400, 8000),
    ("VA", "us-va-monticello",       "Monticello", 38.0088, -78.4529, 3600, 2000),
    ("VA", "us-va-shenandoah",       "Shenandoah National Park", 38.5300, -78.3500, 5400, 25000),
    ("WA", "us-wa-mount-rainier",    "Mount Rainier National Park", 46.8523, -121.7603, 7200, 20000),
    ("WA", "us-wa-pike-place",       "Pike Place Market", 47.6097, -122.3422, 1800, 800),
    ("WV", "us-wv-new-river-gorge",  "New River Gorge National Park", 37.9393, -81.0668, 5400, 15000),
    ("WV", "us-wv-harpers-ferry",    "Harpers Ferry", 39.3256, -77.7386, 3600, 3000),
    ("WI", "us-wi-apostle-islands",  "Apostle Islands Lakeshore", 46.9600, -90.6600, 5400, 20000),
    ("WI", "us-wi-taliesin",         "Taliesin", 43.1409, -90.0698, 3600, 2000),
    ("WY", "us-wy-yellowstone",      "Yellowstone National Park", 44.4280, -110.5885, 7200, 40000),
    ("WY", "us-wy-grand-teton",      "Grand Teton National Park", 43.7904, -110.6818, 7200, 25000),
]

STATE_NAMES = {
    "AL": "Alabama", "AK": "Alaska", "AZ": "Arizona", "AR": "Arkansas", "CA": "California",
    "CO": "Colorado", "CT": "Connecticut", "DE": "Delaware", "FL": "Florida", "GA": "Georgia",
    "HI": "Hawaii", "ID": "Idaho", "IL": "Illinois", "IN": "Indiana", "IA": "Iowa",
    "KS": "Kansas", "KY": "Kentucky", "LA": "Louisiana", "ME": "Maine", "MD": "Maryland",
    "MA": "Massachusetts", "MI": "Michigan", "MN": "Minnesota", "MS": "Mississippi",
    "MO": "Missouri", "MT": "Montana", "NE": "Nebraska", "NV": "Nevada", "NH": "New Hampshire",
    "NJ": "New Jersey", "NM": "New Mexico", "NY": "New York", "NC": "North Carolina",
    "ND": "North Dakota", "OH": "Ohio", "OK": "Oklahoma", "OR": "Oregon", "PA": "Pennsylvania",
    "RI": "Rhode Island", "SC": "South Carolina", "SD": "South Dakota", "TN": "Tennessee",
    "TX": "Texas", "UT": "Utah", "VT": "Vermont", "VA": "Virginia", "WA": "Washington",
    "WV": "West Virginia", "WI": "Wisconsin", "WY": "Wyoming",
}


def validate():
    errs = []
    ids = [c[1] for c in CANON]
    if len(ids) != len(set(ids)):
        errs.append("duplicate placeIds")
    states = {}
    for st, pid, name, lat, lng, dwell, geo in CANON:
        states[st] = states.get(st, 0) + 1
        if st not in STATE_NAMES:
            errs.append(f"unknown state {st}")
        if not (-180 <= lng <= -60):
            errs.append(f"{pid}: lng {lng} outside US range")
        if not (18 <= lat <= 72):
            errs.append(f"{pid}: lat {lat} outside US range")
        if dwell < 600:
            errs.append(f"{pid}: dwell floor {dwell} too low")
        if not (500 <= geo <= 50000):
            errs.append(f"{pid}: geofence {geo} implausible")
        if not pid.startswith("us-" + st.lower() + "-"):
            errs.append(f"{pid}: id does not encode state {st}")
    missing = set(STATE_NAMES) - set(states)
    if missing:
        errs.append(f"states missing: {sorted(missing)}")
    bad = {s: n for s, n in states.items() if n != 2}
    if bad:
        errs.append(f"states without exactly 2 places: {bad}")
    return errs


def emit():
    lines = []
    lines.append("package app.odyssey.engine")
    lines.append("")
    lines.append("/**")
    lines.append(" * Canon release v1 — the fixed national canon for milestone 0.")
    lines.append(" *")
    lines.append(" * Exactly two must-go places per state, 100 entries across all 50 states.")
    lines.append(" * A state completes only when both of its ACTIVE entries are credited, so")
    lines.append(" * state coverage and places coverage move at genuinely different rates.")
    lines.append(" *")
    lines.append(" * Generated data — edit the release, never the user's history. Changing the")
    lines.append(" * canon means publishing v2 and re-folding; there is no migration path")
    lines.append(" * because there is no derived state to migrate.")
    lines.append(" */")
    lines.append("object CanonV1 {")
    lines.append("")
    lines.append("    val stateNames: Map<String, String> = mapOf(")
    for k in sorted(STATE_NAMES):
        lines.append(f'        "{k}" to "{STATE_NAMES[k]}",')
    lines.append("    )")
    lines.append("")
    lines.append("    val release: CanonRelease = CanonRelease(")
    lines.append("        version = 1,")
    lines.append("        entries = listOf(")
    for st, pid, name, lat, lng, dwell, geo in CANON:
        esc = name.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
        lines.append("            CanonEntry(")
        lines.append(f'                placeId = "{pid}",')
        lines.append(f'                usState = "{st}",')
        lines.append(f'                name = "{esc}",')
        lines.append("                lifecycle = Lifecycle.ACTIVE,")
        lines.append(f"                centroid = LatLng({lat}, {lng}),")
        lines.append(f"                minDwellSeconds = {dwell},")
        lines.append(f"                geofenceRadiusMeters = {geo}.0,")
        lines.append("            ),")
    lines.append("        ),")
    lines.append("    )")
    lines.append("")
    lines.append("    fun stateName(code: String): String = stateNames[code] ?: code")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    errs = validate()
    if errs:
        for e in errs:
            print("FAIL:", e)
        raise SystemExit(1)
    out = "/sessions/great-nice-gates/mnt/GitHub/my-odyssey/engine/src/commonMain/kotlin/app/odyssey/engine/CanonV1.kt"
    with open(out, "w") as fh:
        fh.write(emit())
    print(f"OK: {len(CANON)} entries, {len(set(c[0] for c in CANON))} states -> {out}")
