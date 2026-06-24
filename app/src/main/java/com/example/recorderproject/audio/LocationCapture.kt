package com.example.recorderproject.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

/**
 * Snapshot the device's last-known location for tagging a recording.
 *
 * Uses LocationManager directly (no Google Play Services dependency) — the
 * last fix from any provider is plenty for a recording's "where was this
 * captured?" metadata; we don't actively listen for updates here.
 *
 * Returned format (string): "lat,lng,alt,accuracy" — matches the schema
 * RecordFile.locationTag already carries through to iXML's <LOCATION> tag.
 *
 * Returns null when:
 *   - permission isn't granted (caller decides whether to ask)
 *   - no provider has a fix yet
 *   - the location services exception path fires (rare; lazily caught)
 */
object LocationCapture {

    fun snapshot(context: Context): String? {
        val fineOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseOk = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineOk && !coarseOk) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = listOfNotNull(
            if (fineOk) LocationManager.GPS_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        var best: Location? = null
        for (p in providers) {
            val loc = try {
                @Suppress("MissingPermission")
                lm.getLastKnownLocation(p)
            } catch (_: SecurityException) { null }
              catch (_: IllegalArgumentException) { null }
              catch (_: Exception) { null }
            if (loc != null && (best == null || loc.time > best!!.time)) {
                best = loc
            }
        }
        val l = best ?: return null

        val alt = if (l.hasAltitude()) "%.1f".format(l.altitude) else ""
        val acc = if (l.hasAccuracy()) "%.0f".format(l.accuracy) else ""
        return "%.6f,%.6f,%s,%s".format(l.latitude, l.longitude, alt, acc)
    }
}
