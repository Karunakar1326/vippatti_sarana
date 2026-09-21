package com.example.data.capacity

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.population.PopulationClassification
import com.example.data.population.PopulationRole
import com.example.data.population.PopulationScope
import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire format for a carrying-capacity assessment.
 *
 * There is no capacity backend yet, so this is the contract the future relief
 * registry / authority feed can emit or consume: the assessment (including its
 * per-resource states, assumptions and provenance) survives a round-trip
 * unchanged. Absent numbers stay JSON null - never 0 - so a consumer cannot
 * mistake "not measured" for "nothing available".
 */
object CapacityAssessmentJson {

  fun encode(assessment: CapacityAssessment): JSONObject = JSONObject()
    .put("siteId", assessment.siteId)
    .put("siteName", assessment.siteName)
    .put("status", assessment.status.name)
    .put("effectiveCapacity", assessment.effectiveCapacity ?: JSONObject.NULL)
    .put("remainingCapacity", assessment.remainingCapacity ?: JSONObject.NULL)
    .put("shortfall", assessment.shortfall ?: JSONObject.NULL)
    .put("limitingResource", assessment.limitingResource?.name ?: JSONObject.NULL)
    .put("explanation", assessment.explanation)
    .put("assessedAtMillis", assessment.assessedAtMillis)
    .put("assumptions", JSONArray(assessment.assumptions))
    .put(
      "demand",
      JSONObject()
        .put("people", assessment.demand.people ?: JSONObject.NULL)
        .put("state", assessment.demand.state.name)
        .put("source", assessment.demand.source)
        .put("basis", assessment.demand.basis)
        .put("notes", JSONArray(assessment.demand.notes))
        // Population provenance (SIH 26191): what the figure counted, over what
        // area, and how it was produced. All null-preserving on the way back.
        .put("scope", assessment.demand.scope?.name ?: JSONObject.NULL)
        .put("scopeName", assessment.demand.scopeName ?: JSONObject.NULL)
        .put("classification", assessment.demand.classification?.name ?: JSONObject.NULL)
        .put("role", assessment.demand.role?.name ?: JSONObject.NULL)
        .put("derivedFromRole", assessment.demand.derivedFromRole?.name ?: JSONObject.NULL)
        .put("referenceMillis", assessment.demand.referenceMillis ?: JSONObject.NULL)
        .put("confidence", assessment.demand.confidence ?: JSONObject.NULL)
    )
    .put(
      "resources",
      JSONArray().apply {
        assessment.resources.forEach { resource ->
          put(
            JSONObject()
              .put("resource", resource.resource.name)
              .put("peopleSupported", resource.peopleSupported ?: JSONObject.NULL)
              .put("state", resource.state.name)
              .put("basis", resource.basis)
              .put("source", resource.source)
              .put("recordedAbsence", resource.recordedAbsence)
          )
        }
      }
    )
    .put(
      "provenance",
      JSONObject()
        .put("source", assessment.provenance.source)
        .put("status", assessment.provenance.status)
        .put("confidence", assessment.provenance.confidence)
        .put("isVerified", assessment.provenance.isVerified)
        .put("classification", assessment.provenance.classification.name)
        .put("lastUpdatedMillis", assessment.provenance.lastUpdatedMillis)
    )

  /** Decodes [encode]'s output; returns null when the payload is not usable. */
  fun decode(payload: String): CapacityAssessment? = try {
    decodeObject(JSONObject(payload))
  } catch (_: Exception) {
    null
  }

  fun decodeObject(obj: JSONObject): CapacityAssessment? {
    val siteId = obj.optString("siteId")
    if (siteId.isBlank()) return null
    val status = enumOrNull<FeasibilityStatus>(obj.optString("status")) ?: return null
    val demandObj = obj.optJSONObject("demand") ?: return null
    val demand = RelocationDemand(
      people = demandObj.optIntOrNull("people"),
      state = enumOrNull<ResourceDataState>(demandObj.optString("state"))
        ?: ResourceDataState.NOT_PROVIDED,
      source = demandObj.optString("source"),
      basis = demandObj.optString("basis"),
      notes = demandObj.optJSONArray("notes").toStringList(),
      scope = enumOrNull<PopulationScope>(demandObj.optString("scope")),
      scopeName = demandObj.optStringOrNull("scopeName"),
      classification = enumOrNull<PopulationClassification>(demandObj.optString("classification")),
      role = enumOrNull<PopulationRole>(demandObj.optString("role")),
      derivedFromRole = enumOrNull<PopulationRole>(demandObj.optString("derivedFromRole")),
      referenceMillis = demandObj.optLongOrNull("referenceMillis"),
      confidence = demandObj.optDoubleOrNull("confidence")
    )
    val resources = obj.optJSONArray("resources").toObjectList().mapNotNull { item ->
      val resource = enumOrNull<CapacityResource>(item.optString("resource")) ?: return@mapNotNull null
      ResourceCapacity(
        resource = resource,
        peopleSupported = item.optIntOrNull("peopleSupported"),
        state = enumOrNull<ResourceDataState>(item.optString("state"))
          ?: ResourceDataState.NOT_PROVIDED,
        basis = item.optString("basis"),
        source = item.optString("source"),
        recordedAbsence = item.optBoolean("recordedAbsence", false)
      )
    }
    val provenanceObj = obj.optJSONObject("provenance")
    return CapacityAssessment(
      siteId = siteId,
      siteName = obj.optString("siteName"),
      demand = demand,
      resources = resources,
      effectiveCapacity = obj.optIntOrNull("effectiveCapacity"),
      limitingResource = enumOrNull<CapacityResource>(obj.optString("limitingResource")),
      status = status,
      remainingCapacity = obj.optIntOrNull("remainingCapacity"),
      shortfall = obj.optIntOrNull("shortfall"),
      explanation = obj.optString("explanation"),
      assumptions = obj.optJSONArray("assumptions").toStringList(),
      assessedAtMillis = obj.optLong("assessedAtMillis", 0L),
      provenance = DataProvenance(
        source = provenanceObj?.optString("source") ?: "Unknown source",
        status = provenanceObj?.optString("status") ?: DataProvenance.STATUS_FIELD,
        confidence = provenanceObj?.optDouble("confidence", 0.0) ?: 0.0,
        isVerified = provenanceObj?.optBoolean("isVerified", false) ?: false,
        classification = provenanceObj
          ?.optString("classification")
          ?.let { enumOrNull<DataClassification>(it) }
          ?: DataClassification.SIMULATED,
        lastUpdatedMillis = provenanceObj?.optLong("lastUpdatedMillis", 0L) ?: 0L
      )
    )
  }

  /** JSON null and a missing key both mean "not provided". */
  private fun JSONObject.optIntOrNull(key: String): Int? =
    if (isNull(key) || !has(key)) null else optInt(key)

  private fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key) || !has(key)) null else optLong(key)

  private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key)

  private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

  private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optString(it).takeIf { text -> text.isNotBlank() } }
  }

  private fun JSONArray?.toObjectList(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
  }

  private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? {
    if (name.isNullOrBlank()) return null
    return enumValues<T>().firstOrNull { it.name == name }
  }
}
