package net.malusic.daclans

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.malusic.daclans.util.ItemFactory
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.meta.SkullMeta
import java.io.File
import java.util.UUID

class SettingsGui(private val plugin: DaClans) : Listener {

    enum class Type { MAIN, RANKS, MEMBERS }

    class Holder(val clanId: UUID, var type: Type) : InventoryHolder {
        lateinit var inv: Inventory
        val slots = HashMap<Int, String>()
        override fun getInventory(): Inventory = inv
    }

    private val mm = MiniMessage.miniMessage()
    private val file = File(plugin.dataFolder, "settings.yml")
    private val actions = listOf("invite", "kick", "rename", "settings", "warn")
    private var cfg = YamlConfiguration()
    private var size = 45

    init {
        reload()
    }

    fun reload() {
        if (!file.exists()) plugin.saveResource("settings.yml", false)
        cfg = YamlConfiguration.loadConfiguration(file)
        size = (cfg.getInt("size", 45).coerceIn(27, 54) / 9) * 9
    }

    fun open(player: Player, clan: Clan) {
        val holder = Holder(clan.id, Type.MAIN)
        val title = mm.deserialize(cfg.getString("title", "<dark_gray>Clan <clan>")!!, Placeholder.unparsed("clan", clan.name))
        holder.inv = Bukkit.createInventory(holder, size, title)
        render(holder, clan)
        player.openInventory(holder.inv)
    }

    private fun interior(): List<Int> = (0 until size).filter {
        val r = it / 9
        val c = it % 9
        r in 1 until size / 9 - 1 && c in 1..7
    }

    private fun render(h: Holder, clan: Clan) {
        h.inv.clear()
        h.slots.clear()
        val border = ItemFactory.build(plugin, cfg.getConfigurationSection("border"))
        for (i in 0 until size) {
            val r = i / 9
            val c = i % 9
            if (r == 0 || r == size / 9 - 1 || c == 0 || c == 8) h.inv.setItem(i, border)
        }
        when (h.type) {
            Type.MAIN -> main(h, clan)
            Type.RANKS -> ranks(h, clan)
            Type.MEMBERS -> members(h, clan)
        }
        if (h.type != Type.MAIN) {
            h.inv.setItem(size - 5, ItemFactory.build(plugin, cfg.getConfigurationSection("back")))
            h.slots[size - 5] = "back"
        }
    }

    private fun main(h: Holder, clan: Clan) {
        val upgrade = cfg.getConfigurationSection("items.upgrade")
        val base = listOf(
            "clan" to clan.name,
            "members" to clan.members.size.toString(),
            "max" to clan.maxMembers.toString(),
            "amount" to (upgrade?.getInt("amount", 5) ?: 5).toString(),
            "cost" to ClanManager.money(upgrade?.getDouble("cost", 0.0) ?: 0.0)
        )
        for (key in listOf("upgrade", "ranks", "members", "open", "invite")) {
            val sec = cfg.getConfigurationSection("items.$key") ?: continue
            val state = when (key) {
                "open" -> cfg.getString(if (clan.open) "states.open" else "states.closed", "")!!
                "invite" -> cfg.getString(if (clan.inviteLeaderOnly) "states.leader-only" else "states.everyone", "")!!
                else -> ""
            }
            val slot = sec.getInt("slot")
            h.inv.setItem(slot, ItemFactory.build(plugin, sec, base + ("state" to state)))
            h.slots[slot] = key
        }
    }

    private fun ranks(h: Holder, clan: Clan) {
        val template = cfg.getString("permissions-gui.name", "<white><action>: <rank>")!!
        val extra = cfg.getStringList("permissions-gui.lore")
        interior().zip(actions).forEach { (slot, action) ->
            val rank = plugin.ranks.minRank(clan, action)
            val label = cfg.getString("action-names.$action", action)!!
            val name = template.replace("<rank>", plugin.ranks.display(rank))
            h.inv.setItem(
                slot,
                ItemFactory.build(plugin, plugin.config.getConfigurationSection("ranks.$rank"), listOf("action" to label), name, extra)
            )
            h.slots[slot] = action
        }
    }

