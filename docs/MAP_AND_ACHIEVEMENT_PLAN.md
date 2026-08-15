# Map and Achievement Development Plan

## Product decisions

- `Home` remains the achievement dashboard with summary cards and recent places.
- `Log` shows one item per place/entity, not one item per visit event.
- Expanding a place shows all visit dates and notes for that place.
- `Explore` will be a separate map tab. It will start at the world view and
  support drilling down through continent, country, and first-level region.
- Check-in keeps search as the reliable primary interaction; the map is an
  optional visual confirmation and future alternative entry point.
- Countries with available first-level administrative data should require a
  region selection. Country-only check-ins remain a fallback for countries
  without usable region data.

## Implementation phases

1. Fix the product rules and data contracts.
2. Add grouped achievement APIs while retaining event-level check-in APIs.
3. Refactor Android Log to grouped places with expandable visit history.
4. Add the Explore map MVP and render existing check-in coordinates.
5. Add country/region drill-down, search, breadcrumbs, and map markers.
6. Add vector tile loading, local caching, viewport-based loading, and
   preloading of likely next regions.
7. Validate Android emulator/device E2E behavior and deploy a PostgreSQL/Cloud
   SQL staging environment.

## Current slice

- Added `GET /v1/achievements` for user-scoped place aggregates.
- Preserved `GET /v1/checkins` for individual visit events.
- Android Log groups cached visit events by `entityId` and expands dates/notes.
- Explore now supports two-step country/admin1 selection, floating search
  menus, breadcrumbs/back navigation, selected-area markers, and camera focus.
- MapLibre style URLs remain configurable; the Android map reports camera idle
  center, zoom, and visible bounds as the foundation for viewport loading.
- Vector tile caching, viewport-based catalog loading, and next-region
  preloading remain the next implementation slice.

## Open technical decisions for review

- MapLibre versus Google Maps versus Mapbox.
- Vector tile provider, licensing, access tokens, and offline usage policy.
- Whether country achievement counts are derived automatically from region
  check-ins or stored as separate unlock rows.
- Guest data ownership, sign-in import, and signed-out cache isolation.

## Map style configuration

The Android client keeps the MapLibre style URL configurable instead of
hard-coding a provider into the UI. The default MVP style is OpenFreeMap's
public OpenMapTiles/OSM-backed style. It is suitable for the prototype, but
provider terms, attribution, availability, and traffic limits must be reviewed
before production. After selecting a provider, build with its style URL, for
example:

```bash
cd app
./gradlew :composeApp:assembleDebug \
  -PMAP_STYLE_URL="https://your-provider.example/style.json"
```

## Boundary overlay contract

The basemap and administrative boundaries are intentionally separate. A
catalog country or admin-1 record may expose `boundaryGeoJsonUrl` through the
API. The GeoJSON FeatureCollection must include a `catalog_id` feature
property. The pipeline also adds `catalog_bounds` to matched features.
Android renders that URL as a MapLibre GeoJSON source, draws the admin-1
outlines, fits the camera to the returned bounds, and fills plus outlines the
selected catalog entity. This keeps boundary
data replaceable: the first data pipeline can use geoBoundaries, while a later
deployment can serve simplified GeoJSON or PMTiles from company-controlled
storage without changing Explore's UI.

The style URL and any provider token policy must be reviewed before release.
The Android map now reports its camera center, zoom, and visible north/south/
east/west bounds when camera movement becomes idle. The next data-loading slice
can use those bounds for viewport-based catalog requests and cache keys.
