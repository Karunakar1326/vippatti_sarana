package com.example.data.news

/**
 * ============================================================================
 * DISASTER-RELEVANCE FILTER — keeps the news feed useful and on-topic.
 * ============================================================================
 *
 * GNews keyword queries occasionally surface general news (politics, business,
 * sports) that shares a keyword. Every article — whether it arrives live from
 * the API or from the on-device cache — must match a resilience-focused
 * keyword in its title/description/content before it reaches the dispatches
 * screen. Matching is transparent and conservative; articles that do not
 * match are dropped (never shown), and when nothing matches the feed shows an
 * honest empty state or previous disaster coverage from the cache.
 */
object NewsFilter {

  /** Resilience vocabulary — matching any one keeps the article actionable. */
  private val DISASTER_TERMS = listOf(
    // Event types
    "flood", "cyclone", "landslide", "mudslide", "earthquake", "quake",
    "tsunami", "avalanche", "drought", "wildfire", "cloudburst", "heatwave",
    "heat wave", "heatwave", "cold wave", "coldwave", "squall", "hail",
    "lightning", "gale", "storm", "heavy rain", "downpour", "monsoon",
    "glacial", "eruption", "tremor", "aftershock", "dam breach", "embankment",
    "rain", "rainfall",
    // Response / impact
    "evacuat", "rescue", "relief", "shelter", "displaced", "victim",
    "casualt", "death", "injur", "affected", "stranded", "trapped",
    "NDRF", "SDRF", "relief camp", "emergency", "advisory", "warning",
    "alert", "restoration", "rehabilitation", "assistance", "recover")
    // "disaster"/"crisis" handled implicitly by queries + classifier

  /** True when the article's own text signals disaster relevance. */
  fun isDisasterRelevant(article: NewsArticle): Boolean {
    val haystack = buildString {
      append(article.title.lowercase())
      append(' ')
      append(article.description.lowercase())
      append(' ')
      append(article.content.lowercase())
    }
    return DISASTER_TERMS.any { haystack.contains(it) }
  }

  /** Filters a list, keeping disabled/unmatched articles out. */
  fun filterForDisaster(articles: List<NewsArticle>): List<NewsArticle> =
    articles.filter { isDisasterRelevant(it) }
}