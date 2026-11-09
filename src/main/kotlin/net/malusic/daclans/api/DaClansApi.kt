package net.malusic.daclans.api

import net.malusic.daclans.Clan
import net.malusic.daclans.ClanManager
import net.malusic.daclans.DaClans
import net.malusic.daclans.util.Money
import java.util.UUID

class DaClansApi internal constructor(private val plugin: DaClans) {

    fun clans(): Collection<Clan> = plugin.clans.all()

    fun clan(id: UUID): Clan? = plugin.clans.get(id)

    fun clan(name: String): Clan? = plugin.clans.byName(name)

    fun clanOf(player: UUID): Clan? = plugin.clans.ofPlayer(player)

    fun top(type: String = plugin.clans.sort): List<Clan> = plugin.clans.top(type)

    fun types(): List<String> = ClanManager.TYPES

    fun balance(clan: Clan): String = Money.format(clan.bankCents)

    fun balanceCents(clan: Clan): Long = clan.bankCents

    fun onlineMembers(clan: Clan): Int = clan.members.keys.count { plugin.server.getPlayer(it) != null }
}

object DaClansProvider {

    @Volatile
    private var instance: DaClansApi? = null

    fun api(): DaClansApi? = instance

    internal fun register(api: DaClansApi) {
        instance = api
    }

    internal fun unregister() {
        instance = null
    }
}
