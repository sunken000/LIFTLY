package com.liftly.app.commercial

/**
 * Capabilities that can be granted by a verified subscription or a gym contract.
 *
 * Keeping these values independent from the UI makes the same entitlement checks reusable by
 * screens, ViewModels and a future backend-verified billing implementation.
 */
enum class CommercialEntitlement {
    ADVANCED_PROGRESSION_COACH,
    SMART_EXERCISE_SUBSTITUTION,
    HEALTH_CONNECT_SYNC,
    ADVANCED_REPORTS,
    MEMBER_MANAGEMENT,
    COACH_ACCOUNTS,
    GYM_WORKOUT_LIBRARY,
    MEMBER_INVITES,
    GYM_BRANDING,
    DATA_EXPORT,
    PRIORITY_SUPPORT,
    MULTI_LOCATION,
    SSO_AND_AUDIT_LOG,
}

data class CommercialFeature(
    val entitlement: CommercialEntitlement,
    val title: String,
    val description: String,
)

object CommercialFeatureCatalog {
    val all: List<CommercialFeature> = listOf(
        CommercialFeature(
            CommercialEntitlement.ADVANCED_PROGRESSION_COACH,
            "Coach de progressão",
            "Sugestões de carga e repetições com base no histórico, RIR e dor informada.",
        ),
        CommercialFeature(
            CommercialEntitlement.SMART_EXERCISE_SUBSTITUTION,
            "Substituição inteligente",
            "Alternativas compatíveis por músculo, movimento, equipamento e dificuldade.",
        ),
        CommercialFeature(
            CommercialEntitlement.HEALTH_CONNECT_SYNC,
            "Health Connect",
            "Leitura de peso e sono e exportação dos treinos concluídos, sempre com permissão.",
        ),
        CommercialFeature(
            CommercialEntitlement.ADVANCED_REPORTS,
            "Relatórios de evolução",
            "Indicadores de frequência, volume e evolução para decisões mais rápidas.",
        ),
        CommercialFeature(
            CommercialEntitlement.MEMBER_MANAGEMENT,
            "Gestão de alunos",
            "Visão centralizada de alunos ativos, planos de treino e adesão.",
        ),
        CommercialFeature(
            CommercialEntitlement.COACH_ACCOUNTS,
            "Equipe de professores",
            "Acessos individuais e separação de responsabilidades por profissional.",
        ),
        CommercialFeature(
            CommercialEntitlement.GYM_WORKOUT_LIBRARY,
            "Biblioteca da academia",
            "Modelos padronizados que a equipe pode reaproveitar e adaptar.",
        ),
        CommercialFeature(
            CommercialEntitlement.MEMBER_INVITES,
            "Convites de alunos",
            "Entrada orientada de membros vinculados à academia.",
        ),
        CommercialFeature(
            CommercialEntitlement.GYM_BRANDING,
            "Identidade da academia",
            "Nome, cores e presença da marca na experiência do aluno.",
        ),
        CommercialFeature(
            CommercialEntitlement.DATA_EXPORT,
            "Exportação de dados",
            "Dados operacionais exportáveis para análises e rotinas administrativas.",
        ),
        CommercialFeature(
            CommercialEntitlement.PRIORITY_SUPPORT,
            "Suporte prioritário",
            "Atendimento com prioridade para responsáveis da operação.",
        ),
        CommercialFeature(
            CommercialEntitlement.MULTI_LOCATION,
            "Múltiplas unidades",
            "Administração consolidada de unidades e equipes.",
        ),
        CommercialFeature(
            CommercialEntitlement.SSO_AND_AUDIT_LOG,
            "SSO e auditoria",
            "Controles corporativos de acesso e trilha de ações administrativas.",
        ),
    )

    private val byEntitlement = all.associateBy(CommercialFeature::entitlement)

    fun feature(entitlement: CommercialEntitlement): CommercialFeature =
        requireNotNull(byEntitlement[entitlement]) {
            "Entitlement sem descrição no catálogo: $entitlement"
        }

    fun featuresFor(plan: CommercialPlan): List<CommercialFeature> =
        plan.entitlements.map(::feature)
}

enum class CommercialAudience {
    INDIVIDUAL,
    GYM,
    ENTERPRISE,
}

enum class CommercialBillingCadence {
    MONTHLY,
    ANNUAL,
    CUSTOM,
}

enum class CommercialSalesChannel {
    PLAY_STORE,
    SALES_CONTRACT,
}

data class CommercialPriceOption(
    val id: String,
    val displayPrice: String,
    val cadence: CommercialBillingCadence,
    /**
     * Play product/base-plan identifier. It is metadata only; its presence never grants access.
     */
    val storeProductId: String? = null,
)

data class CommercialPlan(
    val id: String,
    val name: String,
    val audience: CommercialAudience,
    val summary: String,
    val capacityLabel: String,
    val priceOptions: List<CommercialPriceOption>,
    val salesChannel: CommercialSalesChannel,
    val entitlements: Set<CommercialEntitlement>,
    val featured: Boolean = false,
)

