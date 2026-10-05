package net.kigawa.kalender.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import kalender.app.generated.resources.Res
import kalender.app.generated.resources.noto_sans_jp
import org.jetbrains.compose.resources.Font

/** 日本語(CJK)を含む全プラットフォーム共通の書体。Skiaレンダラはシステムフォントを持たないためバンドルが必須 */
@Composable
fun kalenderTypography(): Typography {
    val notoSansJpFont = Font(Res.font.noto_sans_jp)
    return remember(notoSansJpFont) {
        val notoSansJp = FontFamily(notoSansJpFont)
        val base = Typography()
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = notoSansJp),
            displayMedium = base.displayMedium.copy(fontFamily = notoSansJp),
            displaySmall = base.displaySmall.copy(fontFamily = notoSansJp),
            headlineLarge = base.headlineLarge.copy(fontFamily = notoSansJp),
            headlineMedium = base.headlineMedium.copy(fontFamily = notoSansJp),
            headlineSmall = base.headlineSmall.copy(fontFamily = notoSansJp),
            titleLarge = base.titleLarge.copy(fontFamily = notoSansJp),
            titleMedium = base.titleMedium.copy(fontFamily = notoSansJp),
            titleSmall = base.titleSmall.copy(fontFamily = notoSansJp),
            bodyLarge = base.bodyLarge.copy(fontFamily = notoSansJp),
            bodyMedium = base.bodyMedium.copy(fontFamily = notoSansJp),
            bodySmall = base.bodySmall.copy(fontFamily = notoSansJp),
            labelLarge = base.labelLarge.copy(fontFamily = notoSansJp),
            labelMedium = base.labelMedium.copy(fontFamily = notoSansJp),
            labelSmall = base.labelSmall.copy(fontFamily = notoSansJp),
        )
    }
}
