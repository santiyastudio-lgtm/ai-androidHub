package com.santiya.localaihub.ui.screen.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CleanSupportQuickSection() {
    val context = LocalContext.current
    Surface(
        onClick = { openSupportBotCard(context, "support") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Standards.CardCornerRadius),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(Standards.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
        ) {
            Icon(
                TnIcons.Sparkles,
                contentDescription = null,
                modifier = Modifier.size(Standards.IconLg),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localizedText("Поддержка", "Support"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = localizedText(
                        "Открывает @SantiyaSupportBot для отправки логов и комментария в поддержку.",
                        "Opens @SantiyaSupportBot to send logs and a support comment."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun openSupportBotCard(
    context: Context,
    startPayload: String,
) {
    val tgUri = Uri.parse("https://t.me/SantiyaSupportBot?start=$startPayload")
    val intent = Intent(Intent.ACTION_VIEW, tgUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
