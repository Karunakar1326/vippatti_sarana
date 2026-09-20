package com.example.data.historical

/**
 * ============================================================================
 * HISTORICAL DISASTER SOURCE BOUNDARY
 * ============================================================================
 *
 * EM-DAT is an archive that is updated periodically by CRED - it is NOT a live
 * feed, so nothing here is ever reported as live. The prepared dataset ships as
 * an app asset (see `tools/emdat_prepare.py`) and is loaded ONCE per process:
 * the app never needs the original XLSX at runtime, and no launch depends on a
 * network call.
 *
 * A different transport (an authority backend, an SDMA historical service) can
 * implement the same interface later without touching the catalog, the UI or
 * the tests.
 */
sealed class HistoricalLoadResult {
  /** Dataset parsed successfully. An empty catalog is still a valid load. */
  data class Loaded(val catalog: HistoricalDisasterCatalog) : HistoricalLoadResult()

  /** No dataset is attached/configured in this build - not an error. */
  data class Unavailable(val reason: String) : HistoricalLoadResult()

  /** A dataset was attached but could not be read or parsed. */
  data class Failed(val reason: String) : HistoricalLoadResult()
}

/** Reads a bundled asset by name. Android wires this to `assets.open`. */
fun interface HistoricalDatasetReader {
  suspend fun read(assetName: String): String?
}

/** Anything that can supply a historical catalog. */
fun interface HistoricalDataProvider {
  suspend fun load(nowMillis: Long): HistoricalLoadResult
}

/**
 * Loads the bundled prepared EM-DAT CSV.
 *
 * [accessedOnProvider] exists so the "when was this dataset obtained" line is
 * supplied by a real value (the file's own creation stamp) instead of being
 * invented from the device clock at parse time.
 */
class BundledHistoricalDataProvider(
  private val reader: HistoricalDatasetReader,
  private val accessedOnProvider: () -> String? = { null },
  private val csvAsset: String = DEFAULT_CSV_ASSET,
  private val infoAsset: String = DEFAULT_INFO_ASSET
) : HistoricalDataProvider {

  override suspend fun load(nowMillis: Long): HistoricalLoadResult {
    val csv = try {
      reader.read(csvAsset)
    } catch (error: Exception) {
      return HistoricalLoadResult.Failed(
        "Bundled historical dataset could not be read: " +
          (error.message ?: error.javaClass.simpleName)
      )
    } ?: return HistoricalLoadResult.Unavailable(
      "No historical dataset is bundled in this build ($csvAsset missing)."
    )

    if (csv.isBlank()) {
      return HistoricalLoadResult.Failed("Bundled historical dataset is empty.")
    }

    // The info sheet is optional: without it the source is still named, but no
    // version or creation date is claimed.
    val infoRaw = try {
      reader.read(infoAsset)
    } catch (_: Exception) {
      null
    }
    val info = EmdatCsvParser.parseInfo(infoRaw)
    val accessedOn = accessedOnProvider() ?: info?.fileCreated

    return try {
      val parsed = EmdatCsvParser.parse(csv, infoRaw, accessedOn)
      if (parsed.events.isEmpty()) {
        return HistoricalLoadResult.Failed(
          "Historical dataset parsed to zero usable records " +
            "(${parsed.rejected.size} rejected)."
        )
      }
      HistoricalLoadResult.Loaded(
        HistoricalDisasterCatalog(
          events = parsed.events,
          info = (info ?: HistoricalDatasetInfo(
            source = parsed.source,
            version = parsed.version
          )).copy(parsedRecordCount = parsed.acceptedCount),
          rejected = parsed.rejected,
          importNotes = parsed.notes,
          loadedAtMillis = nowMillis
        )
      )
    } catch (error: IllegalArgumentException) {
      HistoricalLoadResult.Failed(
        "Historical dataset is not usable: ${error.message ?: "unknown layout"}"
      )
    } catch (error: Exception) {
      HistoricalLoadResult.Failed(
        "Historical dataset import failed: ${error.message ?: error.javaClass.simpleName}"
      )
    }
  }

  companion object {
    const val DEFAULT_CSV_ASSET = "emdat/emdat_india_historical.csv"
    const val DEFAULT_INFO_ASSET = "emdat/emdat_dataset_info.json"
  }
}

/** Default for builds/tests with no historical source attached. */
object NoHistoricalDataProvider : HistoricalDataProvider {
  override suspend fun load(nowMillis: Long): HistoricalLoadResult =
    HistoricalLoadResult.Unavailable("No historical disaster dataset is configured.")
}
