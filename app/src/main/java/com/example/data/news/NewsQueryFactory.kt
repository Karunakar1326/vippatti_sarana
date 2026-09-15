package com.example.data.news

/**
 * GNews boolean search queries, one per [NewsScope].
 *
 * Every query below was live-validated against the real GNews /search
 * endpoint (boolean AND/OR groups + quoted exact phrases, lang=en,
 * country=in, sortby=publishedAt):
 *  - MY_AREA:  ~200 matching articles (12h-delayed free-plan index)
 *  - MY_STATE: ~1.7k matching articles
 *  - INDIA:    ~45k matching articles
 */
object NewsQueryFactory {

  const val MY_AREA_QUERY =
    "\"Idukki\" AND (flood OR landslide OR cyclone OR \"heavy rain\" OR " +
      "\"disaster management\" OR evacuation)"

  const val MY_STATE_QUERY =
    "\"Kerala\" AND (flood OR landslide OR cyclone OR \"heavy rain\" OR evacuation)"

  const val INDIA_QUERY =
    "(flood OR landslide OR cyclone OR \"heavy rain\" OR " +
      "\"disaster management\" OR evacuation)"

  fun queryFor(scope: NewsScope): String = when (scope) {
    NewsScope.MY_AREA -> MY_AREA_QUERY
    NewsScope.MY_STATE -> MY_STATE_QUERY
    NewsScope.INDIA -> INDIA_QUERY
  }

  /** The GNews free plan silently caps any higher value at 10 articles. */
  const val MAX_ARTICLES_PER_REQUEST = 10
  const val LANGUAGE = "en"
  const val COUNTRY = "in"
}
