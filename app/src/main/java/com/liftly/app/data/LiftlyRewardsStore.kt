package com.liftly.app.data

import androidx.room.withTransaction
import com.liftly.app.domain.RewardProgression
import com.liftly.app.domain.WorkoutRewardDecision
import com.liftly.app.domain.WorkoutRewardMetrics
import com.liftly.app.domain.WorkoutRewardPolicy
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Transactional persistence boundary for the Liftly Rewards economy. */
class LiftlyRewardsStore(private val database: LiftlyDatabase) {
    private val dao = database.dao()

    val snapshot: Flow<RewardSnapshot> = combine(
        dao.observeRewardWallet(),
        dao.observeRewardCatalog(),
        dao.observeRewardInventory(),
        dao.observeRewardMissions(),
        dao.observeRecentRewardLedger(),
    ) { wallet, catalog, inventory, missions, ledger ->
        val resolvedWallet = wallet ?: RewardWalletEntity(updatedAt = 0L)
        val owned = inventory.associateBy(RewardInventoryEntity::itemId)
        val now = System.currentTimeMillis()
        RewardSnapshot(
            wallet = resolvedWallet,
            level = RewardProgression.fromLifetimeXp(resolvedWallet.lifetimeXp),
            store = catalog.map { item ->
                RewardStoreItem(item, item.id in owned, owned[item.id]?.equippedSlot != null)
            },
            missions = missions.filter { it.periodStart <= now && it.periodEnd > now },
            recentActivity = ledger,
        )
    }

    suspend fun initialize(now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault()) =
        database.withTransaction { initializeInTransaction(now, zoneId) }

    suspend fun awardWorkoutCompletion(
        sessionId: String,
        metrics: WorkoutRewardMetrics,
        occurredAt: Long = System.currentTimeMillis(),
    ): RewardGrant = database.withTransaction {
        val session = requireNotNull(dao.session(sessionId)) { "Sessão não encontrada." }
        require(!session.isTestMode && session.finishedAt != null) {
            "Somente treinos válidos e finalizados geram recompensas."
        }
        awardWorkoutCompletionInTransaction(sessionId, metrics, occurredAt)
    }

    /** Called by LiftlyRepository while its finish-session Room transaction is still open. */
    internal suspend fun awardWorkoutCompletionInTransaction(
        sessionId: String,
        metrics: WorkoutRewardMetrics,
        occurredAt: Long,
    ): RewardGrant {
        val decision = WorkoutRewardPolicy.calculate(metrics)
        if (decision == WorkoutRewardDecision.NONE) return RewardGrant.NONE
        initializeInTransaction(occurredAt)
        val ledgerId = "session:$sessionId"
        if (
            dao.insertRewardLedger(
                RewardLedgerEntity(
                    id = ledgerId,
                    sourceType = "WORKOUT",
                    sourceId = sessionId,
                    deltaXp = decision.xp,
                    deltaCoins = decision.coins,
                    createdAt = occurredAt,
                    description = decision.reasons.joinToString(" • "),
                )
            ) == -1L
        ) return RewardGrant(duplicate = true)
        check(dao.incrementRewardWallet(decision.xp, decision.coins, occurredAt) == 1)

        var missionXp = 0L
        var missionCoins = 0L
        val completedMissions = mutableListOf<String>()
        decision.missionProgress.forEach { (metric, amount) ->
            dao.activeRewardMissions(metric.name, occurredAt).forEach { mission ->
                if (mission.completedAt == null) {
                    dao.incrementRewardMission(mission.id, amount)
                    val grant = settleMissionInTransaction(mission.id, occurredAt)
                    missionXp += grant.xp
                    missionCoins += grant.coins
                    completedMissions += grant.completedMissionIds
                }
            }
        }
        return RewardGrant(
            xp = decision.xp + missionXp,
            coins = decision.coins + missionCoins,
            completedMissionIds = completedMissions,
        )
    }

    suspend fun purchase(itemId: String, now: Long = System.currentTimeMillis()) = database.withTransaction {
        initializeInTransaction(now)
        val item = requireNotNull(dao.rewardCatalogItem(itemId)) { "Item não encontrado na loja." }
        require(item.enabled) { "Este item não está disponível." }
        require(dao.rewardInventoryItem(itemId) == null) { "Este item já pertence a você." }
        val wallet = requireNotNull(dao.rewardWallet())
        require(RewardProgression.fromLifetimeXp(wallet.lifetimeXp).level >= item.requiredLevel) {
            "Alcance o nível ${item.requiredLevel} para liberar este item."
        }
        require(dao.spendRewardCoins(item.priceCoins, now) == 1) { "Lift Coins insuficientes." }
        check(dao.insertRewardInventory(RewardInventoryEntity(itemId, now)) != -1L)
        check(
            dao.insertRewardLedger(
                RewardLedgerEntity(
                    id = "purchase:$itemId",
                    sourceType = "PURCHASE",
                    sourceId = itemId,
                    deltaXp = 0,
                    deltaCoins = -item.priceCoins,
                    createdAt = now,
                    description = "Compra: ${item.title}",
                )
            ) != -1L
        )
    }

    suspend fun equip(itemId: String) = database.withTransaction {
        val item = requireNotNull(dao.rewardCatalogItem(itemId)) { "Item não encontrado." }
        require(dao.rewardInventoryItem(itemId) != null) { "Compre o item antes de equipá-lo." }
        dao.unequipRewardSlot(item.slot)
        check(dao.equipRewardItem(itemId, item.slot) == 1)
    }

