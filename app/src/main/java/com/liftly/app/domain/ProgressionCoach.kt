package com.liftly.app.domain

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** Resultado principal que a interface pode usar para cor, ícone e ação sugerida. */
enum class ProgressionStatus {
    INCREASE,
    KEEP,
    REDUCE,
    DELOAD,
    CAUTION,
}

/**
 * Um desempenho anterior do mesmo exercício, em ordem do mais recente para o mais antigo.
 *
 * [actualReps] deve representar a menor quantidade de repetições entre as séries de trabalho
 * comparáveis. Assim, atingir o topo da faixa significa que todas as séries o atingiram.
 */
data class HistoricalExercisePerformance(
    val actualReps: Int,
    val actualLoadKg: Double,
    val rir: Int? = null,
    val painLevel: Int = 0,
)

/** Uma série de trabalho concluída na sessão que está sendo avaliada. */
data class CurrentExerciseSetPerformance(
    val setNumber: Int,
    val reps: Int,
    val loadKg: Double,
    val rir: Int? = null,
    val painLevel: Int = 0,
)

/**
 * Entrada de uma sessão para um exercício. [rir] é a estimativa de repetições em reserva da
 * última série de trabalho. Dor significa dor durante o movimento, não esforço ou queimação
 * muscular esperada.
 */
data class ProgressionCoachInput(
    val exerciseName: String,
    val category: String,
    val plannedRepMin: Int,
    val plannedRepMax: Int,
    val plannedLoadKg: Double,
    val actualReps: Int,
    val actualLoadKg: Double,
    val rir: Int? = null,
    val painLevel: Int = 0,
    val recentPerformances: List<HistoricalExercisePerformance> = emptyList(),
    /**
     * Quando preenchidas, estas séries têm precedência total sobre os campos agregados
     * [actualReps], [actualLoadKg], [rir] e [painLevel]. O histórico nunca vira carga-base.
     */
    val currentSets: List<CurrentExerciseSetPerformance> = emptyList(),
)

data class ProgressionRecommendation(
    val status: ProgressionStatus,
    val title: String,
    val message: String,
    /** Nulo quando sugerir uma carga sem avaliação individual não é apropriado. */
    val suggestedLoadKg: Double?,
    /** Nulo junto com [suggestedRepMax] quando o treino deve ser interrompido e avaliado. */
    val suggestedRepMin: Int?,
    val suggestedRepMax: Int?,
    val reasons: List<String>,
)

data class ProgressionCoachConfig(
    val upperBodyLoadIncreaseFraction: Double = 0.025,
    val lowerBodyLoadIncreaseFraction: Double = 0.05,
    val highRirLoadIncreaseFraction: Double = 0.05,
    val reductionFraction: Double = 0.075,
    val deloadReductionFraction: Double = 0.125,
    val painReductionFraction: Double = 0.10,
    val loadStepKg: Double = 0.5,
    val comparableLoadToleranceFraction: Double = 0.05,
)

/**
 * Coach local e determinístico baseado em progressão dupla e autorregulação por RIR.
 *
 * Ele deliberadamente evita estimar 1RM, diagnosticar dor ou prometer uma velocidade de
 * progresso. Aumento de carga ocorre ao atingir o topo da faixa com margem (RIR >= 2) ou quando
 * todas as séries atingem o mínimo e a margem é claramente alta (RIR >= 4). Sem RIR, são exigidas
 * três exposições comparáveis no topo. Dor sempre tem prioridade, e deload exige falha repetida.
 * Os percentuais são passos heurísticos conservadores, não medidas de capacidade fisiológica.
 */
