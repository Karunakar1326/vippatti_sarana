package com.example.data.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Outcome of one GNews /search call. */
sealed class GNewsCall {
  data class Success(
    val articles: List<NewsArticle>,
    val totalArticles: Int
  ) : GNewsCall()

  data class Failure(val error: NewsError) : GNewsCall()
}

/** Network boundary for the GNews v4 search endpoint. */
interface GNewsService {
  suspend fun search(scope: NewsScope, apiKey: String): GNewsCall
}

/**
 * Real OkHttp-backed GNews client, following the OsrmRoutingService network
 * convention (OkHttp + org.json, short timeouts, honest error mapping).
 *
 * Live-validated error behaviour:
 *  - placeholder/blank key -> honest NO_API_KEY error without any network call
 *  - HTTP 400 (observed with an invalid key), 401/403 -> key problems
 *  - HTTP 429 -> daily free-plan quota exhausted
 *  - connectivity failure -> offline error (the cached feed stays usable)
 */
class GNewsServiceImpl(
  private val httpClient: OkHttpClient = defaultHttpClient()
) : GNewsService {

  override suspend fun search(scope: NewsScope, apiKey: String): GNewsCall =
    withContext(Dispatchers.IO) {
      val key = apiKey.trim()
      if (key.isBlank() || key == GNEWS_PLACEHOLDER_KEY) {
        return@withContext GNewsCall.Failure(
          NewsError(NewsErrorKind.NO_API_KEY, NO_KEY_MESSAGE)
        )
      }
      val query = URLEncoder.encode(NewsQueryFactory.queryFor(scope), "UTF-8")
        .replace("+", "%20")
      val url = "https://gnews.io/api/v4/search" +
        "?q=$query" +
        "&lang=${NewsQueryFactory.LANGUAGE}" +
        "&country=${NewsQueryFactory.COUNTRY}" +
        "&max=${NewsQueryFactory.MAX_ARTICLES_PER_REQUEST}" +
        "&sortby=publishedAt" +
        "&apikey=$key"
      try {
        val request = Request.Builder()
          .url(url)
          .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; GNews Intelligence)")
          .build()
        httpClient.newCall(request).execute().use { response ->
          val body = response.body?.string()
          if (response.isSuccessful && !body.isNullOrBlank()) {
            GNewsCall.Success(
              articles = GNewsJsonParser.parseArticles(body, scope),
              totalArticles = GNewsJsonParser.parseTotal(body)
            )
          } else if (response.isSuccessful) {
            GNewsCall.Failure(
              NewsError(NewsErrorKind.SERVER, "GNews returned an empty response — try syncing again.")
            )
          } else {
            GNewsCall.Failure(mapHttpError(response.code))
          }
        }
      } catch (e: IOException) {
        GNewsCall.Failure(
          NewsError(NewsErrorKind.NETWORK, "No internet connection — cached disaster news stays available offline.")
        )
      } catch (e: Exception) {
        GNewsCall.Failure(
          NewsError(NewsErrorKind.NETWORK, "GNews request failed (${e.javaClass.simpleName}) — cached news stays available.")
        )
      }
    }

  private fun mapHttpError(code: Int): NewsError = when (code) {
    400 -> NewsError(
      NewsErrorKind.BAD_REQUEST,
      "GNews rejected the request (HTTP 400 — usually an invalid API key). Verify GNEWS_API_KEY in app/.env."
    )
    401, 403 -> NewsError(
      NewsErrorKind.INVALID_KEY,
      "GNews rejected the API key (HTTP $code). Verify GNEWS_API_KEY in app/.env."
    )
    429 -> NewsError(
      NewsErrorKind.QUOTA_EXCEEDED,
      "Daily GNews request quota reached. Cached articles remain available; the quota resets daily."
    )
    in 500..599 -> NewsError(
      NewsErrorKind.SERVER,
      "GNews server error (HTTP $code). Try syncing again in a few minutes."
    )
    else -> NewsError(
      NewsErrorKind.SERVER,
      "GNews request failed (HTTP $code)."
    )
  }

  companion object {
    fun defaultHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
  }
}
