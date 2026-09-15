package com.example.data.news

/**
 * Composes the spoken disaster-intelligence bulletin from REAL state only:
 * the user's personal risk level, the recommended action and the latest
 * real GNews headlines. Nothing is fabricated — when no news is loaded the
 * bulletin says so and points to the Sync control.
 */
object NewsTtsBulletin {

  fun compose(
    riskLevelLabel: String,
    recommendedActionTitle: String?,
    actionExplanation: String?,
    articles: List<NewsArticle>,
    nowMillis: Long,
    isFromCache: Boolean
  ): String {
    val sb = StringBuilder()
    sb.append("Vippatti Sarana disaster intelligence bulletin. ")
    sb.append("Your current personal risk level is $riskLevelLabel. ")
    recommendedActionTitle?.let { sb.append("Recommended action: ${cleanForSpeech(it)}. ") }
    actionExplanation?.let { sb.append("${cleanForSpeech(it)} ") }
    if (articles.isEmpty()) {
      sb.append(
        "No disaster news articles are currently loaded. " +
          "Open the intelligence tab and tap Sync to fetch the latest news. "
      )
    } else {
      sb.append("Latest disaster news headlines. ")
      articles.take(3).forEachIndexed { index, article ->
        sb.append(
          "Headline ${index + 1}: ${cleanForSpeech(article.title)}. " +
            "Published ${NewsPresentation.spokenAge(article.publishedAtMillis, nowMillis)}, " +
            "from ${cleanForSpeech(article.sourceName)}. "
        )
      }
    }
    if (isFromCache) {
      sb.append("These headlines come from the offline news cache. ")
    }
    sb.append(
      "News headlines are provided by G News and are not official government alerts."
    )
    return sb.toString()
  }

  private fun cleanForSpeech(text: String): String =
    text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
}
