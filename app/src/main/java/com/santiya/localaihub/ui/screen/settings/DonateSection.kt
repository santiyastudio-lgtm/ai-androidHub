package com.santiya.localaihub.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.santiya.localaihub.global.Standards
import com.santiya.localaihub.global.localizedText
import com.santiya.localaihub.ui.icons.TnIcons

private data class DonateEndpoint(
    val labelRu: String,
    val labelEn: String,
    val value: String,
    val noteRu: String? = null,
    val noteEn: String? = null,
)

private val donateEndpoints = listOf(
    DonateEndpoint("Solana", "Solana", "97j3xnrjHtM5dDUZ8xAkAKqxY1Axro4gvsPCkqgZKQTj"),
    DonateEndpoint("Ethereum", "Ethereum", "0x061dE20Bb9b2fA9c1C3d8E38939092aCB76284fe"),
    DonateEndpoint("Bitcoin", "Bitcoin", "bc1qfyzhnhajm8rslkhell9mg54na2tla90e6dkf3d"),
    DonateEndpoint("T-Bank карта", "T-Bank card", "2200701933182781", noteRu = "Только РФ", noteEn = "RF only"),
    DonateEndpoint("Ozon Bank карта", "Ozon Bank card", "2204320688009192", noteRu = "Только РФ", noteEn = "RF only"),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DonateQuickSection() {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.CardCornerRadius),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
            ) {
                Icon(
                    TnIcons.Coins,
                    contentDescription = null,
                    modifier = Modifier.size(Standards.IconLg),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localizedText("Донат", "Donate"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        localizedText(
                            "Кошельки и карты для поддержки проекта. Telegram-бот здесь не используется для доната.",
                            "Wallets and cards for supporting the project. The Telegram bot is not used for donations here."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DonateDestinations(context)
            StarsSupportCard()
        }
    }
}

@Composable
private fun DonateDestinations(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(Standards.SpacingXs)) {
        donateEndpoints.forEach { endpoint ->
            Surface(
                onClick = {
                    copyToClipboard(context, endpoint.labelEn, endpoint.value)
                    Toast.makeText(context, localizedText(context, "Скопировано", "Copied"), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Standards.RadiusLg),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = localizedText(context, endpoint.labelRu, endpoint.labelEn),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = endpoint.value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val note = localizedText(context, endpoint.noteRu.orEmpty(), endpoint.noteEn.orEmpty())
                    if (note.isNotBlank()) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StarsSupportCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.RadiusLg),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = localizedText("Поддержка звёздами", "Support with stars"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = localizedText(
                        "Можно поддержать проект, отправив звёзды боту @SantiyaSupportBot.",
                        "You can support the project by sending stars to @SantiyaSupportBot."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
