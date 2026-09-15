package com.example.data.disaster

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * ============================================================================
 * NASA GIBS FLOOD EXTENT — official keyless WMTS raster layer.
 * ============================================================================
 *
 * Documented GIBS WMTS endpoint (verified live; capabilities at
 * https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/1.0.0/WMTSCapabilities.xml):
 *
 *   https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/
 *     VIIRS_Combined_Flood_1-Day/default/GoogleMapsCompatible_Level9/
 *     {TileMatrix}/{TileRow}/{TileCol}.png
 *
 * WMTS order {TileMatrix}/{TileRow}/{TileCol} == web-mercator z/y/x —
 * verified by direct tile requests (an India-coverage tile returned PNG
 * content). This is satellite-derived surface-water extent (NASA combined
 * flood product), an official hazard layer. Raster layers stay map overlays
 * — they are NOT converted into individual DisasterEvent records.
 *
 * Bhuvan note: ISRO Bhuvan GIS endpoints could not be resolved publicly from
 * this environment (documented limitation) — NASA GIBS is the honest,
 * reachable, official flood-extent alternative.
 */
object FloodRasterLayers {

  const val VIIRS_FLOOD_LAYER_ID = "VIIRS_Combined_Flood_1-Day"
  const val MODIS_FLOOD_LAYER_ID = "MODIS_Combined_Flood_1-Day"
  private const val GIBS_BASE = "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best"
  private const val TILE_MATRIX_SET = "GoogleMapsCompatible_Level9"

  /** osmdroid tile source for the VIIRS 1-day combined flood product. */
  fun viirsFloodTileSource(): OnlineTileSourceBase = gibsTileSource(
    name = "Flood Extent (Satellite, VIIRS)",
    layerId = VIIRS_FLOOD_LAYER_ID
  )

  /** osmdroid tile source for the MODIS 1-day combined flood product. */
  fun modisFloodTileSource(): OnlineTileSourceBase = gibsTileSource(
    name = "Flood Extent (Satellite, MODIS)",
    layerId = MODIS_FLOOD_LAYER_ID
  )

  /**
   * osmdroid 6.1.x uses a packed long tile index; zoom/x/y are decoded via
   * [MapTileIndex]. GIBS REST order is z/y/x.
   */
  private fun gibsTileSource(name: String, layerId: String): OnlineTileSourceBase =
    object : OnlineTileSourceBase(
      /* aName = */ name,
      /* aZoomMinLevel = */ 0,
      /* aZoomMaxLevel = */ 9,
      /* aTileSizePixels = */ 256,
      /* aImageFilenameEnding = */ ".png",
      /* aBaseUrl = */ arrayOf(
        "$GIBS_BASE/$layerId/default/$TILE_MATRIX_SET/{z}/{y}/{x}.png"
      )
    ) {
      override fun getTileURLString(pMapTileIndex: Long): String =
        getBaseUrl()
          .replace("{z}", MapTileIndex.getZoom(pMapTileIndex).toString())
          .replace("{y}", MapTileIndex.getY(pMapTileIndex).toString())
          .replace("{x}", MapTileIndex.getX(pMapTileIndex).toString())
    }

  /** Attribution required by GIBS when the flood layer is displayed. */
  const val GIBS_ATTRIBUTION = "Flood extent: NASA EOSDIS GIBS (VIIRS/MODIS)"
}