    private fun members(h: Holder, clan: Clan) {
        val sec = cfg.getConfigurationSection("members-gui")
        val sorted = clan.members.entries.sortedByDescending { plugin.ranks.level(it.value) }
        interior().zip(sorted).forEach { (slot, entry) ->
            val off = Bukkit.getOfflinePlayer(entry.key)
            val name = sec?.getString("name")?.replace("<rank>", plugin.ranks.display(entry.value))
            val item = ItemFactory.build(plugin, sec, listOf("player" to (off.name ?: "?")), name)
            (item.itemMeta as? SkullMeta)?.let {
                it.owningPlayer = off
                item.itemMeta = it
            }
            h.inv.setItem(slot, item)
            h.slots[slot] = entry.key.toString()
        }
    }

    @EventHandler
    fun onDrag(e: InventoryDragEvent) {
        if (e.inventory.holder is Holder) e.isCancelled = true
    }

    @EventHandler
    fun onClick(e: InventoryClickEvent) {
        val h = e.inventory.holder as? Holder ?: return
        e.isCancelled = true
        if (e.clickedInventory != e.inventory) return
        val player = e.whoClicked as? Player ?: return
        val clan = plugin.clans.get(h.clanId)
        if (clan == null || !plugin.ranks.canDo(clan, player.uniqueId, "settings")) {
            player.closeInventory()
            return
        }
        val key = h.slots[e.slot] ?: return
        val leader = clan.leader == player.uniqueId
        if (key == "back") {
            h.type = Type.MAIN
        } else {
            when (h.type) {
                Type.MAIN -> when (key) {
                    "open" -> {
                        clan.open = !clan.open
                        plugin.clans.save(clan)
                    }
                    "upgrade" -> if (leader) upgrade(player, clan) else deny(player)
                    "ranks" -> if (leader) h.type = Type.RANKS else deny(player)
                    "members" -> if (leader) h.type = Type.MEMBERS else deny(player)
                    "invite" -> if (leader) {
                        clan.inviteLeaderOnly = !clan.inviteLeaderOnly
                        plugin.clans.save(clan)
                    } else deny(player)
                }
                Type.RANKS -> if (leader) cycle(clan, key, e.isRightClick) else deny(player)
                Type.MEMBERS -> if (leader) shift(clan, key, e.isRightClick) else deny(player)
            }
        }
        render(h, clan)
    }

    private fun deny(player: Player) = plugin.messages.send(player, "not-leader")

    private fun cycle(clan: Clan, action: String, backwards: Boolean) {
        val order = plugin.ranks.order
        val i = order.indexOf(plugin.ranks.minRank(clan, action)).coerceAtLeast(0)
        clan.perms[action] = order[(i + if (backwards) order.size - 1 else 1) % order.size]
        plugin.clans.save(clan)
    }

    private fun shift(clan: Clan, key: String, demote: Boolean) {
        val target = UUID.fromString(key)
        val rank = plugin.clans.shiftRank(clan, target, if (demote) -1 else 1, plugin.ranks.nonLeader()) ?: return
        plugin.messages.clan(clan, if (demote) "demoted" else "promoted", "player" to (Bukkit.getOfflinePlayer(target).name ?: "?"), "rank" to rank)
    }

    private fun upgrade(player: Player, clan: Clan) {
        val sec = cfg.getConfigurationSection("items.upgrade") ?: return
        val amount = sec.getInt("amount", 5)
        val max = sec.getInt("max", 50)
        val cost = sec.getDouble("cost", 0.0)
        when {
            clan.maxMembers >= max -> plugin.messages.send(player, "upgrade-max")
            cost > 0 && clan.bank < cost -> plugin.messages.send(player, "upgrade-no-funds", "cost" to ClanManager.money(cost))
            else -> {
                clan.bank -= cost
                clan.maxMembers = minOf(max, clan.maxMembers + amount)
                plugin.clans.save(clan)
                plugin.messages.send(player, "upgraded", "max" to clan.maxMembers.toString())
            }
        }
    }
}
