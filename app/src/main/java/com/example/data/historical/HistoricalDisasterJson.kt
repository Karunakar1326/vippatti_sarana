package com.example.data.historical

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire format for historical EM-DAT records.
 *
 * The contract a backend historical service would use, and what the app can
 * export for review. Absent numbers stay JSON null on the way out and decode
 * back to null - a consumer must never be able to read "not collected" as zero.
 */
object HistoricalDisasterJson {

  fun encode(event: HistoricalDisasterEvent): JSONObject = JSONObject()
    .put("id", event.id)
    .put("historicFlag", event.historicFlag ?: JSONObject.NULL)
    .put("group", event.group)
    .put("subgroup", event.subgroup)
    .put("type", event.type)
    .put("subtype", event.subtype)
    .put("eventName", event.eventName ?: JSONObject.NULL)
    .put("iso", event.iso ?: JSONObject.NULL)
    .put("country", event.country)
    .put("locationText", event.locationText ?: JSONObject.NULL)
    .put("adminUnitNames", JSONArray(event.adminUnitNames))
    .put("startYear", event.startDate.year)
    .put("startMonth", event.startDate.month ?: JSONObject.NULL)
    .put("startDay", event.startDate.day ?: JSONObject.NULL)
    .put("endYear", event.endDate?.year ?: JSONObject.NULL)
    .put("endMonth", event.endDate?.month ?: JSONObject.NULL)
    .put("endDay", event.endDate?.day ?: JSONObject.NULL)
    .put("totalDeaths", event.impacts.totalDeaths ?: JSONObject.NULL)
    .put("noInjured", event.impacts.injured ?: JSONObject.NULL)
    .put("noAffected", event.impacts.affected ?: JSONObject.NULL)
    .put("noHomeless", event.impacts.homeless ?: JSONObject.NULL)
    .put("totalAffected", event.impacts.totalAffected ?: JSONObject.NULL)
    .put("damageThousandUsd", event.impacts.damageThousandUsd ?: JSONObject.NULL)
    .put(
      "damageAdjustedThousandUsd",
      event.impacts.damageAdjustedThousandUsd ?: JSONObject.NULL
    )
    .put("magnitude", event.magnitude ?: JSONObject.NULL)
    .put("magnitudeScale", event.magnitudeScale ?: JSONObject.NULL)
    .put("latitude", event.latitude ?: JSONObject.NULL)
    .put("longitude", event.longitude ?: JSONObject.NULL)
    .put("entryDate", event.entryDate ?: JSONObject.NULL)
    .put("lastUpdate", event.lastUpdate ?: JSONObject.NULL)
    .put("classification", event.classification.name)
    .put("source", event.source)
    .put("datasetVersion", event.datasetVersion)
    .put("accessedOn", event.accessedOn ?: JSONObject.NULL)
    .put("spatialPrecision", event.spatialPrecision.name)
    .put("notes", JSONArray(event.notes))

  fun encodeAll(events: List<HistoricalDisasterEvent>): JSONArray =
    JSONArray().apply { events.forEach { put(encode(it)) } }

  fun decode(payload: String): HistoricalDisasterEvent? = try {
    decodeObject(JSONObject(payload))
  } catch (_: Exception) {
    null
  }

  fun decodeAll(payload: String): List<HistoricalDisasterEvent> = try {
    val array = JSONArray(payload)
    (0 until array.length()).mapNotNull { index ->
      array.optJSONObject(index)?.let { decodeObject(it) }
    }
  } catch (_: Exception) {
    emptyList()
  }

  fun decodeObject(json: JSONObject): HistoricalDisasterEvent? {
    val id = json.optString("id")
    if (id.isBlank()) return null
    // An unreadable classification is not silently reshaped: a record whose
    // meaning cannot be trusted is rejected.
    val classification = enumOrNull<com.example.data.model.DataClassification>(
      json.optString("classification")
    ) ?: return null
    val startYear = json.optIntOrNull("startYear") ?: return null
    val precision = enumOrNull<HistoricalSpatialPrecision>(json.optString("spatialPrecision"))
      ?: HistoricalSpatialPrecision.NOT_SUFFICIENT

    val latitude = json.optDoubleOrNull("latitude")
    val longitude = json.optDoubleOrNull("longitude")
    return HistoricalDisasterEvent(
      id = id,
      historicFlag = json.optBooleanOrNull("historicFlag"),
      group = json.optString("group"),
      subgroup = json.optString("subgroup"),
      type = json.optString("type"),
      subtype = json.optString("subtype"),
      eventName = json.optStringOrNull("eventName"),
      iso = json.optStringOrNull("iso"),
      country = json.optString("country"),
      locationText = json.optStringOrNull("locationText"),
      adminUnitNames = json.optJSONArray("adminUnitNames").toStringList(),
      startDate = HistoricalDate(
        year = startYear,
        month = json.optIntOrNull("startMonth"),
        day = json.optIntOrNull("startDay")
      ),
      endDate = json.optIntOrNull("endYear")?.let { year ->
        HistoricalDate(
          year = year,
          month = json.optIntOrNull("endMonth"),
          day = json.optIntOrNull("endDay")
        )
      },
      impacts = HistoricalImpacts(
        totalDeaths = json.optLongOrNull("totalDeaths"),
        injured = json.optLongOrNull("noInjured"),
        affected = json.optLongOrNull("noAffected"),
        homeless = json.optLongOrNull("noHomeless"),
        totalAffected = json.optLongOrNull("totalAffected"),
        damageThousandUsd = json.optLongOrNull("damageThousandUsd"),
        damageAdjustedThousandUsd = json.optLongOrNull("damageAdjustedThousandUsd")
      ),
      magnitude = json.optDoubleOrNull("magnitude"),
      magnitudeScale = json.optStringOrNull("magnitudeScale"),
      latitude = latitude,
      longitude = longitude,
      entryDate = json.optStringOrNull("entryDate"),
      lastUpdate = json.optStringOrNull("lastUpdate"),
      classification = classification,
      source = json.optString("source"),
      datasetVersion = json.optString("datasetVersion"),
      accessedOn = json.optStringOrNull("accessedOn"),
      spatialPrecision = if (latitude == null || longitude == null) {
        // Coordinates cannot be claimed when one is missing.
        if (precision == HistoricalSpatialPrecision.SOURCE_COORDINATES) {
          HistoricalSpatialPrecision.NOT_SUFFICIENT
        } else {
          precision
        }
      } else {
        precision
      },
      notes = json.optJSONArray("notes").toStringList()
    )
  }

  // ---------------------------------------------------------- JSON helpers

  private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key)

  private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)

  private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key)

  private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (isNull(key) || !has(key)) null else optBoolean(key)

  private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

  private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
      optString(index).takeIf { it.isNotBlank() }
    }
  }

  private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? {
    if (name.isNullOrBlank()) return null
    return enumValues<T>().firstOrNull { it.name == name }
  }
}
