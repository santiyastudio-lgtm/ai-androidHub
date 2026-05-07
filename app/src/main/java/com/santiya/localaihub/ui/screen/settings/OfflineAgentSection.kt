package com.santiya.localaihub.ui.screen.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.santiya.localaihub.global.AppLanguage
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.ui.components.SectionDivider
import com.santiya.localaihub.ui.components.SectionHeader
import com.santiya.localaihub.ui.components.StandardCard
import com.santiya.localaihub.ui.icons.TnIcons

internal fun LazyListScope.offlineAgentSection(
    language: AppLanguage,
    onOpenOfflineCity: () -> Unit,
) {
    item { SectionDivider() }
    item { SectionHeader(title = localizedText(language, "Офлайн-карты и городской агент", "Offline maps and city agent")) }
    item {
        StandardCard(
            title = localizedText(language, "Офлайн-город", "Offline City Agent"),
            description = localizedText(
                language,
                "Загрузите локальный пакет города или выберите готовый пакет по названию. После этого можно офлайн спрашивать: где вы, где поесть, как пройти и какое расписание автобусов.",
                "Load a local city pack or choose a ready-made package by city name. Then you can ask offline: where you are, where to eat, how to get somewhere, and bus schedules."
            ),
            icon = TnIcons.World,
            onClick = onOpenOfflineCity,
        )
    }
}
