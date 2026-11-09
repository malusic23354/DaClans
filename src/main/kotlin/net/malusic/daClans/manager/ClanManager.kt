package net.malusic.daclans.manager

import net.malusic.daclans.model.Clan
import net.malusic.daclans.storage.ClanStorage
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ClanManager(private val storage: ClanStorage) {

    companion object {
        fun money(value: Double): String = String.format(Locale.US, "%.2f", value)
    }

    private val idIndex = ConcurrentHashMap<UUID, Clan>()
    private val nameIndex = ConcurrentHashMap<String, Clan>()
    private val playerIndex = ConcurrentHashMap<UUID, Clan>()
    private val invites = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>()
    private val allyRequests = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>()

    @Volatile
    private var topCache: List<Clan> = emptyList()
    var sort = "bank"

    fun load() {
        storage.loadAll().forEach(::index)
    }

    fun all(): Collection<Clan> = idIndex.values

    fun get(id: UUID): Clan? = idIndex[id]

    fun byName(name: String): Clan? = nameIndex[name.lowercase()]

    fun ofPlayer(player: UUID): Clan? = playerIndex[player]

    fun areTeammates(a: UUID, b: UUID): Boolean {
        val ca = playerIndex[a] ?: return false
        val cb = playerIndex[b] ?: return false
        return ca.id == cb.id || cb.id in ca.allies
    }

    fun add(clan: Clan) {
        index(clan)
        storage.save(clan)
    }

    fun remove(clan: Clan) {
        idIndex.remove(clan.id)
        nameIndex.remove(clan.name.lowercase())
        clan.members.keys.forEach { playerIndex.remove(it) }
        storage.delete(clan.id)
        idIndex.values.forEach {
            val a = it.allies.remove(clan.id)
            val e = it.enemies.remove(clan.id)
            if (a || e) storage.save(it)
        }
    }

    fun save(clan: Clan) {
        storage.save(clan)
    }

    fun rename(clan: Clan, newName: String) {
        nameIndex.remove(clan.name.lowercase())
        clan.name = newName
        nameIndex[newName.lowercase()] = clan
        storage.save(clan)
    }

    fun setOwner(clan: Clan, newLeader: UUID, formerRank: String) {
        clan.members[clan.leader] = formerRank
        clan.leader = newLeader
        clan.members[newLeader] = "leader"
        storage.save(clan)
    }

    fun addMember(clan: Clan, player: UUID, role: String) {
        clan.members[player] = role
        playerIndex[player] = clan
        invites.remove(player)
        storage.save(clan)
    }

    fun removeMember(clan: Clan, player: UUID) {
        clan.members.remove(player)
        playerIndex.remove(player)
        storage.save(clan)
    }

    fun shiftRank(clan: Clan, player: UUID, delta: Int, order: List<String>): String? {
        val current = clan.members[player] ?: return null
        val i = order.indexOf(current)
        val j = i + delta
        if (i < 0 || j !in order.indices) return null
        clan.members[player] = order[j]
        storage.save(clan)
        return order[j]
    }

    fun invite(target: UUID, clan: Clan, ttlMillis: Long) {
        invites.computeIfAbsent(target) { ConcurrentHashMap() }[clan.id] = System.currentTimeMillis() + ttlMillis
    }

    fun hasInvite(target: UUID, clan: Clan): Boolean {
        val expiry = invites[target]?.get(clan.id) ?: return false
        return expiry > System.currentTimeMillis()
    }

    fun pendingInvites(target: UUID): List<Clan> {
        val map = invites[target] ?: return emptyList()
        val now = System.currentTimeMillis()
        map.values.removeIf { it <= now }
        return map.keys.mapNotNull { idIndex[it] }
    }

    fun requestAlly(from: Clan, to: Clan, ttlMillis: Long) {
        allyRequests.computeIfAbsent(to.id) { ConcurrentHashMap() }[from.id] = System.currentTimeMillis() + ttlMillis
    }

    fun hasAllyRequest(from: Clan, to: Clan): Boolean {
        val expiry = allyRequests[to.id]?.get(from.id) ?: return false
        return expiry > System.currentTimeMillis()
    }

    fun makeAllies(a: Clan, b: Clan) {
        a.allies.add(b.id)
        b.allies.add(a.id)
        a.enemies.remove(b.id)
        b.enemies.remove(a.id)
        allyRequests[a.id]?.remove(b.id)
        allyRequests[b.id]?.remove(a.id)
        storage.save(a)
        storage.save(b)
    }

    fun breakAlly(a: Clan, b: Clan) {
        a.allies.remove(b.id)
        b.allies.remove(a.id)
        storage.save(a)
        storage.save(b)
    }

    fun setEnemy(a: Clan, b: Clan) {
        a.enemies.add(b.id)
        a.allies.remove(b.id)
        b.allies.remove(a.id)
        storage.save(a)
        storage.save(b)
    }

    fun clearEnemy(a: Clan, b: Clan) {
        a.enemies.remove(b.id)
        storage.save(a)
    }

    fun refreshTop() {
        topCache = idIndex.values.sortedByDescending { if (sort == "members") it.members.size.toDouble() else it.bank }
    }

    fun top(): List<Clan> = topCache

    fun topValue(clan: Clan): String = if (sort == "members") clan.members.size.toString() else money(clan.bank)

    private fun index(clan: Clan) {
        idIndex[clan.id] = clan
        nameIndex[clan.name.lowercase()] = clan
        clan.members.keys.forEach { playerIndex[it] = clan }
    }
}
