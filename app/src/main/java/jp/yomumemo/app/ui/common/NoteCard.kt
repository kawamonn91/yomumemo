package jp.yomumemo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import jp.yomumemo.app.data.db.entity.NoteEntity
import jp.yomumemo.app.data.db.entity.NoteType

fun NoteType.label(): String = when (this) {
    NoteType.QUOTE -> "引用"
    NoteType.THOUGHT -> "感想"
    NoteType.QUESTION -> "疑問"
    NoteType.SUMMARY -> "要約"
}

/** 1件のメモ。引用は縦罫を引いて自分の言葉と視覚的に区別する。 */
@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = note.type.label(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                note.page?.let {
                    Text(
                        text = "p.$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (note.quote.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row {
                    // 引用であることを示す縦罫
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(intrinsicQuoteHeight(note.quote))
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = note.quote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (note.comment.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(text = note.comment, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** 行数に応じた縦罫の高さ。厳密である必要はなく、目安で足りる。 */
private fun intrinsicQuoteHeight(quote: String): androidx.compose.ui.unit.Dp {
    val approxLines = (quote.length / 22) + 1
    return (approxLines.coerceAtMost(8) * 24).dp
}
