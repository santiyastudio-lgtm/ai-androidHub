package com.santiya.localaihub.offlinecity

import android.content.Context
import java.util.Locale

data class OfflineCityCatalogEntry(
    val id: String,
    val cityName: String,
    val regionCountry: String,
    val sourceCoverage: String,
    val packageSizeBytes: Long,
    val assetFileName: String? = null,
    val tileDisplayName: String? = null,
    val centerLatitude: Double,
    val centerLongitude: Double,
)

class OfflineCityCatalog(
    private val context: Context,
) {
    private val entries: List<OfflineCityCatalogEntry> by lazy {
        buildList {
            add(
                assetEntry(
                    id = "moscow-center-demo",
                    cityName = "Москва",
                    regionCountry = "Россия • Москва",
                    sourceCoverage = "Демо-пакет: места, остановки, расписания и карта",
                    assetFileName = "offline_city/moscow-center-city-pack.json",
                    centerLatitude = 55.7558,
                    centerLongitude = 37.6176,
                )
            )
            add(
                assetEntry(
                    id = "saint-petersburg-center-demo",
                    cityName = "Санкт-Петербург",
                    regionCountry = "Россия • Санкт-Петербург",
                    sourceCoverage = "Демо-пакет: места, остановки, расписания и карта",
                    assetFileName = "offline_city/spb-center-city-pack.json",
                    centerLatitude = 59.9343,
                    centerLongitude = 30.3351,
                )
            )

            addAll(russiaBaselineEntries())
        }
    }

    fun search(query: String): List<OfflineCityCatalogEntry> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return entries
        return entries.filter { entry ->
            entry.cityName.lowercase(Locale.ROOT).contains(normalized) ||
                entry.regionCountry.lowercase(Locale.ROOT).contains(normalized) ||
                entry.sourceCoverage.lowercase(Locale.ROOT).contains(normalized)
        }
    }

    private fun assetEntry(
        id: String,
        cityName: String,
        regionCountry: String,
        sourceCoverage: String,
        assetFileName: String,
        centerLatitude: Double,
        centerLongitude: Double,
    ): OfflineCityCatalogEntry {
        val assetSize = context.assets.open(assetFileName).use { it.available().toLong() }
        return OfflineCityCatalogEntry(
            id = id,
            cityName = cityName,
            regionCountry = regionCountry,
            sourceCoverage = sourceCoverage,
            packageSizeBytes = assetSize,
            assetFileName = assetFileName,
            tileDisplayName = "Встроенная демо-карта",
            centerLatitude = centerLatitude,
            centerLongitude = centerLongitude,
        )
    }

    private fun baselineEntry(
        id: String,
        cityName: String,
        regionCountry: String,
        latitude: Double,
        longitude: Double,
    ): OfflineCityCatalogEntry {
        return OfflineCityCatalogEntry(
            id = id,
            cityName = cityName,
            regionCountry = regionCountry,
            sourceCoverage = "Базовый пакет: границы города и карта. Места, остановки и расписания можно импортировать дополнительно.",
            packageSizeBytes = 96_000L,
            assetFileName = null,
            tileDisplayName = "Встроенная базовая карта",
            centerLatitude = latitude,
            centerLongitude = longitude,
        )
    }

    private fun russiaBaselineEntries(): List<OfflineCityCatalogEntry> = listOf(
        baselineEntry("adygea-maykop", "Майкоп", "Россия • Республика Адыгея", 44.6098, 40.1007),
        baselineEntry("altai-gorno-altaisk", "Горно-Алтайск", "Россия • Республика Алтай", 51.9581, 85.9603),
        baselineEntry("bashkortostan-ufa", "Уфа", "Россия • Республика Башкортостан", 54.7388, 55.9721),
        baselineEntry("buryatia-ulan-ude", "Улан-Удэ", "Россия • Республика Бурятия", 51.8335, 107.5841),
        baselineEntry("dagestan-makhachkala", "Махачкала", "Россия • Республика Дагестан", 42.9849, 47.5047),
        baselineEntry("ingushetia-magas", "Магас", "Россия • Республика Ингушетия", 43.1667, 44.8167),
        baselineEntry("kabardino-balkaria-nalchik", "Нальчик", "Россия • Кабардино-Балкарская Республика", 43.4853, 43.6071),
        baselineEntry("kalmykia-elista", "Элиста", "Россия • Республика Калмыкия", 46.3077, 44.2558),
        baselineEntry("karachay-cherkessia-cherkessk", "Черкесск", "Россия • Карачаево-Черкесская Республика", 44.2230, 42.0578),
        baselineEntry("karelia-petrozavodsk", "Петрозаводск", "Россия • Республика Карелия", 61.7849, 34.3469),
        baselineEntry("komi-syktyvkar", "Сыктывкар", "Россия • Республика Коми", 61.6688, 50.8364),
        baselineEntry("crimea-simferopol", "Симферополь", "Россия • Республика Крым", 44.9521, 34.1024),
        baselineEntry("mari-el-yoshkar-ola", "Йошкар-Ола", "Россия • Республика Марий Эл", 56.6344, 47.8998),
        baselineEntry("mordovia-saransk", "Саранск", "Россия • Республика Мордовия", 54.1838, 45.1749),
        baselineEntry("sakha-yakutsk", "Якутск", "Россия • Республика Саха (Якутия)", 62.0281, 129.7326),
        baselineEntry("north-ossetia-vladikavkaz", "Владикавказ", "Россия • Республика Северная Осетия — Алания", 43.0205, 44.6819),
        baselineEntry("tatarstan-kazan", "Казань", "Россия • Республика Татарстан", 55.7961, 49.1064),
        baselineEntry("tuva-kyzyl", "Кызыл", "Россия • Республика Тыва", 51.7191, 94.4378),
        baselineEntry("udmurtia-izhevsk", "Ижевск", "Россия • Удмуртская Республика", 56.8526, 53.2045),
        baselineEntry("khakassia-abakan", "Абакан", "Россия • Республика Хакасия", 53.7223, 91.4437),
        baselineEntry("chechnya-grozny", "Грозный", "Россия • Чеченская Республика", 43.3180, 45.6982),
        baselineEntry("chuvashia-cheboksary", "Чебоксары", "Россия • Чувашская Республика", 56.1439, 47.2489),
        baselineEntry("sevastopol", "Севастополь", "Россия • Севастополь", 44.6166, 33.5254),
        baselineEntry("kaliningrad", "Калининград", "Россия • Калининградская область", 54.7104, 20.4522),
        baselineEntry("krasnodar", "Краснодар", "Россия • Краснодарский край", 45.0355, 38.9753),
        baselineEntry("sochi", "Сочи", "Россия • Краснодарский край", 43.5855, 39.7231),
        baselineEntry("novosibirsk", "Новосибирск", "Россия • Новосибирская область", 55.0084, 82.9357),
        baselineEntry("yekaterinburg", "Екатеринбург", "Россия • Свердловская область", 56.8389, 60.6057),
        baselineEntry("nizhny-novgorod", "Нижний Новгород", "Россия • Нижегородская область", 56.2965, 43.9361),
        baselineEntry("samara", "Самара", "Россия • Самарская область", 53.1959, 50.1002),
        baselineEntry("omsk", "Омск", "Россия • Омская область", 54.9885, 73.3242),
        baselineEntry("rostov-on-don", "Ростов-на-Дону", "Россия • Ростовская область", 47.2357, 39.7015),
        baselineEntry("ufa", "Уфа", "Россия • Республика Башкортостан", 54.7388, 55.9721),
        baselineEntry("perm", "Пермь", "Россия • Пермский край", 58.0105, 56.2502),
        baselineEntry("voronezh", "Воронеж", "Россия • Воронежская область", 51.6608, 39.2003),
        baselineEntry("volgograd", "Волгоград", "Россия • Волгоградская область", 48.7080, 44.5133),
        baselineEntry("krasnoyarsk", "Красноярск", "Россия • Красноярский край", 56.0153, 92.8932),
        baselineEntry("irkutsk", "Иркутск", "Россия • Иркутская область", 52.2871, 104.2810),
        baselineEntry("vladivostok", "Владивосток", "Россия • Приморский край", 43.1155, 131.8855),
        baselineEntry("khabarovsk", "Хабаровск", "Россия • Хабаровский край", 48.4802, 135.0719),
        baselineEntry("petropavlovsk-kamchatsky", "Петропавловск-Камчатский", "Россия • Камчатский край", 53.0370, 158.6559),
        baselineEntry("murmansk", "Мурманск", "Россия • Мурманская область", 68.9585, 33.0827),
        baselineEntry("arkhangelsk", "Архангельск", "Россия • Архангельская область", 64.5393, 40.5187),
        baselineEntry("yaroslavl", "Ярославль", "Россия • Ярославская область", 57.6261, 39.8845),
        baselineEntry("tyumen", "Тюмень", "Россия • Тюменская область", 57.1522, 65.5272)
    )
}
