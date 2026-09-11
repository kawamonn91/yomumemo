package jp.yomumemo.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage

/**
 * 書影。画像が無ければタイトルから生成したプレースホルダを描く。
 *
 * openBD は表紙を約6%しか持たず、補完元のキーが未設定なら全冊がここに落ちるため、
 * プレースホルダの見栄えが本棚の印象をほぼ決める。色はタイトルから決定的に導くので、
 * 同じ本はいつでも同じ色になり、背表紙のように識別の手がかりになる。
 */
@Composable
fun BookCover(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(modifier = modifier.clip(shape)) {
        if (coverUrl.isNullOrBlank()) {
            PlaceholderCover(title)
        } else {
            SubcomposeAsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { PlaceholderCover(title) },
                error = { PlaceholderCover(title) },
            )
        }
    }
}

@Composable
private fun PlaceholderCover(title: String) {
    val (top, bottom) = placeholderColors(title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

private val PLACEHOLDER_PALETTE = listOf(
    Color(0xFF7C5E48) to Color(0xFF5D4636),
    Color(0xFF5F7355) to Color(0xFF45543E),
    Color(0xFF4E6272) to Color(0xFF3A4955),
    Color(0xFF7A5A6E) to Color(0xFF5B4353),
    Color(0xFF6B6650) to Color(0xFF4F4B3B),
    Color(0xFF8B5E3C) to Color(0xFF69472D),
)

/**
 * タイトルから色を決める。落ち着いた色相のみを使い、本棚が騒がしくならないようにする。
 *
 * hashCode は負になりうる。Kotlin の % は被除数の符号を引き継ぐため、
 * 素直に剰余を取ると負の添字になって落ちる。floorMod で必ず 0 以上にする。
 */
internal fun placeholderColorIndex(title: String, paletteSize: Int): Int =
    Math.floorMod(title.hashCode(), paletteSize)

private fun placeholderColors(title: String): Pair<Color, Color> =
    PLACEHOLDER_PALETTE[placeholderColorIndex(title, PLACEHOLDER_PALETTE.size)]
