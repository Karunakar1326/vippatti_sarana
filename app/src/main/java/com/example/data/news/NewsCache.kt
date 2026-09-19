package com.example.data.news

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Freshness rules for the on-device news cache (pure logic, unit-testable).
 *
 * The fresh window is deliberately quota-friendly: within it a cold start
 * serves the cache without spending any of the free plan's daily requests.
 * Disaster news older than the max age is discarded — stale hazard news
 * misleads.
 */
object NewsCachePolicy {

  const val FRESH_TTL_MILLIS: Long = 30L * 60L * 1000L
  const val MAX_AGE_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

  fun isFresh(fetchedAtMillis: Long, nowMillis: Long): Boolean =
    fetchedAtMillis > 0L && nowMillis - fetchedAtMillis in 0 until FRESH_TTL_MILLIS

  fun isUsableStale(fetchedAtMillis: Long, nowMillis: Long): Boolean =
    fetchedAtMillis > 0L && nowMillis - fetchedAtMillis <= MAX_AGE_MILLIS
}

/** One cached scope shard. */
data class CachedFeed(
  val articles: List<NewsArticle>,
  val fetchedAtMillis: Long
)

/** On-device news cache boundary (in-memory in tests, files in production). */
interface NewsCache {
  fun read(scope: NewsScope): CachedFeed?
  fun write(scope: NewsScope, articles: List<NewsArticle>, fetchedAtMillis: Long)
  fun clear()
}

/** In-memory cache — the default when no file storage is provided (tests). */
class MemoryNewsCache : NewsCache {
  private val map = mutableMapOf<NewsScope, CachedFeed>()
  override fun read(scope: NewsScope): CachedFeed? = map[scope]
  override fun write(scope: NewsScope, articles: List<NewsArticle>, fetchedAtMillis: Long) {
    map[scope] = CachedFeed(articles, fetchedAtMillis)
  }
  override fun clear() {
    map.clear()
  }
}

/**
 * File-backed cache (one JSON shard per scope) inside the app's private
 * storage. Corrupt shards are dropped instead of crashing — the feed then
 * honestly refetches.
 */
class NewsFileCache(cacheDir: File) : NewsCache {

  private val dir: File = cacheDir.apply { mkdirs() }

  override fun read(scope: NewsScope): CachedFeed? {
    val file = shardFile(scope)
    if (!file.isFile) return null
    return try {
      val root = JSONObject(file.readText())
      val fetchedAt = root.optLong("fetchedAtMillis", 0L)
      val arr = root.optJSONArray("articles") ?: JSONArray()
      val articles = mutableListOf<NewsArticle>()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val id = obj.optString("id")
        val title = obj.optString("title")
        val url = obj.optString("url")
        if (id.isBlank() || title.isBlank() || url.isBlank()) continue
        articles += NewsArticle(
          id = id,
          title = title,
          description = obj.optString("description"),
          content = obj.optString("content"),
          url = url,
          imageUrl = obj.optString("image").takeIf { it.isNotBlank() },
          publishedAtIso = obj.optString("publishedAtIso"),
          publishedAtMillis = obj.optLong("publishedAtMillis", 0L),
          language = obj.optString("lang"),
          sourceName = obj.optString("sourceName").ifBlank { "Unknown source" },
          sourceUrl = obj.optString("sourceUrl"),
          scope = runCatching { NewsScope.valueOf(obj.optString("scope")) }
            .getOrDefault(scope),
          category = runCatching { NewsCategory.valueOf(obj.optString("category")) }
            .getOrDefault(NewsCategory.GENERAL)
        )
      }
      CachedFeed(articles, fetchedAt)
    } catch (e: Exception) {
      file.delete() // corrupt shard — drop it honestly and refetch later
      null
    }
  }

  override fun write(scope: NewsScope, articles: List<NewsArticle>, fetchedAtMillis: Long) {
    try {
      val arr = JSONArray()
      articles.forEach { article ->
        arr.put(
          JSONObject()
            .put("id", article.id)
            .put("title", article.title)
            .put("description", article.description)
            .put("content", article.content)
            .put("url", article.url)
            .put("image", article.imageUrl ?: "")
            .put("publishedAtIso", article.publishedAtIso)
            .put("publishedAtMillis", article.publishedAtMillis)
            .put("lang", article.language)
            .put("sourceName", article.sourceName)
            .put("sourceUrl", article.sourceUrl)
            .put("scope", article.scope.name)
            .put("category", article.category.name)
        )
      }
      val target = shardFile(scope)
      // Atomic write: a process kill mid-write must not leave a truncated
      // shard that would blank the whole feed on next read.
      val tmp = File(dir, "${target.name}.tmp")
      tmp.writeText(
        JSONObject()
          .put("fetchedAtMillis", fetchedAtMillis)
          .put("articles", arr)
          .toString()
      )
if (!tmp.renameTo(target)) {
        target.writeText(tmp.readText())
        tmp.delete()
      }
    } catch (e: Exception) {
      // Cache write failure is non-fatal: the live feed still renders.
    }
  }

  override fun clear() {
    NewsScope.values().forEach { shardFile(it).delete() }
  }

  private fun shardFile(scope: NewsScope): File = File(dir, "news_${scope.name}.json")
}
