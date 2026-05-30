package vn.delfi.xcloudwms.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightScannerColorScheme = lightColorScheme(
    primary = ScannerBlue,
    onPrimary = ScannerSurface,
    primaryContainer = ScannerBlue.copy(alpha = 0.12f),
    onPrimaryContainer = ScannerText,
    secondary = ScannerSuccess,
    onSecondary = ScannerSurface,
    tertiary = ScannerWarning,
    onTertiary = ScannerSurface,
    background = ScannerPaper,
    onBackground = ScannerText,
    surface = ScannerSurface,
    onSurface = ScannerText,
    surfaceVariant = ScannerSurfaceMuted,
    onSurfaceVariant = ScannerMutedText,
    outline = ScannerBorder,
    outlineVariant = ScannerBorder.copy(alpha = 0.8f),
    error = ScannerError,
    onError = ScannerSurface,
)

private val ScannerShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun XcloudWmsTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightScannerColorScheme,
        typography = ScannerTypography,
        shapes = ScannerShapes,
        content = content,
    )
}
