package com.example.data.population

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire format for population records.
 *
 * This is the contract a census/registrar or disaster-authority feed will use.
 * A missing figure stays JSON null so a consumer can never read "not measured"
 * as zero, and every record keeps its role, scope, classification, source kind
 * and reference time.
 */
object PopulationRecordJson {

  fun encode(record: PopulationRecord): JSONObject = JSONObject()
    .put("id", record.id)
    .put("role", record.role.name)
    .put("scope", record.scope.name)
    .put("areaName", record.areaName ?: JSONObject.NULL)
    .put("value", record.value ?: JSONObject.NULL)
    .put("classification", record.classification.name)
    .put("sourceKind", record.sourceKind.name)
    .put("source", record.source)
    .put("referenceMillis", record.referenceMillis ?: JSONObject.NULL)
    .put("confidence", record.confidence ?: JSONObject.NULL)
    .put("notes", JSONArray(record.notes))

  fun encodeAll(records: List<PopulationRecord>): JSONArray =
    JSONArray().apply { records.forEach { put(encode(it)) } }

  fun decode(payload: String): PopulationRecord? = try {
    decodeObject(JSONObject(payload))
  } catch (_: Exception) {
    null
  }

  fun decodeObject(obj: JSONObject): PopulationRecord? {
    val id = obj.optString("id")
    if (id.isBlank()) return null
    // A record with an unreadable role/scope is not silently reshaped: it is
    // rejected, because its meaning cannot be trusted.
    val role = enumOrNull<PopulationRole>(obj.optString("role")) ?: return null
    val scope = enumOrNull<PopulationScope>(obj.optString("scope")) ?: return null
    return PopulationRecord(
      id = id,
      role = role,
      scope = scope,
      areaName = if (obj.isNull("areaName")) null else obj.optString("areaName").ifBlank { null },
      value = if (obj.isNull("value") || !obj.has("value")) null else obj.optInt("value"),
      classification = enumOrNull<PopulationClassification>(obj.optString("classification"))
        ?: PopulationClassification.NOT_PROVIDED,
      sourceKind = enumOrNull<PopulationSourceKind>(obj.optString("sourceKind"))
        ?: PopulationSourceKind.UNKNOWN,
      source = obj.optString("source"),
      // JSON null AND a missing key both mean "not stated" (same contract as
      // CapacityAssessmentJson): a missing reference time is null, never the
      // 1970 epoch, and a missing confidence is null, never NaN (which would
      // otherwise outrank every stated confidence in the resolver order).
      referenceMillis = if (obj.isNull("referenceMillis") || !obj.has("referenceMillis")) {
        null
      } else {
        obj.optLong("referenceMillis")
      },
      confidence = if (obj.isNull("confidence") || !obj.has("confidence")) {
        null
      } else {
        obj.optDouble("confidence")
      },
      notes = obj.optJSONArray("notes").toStringList()
    )
  }

  fun decodeAll(payload: String): List<PopulationRecord> = try {
    val array = JSONArray(payload)
    (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let { obj -> decodeObject(obj) } }
  } catch (_: Exception) {
    emptyList()
  }

  private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { text -> text.isNotBlank() } }
  }

  private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? {
    if (name.isNullOrBlank()) return null
    return enumValues<T>().firstOrNull { it.name == name }
  }
}
