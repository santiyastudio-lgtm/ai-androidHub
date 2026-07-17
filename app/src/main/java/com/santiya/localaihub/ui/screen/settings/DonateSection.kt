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
    DonateEndpoint("Bitcoin (BTC)", "Bitcoin (BTC)", "bc1qhft9dxkn0g07zm9ht8zrfqyrh85djhueu4q49k", noteRu = "Сеть Bitcoin", noteEn = "Bitcoin network"),
    DonateEndpoint("Ethereum (ETH)", "Ethereum (ETH)", "0x5311B0318A24F63196A572b447609bc336A4C7b2", noteRu = "Сеть Ethereum", noteEn = "Ethereum network"),
    DonateEndpoint("Solana (SOL)", "Solana (SOL)", "9i76uPGouNh8KVB8LtippfFY7p6kG2ZSLbtLwPqb6i76", noteRu = "Сеть Solana", noteEn = "Solana network"),
    DonateEndpoint("USDT (Solana)", "USDT (Tether, Solana)", "9i76uPGouNh8KVB8LtippfFY7p6kG2ZSLbtLwPqb6i76", noteRu = "Сеть Solana (SPL)", noteEn = "Solana network (SPL)"),
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
                            "Кошельки для поддержки проекта.",
                            "Wallets for supporting the project."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DonateDestinations(context)
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

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
