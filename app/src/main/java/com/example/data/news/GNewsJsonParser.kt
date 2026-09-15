package com.example.data.news

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the live-validated GNews /api/v4/search response schema:
 *
 * { "totalArticles": 206,
 *   "articles": [ { "id": "...", "title": "...", "description": "...",
 *     "content": "... (truncated by GNews at ~1.1k chars)", "url": "...",
 *     "image": "https://...", "publishedAt": "2026-08-30T10:31:05Z",
 *     "lang": "en", "source": { "id", "name", "url", "country" } } ] }
 *
 * Optional fields are treated honestly: a missing image stays null and a
 * missing description stays empty — nothing is invented or padded.
 */
object GNewsJsonParser {

  /** ISO-8601 UTC format GNews returns, e.g. 2026-08-30T10:31:05Z. */
  private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

  fun parseTotal(responseBody: String): Int = runCatching {
    JSONObject(responseBody).optInt("totalArticles", 0)
  }.getOrDefault(0)

  fun parseArticles(responseBody: String, scope: NewsScope): List<NewsArticle> {
    val articles = runCatching {
      JSONObject(responseBody).optJSONArray("articles")
    }.getOrNull() ?: return emptyList()
    val out = mutableListOf<NewsArticle>()
    for (i in 0 until articles.length()) {
      val obj = articles.optJSONObject(i) ?: continue
      val title = obj.optString("title").trim()
      val url = obj.optString("url").trim()
      // Skip malformed entries rather than fabricating placeholders.
      if (title.isEmpty() || url.isEmpty()) continue
      val description = obj.optString("description").trim()
      val source = obj.optJSONObject("source")
      val publishedIso = obj.optString("publishedAt").trim()
      out += NewsArticle(
        id = obj.optString("id").takeIf { it.isNotBlank() } ?: url,
        title = title,
        description = description,
        content = obj.optString("content").trim(),
        url = url,
        imageUrl = obj.optString("image").takeIf { it.isNotBlank() },
        publishedAtIso = publishedIso,
        publishedAtMillis = parseIsoToMillis(publishedIso),
        language = obj.optString("lang"),
        sourceName = source?.optString("name")?.takeIf { it.isNotBlank() }
          ?: "Unknown source",
        sourceUrl = source?.optString("url") ?: "",
        scope = scope,
        category = NewsClassifier.classify(title, description)
      )
    }
    return out
  }

  /** "2026-08-30T10:31:05Z" -> epoch millis; 0 when the value is unusable. */
  fun parseIsoToMillis(iso: String): Long {
    // Expected input length is 20 ("2026-08-30T10:31:05Z") — NOT the pattern
    // string length, whose quote characters never appear in the input.
    if (iso.length != 20) return 0L
    return try {
      val format = SimpleDateFormat(ISO_PATTERN, Locale.US)
      format.timeZone = TimeZone.getTimeZone("UTC")
      format.isLenient = false
      format.parse(iso)?.time ?: 0L
    } catch (e: Exception) {
      0L
    }
  }
}

/**
 * Transparent keyword heuristic that assigns one editorial category per
 * article from real headline/description text only — never presented as an
 * official government classification. Ordering is safety-first: life-loss
 * and rescue wording outranks road impact, then shelters, government
 * advisories and weather.
 */
object NewsClassifier {

  fun classify(title: String, description: String): NewsCategory {
    val text = "$title . $description".lowercase()
    return when {
      SEVERE_KEYWORDS.any { text.contains(it) } -> NewsCategory.SEVERE_ALERTS
      ROAD_KEYWORDS.any { text.contains(it) } -> NewsCategory.ROAD_IMPACT
      SHELTER_KEYWORDS.any { text.contains(it) } -> NewsCategory.SHELTER
      GOVERNMENT_KEYWORDS.any { text.contains(it) } -> NewsCategory.GOVERNMENT
      WEATHER_KEYWORDS.any { text.contains(it) } -> NewsCategory.WEATHER
      else -> NewsCategory.GENERAL
    }
  }

  private val SEVERE_KEYWORDS = listOf(
    "died", "death", "dead", "killed", "casualties", "evacuat", "rescue",
    "trapped", "missing", "flash flood", "inundat", "red alert",
    "orange alert", "collapsed", "destroyed", "landslide", "mudslide",
    "deluge", "swept away"
  )
  private val ROAD_KEYWORDS = listOf(
    "road block", "road closed", "roads closed", "blocked", "boulder",
    "traffic diverted", "highway shut", "route cut", "landslip"
  )
  private val SHELTER_KEYWORDS = listOf(
    "relief camp", "shelter", "rehabilitat", "displaced", "camp opened",
    "relief centre", "relief center"
  )
  private val GOVERNMENT_KEYWORDS = listOf(
    "ksdma", "ndma", "sdma", "district collector", "chief minister",
    "disaster management authority", "revenue minister",
    "district magistrate", "administration issued"
  )
  private val WEATHER_KEYWORDS = listOf(
    "rain", "monsoon", "forecast", "imd", "weather", "thunderstorm",
    "cyclone", "low pressure", "yellow alert", "showers",
    "heavy to very heavy"
  )
}
