package net.malusic.daclans

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Level
import kotlin.math.ceil
import kotlin.math.max

class ClanCommand(private val plugin: DaClans) {

    companion object {
        private val NAMED_COLORS = setOf(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
            "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
            "yellow", "white"
        )
        private val COLOR_ALIASES = mapOf("grey" to "gray", "dark_grey" to "dark_gray")
    }

    private val nameRegex get() = Regex(plugin.config.getString("clan.name-regex", "[A-Za-z0-9_]{3,16}")!!)
    private val inviteTtl get() = plugin.config.getLong("clan.invite-expiry-seconds", 60L) * 1000L
    private val allyTtl get() = plugin.config.getLong("clan.ally-request-expiry-seconds", 120L) * 1000L

    private fun cmd(name: String) = Commands.literal(name).requires { it.sender.hasPermission("daclans.command.$name") }

    private fun memberArg(name: String): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument(name, StringArgumentType.word()).suggests { ctx, builder ->
            val clan = (ctx.source.sender as? Player)?.let { plugin.clans.ofPlayer(it.uniqueId) }
            clan?.members?.keys?.mapNotNull { Bukkit.getOfflinePlayer(it).name }
                ?.filter { it.startsWith(builder.remaining, true) }?.forEach { builder.suggest(it) }
            builder.buildFuture()
        }

    private fun clanArg(name: String): RequiredArgumentBuilder<CommandSourceStack, String> =
        Commands.argument(name, StringArgumentType.word()).suggests { _, builder ->
            plugin.clans.all().map { it.name }.filter { it.startsWith(builder.remaining, true) }.forEach { builder.suggest(it) }
            builder.buildFuture()
        }

