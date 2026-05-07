package com.santiya.localaihub.offlinecity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class OfflineCityLocationProvider(
    private val context: Context,
) {
    fun canUseLocation(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun readLastKnownLocation(): OfflineCityLocationSnapshot? {
        if (!canUseLocation()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        val best = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { scoreLocation(it) }
            ?: return null

        return OfflineCityLocationSnapshot(
            latitude = best.latitude,
            longitude = best.longitude,
            accuracyMeters = best.takeIf { it.hasAccuracy() }?.accuracy,
            provider = best.provider,
        )
    }

    private fun scoreLocation(location: Location): Long {
        val accuracyPenalty = if (location.hasAccuracy()) location.accuracy.toLong() else 10_000L
        val timeScore = location.time
        return timeScore - accuracyPenalty
    }
}
