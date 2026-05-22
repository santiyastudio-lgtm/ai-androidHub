package com.santiya.localaihub.ui.screen.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.ui.components.ActionTextButton
import com.santiya.localaihub.ui.icons.TnIcons

@Composable
fun TermsAndConditionsScreen(
    onAccept: () -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = localizedText("Условия использования", "Terms of use"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = localizedText(
                    "Коротко: вы управляете тем, что делает локальный AI-хаб и какие модели в нём работают.",
                    "In short: you control what the local AI hub does and which models run inside it."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Standards.SpacingSm, bottom = Standards.SpacingLg),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd),
            ) {
                TermsCard(
                    title = localizedText("1. Ответы и контент", "1. Responses and content"),
                    content = localizedText(
                        "Хаб может генерировать текст, изображения, голос и другой контент. Он может ошибаться и не должен быть единственным источником истины.",
                        "The hub can generate text, images, speech, and other content. It can be wrong and should not be your only source of truth."
                    )
                )
                TermsCard(
                    title = localizedText("2. Ответственность", "2. Responsibility"),
                    content = localizedText(
                        "Вы сами отвечаете за загруженные модели, отправленные запросы и использование результатов.",
                        "You are responsible for the models you load, the prompts you send, and the way you use the results."
                    )
                )
                TermsCard(
                    title = localizedText("3. Критические решения", "3. Critical decisions"),
                    content = localizedText(
                        "Не используйте ответы хаба как единственную основу для медицинских, юридических, финансовых или иных критичных решений.",
                        "Do not use hub outputs as the only basis for medical, legal, financial, or other critical decisions."
                    )
                )
                TermsCard(
                    title = localizedText("4. Внешние модели", "4. External models"),
                    content = localizedText(
                        "Внешние модели и raw assets могут вести себя непредсказуемо. Карточка в каталоге не гарантирует запуск на конкретном устройстве.",
                        "External models and raw assets can behave unpredictably. A catalog card does not guarantee successful execution on a specific device."
                    )
                )
                Spacer(modifier = Modifier.height(Standards.SpacingMd))
            }

            ActionTextButton(
                onClickListener = onAccept,
                icon = TnIcons.Check,
                text = localizedText("Принимаю", "Accept"),
                contentDescription = localizedText("Принять условия", "Accept terms"),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Standards.SpacingLg),
            )
        }
    }
}

@Composable
private fun TermsCard(
    title: String,
    content: String,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
