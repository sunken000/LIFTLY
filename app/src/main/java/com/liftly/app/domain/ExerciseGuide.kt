package com.liftly.app.domain

import com.liftly.app.data.ExerciseEntity
import com.liftly.app.data.ExerciseVisualKey
import com.liftly.app.data.ExerciseVisualResolver
import java.text.Normalizer
import java.util.Locale

/** Structured technique content derived from the stable exercise catalog metadata. */
data class ExerciseGuide(
    val exerciseId: String,
    val movementFamily: ExerciseMovementFamily,
    val steps: List<String>,
    val primaryMuscle: String,
    val secondaryMuscles: List<String>,
    val commonMistakes: List<String>,
    val postureTips: List<String>,
)

enum class ExerciseMovementFamily(val label: String) {
    HORIZONTAL_PUSH("Empurrada horizontal"),
    VERTICAL_PUSH("Empurrada vertical"),
    HORIZONTAL_PULL("Puxada horizontal"),
    VERTICAL_PULL("Puxada vertical"),
    SQUAT("Agachamento"),
    HIP_HINGE("Dobradiça de quadril"),
    SINGLE_LEG("Movimento unilateral"),
    ARMS("Braços"),
    SHOULDERS("Ombros"),
    CORE("Estabilidade do core"),
    CARDIO("Cardiorrespiratório"),
    MOBILITY("Mobilidade"),
    PLYOMETRIC("Potência e salto"),
    OLYMPIC_LIFT("Levantamento olímpico"),
    FULL_BODY("Corpo inteiro"),
    GENERAL_STRENGTH("Força geral"),
}

/**
 * Keeps the library complete without duplicating 263 large guide objects. The exercise-specific
 * instruction and caution remain the source of truth; movement templates add concise setup,
 * posture and error cues that are shared only when the movement mechanics are compatible.
 */
object ExerciseGuideResolver {
    fun resolve(exercise: ExerciseEntity): ExerciseGuide {
        val family = ExerciseVisualResolver.fallbackKeyFor(exercise).toMovementFamily()
        val template = templates.getValue(family)
        val instructionSteps = exercise.instructions
            .toSentences()
            .ifEmpty { listOf(template.execution) }

        return ExerciseGuide(
            exerciseId = exercise.id,
            movementFamily = family,
            steps = (listOf(template.setup) + instructionSteps + template.finish)
                .distinctMeaningfully()
                .take(MAX_GUIDE_ITEMS),
            primaryMuscle = exercise.muscleGroup.ifBlank { "Corpo inteiro" },
            secondaryMuscles = exercise.secondaryMuscles
                .split(',', '/', ';')
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinctMeaningfully(),
            commonMistakes = (exercise.cautions.toSentences() + template.commonMistakes)
                .distinctMeaningfully()
                .take(MAX_GUIDE_ITEMS),
            postureTips = template.postureTips.distinctMeaningfully().take(MAX_GUIDE_ITEMS),
        )
    }

    private fun ExerciseVisualKey.toMovementFamily(): ExerciseMovementFamily = when (this) {
        ExerciseVisualKey.HORIZONTAL_PUSH -> ExerciseMovementFamily.HORIZONTAL_PUSH
        ExerciseVisualKey.VERTICAL_PUSH -> ExerciseMovementFamily.VERTICAL_PUSH
        ExerciseVisualKey.HORIZONTAL_PULL -> ExerciseMovementFamily.HORIZONTAL_PULL
        ExerciseVisualKey.VERTICAL_PULL -> ExerciseMovementFamily.VERTICAL_PULL
        ExerciseVisualKey.SQUAT -> ExerciseMovementFamily.SQUAT
        ExerciseVisualKey.HIP_HINGE -> ExerciseMovementFamily.HIP_HINGE
        ExerciseVisualKey.SINGLE_LEG -> ExerciseMovementFamily.SINGLE_LEG
        ExerciseVisualKey.ARMS -> ExerciseMovementFamily.ARMS
        ExerciseVisualKey.SHOULDERS -> ExerciseMovementFamily.SHOULDERS
        ExerciseVisualKey.CORE -> ExerciseMovementFamily.CORE
        ExerciseVisualKey.CARDIO -> ExerciseMovementFamily.CARDIO
        ExerciseVisualKey.MOBILITY -> ExerciseMovementFamily.MOBILITY
        ExerciseVisualKey.PLYOMETRIC -> ExerciseMovementFamily.PLYOMETRIC
        ExerciseVisualKey.OLYMPIC_LIFT -> ExerciseMovementFamily.OLYMPIC_LIFT
        ExerciseVisualKey.FULL_BODY -> ExerciseMovementFamily.FULL_BODY
        ExerciseVisualKey.GENERIC_STRENGTH -> ExerciseMovementFamily.GENERAL_STRENGTH
    }

    private data class GuideTemplate(
        val setup: String,
        val execution: String,
        val finish: String,
        val commonMistakes: List<String>,
        val postureTips: List<String>,
    )

