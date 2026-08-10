from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_required(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise RuntimeError(f"Trecho esperado não encontrado em {path}: {old[:100]!r}")
    file.write_text(text.replace(old, new), encoding="utf-8")


# Version: a visual update should install as a real update over 1.5.3.
replace_required("app/build.gradle.kts", 'versionCode = 35', 'versionCode = 36')
replace_required("app/build.gradle.kts", 'versionName = "1.5.3"', 'versionName = "1.5.4"')

# Shape system: less uniformly rounded, with a larger radius reserved for true hero surfaces.
replace_required(
    "app/src/main/java/com/liftly/app/ui/theme/Theme.kt",
    '''private val LiftlyShapes = Shapes(\n    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),\n    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),\n    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),\n    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),\n    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),\n)''',
    '''private val LiftlyShapes = Shapes(\n    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),\n    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),\n    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),\n    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),\n    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),\n)''',
)
replace_required("app/src/main/java/com/liftly/app/ui/theme/Theme.kt", 'glow = LiftlyPurple.copy(alpha = 0.30f)', 'glow = LiftlyPurple.copy(alpha = 0.18f)')
replace_required("app/src/main/java/com/liftly/app/ui/theme/Theme.kt", 'glow = LiftlyPurple.copy(alpha = 0.14f)', 'glow = LiftlyPurple.copy(alpha = 0.08f)')
replace_required(
    "app/src/main/java/com/liftly/app/ui/theme/Theme.kt",
    'glow = color(primaryLong).copy(alpha = if (darkBackground) 0.42f else 0.18f)',
    'glow = color(primaryLong).copy(alpha = if (darkBackground) 0.24f else 0.10f)',
)

components = "app/src/main/java/com/liftly/app/ui/components/Components.kt"

# Ambient background remains part of the Liftly identity, but becomes atmospheric instead of dominant.
for old, new in [
    ('primary.copy(alpha = 0.15f)', 'primary.copy(alpha = 0.09f)'),
    ('glow.copy(alpha = 0.065f)', 'glow.copy(alpha = 0.035f)'),
    ('secondary.copy(alpha = 0.11f)', 'secondary.copy(alpha = 0.06f)'),
    ('tertiary.copy(alpha = 0.045f)', 'tertiary.copy(alpha = 0.025f)'),
    ('copy(alpha = 0.055f)', 'copy(alpha = 0.032f)'),
    ('glow.copy(alpha = 0.095f)', 'glow.copy(alpha = 0.050f)'),
    ('copy(alpha = 0.07f)', 'copy(alpha = 0.040f)'),
    ('alpha = 0.14f - distanceFromCenter * 0.055f', 'alpha = 0.080f - distanceFromCenter * 0.030f'),
    ('val alpha = 0.05f + (index % 4) * 0.012f', 'val alpha = 0.026f + (index % 4) * 0.007f'),
    ('primary.copy(alpha = 0.12f)', 'primary.copy(alpha = 0.07f)'),
    ('secondary.copy(alpha = 0.08f)', 'secondary.copy(alpha = 0.045f)'),
]:
    replace_required(components, old, new)

# Icon glow is now a selected-state accent only, and substantially softer.
replace_required(components, 'if (safeIntensity > 0f) {', 'if (safeIntensity > 0f && selected) {')
replace_required(
    components,
    'glowColor.copy(alpha = (if (selected) 0.40f else 0.18f) * safeIntensity.coerceAtMost(1f)),',
    'glowColor.copy(alpha = 0.16f * safeIntensity.coerceAtMost(1f)),',
)
replace_required(
    components,
    'glowColor.copy(alpha = 0.10f * safeIntensity.coerceAtMost(1.5f)),',
    'glowColor.copy(alpha = 0.04f * safeIntensity.coerceAtMost(1.5f)),',
)

# GlassCard becomes a restrained product surface instead of a permanent visual effect.
replace_required(components, 'elevation: androidx.compose.ui.unit.Dp = 6.dp,', 'elevation: androidx.compose.ui.unit.Dp = 1.dp,')
replace_required(components, 'colors.primary.copy(alpha = 0.62f),', 'colors.primary.copy(alpha = 0.20f),')
replace_required(components, 'colors.secondary.copy(alpha = 0.22f),', 'colors.secondary.copy(alpha = 0.10f),')
replace_required(components, 'colors.outlineVariant.copy(alpha = 0.50f),', 'colors.outlineVariant.copy(alpha = 0.42f),')
replace_required(components, 'tonalElevation = 1.dp,', 'tonalElevation = 0.dp,')

# Interactive cards retain tactile feedback but stop looking like animated concept mockups.
replace_required(components, 'elevation: androidx.compose.ui.unit.Dp = 7.dp,', 'elevation: androidx.compose.ui.unit.Dp = 1.dp,')
replace_required(components, 'targetValue = if (pressed && enabled) 0.976f else 1f', 'targetValue = if (pressed && enabled) 0.99f else 1f')
replace_required(components, 'indication = null,', 'indication = androidx.compose.foundation.LocalIndication.current,')

# Primary CTA keeps a subtle brand gradient, with much less artificial depth.
replace_required(components, '.shadow(if (enabled) 9.dp else 0.dp, shape, clip = false)', '.shadow(if (enabled) 2.dp else 0.dp, shape, clip = false)')
replace_required(
    components,
    'colors = listOf(colors.primary, colors.tertiary, colors.secondary),',
    'colors = listOf(colors.primary, colors.tertiary),',
)
replace_required(components, 'targetValue = if (pressed && enabled) 0.97f else 1f', 'targetValue = if (pressed && enabled) 0.985f else 1f')

# Navigation already has a selected indicator, so it does not need a second halo signal.
replace_required(
    "app/src/main/java/com/liftly/app/ui/LiftlyApp.kt",
    'intensity = if (selected) 0.55f else 0f,',
    'intensity = 0f,',
)
replace_required("app/src/main/java/com/liftly/app/ui/LiftlyApp.kt", 'shadowElevation = 8.dp,', 'shadowElevation = 4.dp,')
replace_required(
    "app/src/main/java/com/liftly/app/ui/LiftlyApp.kt",
    'MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),',
    'MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),',
)

# Onboarding becomes cleaner; brand motion and CTA are enough without an icon halo.
replace_required(
    "app/src/main/java/com/liftly/app/ui/screens/OnboardingScreen.kt",
    'NeonIcon(pages[index].icon, null, selected = true, intensity = 1.3f, size = 48.dp)',
    'NeonIcon(pages[index].icon, null, selected = true, intensity = 0f, size = 48.dp)',
)

# Release notes used by the repository and APK distribution.
patch_notes = ROOT / "docs/PATCH_NOTES_1.5.4.md"
patch_notes.write_text(
    """# Liftly 1.5.4\n\n## Refinamento visual\n\n- superfícies principais mais sóbrias, com bordas e elevação discretas;\n- redução do glow em ícones e remoção do halo da navegação principal;\n- fundo ambiental preservado com menor contraste, menos partículas e menos ruído;\n- botões principais com gradiente mais contido e profundidade reduzida;\n- feedback tátil dos cards mais sutil, com ripple nativo restaurado;\n- escala de shapes revisada para diferenciar componentes utilitários de momentos de destaque;\n- onboarding visualmente mais limpo;\n- nenhuma função de treino, histórico, Rewards, integrações ou dados foi removida.\n\nA direção da 1.5.4 privilegia hierarquia, conteúdo e consistência em vez de aplicar efeitos premium em toda a interface.\n""",
    encoding="utf-8",
)

readme = ROOT / "README.md"
readme_text = readme.read_text(encoding="utf-8")
readme_text = readme_text.replace("## Versão atual — 1.5.3", "## Versão atual — 1.5.4", 1)
readme_text = readme_text.replace("**versionName:** `1.5.3`", "**versionName:** `1.5.4`", 1)
readme_text = readme_text.replace("**versionCode:** `35`", "**versionCode:** `36`", 1)
readme_text = readme_text.replace(
    "## Novidades da 1.5.3",
    "## Novidades da 1.5.4\n\n- refinamento visual para reduzir excesso de glass, glow, sombra e gradientes;\n- superfícies utilitárias mais sóbrias e hierarquia visual mais clara;\n- fundo ambiental e animações mantidos com intensidade reduzida;\n- navegação e onboarding mais limpos, preservando a identidade roxa do Liftly.\n\nConsulte [Patch notes 1.5.4](docs/PATCH_NOTES_1.5.4.md).\n\n## Novidades da 1.5.3",
    1,
)
readme.write_text(readme_text, encoding="utf-8")

changelog = ROOT / "CHANGELOG.md"
if changelog.exists():
    text = changelog.read_text(encoding="utf-8")
    if "## 1.5.4" not in text:
        marker = "# Changelog\n"
        entry = "\n## 1.5.4 — 10/08/2026\n\n- refinamento visual com superfícies mais discretas e hierarquia mais clara;\n- redução de glow, sombras, gradientes e ruído do fundo ambiental;\n- feedback de toque mais nativo e menos ornamental;\n- versão Android atualizada para `versionCode 36` / `versionName 1.5.4`.\n"
        text = text.replace(marker, marker + entry, 1) if marker in text else entry + "\n" + text
        changelog.write_text(text, encoding="utf-8")

print("Passe visual Liftly 1.5.4 aplicado.")
