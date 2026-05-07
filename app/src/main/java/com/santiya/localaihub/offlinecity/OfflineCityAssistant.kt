package com.santiya.localaihub.offlinecity

import com.santiya.localaihub.global.AppLanguage
import java.time.LocalTime
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class OfflineCityAssistant {

    companion object {
        private val TRAVEL_KEYWORDS = listOf(
            "где я", "where am i",
            "поесть", "еда", "кафе", "ресторан",
            "where to eat", "food", "cafe", "restaurant",
            "распис", "автобус", "bus schedule", "bus",
            "как пройти", "как добраться", "маршрут до",
            "how to get", "route to",
            "останов", "nearest stop", "ближайшая остановка",
            "карта", "map"
        )

        fun looksLikeTravelQuery(query: String): Boolean {
            val normalized = query.trim().lowercase(Locale.ROOT)
            return TRAVEL_KEYWORDS.any { normalized.contains(it) }
        }
    }

    fun answer(
        dataset: OfflineCityDataset?,
        query: String,
        language: AppLanguage,
        location: OfflineCityLocationSnapshot?,
    ): OfflineCityAnswer {
        if (dataset == null) {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Нет данных города" else "No city data",
                body = if (language == AppLanguage.RUSSIAN) {
                    "Сначала импортируйте city-pack.json, GeoJSON или GTFS zip."
                } else {
                    "Import a city-pack.json, GeoJSON, or GTFS zip first."
                },
                suggestions = defaultSuggestions(language),
            )
        }

        val normalized = normalize(query)
        if (normalized.isBlank()) {
            return OfflineCityAnswer(
                title = dataset.name,
                body = if (language == AppLanguage.RUSSIAN) {
                    "Можно спросить: где я, где поесть, ближайшая остановка, расписание автобусов, как пройти до ..."
                } else {
                    "Try asking: where am I, where to eat, nearest stop, bus schedule, how to get to ..."
                },
                suggestions = defaultSuggestions(language),
                mapPayload = dataset.buildMapPayload(
                    focus = location?.toPoint(),
                    markers = listOfNotNull(location?.toUserMarker(language))
                ),
            )
        }

        return when {
            normalized.contains("где я") || normalized.contains("where am i") ->
                whereAmI(dataset, language, location)

            normalized.contains("поесть") ||
                normalized.contains("еда") ||
                normalized.contains("кафе") ||
                normalized.contains("ресторан") ||
                normalized.contains("where to eat") ||
                normalized.contains("food") ||
                normalized.contains("cafe") ||
                normalized.contains("restaurant") ->
                whereToEat(dataset, language, location)

            normalized.contains("распис") ||
                normalized.contains("автобус") ||
                normalized.contains("bus schedule") ||
                normalized.contains("bus") ->
                busSchedule(dataset, normalized, language, location)

            normalized.contains("как пройти") ||
                normalized.contains("как добраться") ||
                normalized.contains("маршрут до") ||
                normalized.contains("how to get") ||
                normalized.contains("route to") ->
                routeToPlace(dataset, normalized, language, location)

            else -> search(dataset, normalized, language, location)
        }
    }

    private fun whereAmI(
        dataset: OfflineCityDataset,
        language: AppLanguage,
        location: OfflineCityLocationSnapshot?,
    ): OfflineCityAnswer {
        if (location == null) {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Геопозиция недоступна" else "Location unavailable",
                body = if (language == AppLanguage.RUSSIAN) {
                    "Разрешите доступ к геопозиции, чтобы показать ближайшие места и остановки."
                } else {
                    "Grant location access to show nearby places and stops."
                },
                suggestions = defaultSuggestions(language),
                mapPayload = dataset.buildMapPayload(
                    focus = dataset.bounds?.centerPoint(),
                    markers = emptyList()
                ),
            )
        }

        val nearestPlace = dataset.places.minByOrNull { distanceMeters(location, it.latitude, it.longitude) }
        val nearestStop = dataset.stops.minByOrNull { distanceMeters(location, it.latitude, it.longitude) }
        val placePart = nearestPlace?.let {
            if (language == AppLanguage.RUSSIAN) {
                "Ближайшее место: ${it.name}, ${distanceLabel(distanceMeters(location, it.latitude, it.longitude), language)}."
            } else {
                "Nearest place: ${it.name}, ${distanceLabel(distanceMeters(location, it.latitude, it.longitude), language)} away."
            }
        }.orEmpty()
        val stopPart = nearestStop?.let {
            if (language == AppLanguage.RUSSIAN) {
                "Ближайшая остановка: ${it.name}, ${distanceLabel(distanceMeters(location, it.latitude, it.longitude), language)}."
            } else {
                "Nearest stop: ${it.name}, ${distanceLabel(distanceMeters(location, it.latitude, it.longitude), language)} away."
            }
        }.orEmpty()

        return OfflineCityAnswer(
            title = if (language == AppLanguage.RUSSIAN) "Ваше положение" else "Your position",
            body = buildString {
                append(
                    if (language == AppLanguage.RUSSIAN) {
                        "Координаты: %.5f, %.5f.".format(Locale.US, location.latitude, location.longitude)
                    } else {
                        "Coordinates: %.5f, %.5f.".format(Locale.US, location.latitude, location.longitude)
                    }
                )
                if (placePart.isNotBlank()) append(' ').append(placePart)
                if (stopPart.isNotBlank()) append(' ').append(stopPart)
            },
            suggestions = listOf(
                if (language == AppLanguage.RUSSIAN) "Где поесть рядом" else "Where to eat nearby",
                if (language == AppLanguage.RUSSIAN) "Ближайшая остановка" else "Nearest stop",
                if (language == AppLanguage.RUSSIAN) "Расписание автобусов" else "Bus schedule",
            ),
            mapPayload = dataset.buildMapPayload(
                focus = location.toPoint(),
                markers = listOfNotNull(
                    location.toUserMarker(language),
                    nearestPlace?.toMarker("place"),
                    nearestStop?.toMarker("stop")
                )
            ),
        )
    }

    private fun whereToEat(
        dataset: OfflineCityDataset,
        language: AppLanguage,
        location: OfflineCityLocationSnapshot?,
    ): OfflineCityAnswer {
        val foodPlaces = dataset.places.filter { place ->
            val haystack = normalize(listOf(place.category, place.name, place.description, place.address).joinToString(" "))
            listOf("еда", "food", "кафе", "cafe", "coffee", "ресторан", "restaurant", "bar", "pizza", "бургер", "canteen")
                .any { haystack.contains(it) }
        }.let { candidates ->
            if (location == null) candidates.take(5)
            else candidates.sortedBy { distanceMeters(location, it.latitude, it.longitude) }.take(5)
        }

        if (foodPlaces.isEmpty()) {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Еда не найдена" else "No food places found",
                body = if (language == AppLanguage.RUSSIAN) {
                    "В загруженных офлайн-данных нет кафе или ресторанов."
                } else {
                    "No cafes or restaurants were found in the imported offline data."
                },
                suggestions = defaultSuggestions(language),
            )
        }

        val lines = foodPlaces.mapIndexed { index, place ->
            val prefix = "${index + 1}. ${place.name}"
            val distance = location?.let { distanceLabel(distanceMeters(it, place.latitude, place.longitude), language) }
            val hours = place.hours?.takeIf { it.isNotBlank() }
            buildString {
                append(prefix)
                if (!place.address.isNullOrBlank()) append(" — ").append(place.address)
                if (distance != null) append(" — ").append(distance)
                if (hours != null) append(" — ").append(hours)
            }
        }

        return OfflineCityAnswer(
            title = if (language == AppLanguage.RUSSIAN) "Где поесть" else "Where to eat",
            body = lines.joinToString("\n"),
            suggestions = listOf(
                if (language == AppLanguage.RUSSIAN) "Как пройти до ${foodPlaces.first().name}" else "How to get to ${foodPlaces.first().name}",
                if (language == AppLanguage.RUSSIAN) "Ближайшая остановка" else "Nearest stop",
            ),
            mapPayload = dataset.buildMapPayload(
                focus = location?.toPoint() ?: foodPlaces.first().toPoint(),
                markers = buildList {
                    location?.toUserMarker(language)?.let(::add)
                    addAll(foodPlaces.map { it.toMarker("food") })
                }
            ),
        )
    }

    private fun busSchedule(
        dataset: OfflineCityDataset,
        normalizedQuery: String,
        language: AppLanguage,
        location: OfflineCityLocationSnapshot?,
    ): OfflineCityAnswer {
        val hint = normalizedQuery
            .replace("расписание", "")
            .replace("автобусов", "")
            .replace("автобус", "")
            .replace("bus schedule", "")
            .replace("bus", "")
            .trim()

        val targetStop = when {
            hint.isNotBlank() -> dataset.stops.bestStopMatch(hint)
            location != null -> dataset.stops.minByOrNull { distanceMeters(location, it.latitude, it.longitude) }
            else -> dataset.stops.firstOrNull()
        }

        if (targetStop == null) {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Остановки не найдены" else "No stops found",
                body = if (language == AppLanguage.RUSSIAN) {
                    "В текущем пакете нет данных GTFS или автобусных остановок."
                } else {
                    "The current package does not include GTFS or bus stop data."
                },
                suggestions = defaultSuggestions(language),
            )
        }

        val departures = dataset.departures
            .filter { it.stopId == targetStop.id || normalize(it.stopName).contains(normalize(targetStop.name)) }
            .sortedBy { parseDepartureMinute(it.departureTime) }
            .let { nextDepartures(it) }
            .take(8)

        if (departures.isEmpty()) {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Нет расписания" else "No schedule found",
                body = if (language == AppLanguage.RUSSIAN) {
                    "Для остановки ${targetStop.name} нет импортированных времён отправления."
                } else {
                    "No departure times were imported for ${targetStop.name}."
                },
                suggestions = defaultSuggestions(language),
                mapPayload = dataset.buildMapPayload(
                    focus = targetStop.toPoint(),
                    markers = buildList {
                        location?.toUserMarker(language)?.let(::add)
                        add(targetStop.toMarker("stop"))
                    },
                    path = approximatePath(location, targetStop.toPoint())
                ),
            )
        }

        return OfflineCityAnswer(
            title = if (language == AppLanguage.RUSSIAN) "Расписание: ${targetStop.name}" else "Schedule: ${targetStop.name}",
            body = departures.joinToString("\n") { departure ->
                "${departure.departureTime} — ${departure.routeLabel}${departure.tripHeadsign?.let { " • $it" }.orEmpty()}"
            },
            suggestions = listOf(
                if (language == AppLanguage.RUSSIAN) "Где я" else "Where am I",
                if (language == AppLanguage.RUSSIAN) "Как пройти до ${targetStop.name}" else "How to get to ${targetStop.name}",
            ),
            mapPayload = dataset.buildMapPayload(
                focus = targetStop.toPoint(),
                markers = buildList {
                    location?.toUserMarker(language)?.let(::add)
                    add(targetStop.toMarker("stop"))
                },
                path = approximatePath(location, targetStop.toPoint())
            ),
        )
    }

    private fun routeToPlace(
        dataset: OfflineCityDataset,
        normalizedQuery: String,
        language: AppLanguage,
        location: OfflineCityLocationSnapshot?,
    ): OfflineCityAnswer {
        if (location == null) {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Нужна геопозиция" else "Location required",
                body = if (language == AppLanguage.RUSSIAN) {
                    "Разрешите геопозицию, чтобы я мог дать офлайн-подсказку по направлению."
                } else {
                    "Grant location access so I can provide offline direction guidance."
                },
                suggestions = defaultSuggestions(language),
            )
        }

        val targetHint = extractRouteTarget(normalizedQuery)
        val place = dataset.places.bestPlaceMatch(targetHint)
        val stop = dataset.stops.bestStopMatch(targetHint)

        val targetName: String
        val targetPoint: OfflineCityMapPoint
        val targetKind: String
        if (place != null) {
            targetName = place.name
            targetPoint = place.toPoint()
            targetKind = "destination_place"
        } else if (stop != null) {
            targetName = stop.name
            targetPoint = stop.toPoint()
            targetKind = "destination_stop"
        } else {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Точка не найдена" else "Destination not found",
                body = if (language == AppLanguage.RUSSIAN) {
                    "Не смог найти место или остановку по запросу \"$targetHint\"."
                } else {
                    "Could not find a place or stop for \"$targetHint\"."
                },
                suggestions = defaultSuggestions(language),
            )
        }

        val meters = distanceMeters(location, targetPoint.latitude, targetPoint.longitude)
        val direction = compassDirection(location.latitude, location.longitude, targetPoint.latitude, targetPoint.longitude, language)
        val nearestStop = dataset.stops.minByOrNull {
            distanceMeters(
                OfflineCityLocationSnapshot(targetPoint.latitude, targetPoint.longitude),
                it.latitude,
                it.longitude
            )
        }

        return OfflineCityAnswer(
            title = if (language == AppLanguage.RUSSIAN) "Как пройти до $targetName" else "How to get to $targetName",
            body = buildString {
                append(
                    if (language == AppLanguage.RUSSIAN) {
                        "Идите примерно $direction на ${distanceLabel(meters, language)}."
                    } else {
                        "Head roughly $direction for ${distanceLabel(meters, language)}."
                    }
                )
                nearestStop?.let {
                    append(' ')
                    append(
                        if (language == AppLanguage.RUSSIAN) {
                            "Ближайшая остановка рядом с целью: ${it.name}."
                        } else {
                            "Nearest stop near the destination: ${it.name}."
                        }
                    )
                }
                append(' ')
                append(
                    if (language == AppLanguage.RUSSIAN) {
                        "Это приблизительная офлайн-подсказка без полноценного роутинга по улицам."
                    } else {
                        "This is an approximate offline hint without full street routing."
                    }
                )
            },
            suggestions = listOf(
                if (language == AppLanguage.RUSSIAN) "Где я" else "Where am I",
                if (language == AppLanguage.RUSSIAN) "Расписание автобусов" else "Bus schedule",
            ),
            mapPayload = dataset.buildMapPayload(
                focus = targetPoint,
                markers = buildList {
                    add(location.toUserMarker(language))
                    add(OfflineCityMapMarker("destination-$targetName", targetName, targetPoint, targetKind))
                    nearestStop?.toMarker("stop")?.let(::add)
                },
                path = approximatePath(location, targetPoint)
            ),
        )
    }

    private fun search(
        dataset: OfflineCityDataset,
        normalizedQuery: String,
        language: AppLanguage,
        location: OfflineCityLocationSnapshot?,
    ): OfflineCityAnswer {
        val placeMatches = dataset.places.searchPlaceMatches(normalizedQuery)
            .let { matches ->
                if (location == null) matches.take(5)
                else matches.sortedBy { distanceMeters(location, it.latitude, it.longitude) }.take(5)
            }
        val stopMatches = dataset.stops.searchStopMatches(normalizedQuery).take(5)

        if (placeMatches.isEmpty() && stopMatches.isEmpty()) {
            return OfflineCityAnswer(
                title = if (language == AppLanguage.RUSSIAN) "Ничего не найдено" else "No matches found",
                body = if (language == AppLanguage.RUSSIAN) {
                    "В загруженном офлайн-пакете нет совпадений по запросу."
                } else {
                    "No matches were found in the imported offline package."
                },
                suggestions = defaultSuggestions(language),
            )
        }

        val body = buildString {
            if (placeMatches.isNotEmpty()) {
                append(if (language == AppLanguage.RUSSIAN) "Места:\n" else "Places:\n")
                append(
                    placeMatches.joinToString("\n") { place ->
                        val distance = location?.let { " • ${distanceLabel(distanceMeters(it, place.latitude, place.longitude), language)}" }.orEmpty()
                        "- ${place.name}$distance"
                    }
                )
            }
            if (stopMatches.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(if (language == AppLanguage.RUSSIAN) "Остановки:\n" else "Stops:\n")
                append(stopMatches.joinToString("\n") { stop -> "- ${stop.name}" })
            }
        }

        return OfflineCityAnswer(
            title = if (language == AppLanguage.RUSSIAN) "Результаты поиска" else "Search results",
            body = body,
            suggestions = defaultSuggestions(language),
            mapPayload = dataset.buildMapPayload(
                focus = location?.toPoint() ?: placeMatches.firstOrNull()?.toPoint() ?: stopMatches.firstOrNull()?.toPoint(),
                markers = buildList {
                    location?.toUserMarker(language)?.let(::add)
                    addAll(placeMatches.map { it.toMarker("place") })
                    addAll(stopMatches.map { it.toMarker("stop") })
                }
            ),
        )
    }

    private fun OfflineCityDataset.buildMapPayload(
        focus: OfflineCityMapPoint?,
        markers: List<OfflineCityMapMarker>,
        path: List<OfflineCityMapPoint> = emptyList(),
    ): OfflineCityMapPayload {
        return OfflineCityMapPayload(
            bounds = bounds ?: deriveBounds(markers, path, focus),
            focus = focus,
            markers = markers.distinctBy { it.id },
            path = path,
            tileSource = tileSource,
        )
    }

    private fun deriveBounds(
        markers: List<OfflineCityMapMarker>,
        path: List<OfflineCityMapPoint>,
        focus: OfflineCityMapPoint?,
    ): OfflineCityBounds? {
        val points = buildList {
            addAll(markers.map { it.point })
            addAll(path)
            focus?.let(::add)
        }
        if (points.isEmpty()) return null
        return OfflineCityBounds(
            south = points.minOf { it.latitude },
            west = points.minOf { it.longitude },
            north = points.maxOf { it.latitude },
            east = points.maxOf { it.longitude },
        )
    }

    private fun OfflineCityBounds.centerPoint(): OfflineCityMapPoint =
        OfflineCityMapPoint(
            latitude = (south + north) / 2.0,
            longitude = (west + east) / 2.0,
        )

    private fun OfflineCityLocationSnapshot.toPoint(): OfflineCityMapPoint =
        OfflineCityMapPoint(latitude, longitude)

    private fun OfflineCityLocationSnapshot.toUserMarker(language: AppLanguage): OfflineCityMapMarker =
        OfflineCityMapMarker(
            id = "user-location",
            label = if (language == AppLanguage.RUSSIAN) "Вы" else "You",
            point = toPoint(),
            kind = "user",
        )

    private fun OfflineCityPlace.toPoint(): OfflineCityMapPoint =
        OfflineCityMapPoint(latitude, longitude)

    private fun OfflineCityPlace.toMarker(kind: String): OfflineCityMapMarker =
        OfflineCityMapMarker(id = id, label = name, point = toPoint(), kind = kind)

    private fun OfflineCityStop.toPoint(): OfflineCityMapPoint =
        OfflineCityMapPoint(latitude, longitude)

    private fun OfflineCityStop.toMarker(kind: String): OfflineCityMapMarker =
        OfflineCityMapMarker(id = id, label = name, point = toPoint(), kind = kind)

    private fun approximatePath(
        location: OfflineCityLocationSnapshot?,
        target: OfflineCityMapPoint,
    ): List<OfflineCityMapPoint> {
        if (location == null) return listOf(target)
        val midpoint = OfflineCityMapPoint(
            latitude = (location.latitude + target.latitude) / 2.0,
            longitude = (location.longitude + target.longitude) / 2.0,
        )
        return listOf(location.toPoint(), midpoint, target)
    }

    private fun defaultSuggestions(language: AppLanguage): List<String> {
        return if (language == AppLanguage.RUSSIAN) {
            listOf("Где я", "Где поесть рядом", "Расписание автобусов")
        } else {
            listOf("Where am I", "Where to eat nearby", "Bus schedule")
        }
    }

    private fun normalize(text: String?): String =
        text.orEmpty().trim().lowercase(Locale.ROOT)

    private fun distanceMeters(
        origin: OfflineCityLocationSnapshot,
        latitude: Double,
        longitude: Double,
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(latitude - origin.latitude)
        val dLon = Math.toRadians(longitude - origin.longitude)
        val startLat = Math.toRadians(origin.latitude)
        val endLat = Math.toRadians(latitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            sin(dLon / 2) * sin(dLon / 2) * cos(startLat) * cos(endLat)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun distanceLabel(meters: Double, language: AppLanguage): String {
        return if (meters >= 1000) {
            val km = (meters / 100.0).roundToInt() / 10.0
            if (language == AppLanguage.RUSSIAN) "$km км" else "$km km"
        } else {
            val rounded = meters.roundToInt()
            if (language == AppLanguage.RUSSIAN) "$rounded м" else "$rounded m"
        }
    }

    private fun compassDirection(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        language: AppLanguage,
    ): String {
        val y = sin(Math.toRadians(endLon - startLon)) * cos(Math.toRadians(endLat))
        val x = cos(Math.toRadians(startLat)) * sin(Math.toRadians(endLat)) -
            sin(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) * cos(Math.toRadians(endLon - startLon))
        val angle = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        val labelsRu = listOf("на север", "на северо-восток", "на восток", "на юго-восток", "на юг", "на юго-запад", "на запад", "на северо-запад")
        val labelsEn = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        val index = (((angle + 22.5) % 360) / 45).toInt()
        return if (language == AppLanguage.RUSSIAN) labelsRu[index] else labelsEn[index]
    }

    private fun parseDepartureMinute(raw: String): Int {
        val parts = raw.split(':')
        if (parts.size < 2) return Int.MAX_VALUE
        val hour = parts[0].toIntOrNull() ?: return Int.MAX_VALUE
        val minute = parts[1].toIntOrNull() ?: return Int.MAX_VALUE
        return hour * 60 + minute
    }

    private fun extractRouteTarget(query: String): String {
        return query
            .replace("как пройти", "")
            .replace("как добраться", "")
            .replace("маршрут до", "")
            .replace("how to get", "")
            .replace("route to", "")
            .replace(Regex("^\\s*(до|to)\\s+"), "")
            .replace('"', ' ')
            .replace('\'', ' ')
            .trim()
    }

    private fun nextDepartures(departures: List<OfflineCityDeparture>): List<OfflineCityDeparture> {
        val now = LocalTime.now()
        val nowMinute = now.hour * 60 + now.minute
        val future = departures.filter { parseDepartureMinute(it.departureTime) >= nowMinute }
        return if (future.isNotEmpty()) future else departures
    }

    private fun List<OfflineCityPlace>.searchPlaceMatches(query: String): List<OfflineCityPlace> {
        if (query.isBlank()) return take(5)
        return filter { place ->
            val haystack = normalize(
                listOf(place.name, place.category, place.description, place.address, place.tags.joinToString(" "))
                    .joinToString(" ")
            )
            haystack.contains(query)
        }
    }

    private fun List<OfflineCityPlace>.bestPlaceMatch(query: String): OfflineCityPlace? =
        searchPlaceMatches(query).minByOrNull { normalize(it.name).length }

    private fun List<OfflineCityStop>.searchStopMatches(query: String): List<OfflineCityStop> {
        if (query.isBlank()) return take(5)
        return filter { stop ->
            val haystack = normalize(listOf(stop.name, stop.routes.joinToString(" ")).joinToString(" "))
            haystack.contains(query)
        }
    }

    private fun List<OfflineCityStop>.bestStopMatch(query: String): OfflineCityStop? =
        searchStopMatches(query).minByOrNull { normalize(it.name).length }
}
