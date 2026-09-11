package jp.yomumemo.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 紙と本を思わせる、落ち着いた暖色系。長時間読むアプリなので彩度は抑える。
private val Ink = Color(0xFF3B322C)
private val Clay = Color(0xFF8B5E3C)
private val Sand = Color(0xFFF6F1E8)
private val Moss = Color(0xFF5F7355)

private val LightColors = lightColorScheme(
    primary = Clay,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E0CE),
    onPrimaryContainer = Color(0xFF2E1B0B),
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6D6),
    onSecondaryContainer = Color(0xFF1B2417),
    background = Sand,
    onBackground = Ink,
    surface = Color(0xFFFFFBF4),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE3D6),
    onSurfaceVariant = Color(0xFF5A5047),
    outline = Color(0xFF8C8177),
    // Card などが使う階層色。既定のままだと紫がかった灰色になり、
    // 紙を思わせる暖色の配色から浮いてしまうので明示的に与える。
    surfaceBright = Color(0xFFFFFBF4),
    surfaceDim = Color(0xFFE6DFD3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF6EC),
    surfaceContainer = Color(0xFFF6F0E4),
    surfaceContainerHigh = Color(0xFFF0EADC),
    surfaceContainerHighest = Color(0xFFEAE3D4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE5BE96),
    onPrimary = Color(0xFF452B14),
    primaryContainer = Color(0xFF5F4128),
    onPrimaryContainer = Color(0xFFF3E0CE),
    secondary = Color(0xFFC0CDB6),
    onSecondary = Color(0xFF2C3A26),
    secondaryContainer = Color(0xFF42513B),
    onSecondaryContainer = Color(0xFFDCE6D6),
    background = Color(0xFF16130F),
    onBackground = Color(0xFFEAE1D8),
    surface = Color(0xFF1E1A16),
    onSurface = Color(0xFFEAE1D8),
    surfaceVariant = Color(0xFF4C443B),
    onSurfaceVariant = Color(0xFFCFC4B8),
    outline = Color(0xFF988D82),
    surfaceBright = Color(0xFF3B352F),
    surfaceDim = Color(0xFF16130F),
    surfaceContainerLowest = Color(0xFF110E0B),
    surfaceContainerLow = Color(0xFF1E1A16),
    surfaceContainer = Color(0xFF221E19),
    surfaceContainerHigh = Color(0xFF2D2823),
    surfaceContainerHighest = Color(0xFF38322C),
)

@Composable
fun YomuMemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Android 12 以降の壁紙連動色。端末の個性を尊重しつつ、既定では自前の配色を使う。 */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = YomuMemoTypography,
        content = content,
    )
}
