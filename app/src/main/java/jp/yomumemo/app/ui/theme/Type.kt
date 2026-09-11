package jp.yomumemo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 日本語は欧文より行が詰まって見えるため、既定より行間を広めに取る。
 * 引用文を読む画面が主役なので、可読性を優先する。
 */
private val jaLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private val base = Typography()

val YomuMemoTypography = Typography(
    headlineMedium = base.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = jaLineHeight,
    ),
    titleLarge = base.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = jaLineHeight,
    ),
    titleMedium = base.titleMedium.copy(lineHeightStyle = jaLineHeight),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 28.sp,
        lineHeightStyle = jaLineHeight,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 24.sp,
        lineHeightStyle = jaLineHeight,
    ),
    bodySmall = base.bodySmall.copy(lineHeight = 20.sp, lineHeightStyle = jaLineHeight),
)