    suspend fun unequip(slot: String) = database.withTransaction { dao.unequipRewardSlot(slot) }

    suspend fun adminGrant(
        xp: Long,
        coins: Long,
        reason: String = "Ajuste administrativo",
        now: Long = System.currentTimeMillis(),
    ): RewardGrant = database.withTransaction {
        require(xp != 0L || coins != 0L) { "Informe um ajuste de XP ou Lift Coins." }
        initializeInTransaction(now)
        val wallet = requireNotNull(dao.rewardWallet())
        require(xp <= 0L || wallet.lifetimeXp <= Long.MAX_VALUE - xp) { "O saldo de XP excederia o limite." }
        require(coins <= 0L || wallet.coinBalance <= Long.MAX_VALUE - coins) {
            "O saldo de Lift Coins excederia o limite."
        }
        // Removal is clamped to the available balance. The ledger always records what
        // actually changed, never the larger amount requested by the administrator.
        val effectiveXp = xp.coerceAtLeast(-wallet.lifetimeXp)
        val effectiveCoins = coins.coerceAtLeast(-wallet.coinBalance)
        if (effectiveXp == 0L && effectiveCoins == 0L) return@withTransaction RewardGrant.NONE
        val id = "admin:${UUID.randomUUID()}"
        check(
            dao.insertRewardLedger(
                RewardLedgerEntity(
                    id = id,
                    sourceType = "ADMIN",
                    sourceId = id,
                    deltaXp = effectiveXp,
                    deltaCoins = effectiveCoins,
                    createdAt = now,
                    description = reason.trim().take(120),
                )
            ) != -1L
        )
        check(dao.incrementRewardWallet(effectiveXp, effectiveCoins, now) == 1)
        RewardGrant(effectiveXp, effectiveCoins)
    }

    suspend fun adminUnlockAll(now: Long = System.currentTimeMillis()): Int = database.withTransaction {
        initializeInTransaction(now)
        var unlocked = 0
        dao.allRewardCatalog().filter(RewardCatalogItemEntity::enabled).forEachIndexed { index, item ->
            if (dao.insertRewardInventory(RewardInventoryEntity(item.id, now + index)) != -1L) unlocked++
        }
        val id = "admin-unlock:${UUID.randomUUID()}"
        dao.insertRewardLedger(
            RewardLedgerEntity(id, "ADMIN", id, 0, 0, now, "Catálogo liberado para teste")
        )
        unlocked
    }

    /** Generates a unique, explicitly administrative workout event and advances live missions. */
    suspend fun adminSimulateWorkout(
        metrics: WorkoutRewardMetrics,
        now: Long = System.currentTimeMillis(),
    ): RewardGrant = database.withTransaction {
        awardWorkoutCompletionInTransaction("admin-${UUID.randomUUID()}", metrics, now)
    }

    suspend fun adminCompleteMission(
        missionId: String,
        now: Long = System.currentTimeMillis(),
    ): RewardGrant = database.withTransaction {
        initializeInTransaction(now)
        val mission = requireNotNull(dao.rewardMission(missionId)) { "Missão não encontrada." }
        dao.adminSetRewardMissionProgress(missionId, mission.target)
        settleMissionInTransaction(missionId, now)
    }

    /** The payout ledger remains, so replaying the same mission cannot mint points twice. */
    suspend fun adminResetCurrentMissions(now: Long = System.currentTimeMillis()): Int =
        database.withTransaction {
            initializeInTransaction(now)
            dao.adminResetCurrentRewardMissions(now)
        }

    suspend fun adminResetEconomy(now: Long = System.currentTimeMillis()) = database.withTransaction {
        clearInTransaction()
        initializeInTransaction(now)
    }

    internal suspend fun initializeInTransaction(
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        if (dao.rewardWallet() == null) dao.upsertRewardWallet(RewardWalletEntity(updatedAt = now))
        dao.upsertRewardCatalog(LiftlyRewardCatalog.items)
        val date = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        dao.insertRewardMissions(RewardMissionFactory.current(date, zoneId))
    }

    internal suspend fun clearInTransaction() {
        dao.clearRewardInventory()
        dao.clearRewardMissions()
        dao.clearRewardLedger()
        dao.clearRewardWallet()
        dao.clearRewardCatalog()
    }

    private suspend fun settleMissionInTransaction(missionId: String, now: Long): RewardGrant {
        val mission = requireNotNull(dao.rewardMission(missionId))
        if (mission.progress < mission.target) return RewardGrant.NONE
        if (dao.completeRewardMissionIfReady(missionId, now) != 1) return RewardGrant.NONE
        val ledgerId = "mission:$missionId"
        if (
            dao.insertRewardLedger(
                RewardLedgerEntity(
                    id = ledgerId,
                    sourceType = "MISSION",
                    sourceId = missionId,
                    deltaXp = mission.xpReward,
                    deltaCoins = mission.coinReward,
                    createdAt = now,
                    description = "Missão concluída: ${mission.title}",
                )
            ) == -1L
        ) return RewardGrant.NONE
        check(dao.incrementRewardWallet(mission.xpReward, mission.coinReward, now) == 1)
        return RewardGrant(mission.xpReward, mission.coinReward, listOf(missionId))
    }
}
