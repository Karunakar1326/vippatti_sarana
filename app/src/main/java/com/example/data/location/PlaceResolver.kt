package com.example.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.model.DataClassification
import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * ============================================================================
 * RUNTIME PLACE RESOLUTION (dynamic-data rule)
 * ============================================================================
 *
 * The app is India-wide, so no district or state name may be baked into the
 * code. Place names for query scoping are resolved AT RUNTIME from the user's
 * own coordinates through the platform reverse geocoder, and are simply absent
 * when the platform cannot resolve them (offline, no geocoder service, no
 * result) - the caller then stays at national scope and says so.
 */
data class ResolvedPlace(
  val district: String? = null,
  val state: String? = null,
  val country: String? = null,
  /** Which real mechanism produced these names, e.g. "Android reverse geocoder". */
  val source: String = SOURCE_ANDROID_GEOCODER,
  val classification: DataClassification = DataClassification.OBSERVED
) {
  /** True when at least one ring smaller than the country could be queried. */
  val isUsable: Boolean
    get() = !district.isNullOrBlank() || !state.isNullOrBlank()

  /** Honest one-line description, e.g. "Munnar, Kerala" or "India-wide only". */
  val summary: String
    get() = listOfNotNull(
      district?.takeIf { it.isNotBlank() },
      state?.takeIf { it.isNotBlank() },
      country?.takeIf { it.isNotBlank() }
    ).joinToString(", ").ifBlank { "India-wide only" }

  companion object {
    const val SOURCE_ANDROID_GEOCODER = "Android reverse geocoder"
  }
}

/**
 * Resolves a coordinate to a place name. `null` means "could not be resolved"
 * and must never be replaced by a guessed or default district.
 */
fun interface PlaceResolver {
  suspend fun resolve(point: GeoPoint): ResolvedPlace?
}

/** Default resolver for tests and for builds without a usable geocoder. */
val UnresolvedPlaceResolver = PlaceResolver { null }

/**
 * Platform reverse geocoder (Android `Geocoder`). Uses the async listener API
 * on Android 13+ and the synchronous call below it, always off the main thread,
 * and returns null on any failure instead of inventing a name.
 *
 * The `Geocoder` service may be unavailable (no Play services, offline) - that
 * is reported as null, not as an error to be papered over.
 */
class AndroidGeocoderPlaceResolver(
  private val context: Context,
  private val source: String = ResolvedPlace.SOURCE_ANDROID_GEOCODER
) : PlaceResolver {

  override suspend fun resolve(point: GeoPoint): ResolvedPlace? = withContext(Dispatchers.IO) {
    try {
      if (!Geocoder.isPresent()) return@withContext null
      val geocoder = Geocoder(context, Locale.ENGLISH)
      val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
          geocoder.getFromLocation(point.lat, point.lon, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
              if (continuation.isActive) continuation.resume(addresses.firstOrNull())
            }

            override fun onError(errorMessage: String?) {
              if (continuation.isActive) continuation.resume(null)
            }
          })
        }
      } else {
        @Suppress("DEPRECATION")
        geocoder.getFromLocation(point.lat, point.lon, 1)?.firstOrNull()
      }
      address?.let { resolved ->
        ResolvedPlace(
          // subAdminArea is the district ring in India; locality is the town.
          district = (resolved.subAdminArea ?: resolved.locality)?.takeIf { it.isNotBlank() },
          state = resolved.adminArea?.takeIf { it.isNotBlank() },
          country = resolved.countryName?.takeIf { it.isNotBlank() },
          source = source
        )
      }
    } catch (_: Exception) {
      null
    }
  }
}
