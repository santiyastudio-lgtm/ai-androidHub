package com.santiya.localaihub.offlinecity

import com.santiya.localaihub.global.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCityAssistantTest {
    private val dataset = OfflineCityDataset(
        id = "moscow-center",
        name = "Moscow Center",
        places = listOf(
            OfflineCityPlace(
                id = "cafe-1",
                name = "Cafe Pushkin",
                category = "restaurant",
                address = "Tverskoy Blvd 26",
                latitude = 55.7648,
                longitude = 37.6045,
                hours = "09:00-23:00",
            ),
            OfflineCityPlace(
                id = "museum-1",
                name = "History Museum",
                category = "museum",
                latitude = 55.7550,
                longitude = 37.6175,
            )
        ),
        stops = listOf(
            OfflineCityStop(
                id = "stop-1",
                name = "Central Market",
                latitude = 55.7600,
                longitude = 37.6100,
                routes = listOf("M1", "24"),
            )
        ),
        routes = listOf(
            OfflineCityRoute(id = "route-1", shortName = "M1", longName = "Central - North"),
        ),
        departures = listOf(
            OfflineCityDeparture(
                routeId = "route-1",
                routeLabel = "M1",
                stopId = "stop-1",
                stopName = "Central Market",
                departureTime = "23:59:00",
                tripHeadsign = "North Station",
            )
        ),
        tileSource = OfflineCityTileSource(
            id = "tiles-1",
            displayName = "Test tiles",
            format = "mbtiles",
            localPath = "/tmp/test.mbtiles",
        )
    )

    private val location = OfflineCityLocationSnapshot(
        latitude = 55.7602,
        longitude = 37.6102,
    )

    @Test
    fun answersWhereToEatInRussian() {
        val assistant = OfflineCityAssistant()
        val answer = assistant.answer(dataset, "где поесть рядом", AppLanguage.RUSSIAN, location)

        assertTrue(answer.title.contains("поесть", ignoreCase = true))
        assertTrue(answer.body.contains("Cafe Pushkin"))
        assertNotNull(answer.mapPayload)
        assertTrue(answer.mapPayload!!.markers.any { it.label == "Cafe Pushkin" })
    }

    @Test
    fun answersBusScheduleInEnglish() {
        val assistant = OfflineCityAssistant()
        val answer = assistant.answer(dataset, "bus schedule", AppLanguage.ENGLISH, location)

        assertTrue(answer.title.contains("Schedule"))
        assertTrue(answer.body.contains("M1"))
        assertTrue(answer.body.contains("23:59"))
        assertEquals("Test tiles", answer.mapPayload?.tileSource?.displayName)
    }

    @Test
    fun answersApproximateRouteHintWithPath() {
        val assistant = OfflineCityAssistant()
        val answer = assistant.answer(dataset, "как пройти до History Museum", AppLanguage.RUSSIAN, location)

        assertTrue(answer.title.contains("History Museum"))
        assertTrue(answer.body.contains("приблиз"))
        assertNotNull(answer.mapPayload)
        assertTrue(answer.mapPayload!!.path.size >= 2)
    }

    @Test
    fun detectsTravelQueries() {
        assertTrue(OfflineCityAssistant.looksLikeTravelQuery("nearest stop"))
        assertTrue(OfflineCityAssistant.looksLikeTravelQuery("как пройти до кафе"))
        assertTrue(!OfflineCityAssistant.looksLikeTravelQuery("write a poem about fog"))
    }
}