object CommercialPlanCatalog {
    val proIndividual = CommercialPlan(
        id = "pro_individual",
        name = "Pro individual",
        audience = CommercialAudience.INDIVIDUAL,
        summary = "Recursos avançados para quem quer acompanhar a própria evolução.",
        capacityLabel = "1 usuário",
        priceOptions = listOf(
            CommercialPriceOption(
                id = "pro_monthly",
                displayPrice = "R$ 14,90/mês",
                cadence = CommercialBillingCadence.MONTHLY,
                storeProductId = "liftly_pro",
            ),
            CommercialPriceOption(
                id = "pro_annual",
                displayPrice = "R$ 119,90/ano",
                cadence = CommercialBillingCadence.ANNUAL,
                storeProductId = "liftly_pro",
            ),
        ),
        salesChannel = CommercialSalesChannel.PLAY_STORE,
        entitlements = linkedSetOf(
            CommercialEntitlement.ADVANCED_PROGRESSION_COACH,
            CommercialEntitlement.SMART_EXERCISE_SUBSTITUTION,
            CommercialEntitlement.HEALTH_CONNECT_SYNC,
            CommercialEntitlement.ADVANCED_REPORTS,
            CommercialEntitlement.DATA_EXPORT,
        ),
    )

    val academyStart = CommercialPlan(
        id = "academy_start",
        name = "Academia Start",
        audience = CommercialAudience.GYM,
        summary = "Operação enxuta para academias que estão digitalizando o acompanhamento.",
        capacityLabel = "Até 50 alunos",
        priceOptions = listOf(
            CommercialPriceOption(
                id = "academy_start_monthly",
                displayPrice = "R$ 199/mês",
                cadence = CommercialBillingCadence.MONTHLY,
            ),
        ),
        salesChannel = CommercialSalesChannel.SALES_CONTRACT,
        entitlements = linkedSetOf(
            CommercialEntitlement.MEMBER_MANAGEMENT,
            CommercialEntitlement.COACH_ACCOUNTS,
            CommercialEntitlement.GYM_WORKOUT_LIBRARY,
            CommercialEntitlement.MEMBER_INVITES,
            CommercialEntitlement.ADVANCED_REPORTS,
            CommercialEntitlement.HEALTH_CONNECT_SYNC,
            CommercialEntitlement.DATA_EXPORT,
        ),
    )

    val academyGrowth = CommercialPlan(
        id = "academy_growth",
        name = "Academia Growth",
        audience = CommercialAudience.GYM,
        summary = "Mais capacidade, identidade própria e suporte para uma equipe em crescimento.",
        capacityLabel = "Até 150 alunos",
        priceOptions = listOf(
            CommercialPriceOption(
                id = "academy_growth_monthly",
                displayPrice = "R$ 399/mês",
                cadence = CommercialBillingCadence.MONTHLY,
            ),
        ),
        salesChannel = CommercialSalesChannel.SALES_CONTRACT,
        entitlements = linkedSetOf(
            CommercialEntitlement.MEMBER_MANAGEMENT,
            CommercialEntitlement.COACH_ACCOUNTS,
            CommercialEntitlement.GYM_WORKOUT_LIBRARY,
            CommercialEntitlement.MEMBER_INVITES,
            CommercialEntitlement.GYM_BRANDING,
            CommercialEntitlement.ADVANCED_REPORTS,
            CommercialEntitlement.HEALTH_CONNECT_SYNC,
            CommercialEntitlement.DATA_EXPORT,
            CommercialEntitlement.PRIORITY_SUPPORT,
        ),
        featured = true,
    )

    val enterprise = CommercialPlan(
        id = "enterprise",
        name = "Enterprise",
        audience = CommercialAudience.ENTERPRISE,
        summary = "Contrato e implantação sob medida para redes e operações de maior porte.",
        capacityLabel = "Capacidade personalizada",
        priceOptions = listOf(
            CommercialPriceOption(
                id = "enterprise_custom",
                displayPrice = "Sob consulta",
                cadence = CommercialBillingCadence.CUSTOM,
            ),
        ),
        salesChannel = CommercialSalesChannel.SALES_CONTRACT,
        entitlements = CommercialEntitlement.entries.toSet(),
    )

    val all: List<CommercialPlan> = listOf(
        proIndividual,
        academyStart,
        academyGrowth,
        enterprise,
    )

    private val byId = all.associateBy(CommercialPlan::id)

    fun find(planId: String): CommercialPlan? = byId[planId]
}

/**
 * Only a backend-verified snapshot should be used to unlock paid capabilities.
 */
data class CommercialEntitlementSnapshot(
    val activeEntitlements: Set<CommercialEntitlement> = emptySet(),
    val activePlanId: String? = null,
    val verifiedByBackend: Boolean = false,
    val validUntilEpochMillis: Long? = null,
) {
    fun grants(entitlement: CommercialEntitlement): Boolean =
        verifiedByBackend && entitlement in activeEntitlements

    companion object {
        val None = CommercialEntitlementSnapshot()
    }
}
