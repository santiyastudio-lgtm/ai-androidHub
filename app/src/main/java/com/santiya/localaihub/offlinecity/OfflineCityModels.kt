package com.santiya.localaihub.offlinecity

import kotlinx.serialization.Serializable

@Serializable
data class OfflineCityBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

@Serializable
data class OfflineCityPlace(
    val id: String,
    val name: String,
    val category: String = "place",
    val description: String? = null,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val hours: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class OfflineCityStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val routes: List<String> = emptyList(),
)

@Serializable
data class OfflineCityRoute(
    val id: String,
    val shortName: String,
    val longName: String? = null,
    val headsign: String? = null,
    val stopIds: List<String> = emptyList(),
)

@Serializable
data class OfflineCityDeparture(
    val routeId: String,
    val routeLabel: String,
    val stopId: String,
    val stopName: String,
    val departureTime: String,
    val tripHeadsign: String? = null,
    val serviceDays: List<String> = emptyList(),
)

@Serializable
data class OfflineCityDataset(
    val id: String,
    val name: String,
    val region: String? = null,
    val description: String? = null,
    val bounds: OfflineCityBounds? = null,
    val places: List<OfflineCityPlace> = emptyList(),
    val stops: List<OfflineCityStop> = emptyList(),
    val routes: List<OfflineCityRoute> = emptyList(),
    val departures: List<OfflineCityDeparture> = emptyList(),
    val tileSource: OfflineCityTileSource? = null,
    val sourceFiles: List<String> = emptyList(),
    val importedAtEpochMs: Long = System.currentTimeMillis(),
) {
    fun summary(): OfflineCityDatasetSummary {
        return OfflineCityDatasetSummary(
            name = name,
            region = region,
            placeCount = places.size,
            stopCount = stops.size,
            routeCount = routes.size,
            departureCount = departures.size,
            sourceFiles = sourceFiles,
        )
    }

    fun mergeWith(other: OfflineCityDataset): OfflineCityDataset {
        fun <T> mergeLists(base: List<T>, incoming: List<T>, key: (T) -> String): List<T> {
            val merged = LinkedHashMap<String, T>()
            base.forEach { merged[key(it)] = it }
            incoming.forEach { merged[key(it)] = it }
            return merged.values.toList()
        }

        return copy(
            name = if (other.name.isNotBlank()) other.name else name,
            region = other.region ?: region,
            description = other.description ?: description,
            bounds = other.bounds ?: bounds,
            places = mergeLists(places, other.places) { it.id },
            stops = mergeLists(stops, other.stops) { it.id },
            routes = mergeLists(routes, other.routes) { it.id },
            departures = mergeLists(departures, other.departures) {
                "${it.routeId}:${it.stopId}:${it.departureTime}:${it.tripHeadsign.orEmpty()}"
            },
            tileSource = other.tileSource ?: tileSource,
            sourceFiles = (sourceFiles + other.sourceFiles).distinct(),
            importedAtEpochMs = maxOf(importedAtEpochMs, other.importedAtEpochMs),
        )
    }
}

@Serializable
data class OfflineCityDatasetSummary(
    val name: String,
    val region: String? = null,
    val placeCount: Int = 0,
    val stopCount: Int = 0,
    val routeCount: Int = 0,
    val departureCount: Int = 0,
    val sourceFiles: List<String> = emptyList(),
)

data class OfflineCityImportResult(
    val dataset: OfflineCityDataset,
    val merged: Boolean,
    val importedFileName: String,
)

data class OfflineCityLocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val provider: String? = null,
)

@Serializable
data class OfflineCityTileSource(
    val id: String,
    val displayName: String,
    val format: String,
    val localPath: String? = null,
)

@Serializable
data class OfflineCityMapPoint(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class OfflineCityMapMarker(
    val id: String,
    val label: String,
    val point: OfflineCityMapPoint,
    val kind: String,
)

@Serializable
data class OfflineCityMapPayload(
    val bounds: OfflineCityBounds? = null,
    val focus: OfflineCityMapPoint? = null,
    val markers: List<OfflineCityMapMarker> = emptyList(),
    val path: List<OfflineCityMapPoint> = emptyList(),
    val tileSource: OfflineCityTileSource? = null,
)

@Serializable
data class OfflineCityAnswer(
    val title: String,
    val body: String,
    val suggestions: List<String> = emptyList(),
    val mapPayload: OfflineCityMapPayload? = null,
)
