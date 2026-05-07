package com.santiya.localaihub.offlinecity

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OfflineCityImporter(
    private val context: Context,
) {
    private val storage = OfflineCityStorage(context)

    suspend fun importUri(uri: Uri): OfflineCityImportResult {
        val fileName = queryDisplayName(uri) ?: "offline-city-import"
        val target = storage.importCopyTarget(fileName)
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read imported file")
        return importBytes(fileName, bytes)
    }

    suspend fun importAsset(assetFileName: String): OfflineCityImportResult {
        val bytes = context.assets.open(assetFileName).use { it.readBytes() }
        val fileName = File(assetFileName).name
        return importBytes(fileName, bytes)
    }

    suspend fun importCatalogEntry(entry: OfflineCityCatalogEntry): OfflineCityImportResult {
        return if (!entry.assetFileName.isNullOrBlank()) {
            importAsset(entry.assetFileName)
        } else {
            val existing = storage.loadDataset()
            val dataset = OfflineCityDataset(
                id = entry.id,
                name = entry.cityName,
                region = entry.regionCountry,
                description = entry.sourceCoverage,
                bounds = OfflineCityBounds(
                    south = entry.centerLatitude - 0.08,
                    west = entry.centerLongitude - 0.08,
                    north = entry.centerLatitude + 0.08,
                    east = entry.centerLongitude + 0.08,
                ),
                places = listOf(
                    OfflineCityPlace(
                        id = "${entry.id}-center",
                        name = "Центр ${entry.cityName}",
                        category = "city_center",
                        description = "Базовая офлайн-карта города",
                        latitude = entry.centerLatitude,
                        longitude = entry.centerLongitude,
                    )
                ),
                tileSource = OfflineCityTileSource(
                    id = "built-in-${entry.id}",
                    displayName = entry.tileDisplayName ?: "Встроенная базовая карта",
                    format = "generated_basemap",
                ),
                sourceFiles = listOf("catalog:${entry.id}")
            )
            val finalDataset = existing?.mergeWith(dataset) ?: dataset
            storage.saveDataset(finalDataset)
            OfflineCityImportResult(
                dataset = finalDataset,
                merged = existing != null,
                importedFileName = entry.id
            )
        }
    }

    private fun importBytes(fileName: String, bytes: ByteArray): OfflineCityImportResult {
        val target = storage.importCopyTarget(fileName)
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)

        val imported = when {
            fileName.lowercase(Locale.US).endsWith(".zip") -> parseGtfsZip(fileName, bytes)
            fileName.lowercase(Locale.US).endsWith(".geojson") -> parseGeoJson(fileName, bytes.toString(Charsets.UTF_8))
            fileName.lowercase(Locale.US).endsWith(".json") -> parseJsonLike(fileName, bytes.toString(Charsets.UTF_8))
            else -> error("Unsupported offline city format: $fileName")
        }.copy(sourceFiles = listOf(target.name))

        val existing = storage.loadDataset()
        val merged = existing != null
        val finalDataset = existing?.mergeWith(imported) ?: imported
        storage.saveDataset(finalDataset)
        return OfflineCityImportResult(
            dataset = finalDataset,
            merged = merged,
            importedFileName = target.name,
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseJsonLike(fileName: String, content: String): OfflineCityDataset {
            return runCatching {
                json.decodeFromString<OfflineCityDataset>(content)
            }.getOrElse {
                parseGeoJson(fileName, content)
            }
        }

        fun parseGeoJson(fileName: String, content: String): OfflineCityDataset {
            val root = json.parseToJsonElement(content).jsonObject
            val features = root["features"]?.jsonArray.orEmpty()
            val places = features.mapIndexedNotNull { index, feature ->
                parseGeoJsonFeature(index, feature)
            }
            val bbox = root["bbox"]?.jsonArray
            val bounds = if (bbox != null && bbox.size >= 4) {
                OfflineCityBounds(
                    south = bbox[1].jsonPrimitive.doubleOrNull ?: 0.0,
                    west = bbox[0].jsonPrimitive.doubleOrNull ?: 0.0,
                    north = bbox[3].jsonPrimitive.doubleOrNull ?: 0.0,
                    east = bbox[2].jsonPrimitive.doubleOrNull ?: 0.0,
                )
            } else {
                computeBounds(places)
            }
            return OfflineCityDataset(
                id = datasetIdFromFile(fileName),
                name = fileName.substringBeforeLast('.'),
                description = "GeoJSON offline map pack",
                bounds = bounds,
                places = places,
            )
        }

        fun parseGtfsZip(fileName: String, bytes: ByteArray): OfflineCityDataset {
            val entries = readZipEntries(bytes)
            val stopsRows = parseCsv(entries["stops.txt"].orEmpty())
            val routesRows = parseCsv(entries["routes.txt"].orEmpty())
            val tripsRows = parseCsv(entries["trips.txt"].orEmpty())
            val stopTimesRows = parseCsv(entries["stop_times.txt"].orEmpty())
            val calendarRows = parseCsv(entries["calendar.txt"].orEmpty())

            val routesById = routesRows.associate { row ->
                val routeId = row["route_id"].orEmpty()
                routeId to OfflineCityRoute(
                    id = routeId,
                    shortName = row["route_short_name"].orEmpty().ifBlank { routeId },
                    longName = row["route_long_name"],
                )
            }
            val serviceDays = calendarRows.associate { row ->
                val days = listOfNotNull(
                    "monday".takeIf { row[it].asGtfsFlag() },
                    "tuesday".takeIf { row[it].asGtfsFlag() },
                    "wednesday".takeIf { row[it].asGtfsFlag() },
                    "thursday".takeIf { row[it].asGtfsFlag() },
                    "friday".takeIf { row[it].asGtfsFlag() },
                    "saturday".takeIf { row[it].asGtfsFlag() },
                    "sunday".takeIf { row[it].asGtfsFlag() },
                )
                row["service_id"].orEmpty() to days
            }
            val tripsById = tripsRows.associate { row ->
                row["trip_id"].orEmpty() to row
            }

            val departures = stopTimesRows.mapNotNull { row ->
                val trip = tripsById[row["trip_id"].orEmpty()] ?: return@mapNotNull null
                val routeId = trip["route_id"].orEmpty()
                val stopId = row["stop_id"].orEmpty()
                val time = row["departure_time"] ?: row["arrival_time"] ?: return@mapNotNull null
                OfflineCityDeparture(
                    routeId = routeId,
                    routeLabel = routesById[routeId]?.shortName ?: routeId,
                    stopId = stopId,
                    stopName = "",
                    departureTime = time,
                    tripHeadsign = trip["trip_headsign"],
                    serviceDays = serviceDays[trip["service_id"].orEmpty()].orEmpty(),
                )
            }

            val stopRoutes = departures.groupBy { it.stopId }.mapValues { (_, items) ->
                items.map { it.routeLabel }.distinct()
            }

            val stops = stopsRows.mapNotNull { row ->
                val stopId = row["stop_id"].orEmpty()
                val lat = row["stop_lat"]?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = row["stop_lon"]?.toDoubleOrNull() ?: return@mapNotNull null
                OfflineCityStop(
                    id = stopId,
                    name = row["stop_name"].orEmpty().ifBlank { stopId },
                    latitude = lat,
                    longitude = lon,
                    routes = stopRoutes[stopId].orEmpty(),
                )
            }
            val stopNames = stops.associateBy({ it.id }, { it.name })

            val hydratedDepartures = departures.map { departure ->
                departure.copy(stopName = stopNames[departure.stopId] ?: departure.stopId)
            }

            val routeStops = hydratedDepartures.groupBy { it.routeId }.mapValues { (_, items) ->
                items.map { it.stopId }.distinct()
            }
            val routes = routesById.values.map { route ->
                route.copy(stopIds = routeStops[route.id].orEmpty())
            }

            return OfflineCityDataset(
                id = datasetIdFromFile(fileName),
                name = fileName.substringBeforeLast('.'),
                description = "GTFS offline transit pack",
                bounds = computeBoundsFromStops(stops),
                stops = stops,
                routes = routes,
                departures = hydratedDepartures,
            )
        }

        private fun parseGeoJsonFeature(index: Int, feature: JsonElement): OfflineCityPlace? {
            val obj = feature.jsonObject
            val geometry = obj["geometry"]?.jsonObject ?: return null
            if (geometry["type"]?.jsonPrimitive?.contentOrNull != "Point") return null
            val coordinates = geometry["coordinates"]?.jsonArray ?: return null
            if (coordinates.size < 2) return null
            val lon = coordinates[0].jsonPrimitive.doubleOrNull ?: return null
            val lat = coordinates[1].jsonPrimitive.doubleOrNull ?: return null
            val props = obj["properties"]?.jsonObject ?: JsonObject(emptyMap())
            val name = props.string("name")
                ?: props.string("title")
                ?: props.string("brand")
                ?: "Place ${index + 1}"
            val category = props.string("amenity")
                ?: props.string("shop")
                ?: props.string("tourism")
                ?: props.string("leisure")
                ?: props.string("category")
                ?: props.string("type")
                ?: "place"
            val description = props.string("description")
            val address = listOfNotNull(
                props.string("addr:street"),
                props.string("addr:housenumber")
            ).joinToString(" ").ifBlank { props.string("address") }
            val tags = props.entries.mapNotNull { (key, value) ->
                value.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { "$key:$it" }
            }
            return OfflineCityPlace(
                id = props.string("id") ?: "geo-$index",
                name = name,
                category = category,
                description = description,
                address = address,
                latitude = lat,
                longitude = lon,
                hours = props.string("opening_hours"),
                tags = tags,
            )
        }

        private fun readZipEntries(bytes: ByteArray): Map<String, String> {
            val entries = linkedMapOf<String, String>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name.lowercase(Locale.US)
                        entries[name] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zip.nextEntry
                }
            }
            return entries
        }

        private fun parseCsv(content: String): List<Map<String, String>> {
            if (content.isBlank()) return emptyList()
            val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8))))
            val lines = reader.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()
            val headers = parseCsvLine(lines.first().trimBom())
            return lines.drop(1).mapNotNull { line ->
                val values = parseCsvLine(line)
                if (values.isEmpty()) return@mapNotNull null
                headers.mapIndexed { index, key -> key to values.getOrElse(index) { "" } }.toMap()
            }
        }

        private fun parseCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val char = line[i]
                when {
                    char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                        current.append('"')
                        i++
                    }
                    char == '"' -> inQuotes = !inQuotes
                    char == ',' && !inQuotes -> {
                        result += current.toString()
                        current.clear()
                    }
                    else -> current.append(char)
                }
                i++
            }
            result += current.toString()
            return result
        }

        private fun computeBounds(places: List<OfflineCityPlace>): OfflineCityBounds? {
            if (places.isEmpty()) return null
            return OfflineCityBounds(
                south = places.minOf { it.latitude },
                west = places.minOf { it.longitude },
                north = places.maxOf { it.latitude },
                east = places.maxOf { it.longitude },
            )
        }

        private fun computeBoundsFromStops(stops: List<OfflineCityStop>): OfflineCityBounds? {
            if (stops.isEmpty()) return null
            return OfflineCityBounds(
                south = stops.minOf { it.latitude },
                west = stops.minOf { it.longitude },
                north = stops.maxOf { it.latitude },
                east = stops.maxOf { it.longitude },
            )
        }

        private fun datasetIdFromFile(fileName: String): String {
            val base = fileName.substringBeforeLast('.').lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
            return if (base.isNotBlank()) base else "offline-city-${UUID.randomUUID()}"
        }

        private fun String.trimBom(): String = removePrefix("\uFEFF")

        private fun String?.asGtfsFlag(): Boolean = this == "1" || this.equals("true", ignoreCase = true)

        private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
