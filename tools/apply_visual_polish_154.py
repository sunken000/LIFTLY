from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Trecho esperado não encontrado em {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def ensure_version() -> None:
    file = ROOT / "app/build.gradle.kts"
    text = file.read_text(encoding="utf-8")
    if 'versionCode = 36' not in text:
        if 'versionCode = 35' not in text:
            raise RuntimeError("versionCode inesperado")
        text = text.replace('versionCode = 35', 'versionCode = 36', 1)
    if 'versionName = "1.5.4"' not in text:
        if 'versionName = "1.5.3"' not in text:
            raise RuntimeError("versionName inesperado")
        text = text.replace('versionName = "1.5.3"', 'versionName = "1.5.4"', 1)
    file.write_text(text, encoding="utf-8")


ensure_version()

# Shapes: utility components become tighter; 28dp is reserved for expressive/hero surfaces.
replace_once(
    "app/src/main/java/com/liftly/app/ui/theme/Theme.kt",
    '''private val LiftlyShapes = Shapes(\n    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),\n    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),\n    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),\n    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),\n    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),\n)''',
    '''private val LiftlyShapes = Shapes(\n    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),\n    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),\n    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),\n    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),\n    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),\n)''',
)

components = "app/src/main/java/com/liftly/app/ui/components/Components.kt"

# Ambient background: keep the Liftly signature, but reduce contrast and visual noise.
for old, new in [
    ('primary.copy(alpha = 0.15f)', 'primary.copy(alpha = 0.09f)'),
    ('glow.copy(alpha = 0.065f)', 'glow.copy(alpha = 0.035f)'),
    ('secondary.copy(alpha = 0.11f)', 'secondary.copy(alpha = 0.06f)'),
    ('tertiary.copy(alpha = 0.045f)', 'tertiary.copy(alpha = 0.025f)'),
    ('(if (band == 0) secondary else primary).copy(alpha = 0.055f),', '(if (band == 0) secondary else primary).copy(alpha = 0.034f),'),
    ('glow.copy(alpha = 0.095f)', 'glow.copy(alpha = 0.052f)'),
    ('(if (band == 0) primary else secondary).copy(alpha = 0.07f),', '(if (band == 0) primary else secondary).copy(alpha = 0.044f),'),
    ('alpha = 0.14f - distanceFromCenter * 0.055f', 'alpha = 0.085f - distanceFromCenter * 0.032f'),
    ('val alpha = 0.05f + (index % 4) * 0.012f', 'val alpha = 0.030f + (index % 4) * 0.007f'),
    ('primary.copy(alpha = 0.12f)', 'primary.copy(alpha = 0.07f)'),
    ('secondary.copy(alpha = 0.08f)', 'secondary.copy(alpha = 0.05f)'),
]:
    replace_once(components, old, new)

# Glow is a special selected-state accent, not a default decoration.
replace_once(components, 'if (safeIntensity > 0f) {', 'if (safeIntensity > 0f && selected) {')
replace_once(
    components,
    'glowColor.copy(alpha = (if (selected) 0.40f else 0.18f) * safeIntensity.coerceAtMost(1f)),',
    'glowColor.copy(alpha = 0.16f * safeIntensity.coerceAtMost(1f)),',
)
replace_once(
    components,
    'glowColor.copy(alpha = 0.10f * safeIntensity.coerceAtMost(1.5f)),',
    'glowColor.copy(alpha = 0.04f * safeIntensity.coerceAtMost(1.5f)),',
)

# GlassCard remains compatible with existing screens, but visually behaves like a restrained product surface.
replace_once(components, 'elevation: androidx.compose.ui.unit.Dp = 6.dp,', 'elevation: androidx.compose.ui.unit.Dp = 1.dp,')
replace_once(components, 'colors.primary.copy(alpha = 0.62f),', 'colors.primary.copy(alpha = 0.18f),')
replace_once(components, 'colors.secondary.copy(alpha = 0.22f),', 'colors.secondary.copy(alpha = 0.08f),')
replace_once(components, 'colors.outlineVariant.copy(alpha = 0.50f),', 'colors.outlineVariant.copy(alpha = 0.44f),')
replace_once(components, 'tonalElevation = 1.dp,', 'tonalElevation = 0.dp,')

# Click feedback becomes closer to native Android: smaller scale change and visible ripple.
replace_once(components, 'elevation: androidx.compose.ui.unit.Dp = 7.dp,', 'elevation: androidx.compose.ui.unit.Dp = 1.dp,')
replace_once(components, 'targetValue = if (pressed && enabled) 0.976f else 1f', 'targetValue = if (pressed && enabled) 0.99f else 1f')
replace_once(components, 'indication = null,', 'indication = androidx.compose.foundation.LocalIndication.current,')

# CTA remains branded but loses the concept-art depth.
replace_once(components, '.shadow(if (enabled) 9.dp else 0.dp, shape, clip = false)', '.shadow(if (enabled) 2.dp else 0.dp, shape, clip = false)')
replace_once(
    components,
    'colors = listOf(colors.primary, colors.tertiary, colors.secondary),',
    'colors = listOf(colors.primary, colors.tertiary),',
)
replace_once(components, 'targetValue = if (pressed && enabled) 0.97f else 1f', 'targetValue = if (pressed && enabled) 0.985f else 1f')

# Navigation already has a Material selected indicator; remove redundant halo/depth.
replace_once(
    "app/src/main/java/com/liftly/app/ui/LiftlyApp.kt",
    'intensity = if (selected) 0.55f else 0f,',
    'intensity = 0f,',
)
replace_once("app/src/main/java/com/liftly/app/ui/LiftlyApp.kt", 'shadowElevation = 8.dp,', 'shadowElevation = 4.dp,')
replace_once(
    "app/src/main/java/com/liftly/app/ui/LiftlyApp.kt",
    'MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),',
    'MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),',
)

# Onboarding: icon becomes graphic content, not another glowing CTA.
replace_once(
    "app/src/main/java/com/liftly/app/ui/screens/OnboardingScreen.kt",
    'NeonIcon(pages[index].icon, null, selected = true, intensity = 1.3f, size = 48.dp)',
    'NeonIcon(pages[index].icon, null, selected = true, intensity = 0f, size = 48.dp)',
)

# Ensure release notes describe the final design pass without duplicating existing 1.5.4 docs.
notes = ROOT / "docs/PATCH_NOTES_1.5.4.md"
if not notes.exists():
    notes.write_text(
        """# Liftly 1.5.4\n\n## Refinamento visual\n\n- paleta roxa menos saturada e superfícies mais sóbrias;\n- redução de glass, glow, sombras, gradientes e ruído ambiental;\n- ripple nativo restaurado nos cards interativos;\n- shapes com hierarquia mais clara;\n- navegação e onboarding mais limpos;\n- nenhuma função de treino, dados, Rewards ou integração foi removida.\n""",
        encoding="utf-8",
    )

print("Passe visual final Liftly 1.5.4 aplicado.")
