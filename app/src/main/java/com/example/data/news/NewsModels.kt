package com.example.data.news

/**
 * ============================================================================
 * GNEWS DISASTER-INTELLIGENCE MODELS — real news data, honestly labeled.
 * ============================================================================
 *
 * Everything in this package is backed by the real GNews API
 * (https://gnews.io) or its on-device cache. Nothing is fabricated: when
 * there is no data, the UI shows an honest empty or error state.
 */

/** Placeholder key value written in .env.example — detected and refused. */
const val GNEWS_PLACEHOLDER_KEY = "YOUR_GNEWS_API_KEY_HERE"

const val NO_KEY_MESSAGE =
  "News key not configured — add GNEWS_API_KEY to app/.env and rebuild. " +
    "Cached news (if any) is shown."

/** Geographic relevance ring of the disaster news feed. */
enum class NewsScope(val label: String) {
  /** District level — Idukki, Kerala (the pilot region). */
  MY_AREA("Idukki District"),
  /** State level — Kerala. */
  MY_STATE("Kerala"),
  /** National level — India. */
  INDIA("India")
}

/**
 * Editorial category assigned by the transparent keyword heuristic in
 * [NewsClassifier] — never presented as an official government tag.
 */
enum class NewsCategory(val displayTag: String) {
  SEVERE_ALERTS("Severe Alert"),
  ROAD_IMPACT("Road Impact"),
  WEATHER("Weather"),
  SHELTER("Shelter / Relief"),
  GOVERNMENT("Govt / Official"),
  GENERAL("News")
}

/** One real news article returned by GNews. */
data class NewsArticle(
  val id: String,
  val title: String,
  val description: String,
  val content: String,
  val url: String,
  val imageUrl: String?,
  /** Raw GNews ISO-8601 timestamp, e.g. "2026-09-14T04:44:31Z". */
  val publishedAtIso: String,
  /** Parsed publication time; 0 when the timestamp cannot be parsed. */
  val publishedAtMillis: Long,
  val language: String,
  val sourceName: String,
  val sourceUrl: String,
  val scope: NewsScope,
  val category: NewsCategory
) {
  /** Honest source line, e.g. "Malayala Manorama • Idukki District". */
  val sourceLine: String get() = "$sourceName • ${scope.label}"
}

/** Failure kinds surfaced honestly to the UI. */
enum class NewsErrorKind {
  NO_API_KEY,
  INVALID_KEY,
  QUOTA_EXCEEDED,
  BAD_REQUEST,
  NETWORK,
  SERVER
}

data class NewsError(
  val kind: NewsErrorKind,
  val userMessage: String
)

/**
 * Full feed state produced by the repository: merged real articles
 * (district first, then state, then nation — deduped, newest first), the
 * severe-alert hero candidate, cache/live provenance and any error.
 */
data class NewsFeed(
  val articles: List<NewsArticle>,
  val hero: NewsArticle?,
  val lastFetchedAtMillis: Long?,
  val isFromCache: Boolean,
  val error: NewsError?
)