    fun register(registrar: Commands) {
        val root = Commands.literal("clan")
            .then(cmd("create").then(Commands.argument("name", StringArgumentType.word()).executes { create(it) }))
            .then(cmd("disband").executes { disband(it) })
            .then(cmd("rename").then(Commands.argument("name", StringArgumentType.word()).executes { rename(it) }))
            .then(
                cmd("color").then(
                    Commands.argument("color", StringArgumentType.greedyString())
                        .suggests { ctx, builder ->
                            val player = ctx.source.sender as? Player
                            if (player == null || player.hasPermission("daclans.command.color.default")) {
                                NAMED_COLORS.filter { it.startsWith(builder.remaining, true) }.forEach { builder.suggest(it) }
                            }
                            if (player != null && player.hasPermission("daclans.command.color.hex")) {
                                builder.suggest("#RRGGBB")
                            }
                            builder.buildFuture()
                        }
                        .executes { color(it) }
                )
            )
            .then(
                cmd("invite").then(
                    Commands.argument("user", StringArgumentType.word())
                        .suggests { _, builder ->
                            Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }
                            builder.buildFuture()
                        }
                        .executes { invite(it) }
                )
            )
            .then(
                cmd("join").executes { join(it, null) }
                    .then(clanArg("name").executes { join(it, StringArgumentType.getString(it, "name")) })
            )
            .then(cmd("leave").executes { leave(it) })
            .then(cmd("promote").then(memberArg("user").executes { shift(it, 1) }))
            .then(cmd("demote").then(memberArg("user").executes { shift(it, -1) }))
            .then(cmd("setowner").then(memberArg("user").executes { setOwner(it) }))
            .then(cmd("kick").then(memberArg("user").executes { kick(it) }))
            .then(cmd("settings").executes { settings(it) })
            .then(cmd("reload").executes { reload(it) })
            .then(cmd("ally").then(clanArg("clan").executes { ally(it) }))
            .then(cmd("enemy").then(clanArg("clan").executes { enemy(it) }))
            .then(
                cmd("warn").then(
                    memberArg("user").then(Commands.argument("warning", StringArgumentType.greedyString()).executes { warn(it) })
                )
            )
            .then(cmd("deposit").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes { deposit(it) }))
            .then(
                cmd("withdraw").executes { withdraw(it, true) }
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes { withdraw(it, false) })
            )
            .then(
                cmd("top").executes { top(it, 1) }
                    .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes { top(it, IntegerArgumentType.getInteger(it, "page")) })
            )
            .build()
        registrar.register(root, listOf("clans", "daclans"))
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? {
        val player = ctx.source.sender as? Player
        if (player == null) plugin.messages.send(ctx.source.sender, "player-only")
        return player
    }

    private fun member(
        ctx: CommandContext<CommandSourceStack>,
        need: String? = null,
        leaderOnly: Boolean = false,
        block: (Player, Clan) -> Unit
    ): Int {
        val player = player(ctx) ?: return 0
        val clan = plugin.clans.ofPlayer(player.uniqueId)
        when {
            clan == null -> plugin.messages.send(player, "not-in-clan")
            leaderOnly && clan.leader != player.uniqueId -> plugin.messages.send(player, "not-leader")
            need != null && !plugin.ranks.canDo(clan, player.uniqueId, need) -> plugin.messages.send(player, "no-permission")
            else -> block(player, clan)
        }
        return Command.SINGLE_SUCCESS
    }

    private fun findMember(clan: Clan, name: String): UUID? =
        clan.members.keys.firstOrNull { Bukkit.getOfflinePlayer(it).name.equals(name, true) }

    private fun nameOf(id: UUID): String = Bukkit.getOfflinePlayer(id).name ?: "?"

    private fun create(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        val name = StringArgumentType.getString(ctx, "name")
        when {
            plugin.clans.ofPlayer(player.uniqueId) != null -> plugin.messages.send(player, "already-in-clan")
            !nameRegex.matches(name) -> plugin.messages.send(player, "invalid-name")
            plugin.clans.byName(name) != null -> plugin.messages.send(player, "name-taken", "name" to name)
            else -> {
                plugin.clans.add(
                    Clan(
                        UUID.randomUUID(),
                        name,
                        player.uniqueId,
                        System.currentTimeMillis(),
                        maxMembers = plugin.config.getInt("clan.max-members", 10),
                        members = mutableMapOf(player.uniqueId to "leader")
                    )
                )
                plugin.messages.send(player, "created", "name" to name)
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun disband(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, leaderOnly = true) { _, clan ->
        val online = clan.members.keys.mapNotNull { Bukkit.getPlayer(it) }
        plugin.clans.remove(clan)
        online.forEach { plugin.messages.send(it, "disbanded", "name" to clan.name) }
    }

    private fun rename(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, need = "rename") { player, clan ->
        val name = StringArgumentType.getString(ctx, "name")
        val existing = plugin.clans.byName(name)
        when {
            !nameRegex.matches(name) -> plugin.messages.send(player, "invalid-name")
            existing != null && existing.id != clan.id -> plugin.messages.send(player, "name-taken", "name" to name)
            else -> {
                plugin.clans.rename(clan, name)
                plugin.messages.clan(clan, "renamed", "name" to name)
            }
        }
    }

    private fun color(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, leaderOnly = true) { player, clan ->
        val input = StringArgumentType.getString(ctx, "color")
        val parsed = parseColor(input)
        when {
            parsed == null -> plugin.messages.send(player, "invalid-color", "color" to input)
            !player.hasPermission(if (parsed.second) "daclans.command.color.hex" else "daclans.command.color.default") ->
                plugin.messages.send(player, "no-permission")
            else -> {
                clan.color = parsed.first
                plugin.clans.save(clan)
                plugin.messages.send(player, "color-set")
            }
        }
    }

    private fun parseColor(input: String): Pair<String, Boolean>? {
        val value = input.trim().removePrefix("<").removeSuffix(">")
        if (value.startsWith("#")) {
            val hex = value.substring(1)
            if (hex.length == 6 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                return "<#${hex.lowercase()}>" to true
            }
            if (hex.length == 3 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                return "<#${hex.map { "$it$it" }.joinToString("").lowercase()}>" to true
            }
            return null
        }
        val name = COLOR_ALIASES[value.lowercase()] ?: value.lowercase()
        return if (name in NAMED_COLORS) "<$name>" to false else null
    }

    private fun invite(ctx: CommandContext<CommandSourceStack>): Int = member(ctx) { player, clan ->
        val target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "user"))
        val allowed = clan.leader == player.uniqueId ||
                (!clan.inviteLeaderOnly && plugin.ranks.canDo(clan, player.uniqueId, "invite"))
        when {
            !allowed -> plugin.messages.send(player, "invite-no-permission")
            target == null -> plugin.messages.send(player, "player-not-found")
            target.uniqueId == player.uniqueId -> plugin.messages.send(player, "invite-self")
            plugin.clans.ofPlayer(target.uniqueId) != null -> plugin.messages.send(player, "target-in-clan", "player" to target.name)
            clan.members.size >= clan.maxMembers -> plugin.messages.send(player, "clan-full")
            else -> {
                plugin.clans.invite(target.uniqueId, clan, inviteTtl)
                plugin.messages.send(player, "invited", "player" to target.name)
                plugin.messages.send(target, "invite-received", "player" to player.name, "name" to clan.name)
            }
        }
    }

    private fun join(ctx: CommandContext<CommandSourceStack>, name: String?): Int {
        val player = player(ctx) ?: return 0
        val id = player.uniqueId
        val clan = if (name != null) plugin.clans.byName(name) else plugin.clans.pendingInvites(id).singleOrNull()
        when {
            plugin.clans.ofPlayer(id) != null -> plugin.messages.send(player, "already-in-clan")
            name == null && clan == null -> plugin.messages.send(player, "join-specify")
            clan == null -> plugin.messages.send(player, "no-such-clan", "name" to (name ?: ""))
            !clan.open && !plugin.clans.hasInvite(id, clan) -> plugin.messages.send(player, "no-invite", "name" to clan.name)
            clan.members.size >= clan.maxMembers -> plugin.messages.send(player, "clan-full")
            else -> {
                plugin.clans.addMember(clan, id, plugin.ranks.default)
                plugin.messages.send(player, "joined", "name" to clan.name)
                plugin.messages.clan(clan, "member-joined", "player" to player.name)
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun leave(ctx: CommandContext<CommandSourceStack>): Int = member(ctx) { player, clan ->
        if (clan.leader == player.uniqueId) {
            plugin.messages.send(player, "leader-cannot-leave")
        } else {
            plugin.clans.removeMember(clan, player.uniqueId)
            plugin.messages.send(player, "left", "name" to clan.name)
            plugin.messages.clan(clan, "member-left", "player" to player.name)
        }
    }

    private fun shift(ctx: CommandContext<CommandSourceStack>, delta: Int): Int = member(ctx, leaderOnly = true) { player, clan ->
        val target = findMember(clan, StringArgumentType.getString(ctx, "user"))
        when {
            target == null -> plugin.messages.send(player, "not-member")
            target == clan.leader -> plugin.messages.send(player, "invalid-target")
            else -> {
                val rank = plugin.clans.shiftRank(clan, target, delta, plugin.ranks.nonLeader())
                if (rank == null) {
                    plugin.messages.send(player, "rank-limit")
                } else {
                    plugin.messages.clan(clan, if (delta > 0) "promoted" else "demoted", "player" to nameOf(target), "rank" to rank)
                }
            }
        }
    }

    private fun setOwner(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, leaderOnly = true) { player, clan ->
        val target = findMember(clan, StringArgumentType.getString(ctx, "user"))
        when {
            target == null -> plugin.messages.send(player, "not-member")
            target == player.uniqueId -> plugin.messages.send(player, "invalid-target")
            else -> {
                plugin.clans.setOwner(clan, target, plugin.ranks.nonLeader().last())
                plugin.messages.clan(clan, "owner-set", "player" to nameOf(target))
            }
        }
    }

    private fun kick(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, need = "kick") { player, clan ->
        val target = findMember(clan, StringArgumentType.getString(ctx, "user"))
        when {
            target == null -> plugin.messages.send(player, "not-member")
            target == player.uniqueId || target == clan.leader -> plugin.messages.send(player, "invalid-target")
            plugin.ranks.level(clan.members[target] ?: "") >= plugin.ranks.level(clan.members[player.uniqueId] ?: "") ->
                plugin.messages.send(player, "no-permission")
            else -> {
                plugin.clans.removeMember(clan, target)
                Bukkit.getPlayer(target)?.let { plugin.messages.send(it, "you-kicked", "name" to clan.name) }
                plugin.messages.clan(clan, "member-kicked", "player" to nameOf(target))
            }
        }
    }

    private fun settings(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, need = "settings") { player, clan ->
        plugin.gui.open(player, clan)
    }

    private fun reload(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        try {
            plugin.reloadAll()
            plugin.messages.send(sender, "reload-success")
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to reload configuration", e)
            plugin.messages.send(sender, "reload-failed")
        }
        return Command.SINGLE_SUCCESS
    }

    private fun ally(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, leaderOnly = true) { player, clan ->
        val arg = StringArgumentType.getString(ctx, "clan")
        val other = plugin.clans.byName(arg)
        when {
            other == null -> plugin.messages.send(player, "no-such-clan", "name" to arg)
            other.id == clan.id -> plugin.messages.send(player, "invalid-target")
            other.id in clan.allies -> {
                plugin.clans.breakAlly(clan, other)
                plugin.messages.clan(clan, "ally-broken", "name" to other.name)
                plugin.messages.clan(other, "ally-broken", "name" to clan.name)
            }
            plugin.clans.hasAllyRequest(other, clan) -> {
                plugin.clans.makeAllies(clan, other)
                plugin.messages.clan(clan, "ally-made", "name" to other.name)
                plugin.messages.clan(other, "ally-made", "name" to clan.name)
            }
            else -> {
                plugin.clans.requestAlly(clan, other, allyTtl)
                plugin.messages.send(player, "ally-sent", "name" to other.name)
                plugin.messages.clan(other, "ally-request", "name" to clan.name)
            }
        }
    }

    private fun enemy(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, leaderOnly = true) { player, clan ->
        val arg = StringArgumentType.getString(ctx, "clan")
        val other = plugin.clans.byName(arg)
        when {
            other == null -> plugin.messages.send(player, "no-such-clan", "name" to arg)
            other.id == clan.id -> plugin.messages.send(player, "invalid-target")
            other.id in clan.enemies -> {
                plugin.clans.clearEnemy(clan, other)
                plugin.messages.clan(clan, "enemy-cleared", "name" to other.name)
            }
            else -> {
                plugin.clans.setEnemy(clan, other)
                plugin.messages.clan(clan, "enemy-set", "name" to other.name)
                plugin.messages.clan(other, "enemy-notice", "name" to clan.name)
            }
        }
    }

    private fun warn(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, need = "warn") { player, clan ->
        val target = findMember(clan, StringArgumentType.getString(ctx, "user"))
        val text = StringArgumentType.getString(ctx, "warning")
        when {
            target == null -> plugin.messages.send(player, "not-member")
            target == player.uniqueId -> plugin.messages.send(player, "invalid-target")
            else -> {
                plugin.messages.send(player, "warn-sent", "player" to nameOf(target))
                Bukkit.getPlayer(target)?.let { plugin.messages.send(it, "warn-received", "player" to player.name, "warning" to text) }
            }
        }
    }

    private fun deposit(ctx: CommandContext<CommandSourceStack>): Int = member(ctx) { player, clan ->
        val eco = plugin.economy
        val amount = DoubleArgumentType.getDouble(ctx, "amount")
        when {
            eco == null || !eco.available() -> plugin.messages.send(player, "no-economy")
            !eco.withdraw(player, amount) -> plugin.messages.send(player, "insufficient-funds")
            else -> {
                clan.bank += amount
                plugin.clans.save(clan)
                plugin.messages.send(player, "deposited", "amount" to ClanManager.money(amount))
            }
        }
    }

    private fun withdraw(ctx: CommandContext<CommandSourceStack>, all: Boolean): Int = member(ctx, leaderOnly = true) { player, clan ->
        val eco = plugin.economy
        val amount = if (all) clan.bank else DoubleArgumentType.getDouble(ctx, "amount")
        when {
            eco == null || !eco.available() -> plugin.messages.send(player, "no-economy")
            amount <= 0.0 || amount > clan.bank -> plugin.messages.send(player, "bank-insufficient")
            else -> {
                eco.deposit(player, amount)
                clan.bank -= amount
                plugin.clans.save(clan)
                plugin.messages.send(player, "withdrawn", "amount" to ClanManager.money(amount))
            }
        }
    }

    private fun top(ctx: CommandContext<CommandSourceStack>, page: Int): Int {
        val sender = ctx.source.sender
        if (!plugin.config.getBoolean("leaderboard.enabled", true)) {
            plugin.messages.send(sender, "leaderboard-disabled")
            return 0
        }
        val list = plugin.clans.top()
        val perPage = max(1, plugin.config.getInt("leaderboard.page-size", 10))
        val pages = max(1, ceil(list.size / perPage.toDouble()).toInt())
        val p = page.coerceIn(1, pages)
        plugin.messages.send(sender, "top-header", "page" to p.toString(), "pages" to pages.toString())
        list.drop((p - 1) * perPage).take(perPage).forEachIndexed { i, clan ->
            plugin.messages.send(
                sender,
                "top-line",
                "rank" to ((p - 1) * perPage + i + 1).toString(),
                "name" to clan.name,
                "value" to plugin.clans.topValue(clan)
            )
        }
        return Command.SINGLE_SUCCESS
    }
}