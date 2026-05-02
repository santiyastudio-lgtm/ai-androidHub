package com.santiya.localaihub.ui.screen.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.ui.components.ActionTextButton
import com.santiya.localaihub.ui.icons.TnIcons

@Composable
fun TermsAndConditionsScreen(
    onAccept: () -> Unit
) {
    val scrollState = rememberScrollState()
    var isScrolledToBottom by remember { mutableStateOf(false) }

    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        isScrolledToBottom = scrollState.value >= scrollState.maxValue - 80 || scrollState.maxValue == 0
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Условия использования",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Коротко: вы управляете тем, что делает и отвечает локальный AI-хаб.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Standards.SpacingSm, bottom = Standards.SpacingLg)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(Standards.SpacingMd)
            ) {
                TermsCard(
                    title = "1. Ответы и контент",
                    content = "SantiyaLocalAiHub может генерировать текст, изображения, голос и другие материалы. Эти ответы могут быть неточными, вредными, незаконными, оскорбительными или просто ошибочными."
                )
                TermsCard(
                    title = "2. Ответственность",
                    content = "Вы сами отвечаете за то, какие модели загружаете, какие запросы отправляете, как используете ответы и что делаете с результатами работы хаба."
                )
                TermsCard(
                    title = "3. Ограничение ответственности",
                    content = "Разработчик и команда хаба не несут ответственности за ответы моделей, действия, совершённые на основе этих ответов, последствия использования хаба, загруженных моделей и созданного контента."
                )
                TermsCard(
                    title = "4. Проверка фактов",
                    content = "Не используйте ответы хаба как единственный источник для медицинских, юридических, финансовых, охранных или других критичных решений. Такие данные нужно проверять отдельно."
                )
                TermsCard(
                    title = "5. Локальные модели и внешние источники",
                    content = "Hub умеет загружать модели из внешних каталогов и открытых источников. Поведение этих моделей не контролируется приложением. Даже если модель помечена как подходящая, запуск на конкретном устройстве не гарантируется."
                )

                Spacer(modifier = Modifier.height(Standards.SpacingSm))

                Text(
                    text = "Нажимая «Принимаю», вы подтверждаете, что понимаете: ответственность за ответы и использование AI-хаба остаётся на пользователе.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Standards.SpacingSm)
                )

                Spacer(modifier = Modifier.height(Standards.SpacingMd))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Standards.SpacingLg),
                contentAlignment = Alignment.Center
            ) {
                ActionTextButton(
                    onClickListener = {
                        if (isScrolledToBottom) {
                            onAccept()
                        }
                    },
                    icon = TnIcons.Check,
                    text = if (isScrolledToBottom) "Принимаю" else "Прокрутите до конца",
                    contentDescription = "Принять условия",
                    modifier = Modifier.fillMaxWidth(0.84f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScrolledToBottom) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (isScrolledToBottom) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun TermsCard(
    title: String,
    content: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