class ProgressionCoach(
    private val config: ProgressionCoachConfig = ProgressionCoachConfig(),
) {
    init {
        require(config.upperBodyLoadIncreaseFraction in 0.0..0.20)
        require(config.lowerBodyLoadIncreaseFraction in 0.0..0.20)
        require(config.highRirLoadIncreaseFraction in 0.0..0.20)
        require(config.reductionFraction in 0.0..0.30)
        require(config.deloadReductionFraction in 0.0..0.40)
        require(config.painReductionFraction in 0.0..0.40)
        require(config.loadStepKg > 0.0)
        require(config.comparableLoadToleranceFraction in 0.0..0.20)
    }

    fun recommend(input: ProgressionCoachInput): ProgressionRecommendation {
        validationError(input)?.let { error ->
            return ProgressionRecommendation(
                status = ProgressionStatus.CAUTION,
                title = "Revise os dados do exercício",
                message = "$error Corrija o registro antes de usar uma sugestão de progressão.",
                suggestedLoadKg = null,
                suggestedRepMin = null,
                suggestedRepMax = null,
                reasons = listOf("Dados inválidos impedem uma recomendação segura."),
            )
        }

        val current = currentPerformance(input)
        val workingLoad = current.loadKg.takeIf { it > 0.0 } ?: input.plannedLoadKg
        if (current.painLevel > 0) return painRecommendation(input, current, workingLoad)

        if (!input.category.supportsLoadProgression()) {
            return ProgressionRecommendation(
                status = ProgressionStatus.KEEP,
                title = "Mantenha e acompanhe o desempenho",
                message = "Este tipo de exercício pede progressão por tempo, distância, ritmo ou técnica; não há evidência suficiente para alterar a carga automaticamente.",
                suggestedLoadKg = workingLoad,
                suggestedRepMin = input.plannedRepMin,
                suggestedRepMax = input.plannedRepMax,
                reasons = listOf("Categoria informada: ${input.category}."),
            )
        }

        val comparableHistory = input.recentPerformances
            .filter(::isValidHistory)
            .filter { performance -> loadsAreComparable(workingLoad, performance.actualLoadKg) }
            .take(2)

        if (shouldDeload(input, current, comparableHistory)) {
            val suggestedLoad = reducedLoad(workingLoad, config.deloadReductionFraction)
            return ProgressionRecommendation(
                status = ProgressionStatus.DELOAD,
                title = "Considere um deload curto",
                message = "A meta ficou abaixo da faixa em três sessões comparáveis e com esforço alto. Teste uma redução temporária de carga e priorize recuperação e técnica.",
                suggestedLoadKg = suggestedLoad,
                suggestedRepMin = input.plannedRepMin,
                suggestedRepMax = input.plannedRepMax,
                reasons = listOf(
                    "Três exposições abaixo de ${input.plannedRepMin} repetições.",
                    "RIR entre 0 e 1 nas exposições consideradas.",
                    "Carga sugerida cerca de ${(config.deloadReductionFraction * 100).toInt()}% menor.",
                ),
            )
        }

        if (shouldIncrease(input, current, comparableHistory)) {
            if (workingLoad <= 0.0) {
                return ProgressionRecommendation(
                    status = ProgressionStatus.INCREASE,
                    title = "Avance a faixa de repetições",
                    message = "Você atingiu o topo da faixa com margem. Como não há carga externa registrada, avance uma repetição na faixa e preserve a técnica.",
                    suggestedLoadKg = 0.0,
                    suggestedRepMin = input.plannedRepMin + 1,
                    suggestedRepMax = input.plannedRepMax + 1,
                    reasons = increaseReasons(input, current),
                )
            }

            val fraction = when {
                current.rir != null && current.rir >= VERY_HIGH_RIR_THRESHOLD -> config.highRirLoadIncreaseFraction
                current.rir == HIGH_RIR_THRESHOLD -> config.upperBodyLoadIncreaseFraction
                input.exerciseName.isLikelyLowerBodyCompound() -> config.lowerBodyLoadIncreaseFraction
                else -> config.upperBodyLoadIncreaseFraction
            }
            return ProgressionRecommendation(
                status = ProgressionStatus.INCREASE,
                title = "Aumente a carga com um passo pequeno",
                message = if (current.rir != null && current.rir >= HIGH_RIR_THRESHOLD && current.reps < input.plannedRepMax) {
                    "A carga ficou leve para a faixa-alvo: todas as séries atingiram o mínimo com bastante margem. Faça um aumento pequeno."
                } else {
                    "Você dominou o topo da faixa. Aumente a carga e volte à parte inferior da faixa de repetições."
                },
                suggestedLoadKg = increasedLoad(workingLoad, fraction),
                suggestedRepMin = input.plannedRepMin,
                suggestedRepMax = input.plannedRepMax,
                reasons = increaseReasons(input, current),
            )
        }

        if (shouldReduce(input, current, comparableHistory)) {
            return ProgressionRecommendation(
                status = ProgressionStatus.REDUCE,
                title = "Reduza um pouco a carga",
                message = "A primeira série ficou no limite inferior da faixa ou abaixo dele, já muito perto da falha. Reduza a carga em um passo conservador para sustentar as próximas séries com técnica estável.",
                suggestedLoadKg = reducedLoad(workingLoad, config.reductionFraction),
                suggestedRepMin = input.plannedRepMin,
                suggestedRepMax = input.plannedRepMax,
                reasons = listOf(
                    "${current.setCount} série(s) atual(is) avaliadas com ${formatLoad(workingLoad)} kg.",
                    if (current.reps < input.plannedRepMin) {
                        "Resultado: ${current.reps} repetição(ões), abaixo da meta mínima de ${input.plannedRepMin}."
                    } else {
                        "Resultado no limite mínimo de ${input.plannedRepMin} repetição(ões), já perto da falha."
                    },
                    current.rir?.let { "Menor RIR das séries avaliadas: $it." }
                        ?: "A meta também não foi alcançada na sessão comparável anterior.",
                ),
            )
        }

        val atTopWithoutMargin = current.reps >= input.plannedRepMax && current.rir != null && current.rir < 2
        return ProgressionRecommendation(
            status = ProgressionStatus.KEEP,
            title = "Mantenha a prescrição atual",
            message = when {
                atTopWithoutMargin -> "Você chegou ao topo da faixa, mas muito perto da falha. Repita a carga até ganhar mais margem e consistência."
                current.reps < input.plannedRepMin && (current.rir ?: 2) >= 2 -> "Você encerrou abaixo da faixa apesar de ainda ter margem. Confirme a técnica e tente completar a faixa antes de alterar a carga."
                current.rir == null -> "O resultado está utilizável, mas informar o RIR em todas as séries torna a próxima sugestão mais confiável."
                else -> "O desempenho está dentro da faixa esperada. Consolide as repetições antes do próximo aumento."
            },
            suggestedLoadKg = workingLoad,
            suggestedRepMin = input.plannedRepMin,
            suggestedRepMax = input.plannedRepMax,
            reasons = buildList {
                add("${current.setCount} série(s) atual(is) avaliadas com ${formatLoad(workingLoad)} kg.")
                add("Menor resultado: ${current.reps} repetição(ões).")
                current.rir?.let { add("Menor RIR das séries: $it.") }
                add("Maior dor informada: ${current.painLevel}/10.")
            },
        )
    }

    private fun painRecommendation(
        input: ProgressionCoachInput,
        current: CurrentPerformance,
        workingLoad: Double,
    ): ProgressionRecommendation {
        if (current.painLevel >= HIGH_PAIN_THRESHOLD) {
            return ProgressionRecommendation(
                status = ProgressionStatus.CAUTION,
                title = "Interrompa este exercício",
                message = "Dor forte não deve ser usada como sinal de esforço produtivo. Não há sugestão automática de carga; procure avaliação de um profissional qualificado antes de repetir o movimento.",
                suggestedLoadKg = null,
                suggestedRepMin = null,
                suggestedRepMax = null,
                reasons = listOf("Maior dor informada nas séries: ${current.painLevel}/10."),
            )
        }

        if (current.painLevel < MODERATE_PAIN_THRESHOLD) {
            return ProgressionRecommendation(
                status = ProgressionStatus.CAUTION,
                title = "Mantenha a carga e observe a dor",
                message = "Não progrida nesta sessão. Mantenha a carga somente se o movimento estiver confortável e interrompa se a dor persistir ou aumentar.",
                suggestedLoadKg = workingLoad,
                suggestedRepMin = input.plannedRepMin,
                suggestedRepMax = input.plannedRepMax,
                reasons = listOf(
                    "Maior dor informada nas séries: ${current.painLevel}/10.",
                    "Dor leve bloqueia o aumento automático de carga.",
                ),
            )
        }

        return ProgressionRecommendation(
            status = ProgressionStatus.REDUCE,
            title = "Reduza a carga nas próximas séries",
            message = "A dor moderada tem prioridade sobre a progressão. Reduza a carga, revise amplitude e técnica e interrompa se a dor persistir ou aumentar.",
            suggestedLoadKg = reducedLoad(workingLoad, config.painReductionFraction),
            suggestedRepMin = input.plannedRepMin,
            suggestedRepMax = input.plannedRepMax,
            reasons = listOf(
                "Maior dor informada nas séries: ${current.painLevel}/10.",
                "A segurança tem prioridade sobre a progressão de carga.",
            ),
        )
    }

    private fun shouldDeload(
        input: ProgressionCoachInput,
        current: CurrentPerformance,
        comparableHistory: List<HistoricalExercisePerformance>,
    ): Boolean = current.reps < input.plannedRepMin &&
        current.rir != null && current.rir <= LOW_RIR_THRESHOLD &&
        comparableHistory.size >= 2 &&
        comparableHistory.all { performance ->
            performance.actualReps < input.plannedRepMin &&
                performance.rir != null && performance.rir <= LOW_RIR_THRESHOLD &&
                performance.painLevel == 0
        }

    private fun shouldIncrease(
        input: ProgressionCoachInput,
        current: CurrentPerformance,
        comparableHistory: List<HistoricalExercisePerformance>,
    ): Boolean {
        if (current.reps < input.plannedRepMin) return false
        if (current.rir != null) {
            val reachedTopWithMargin = current.reps >= input.plannedRepMax && current.rir >= MIN_RIR_FOR_TOP_RANGE_INCREASE
            val clearlyUnderTargetEffort = current.rir >= HIGH_RIR_THRESHOLD
            return reachedTopWithMargin || clearlyUnderTargetEffort
        }
        if (current.reps < input.plannedRepMax) return false
        return comparableHistory.size >= 2 && comparableHistory.all { performance ->
            performance.actualReps >= input.plannedRepMax && performance.painLevel == 0
        }
    }

    private fun shouldReduce(
        input: ProgressionCoachInput,
        current: CurrentPerformance,
        comparableHistory: List<HistoricalExercisePerformance>,
    ): Boolean {
        if (current.rir != null) {
            // Chegar somente ao limite inferior já perto da falha indica que a carga está alta
            // para repetir a faixa nas séries seguintes. Acima do mínimo, a progressão dupla
            // preserva a carga até consolidar repetições.
            return current.rir <= LOW_RIR_THRESHOLD && current.reps <= input.plannedRepMin
        }
        if (current.reps >= input.plannedRepMin) return false
        return comparableHistory.firstOrNull()?.actualReps?.let { it < input.plannedRepMin } == true
    }

    private fun increaseReasons(
        input: ProgressionCoachInput,
        current: CurrentPerformance,
    ): List<String> = buildList {
        add("${current.setCount} série(s) atual(is) avaliada(s); a última série orienta a próxima.")
        add("Última série: ${current.reps} repetição(ões) com ${formatLoad(current.loadKg)} kg.")
        if (current.rir != null) {
            add("Menor RIR das séries: ${current.rir}.")
            add("Maior dor informada: ${current.painLevel}/10.")
            if (current.rir >= HIGH_RIR_THRESHOLD && current.reps < input.plannedRepMax) {
                add("Todas as séries atingiram o mínimo da faixa com margem alta.")
            } else {
                add("Topo da faixa de ${input.plannedRepMax} repetições alcançado com margem.")
            }
        } else {
            add("Topo da faixa repetido em três sessões com cargas comparáveis.")
        }
    }

    private fun validationError(input: ProgressionCoachInput): String? = when {
        input.exerciseName.isBlank() -> "O nome do exercício está vazio."
        input.category.isBlank() -> "A categoria do exercício está vazia."
        input.plannedRepMin <= 0 -> "A repetição mínima precisa ser maior que zero."
        input.plannedRepMax < input.plannedRepMin -> "A faixa de repetições planejada está invertida."
        !input.plannedLoadKg.isFinite() || input.plannedLoadKg < 0.0 -> "A carga planejada é inválida."
        input.currentSets.isNotEmpty() -> input.currentSets.firstNotNullOfOrNull(::setValidationError)
        input.actualReps < 0 -> "As repetições realizadas são inválidas."
        !input.actualLoadKg.isFinite() || input.actualLoadKg < 0.0 -> "A carga realizada é inválida."
        input.rir != null && input.rir !in 0..10 -> "O RIR precisa ficar entre 0 e 10."
        input.painLevel !in 0..10 -> "A dor precisa ficar entre 0 e 10."
        else -> null
    }

    private fun setValidationError(set: CurrentExerciseSetPerformance): String? = when {
        set.setNumber <= 0 -> "O número da série precisa ser maior que zero."
        set.reps < 0 -> "As repetições da série ${set.setNumber} são inválidas."
        !set.loadKg.isFinite() || set.loadKg < 0.0 -> "A carga da série ${set.setNumber} é inválida."
        set.rir != null && set.rir !in 0..10 -> "O RIR da série ${set.setNumber} precisa ficar entre 0 e 10."
        set.painLevel !in 0..10 -> "A dor da série ${set.setNumber} precisa ficar entre 0 e 10."
        else -> null
    }

    private fun currentPerformance(input: ProgressionCoachInput): CurrentPerformance {
        if (input.currentSets.isEmpty()) {
            return CurrentPerformance(
                reps = input.actualReps,
                loadKg = input.actualLoadKg,
                rir = input.rir,
                painLevel = input.painLevel,
                setCount = 1,
            )
        }

        val ordered = input.currentSets.sortedBy(CurrentExerciseSetPerformance::setNumber)
        // A orientação é para a próxima série, portanto a última série avaliada é a única base
        // coerente de carga, repetições e RIR. Combinar o menor valor de cada coluna criava uma
        // série fictícia (por exemplo 60 kg de uma série com as reps de outra feita a 70 kg).
        val latest = ordered.last()
        return CurrentPerformance(
            reps = latest.reps,
            loadKg = latest.loadKg,
            rir = latest.rir,
            // Dor tem prioridade mesmo que tenha surgido em uma série anterior.
            painLevel = ordered.maxOf(CurrentExerciseSetPerformance::painLevel),
            setCount = ordered.size,
        )
    }

    private fun isValidHistory(performance: HistoricalExercisePerformance): Boolean =
        performance.actualReps >= 0 &&
            performance.actualLoadKg.isFinite() && performance.actualLoadKg >= 0.0 &&
            (performance.rir == null || performance.rir in 0..10) &&
            performance.painLevel in 0..10

    private fun loadsAreComparable(first: Double, second: Double): Boolean {
        if (first == 0.0 || second == 0.0) return first == second
        val denominator = max(first, second)
        return kotlin.math.abs(first - second) / denominator <= config.comparableLoadToleranceFraction
    }

    private fun increasedLoad(loadKg: Double, fraction: Double): Double {
        if (loadKg <= 0.0) return 0.0
        val raw = max(loadKg * (1.0 + fraction), loadKg + config.loadStepKg)
        return ceil(raw / config.loadStepKg - ROUNDING_EPSILON) * config.loadStepKg
    }

    private fun reducedLoad(loadKg: Double, fraction: Double): Double {
        if (loadKg <= 0.0) return 0.0
        val raw = (loadKg * (1.0 - fraction)).coerceAtLeast(0.0)
        val rounded = floor(raw / config.loadStepKg + ROUNDING_EPSILON) * config.loadStepKg
        return if (rounded >= loadKg) (loadKg - config.loadStepKg).coerceAtLeast(0.0) else rounded
    }

    private fun formatLoad(loadKg: Double): String = if (loadKg % 1.0 == 0.0) {
        loadKg.toInt().toString()
    } else {
        "%.1f".format(Locale.ROOT, loadKg)
    }

    private fun String.supportsLoadProgression(): Boolean {
        val value = normalized()
        return LOAD_PROGRESSION_CATEGORIES.any(value::contains)
    }

    private fun String.isLikelyLowerBodyCompound(): Boolean {
        val value = normalized()
        return LOWER_BODY_COMPOUND_NAMES.any(value::contains)
    }

    private fun String.normalized(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .trim()

    private companion object {
        const val ROUNDING_EPSILON = 1e-9
        const val LOW_RIR_THRESHOLD = 1
        const val MIN_RIR_FOR_TOP_RANGE_INCREASE = 2
        const val HIGH_RIR_THRESHOLD = 4
        const val VERY_HIGH_RIR_THRESHOLD = 5
        const val MODERATE_PAIN_THRESHOLD = 3
        const val HIGH_PAIN_THRESHOLD = 6

        val LOAD_PROGRESSION_CATEGORIES = listOf(
            "musculacao",
            "forca",
            "resistencia",
            "peso corporal",
            "funcional",
        )

        val LOWER_BODY_COMPOUND_NAMES = listOf(
            "agachamento",
            "levantamento terra",
            "terra romeno",
            "stiff",
            "leg press",
            "hack",
        )
    }

    private data class CurrentPerformance(
        val reps: Int,
        val loadKg: Double,
        val rir: Int?,
        val painLevel: Int,
        val setCount: Int,
    )
}

const val PROGRESSION_COACH_DISCLAIMER: String =
    "Sugestões gerais de treino não substituem acompanhamento de educação física ou avaliação de saúde. " +
        "Interrompa o exercício diante de dor forte, súbita ou crescente."