    private val templates = mapOf(
        ExerciseMovementFamily.HORIZONTAL_PUSH to GuideTemplate(
            setup = "Organize os apoios e estabilize ombros e escápulas antes de iniciar.",
            execution = "Empurre em uma trajetória confortável, sem perder o controle do tronco.",
            finish = "Retorne devagar até a amplitude que você consegue controlar.",
            commonMistakes = listOf("Abrir os cotovelos além da posição confortável.", "Perder a estabilidade dos ombros durante a volta."),
            postureTips = listOf("Mantenha punhos e antebraços alinhados.", "Conserve os apoios firmes durante toda a repetição."),
        ),
        ExerciseMovementFamily.VERTICAL_PUSH to GuideTemplate(
            setup = "Estabilize tronco e pelve antes de levar a carga acima da cabeça.",
            execution = "Empurre verticalmente mantendo a carga sob controle.",
            finish = "Desça sem relaxar os ombros ou alterar a posição do tronco.",
            commonMistakes = listOf("Compensar arqueando excessivamente a lombar.", "Encolher os ombros sem controle."),
            postureTips = listOf("Mantenha costelas e pelve organizadas.", "Conduza os cotovelos em uma trajetória confortável."),
        ),
        ExerciseMovementFamily.HORIZONTAL_PULL to GuideTemplate(
            setup = "Ajuste os apoios e mantenha a coluna estável antes de puxar.",
            execution = "Conduza os cotovelos para trás sem transformar o movimento em balanço.",
            finish = "Estenda os braços com controle, preservando a posição dos ombros.",
            commonMistakes = listOf("Usar impulso do tronco para mover a carga.", "Elevar os ombros em direção às orelhas."),
            postureTips = listOf("Mantenha pescoço e coluna alinhados.", "Aproxime as escápulas sem exagerar a extensão do tronco."),
        ),
        ExerciseMovementFamily.VERTICAL_PULL to GuideTemplate(
            setup = "Firme a pegada e mantenha os ombros ativos antes da puxada.",
            execution = "Leve os cotovelos para baixo com o tronco estável.",
            finish = "Retorne até alongar com controle, sem soltar a carga de uma vez.",
            commonMistakes = listOf("Puxar usando balanço do corpo.", "Perder o controle dos ombros no final da volta."),
            postureTips = listOf("Mantenha o peito confortável e o pescoço neutro.", "Use uma pegada que preserve punhos e cotovelos alinhados."),
        ),
        ExerciseMovementFamily.SQUAT to GuideTemplate(
            setup = "Distribua os pés de forma estável e organize joelhos, quadril e tronco.",
            execution = "Desça mantendo equilíbrio sobre os pés e suba empurrando o chão.",
            finish = "Finalize em pé sem projetar o quadril ou travar os joelhos com impacto.",
            commonMistakes = listOf("Perder o contato estável dos pés com o chão.", "Deixar joelhos ou tronco perderem a trajetória controlada."),
            postureTips = listOf("Mantenha os joelhos acompanhando a direção dos pés.", "Use a amplitude em que a coluna permanece organizada."),
        ),
        ExerciseMovementFamily.HIP_HINGE to GuideTemplate(
            setup = "Crie pressão nos pés e leve o quadril para trás com a coluna neutra.",
            execution = "Estenda o quadril mantendo a carga próxima e o tronco firme.",
            finish = "Retorne pelo quadril sem arredondar a coluna ou perder os apoios.",
            commonMistakes = listOf("Transformar a dobradiça em agachamento.", "Afastar a carga do corpo e perder a posição da coluna."),
            postureTips = listOf("Mantenha cabeça, tronco e pelve alinhados.", "Sinta a pressão distribuída pelo pé inteiro."),
        ),
        ExerciseMovementFamily.SINGLE_LEG to GuideTemplate(
            setup = "Estabeleça um apoio firme e alinhe pé, joelho e quadril.",
            execution = "Mova-se com controle sem transferir o esforço para impulso.",
            finish = "Retorne ao equilíbrio antes de iniciar a próxima repetição.",
            commonMistakes = listOf("Perder o equilíbrio e acelerar a repetição.", "Deixar o joelho escapar da direção do pé."),
            postureTips = listOf("Mantenha o pé de apoio totalmente estável.", "Use o tronco para equilibrar, sem girá-lo."),
        ),
        ExerciseMovementFamily.ARMS to GuideTemplate(
            setup = "Ajuste a pegada e estabilize cotovelos e ombros.",
            execution = "Mova o antebraço sem transformar a repetição em balanço do corpo.",
            finish = "Retorne até a amplitude controlável, mantendo tensão e alinhamento.",
            commonMistakes = listOf("Deslocar os cotovelos para criar impulso.", "Dobrar os punhos para compensar a carga."),
            postureTips = listOf("Mantenha os punhos neutros.", "Conserve ombros baixos e tronco firme."),
        ),
        ExerciseMovementFamily.SHOULDERS to GuideTemplate(
            setup = "Organize as escápulas e escolha uma amplitude confortável para os ombros.",
            execution = "Mova os braços com controle, sem usar impulso do tronco.",
            finish = "Retorne devagar sem deixar a carga puxar a articulação.",
            commonMistakes = listOf("Elevar os ombros sem necessidade.", "Usar carga que reduz o controle da trajetória."),
            postureTips = listOf("Mantenha pescoço relaxado e punhos neutros.", "Trabalhe dentro de uma amplitude sem desconforto."),
        ),
        ExerciseMovementFamily.CORE to GuideTemplate(
            setup = "Organize a respiração e estabilize costelas e pelve.",
            execution = "Mantenha o tronco firme enquanto executa somente o movimento proposto.",
            finish = "Encerre a série antes de perder o alinhamento ou prender a respiração.",
            commonMistakes = listOf("Compensar com a região lombar.", "Prender a respiração durante toda a série."),
            postureTips = listOf("Respire sem perder a tensão abdominal.", "Mantenha pescoço e lombar em posição confortável."),
        ),
        ExerciseMovementFamily.CARDIO to GuideTemplate(
            setup = "Ajuste o equipamento e comece em intensidade confortável.",
            execution = "Sustente um ritmo compatível com a duração planejada.",
            finish = "Reduza o ritmo gradualmente antes de parar.",
            commonMistakes = listOf("Começar acima de um ritmo sustentável.", "Perder a postura para manter velocidade ou resistência."),
            postureTips = listOf("Mantenha respiração ritmada e ombros relaxados.", "Use uma passada ou cadência natural e controlada."),
        ),
        ExerciseMovementFamily.MOBILITY to GuideTemplate(
            setup = "Entre na posição gradualmente e mantenha a respiração calma.",
            execution = "Explore somente a amplitude confortável, sem movimentos bruscos.",
            finish = "Saia da posição devagar e observe se os dois lados respondem de forma semelhante.",
            commonMistakes = listOf("Forçar a articulação além da amplitude confortável.", "Usar impulso para alcançar uma posição maior."),
            postureTips = listOf("Relaxe áreas que não participam do movimento.", "Prefira amplitude controlada a amplitude máxima."),
        ),
        ExerciseMovementFamily.PLYOMETRIC to GuideTemplate(
            setup = "Prepare uma área livre e adote uma base estável antes do salto.",
            execution = "Produza força rapidamente e aterrisse absorvendo o impacto.",
            finish = "Recupere equilíbrio e postura antes da próxima repetição.",
            commonMistakes = listOf("Repetir o salto sem estabilizar a aterrissagem.", "Aumentar altura ou velocidade antes de dominar o controle."),
            postureTips = listOf("Aterrisse com joelhos acompanhando os pés.", "Mantenha o tronco organizado ao absorver o impacto."),
        ),
        ExerciseMovementFamily.OLYMPIC_LIFT to GuideTemplate(
            setup = "Posicione pés, pegada e barra antes de iniciar a sequência.",
            execution = "Acelere a carga mantendo-a próxima do corpo e receba com estabilidade.",
            finish = "Estabilize completamente antes de baixar ou soltar a carga em local permitido.",
            commonMistakes = listOf("Afastar a barra do corpo.", "Aumentar a carga antes de repetir a técnica com consistência."),
            postureTips = listOf("Mantenha pressão equilibrada nos pés.", "Preserve a trajetória da barra e a posição da coluna."),
        ),
        ExerciseMovementFamily.FULL_BODY to GuideTemplate(
            setup = "Organize os apoios e ensaie a sequência sem pressa.",
            execution = "Coordene pernas, tronco e braços mantendo uma trajetória contínua.",
            finish = "Conclua em posição estável antes de reiniciar.",
            commonMistakes = listOf("Acelerar antes de dominar a sequência.", "Perder o alinhamento ao transferir força entre segmentos."),
            postureTips = listOf("Mantenha a carga próxima e o centro de massa equilibrado.", "Respire de acordo com as fases do movimento."),
        ),
        ExerciseMovementFamily.GENERAL_STRENGTH to GuideTemplate(
            setup = "Ajuste o equipamento e adote uma posição inicial estável.",
            execution = "Execute a repetição em trajetória controlada e confortável.",
            finish = "Retorne devagar, sem deixar o equipamento conduzir o movimento.",
            commonMistakes = listOf("Usar impulso para completar a repetição.", "Escolher carga que compromete a amplitude e o controle."),
            postureTips = listOf("Mantenha as articulações alinhadas com a trajetória.", "Conserve respiração e apoios estáveis."),
        ),
    )

    private fun String.toSentences(): List<String> = trim()
        .split(SENTENCE_BOUNDARY)
        .map { it.trim().trimEnd('.', ';', ':').trim() }
        .filter(String::isNotBlank)
        .map { "$it." }

    private fun <T : CharSequence> Iterable<T>.distinctMeaningfully(): List<String> {
        val seen = mutableSetOf<String>()
        return map(CharSequence::toString).filter { seen.add(it.normalizedKey()) }
    }

    private fun String.normalizedKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .trim()

    private val SENTENCE_BOUNDARY = "(?<=[.!?])\\s+".toRegex()
    private val COMBINING_MARKS = "\\p{M}+".toRegex()
    private val NON_ALPHANUMERIC = "[^a-z0-9]+".toRegex()
    private const val MAX_GUIDE_ITEMS = 4
}
