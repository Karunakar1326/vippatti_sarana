package com.example.data.news

import com.example.data.location.ResolvedPlace

/**
 * GNews boolean search queries, built at runtime from the resolved place.
 *
 * The query SHAPE was live-validated against the real GNews /search endpoint
 * (boolean AND/OR groups + quoted exact phrases, lang=en, country=in,
 * sortby=publishedAt). The place names are NOT part of this file: they arrive
 * from [com.example.data.location.PlaceResolver], so the app queries the
 * user's own district and state wherever they are in India.
 *
 * With no resolved place the factory emits the national query only - it never
 * falls back to a hardcoded district or state.
 */
object NewsQueryFactory {

  /** Hazard vocabulary shared by every ring; national query = the terms alone. */
  private const val HAZARD_TERMS =
    "(flood OR landslide OR cyclone OR \"heavy rain\" OR " +
      "\"disaster management\" OR evacuation)"

  const val INDIA_QUERY = HAZARD_TERMS

  /** One search ring: which cache scope it fills, its query, and its label. */
  data class ScopedNewsQuery(
    val scope: NewsScope,
    val query: String,
    val label: String
  )

  /** Quoted exact-phrase query for a resolved name, or null when unusable. */
  fun namedQuery(name: String?): String? =
    sanitize(name)?.let { "\"$it\" AND $HAZARD_TERMS" }

  /**
   * Queries for the resolved place, narrowest first. The national ring is
   * ALWAYS present so the feed is never empty just because a name could not
   * be resolved.
   */
  fun buildQueries(place: ResolvedPlace?): List<ScopedNewsQuery> {
    val out = mutableListOf<ScopedNewsQuery>()
    val district = sanitize(place?.district)
    val state = sanitize(place?.state)
    namedQuery(district)?.let { query ->
      out += ScopedNewsQuery(
        scope = NewsScope.MY_AREA,
        query = query,
        label = NewsScope.MY_AREA.ringLabel(place)
      )
    }
    // Skip a state ring that merely repeats the district name (city-states and
    // place names that are identical at both levels would waste one call).
    if (state != null && !state.equals(district, ignoreCase = true)) {
      namedQuery(state)?.let { query ->
        out += ScopedNewsQuery(
          scope = NewsScope.MY_STATE,
          query = query,
          label = NewsScope.MY_STATE.ringLabel(place)
        )
      }
    }
    out += ScopedNewsQuery(NewsScope.INDIA, INDIA_QUERY, NewsScope.INDIA.ringLabel(place))
    return out
  }

  /** Strips quotes/extra whitespace so a name can never break the boolean query. */
  fun sanitize(name: String?): String? =
    name?.replace("\"", "")?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }

  /** The GNews free plan silently caps any higher value at 10 articles. */
  const val MAX_ARTICLES_PER_REQUEST = 10
  const val LANGUAGE = "en"
  const val COUNTRY = "in"
}
