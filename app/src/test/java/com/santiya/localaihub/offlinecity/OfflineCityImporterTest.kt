package com.santiya.localaihub.offlinecity

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCityImporterTest {

    @Test
    fun parsesGeoJsonIntoPlaces() {
        val dataset = OfflineCityImporter.parseGeoJson(
            fileName = "center.geojson",
            content = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": {
                        "name": "Coffee Point",
                        "amenity": "cafe",
                        "opening_hours": "08:00-22:00"
                      },
                      "geometry": {
                        "type": "Point",
                        "coordinates": [37.62, 55.75]
                      }
                    }
                  ]
                }
            """.trimIndent()
        )

        assertEquals("center", dataset.id)
        assertEquals(1, dataset.places.size)
        assertEquals("Coffee Point", dataset.places.first().name)
    }

    @Test
    fun parsesGtfsZipIntoStopsRoutesAndDepartures() {
        val bytes = buildGtfsZip(
            mapOf(
                "stops.txt" to """
                    stop_id,stop_name,stop_lat,stop_lon
                    stop-1,Central Market,55.7600,37.6100
                """.trimIndent(),
                "routes.txt" to """
                    route_id,route_short_name,route_long_name
                    route-1,M1,Central - North
                """.trimIndent(),
                "trips.txt" to """
                    route_id,service_id,trip_id,trip_headsign
                    route-1,weekday,trip-1,North Station
                """.trimIndent(),
                "stop_times.txt" to """
                    trip_id,arrival_time,departure_time,stop_id,stop_sequence
                    trip-1,08:30:00,08:30:00,stop-1,1
                """.trimIndent(),
                "calendar.txt" to """
                    service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday
                    weekday,1,1,1,1,1,0,0
                """.trimIndent()
            )
        )

        val dataset = OfflineCityImporter.parseGtfsZip("moscow-gtfs.zip", bytes)

        assertEquals(1, dataset.stops.size)
        assertEquals(1, dataset.routes.size)
        assertEquals(1, dataset.departures.size)
        assertEquals("Central Market", dataset.departures.first().stopName)
        assertTrue(dataset.departures.first().serviceDays.contains("monday"))
    }

    private fun buildGtfsZip(files: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
