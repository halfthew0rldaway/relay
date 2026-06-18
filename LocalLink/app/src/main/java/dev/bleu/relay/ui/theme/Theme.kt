package dev.bleu.relay.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warna desain "The Logical Architect"
val Primary = Color(0xFF00488D)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF005FB8)
val OnPrimaryContainer = Color(0xFFCADCFF)
val Secondary = Color(0xFF4A5F83)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFC0D5FF)
val OnSecondaryContainer = Color(0xFF475C7F)
val Tertiary = Color(0xFF7B3200)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFA04401)
val OnTertiaryContainer = Color(0xFFFFD1BC)
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)
val Background = Color(0xFFFDF8FD)
val OnBackground = Color(0xFF1C1B1F)
val Surface = Color(0xFFFDF8FD)
val OnSurface = Color(0xFF1C1B1F)
val SurfaceVariant = Color(0xFFE5E1E7)
val OnSurfaceVariant = Color(0xFF424752)
val Outline = Color(0xFF727783)
val OutlineVariant = Color(0xFFC2C6D4)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF7F2F8)
val SurfaceContainer = Color(0xFFF1ECF2)
val SurfaceContainerHigh = Color(0xFFEBE7EC)
val SurfaceContainerHighest = Color(0xFFE5E1E7)
val InverseSurface = Color(0xFF313034)
val InverseOnSurface = Color(0xFFF4EFF5)
val InversePrimary = Color(0xFFA8C8FF)
val SurfaceTint = Color(0xFF005DB5)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
    surfaceTint = SurfaceTint,
)

@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
