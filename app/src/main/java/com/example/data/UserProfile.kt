package com.example.data

/**
 * Editable citizen identity shown on the Profile tab and attached to every
 * SOS broadcast / NDRF situation report.
 *
 * Defaults describe the pilot household so the app boots fully populated;
 * users edit their own real details via the profile editor dialog. The
 * vulnerable-category set and medical-support flag feed the shelter
 * prioritization engines (SafeZoneEvaluator / RelocationPlanner).
 */
data class UserProfile(
  val fullName: String = "Aditya Vardhan",
  val citizenId: String = "SARANA-AP-89241",
  val bloodGroup: String = "O+ POSITIVE",
  val medicalTag: String = "Asthma / Inhaler",
  val medicalNotes: String = "Requires Mobility Support",
  val dependentsCount: Int = 3,
  val dependentsDetail: String = "1 Elder, 1 Child (4yo), Spouse",
  /** IDs from RelocationPlanner.VULNERABLE_CATEGORIES (elderly/children/...). */
  val vulnerableCategoryIds: Set<String> = setOf("elderly", "children"),
  /** True when the household needs a shelter with on-site medical support. */
  val needsMedicalSupport: Boolean = false
) {
  val bloodGroupLabel: String get() = bloodGroup.trim().uppercase()
  val dependentsLabel: String get() = "$dependentsCount Dependents"
}
