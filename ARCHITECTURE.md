# Vippatti Sarana — Architecture Map

All-India disaster-relief decision-support app. Single-activity Jetpack
Compose app with one ViewModel and honest, labeled data sources. Idukki
district, Kerala appears only as configurable pilot/demo data (sample shelter
network and demonstration records), not as a scope limit: providers query
India-wide bounding boxes and the resolved user location drives scoping.

## Primary flow (single pipeline, no duplicates)

```
Compose UI (ui/screens, ui/components)
   └── VippattiViewModel (viewmodel/VippattiViewModel.kt)  ← single source of truth
         ├── Location: REAL device GPS (osmdroid overlay) or labeled India fallback
         ├── data/risk/*        RiskAssessmentEngine → ActionAdvisor → RelocationPlanner
         ├── data/shelters/*    SafeZoneEvaluator (filter-then-rank, safety-dominant)
         ├── data/routing/*     OsrmRoutingService (live FOSSGIS OSRM + offline corridor
         │                      fallback + HazardRoutingPolicy + alternatives)
         ├── data/reports/*     EmergencyReportService (NDRF pilot simulation)
         └── data/news/*       GNews disaster-news pipeline (below)
ui/components/OsmDroidRadarMapView.kt — THE single map engine (OSMDroid + OSM)
```

## News / disaster-intelligence pipeline (real GNews)

- `data/news/NewsModels.kt` — articles, scopes (Idukki → Kerala → India),
  categories, honest error model. All queries live-validated against
  https://gnews.io/api/v4/search (boolean AND/OR, lang=en, country=in,
  sortby=publishedAt, free plan caps max at 10).
- `data/news/NewsQueryFactory.kt` — one disaster query per scope.
- `data/news/GNewsJsonParser.kt` — org.json parsing + `NewsClassifier`
  (transparent keyword heuristic; safety-first ordering) + ISO-8601 timestamps.
- `data/news/GNewsService.kt` — OkHttp client (mirrors OsrmRoutingService).
  Live-validated errors: invalid key → HTTP 400/401/403; quota → 429; honest
  messages for network/server failures. Placeholder keys are refused.
- `data/news/NewsCache.kt` — file cache per scope + `NewsCachePolicy`
  (fresh 30 min, discarded after 7 days). Corrupt shards dropped honestly.
- `data/news/NewsRepository.kt` — cache-first loading, quota-aware live
  refresh (scope cascade stops on quota), dedupe by id/title, newest first.
- `data/news/NewsPresentation.kt` — pure article → UI-model mapping, hero
  pick, relative ages. Nothing fabricated.
- `data/news/NewsTtsBulletin.kt` — real spoken bulletin composed ONLY from
  real state (risk level, recommended action, top headlines). Spoken by the
  Android TextToSpeech engine wired in MainActivity.

## Honesty rules (non-negotiable)

- Location labeled `• DEVICE GPS` or `• INDIA FALLBACK` — never silent fallback.
- News badge: `GNEWS • NOT AN OFFICIAL ALERT`; free plan publishes articles
  with ~12-hour delay — disclosed in the feed banner.
- No fabricated alerts, routes, "verified" badges or filler dispatches; empty
  and error states say exactly what happened.
- Safe-zone ranking: reject (hazard/full/closed) then rank (safety 0.30 ≫
  distance 0.15).

## Secrets

`GNEWS_API_KEY` lives in `app/.env` (git-ignored; placeholder in
`app/.env.example`), injected by the Secrets Gradle Plugin as
`BuildConfig.GNEWS_API_KEY` — never hardcoded in Kotlin.

## Build & verify

```
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```
