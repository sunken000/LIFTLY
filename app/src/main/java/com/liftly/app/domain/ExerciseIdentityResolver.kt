package com.liftly.app.domain

import java.text.Normalizer
import java.util.Locale

data class ExerciseIdentity(
    val normalizedName: String,
    val canonicalKey: String,
    val movementPattern: String,
    val family: String,
    val tags: Set<String>,
    val isCompound: Boolean,
)

/**
 * Classificação tolerante a variações de escrita. O nome inteiro e todos os metadados são
 * avaliados e podem produzir várias tags; a identificação não para na primeira palavra-chave.
 */
object ExerciseIdentityResolver {
    fun resolve(
        name: String,
        movementType: String = "",
        muscleGroup: String = "",
        secondaryMuscles: String = "",
        equipment: String = "",
        category: String = "",
    ): ExerciseIdentity {
        val normalizedName = normalize(name)
        val movement = normalize(movementType)
        val primary = normalize(muscleGroup)
        val secondary = normalize(secondaryMuscles)
        val gear = normalize(equipment)
        val categoryText = normalize(category)
        val corpus = listOf(normalizedName, movement, primary, secondary, gear, categoryText).joinToString(" ")
        val tags = linkedSetOf<String>()

        fun tag(value: String, vararg aliases: String) {
            if (aliases.any { corpus.contains(it) }) tags += value
        }

        tag("supino", "supino", "bench press", "chest press")
        tag("inclinado", "inclinad", "incline")
        tag("declinado", "declinad", "decline")
        tag("agachamento", "agach", "squat")
        tag("bulgaro", "bulgar", "bulgarian")
        tag("unilateral", "unilateral", "uma perna", "single leg", "afundo", "passada", "bulgar")
        tag("hack", "hack")
        tag("leg_press", "leg press", "legpress")
        tag("terra", "levantamento terra", "deadlift", "terra ")
        tag("romeno", "romeno", "rdl", "stiff")
        tag("remada", "remada", "row")
        tag("puxada", "puxada", "pulldown", "barra fixa", "pull up", "pull-up")
        tag("desenvolvimento", "desenvolvimento", "shoulder press", "overhead press", "militar")
        tag("elevacao_lateral", "elevacao lateral", "lateral raise")
        tag("rosca", "rosca", "curl")
        tag("triceps", "triceps", "tríceps", "pushdown")
        tag("extensao_joelho", "extensao de joelho", "cadeira extensora", "leg extension")
        tag("flexao_joelho", "flexao de joelho", "mesa flexora", "cadeira flexora", "leg curl")
        tag("panturrilha", "panturr", "calf")
        tag("core", "core", "abdominal", "prancha", "plank", "anti-rot", "anti rot")

        tag("halteres", "halter", "dumbbell", " dumbell", " db ")
        tag("barra", "barra", "barbell")
        tag("smith", "smith")
        tag("maquina", "maquina", "machine")
        tag("cabo", "cabo", "polia", "cable")
        tag("peso_corporal", "peso corporal", "bodyweight")

        tag("peitoral", "peitoral", "peito", "chest", "supino")
        tag("quadriceps", "quadriceps", "quadríceps", "quad", "agach", "hack", "leg press", "extensora")
        tag("gluteos", "glute", "glúte", "bulgar", "afundo", "hip thrust", "elevacao pelvica")
        tag("posterior_coxa", "posterior", "isqui", "hamstring", "romeno", "stiff", "flexora")
        tag("costas", "costas", "dorsal", "latissimo", "latíss", "remada", "puxada")
        tag("ombros", "ombro", "delto", "shoulder")
        tag("biceps", "biceps", "bíceps", "rosca")
        tag("triceps", "triceps", "tríceps")

        val movementPattern = when {
            tags.any { it in setOf("agachamento", "bulgaro", "hack", "leg_press") } ||
                movement.contains("agachar") || movement.contains("afundo") || movement.contains("empurrar com pernas") -> "squat"
            tags.any { it in setOf("terra", "romeno") } || movement.contains("hinge") || movement.contains("extensao de quadril") -> "hip_hinge"
            tags.contains("supino") || movement.contains("aducao horizontal") ||
                (movement.contains("empurrar") && !movement.contains("vertical")) -> "horizontal_push"
            tags.contains("desenvolvimento") || movement.contains("empurrar vertical") -> "vertical_push"
            tags.contains("remada") || movement.contains("puxar horizontal") || movement.contains("remar") -> "horizontal_pull"
            tags.contains("puxada") || movement.contains("puxar vertical") || movement.contains("extensao de ombro") -> "vertical_pull"
            tags.contains("flexao_joelho") || movement.contains("flexao de joelho") -> "knee_flexion"
            tags.contains("extensao_joelho") || movement.contains("extensao de joelho") -> "knee_extension"
            tags.contains("rosca") || movement.contains("flexao de cotovelo") || primary.contains("biceps") -> "elbow_flexion"
            tags.contains("triceps") || movement.contains("extensao de cotovelo") || primary.contains("triceps") -> "elbow_extension"
            tags.contains("panturrilha") -> "calf"
            tags.contains("core") -> "core"
            categoryText.contains("cardio") -> "cardio"
            categoryText.contains("mobil") -> "mobility"
            else -> movement.substringBefore(" unilateral").ifBlank { primary.ifBlank { familyFallback(tags) } }
        }.ifBlank { "general" }

        val family = when (movementPattern) {
            "horizontal_push" -> if (tags.contains("supino")) "chest_press" else "horizontal_push"
            "vertical_push" -> "shoulder_press"
            "horizontal_pull" -> "row"
            "vertical_pull" -> "vertical_pull"
            "squat" -> if (tags.contains("bulgaro")) "bulgarian_squat" else "squat"
            "hip_hinge" -> if (tags.contains("romeno")) "romanian_deadlift" else "hip_hinge"
            else -> movementPattern
        }

        val compound = movementPattern in setOf("horizontal_push", "vertical_push", "horizontal_pull", "vertical_pull", "squat", "hip_hinge") ||
            listOf("empurrar", "puxar", "agachar", "hinge", "levantamento", "remar", "corpo inteiro").any(corpus::contains)

        val identityTags = buildList {
            add(family)
            add(movementPattern)
            addAll(tags)
        }.filter(String::isNotBlank).distinct()

        return ExerciseIdentity(
            normalizedName = normalizedName,
            canonicalKey = identityTags.joinToString("|"),
            movementPattern = movementPattern,
            family = family,
            tags = identityTags.toSet(),
            isCompound = compound,
        )
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun familyFallback(tags: Set<String>): String = when {
        "peitoral" in tags -> "horizontal_push"
        "costas" in tags -> "horizontal_pull"
        "quadriceps" in tags || "gluteos" in tags -> "squat"
        "posterior_coxa" in tags -> "hip_hinge"
        "ombros" in tags -> "vertical_push"
        "biceps" in tags -> "elbow_flexion"
        "triceps" in tags -> "elbow_extension"
        else -> "general"
    }
}
