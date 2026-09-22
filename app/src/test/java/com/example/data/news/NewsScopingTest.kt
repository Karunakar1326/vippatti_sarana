package com.example.data.news

import com.example.data.location.ResolvedPlace
import com.example.viewmodel.VippattiUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DYNAMIC-DATA RULE - news scoping contracts.
 *
 * The app is India-wide, so no district or state may be compiled in:
 *  - with no resolved place the feed is national only and says so;
 *  - with a resolved place the rings are named after THAT place;
 *  - names are sanitized so they cannot break the boolean query;
 *  - ring labels never name a place the app was not given.
 */
class NewsScopingTest {

  private val place = ResolvedPlace(
    district = "Wayanad",
    state = "Kerala",
    country = "India",
    source = "test resolver"
  )

  @Test
  fun `no resolved place means the national ring only, never a default district`() {
    val rings = NewsQueryFactory.buildQueries(null)
    assertEquals(1, rings.size)
    assertEquals(NewsScope.INDIA, rings[0].scope)
    assertEquals(NewsQueryFactory.INDIA_QUERY, rings[0].query)
    assertTrue(NewsQueryFactory.sanitize(null) == null)
  }

  @Test
  fun `a resolved place produces district then state then national rings`() {
    val rings = NewsQueryFactory.buildQueries(place)
    assertEquals(listOf(NewsScope.MY_AREA, NewsScope.MY_STATE, NewsScope.INDIA), rings.map { it.scope })
    assertTrue(rings[0].query.contains("\"Wayanad\" AND"))
    assertTrue(rings[1].query.contains("\"Kerala\" AND"))
    // Every ring still carries the hazard vocabulary.
    rings.forEach { assertTrue(it.query.contains("flood")) }
  }

  @Test
  fun `a state ring identical to the district is not queried twice`() {
    val city = ResolvedPlace(district = "Kolkata", state = "Kolkata", source = "test resolver")
    val rings = NewsQueryFactory.buildQueries(city)
    assertEquals(2, rings.size)
    assertEquals(NewsScope.MY_AREA, rings[0].scope)
    assertEquals(NewsScope.INDIA, rings[1].scope)
  }

  @Test
  fun `place names are sanitized before entering a boolean query`() {
    val messy = ResolvedPlace(district = "  \"Wayanad\"   district ", state = "Kerala", source = "test")
    val rings = NewsQueryFactory.buildQueries(messy)
    assertTrue(rings[0].query.contains("\"Wayanad district\""))
    assertFalse("quotes inside a name must never survive", rings[0].query.contains("\"\""))
  }

  @Test
  fun `ring labels never name a place that was not resolved`() {
    assertEquals("District scope (not resolved)", NewsScope.MY_AREA.ringLabel(null))
    assertEquals("State scope (not resolved)", NewsScope.MY_STATE.ringLabel(null))
    assertEquals("India", NewsScope.INDIA.ringLabel(null))
    assertEquals("Wayanad", NewsScope.MY_AREA.ringLabel(place))
    assertEquals("Kerala", NewsScope.MY_STATE.ringLabel(place))
  }

  @Test
  fun `the ui states the real scope and admits when it is national only`() {
    val unresolved = VippattiUiState()
    assertTrue(unresolved.newsScopeNote.contains("India-wide"))
    assertTrue(unresolved.newsScopeNote.contains("not resolved"))

    val scoped = VippattiUiState(resolvedPlace = place)
    assertTrue(scoped.newsScopeNote.contains("Wayanad"))
    assertTrue(scoped.newsScopeNote.contains("Kerala"))
    assertTrue(scoped.newsScopeNote.contains("India"))
    assertTrue(scoped.newsScopeNote.contains("test resolver"))
  }

  @Test
  fun `a resolved place is usable only when a sub-country ring exists`() {
    assertTrue(place.isUsable)
    assertFalse(ResolvedPlace(source = "test").isUsable)
    assertEquals("India-wide only", ResolvedPlace(source = "test").summary)
    assertEquals("Wayanad, Kerala, India", place.summary)
  }
}
