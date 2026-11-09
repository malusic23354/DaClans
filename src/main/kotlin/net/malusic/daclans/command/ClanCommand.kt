package net.malusic.daclans

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.malusic.daclans.api.ClanAllyEvent
import net.malusic.daclans.api.ClanBankDepositEvent
import net.malusic.daclans.api.ClanBankWithdrawEvent
import net.malusic.daclans.api.ClanColorEvent
import net.malusic.daclans.api.ClanCreateEvent
import net.malusic.daclans.api.ClanDescriptionEvent
import net.malusic.daclans.api.ClanDisbandEvent
import net.malusic.daclans.api.ClanEnemyEvent
import net.malusic.daclans.api.ClanJoinEvent
import net.malusic.daclans.api.ClanKickEvent
import net.malusic.daclans.api.ClanLeaderChangeEvent
import net.malusic.daclans.api.ClanLeaveEvent
import net.malusic.daclans.api.ClanRenameEvent
import net.malusic.daclans.util.Money
import net.malusic.daclans.util.TimeFormat
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
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
    private val lastSeenEnabled get() = plugin.config.getBoolean("clan.last-seen.enabled", true)

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
            .then(cmd("deposit").then(Commands.argument("amount", StringArgumentType.word()).executes { deposit(it) }))
            .then(
                cmd("withdraw").executes { withdraw(it, null) }
                    .then(Commands.argument("amount", StringArgumentType.word()).executes { withdraw(it, StringArgumentType.getString(it, "amount")) })
            )
            .then(
                cmd("top").executes { top(it, 1, null) }
                    .then(
                        Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes { top(it, IntegerArgumentType.getInteger(it, "page"), null) }
                            .then(
                                Commands.argument("type", StringArgumentType.word())
                                    .suggests { _, builder ->
                                        ClanManager.TYPES.forEach { builder.suggest(it) }
                                        builder.buildFuture()
                                    }
                                    .executes { top(it, IntegerArgumentType.getInteger(it, "page"), StringArgumentType.getString(it, "type")) }
                            )
                    )
            )
            .then(
                cmd("info").executes { info(it, null) }
                    .then(clanArg("clan").executes { info(it, StringArgumentType.getString(it, "clan")) })
            )
            .then(
                cmd("list").executes { list(it, 1) }
                    .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes { list(it, IntegerArgumentType.getInteger(it, "page")) })
            )
            .then(
                cmd("members").executes { members(it, null) }
                    .then(clanArg("clan").executes { members(it, StringArgumentType.getString(it, "clan")) })
            )
            .then(
                cmd("description").executes { description(it, null) }
                    .then(Commands.argument("text", StringArgumentType.greedyString()).executes { description(it, StringArgumentType.getString(it, "text")) })
            )
            .then(
                cmd("logs").executes { logs(it, null) }
                    .then(clanArg("clan").executes { logs(it, StringArgumentType.getString(it, "clan")) })
            )
            .then(cmd("version").executes { version(it) })
            .build()
        registrar.register(root, listOf("clans", "daclans"))

        val clanChat = Commands.literal("cc")
            .requires { it.sender.hasPermission("daclans.command.cc") }
            .then(Commands.argument("message", StringArgumentType.greedyString()).executes { chat(it, false) })
            .build()
        registrar.register(clanChat)

        val allyChat = Commands.literal("ac")
            .requires { it.sender.hasPermission("daclans.command.ac") }
            .then(Commands.argument("message", StringArgumentType.greedyString()).executes { chat(it, true) })
            .build()
        registrar.register(allyChat)
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

    private fun findMember(clan: Clan, name: String): UUID? {
        clan.members.keys.firstOrNull { it.toString().equals(name, true) }?.let { return it }
        return clan.members.keys.firstOrNull { Bukkit.getOfflinePlayer(it).name.equals(name, true) }
    }

    private fun nameOf(id: UUID): String = Bukkit.getOfflinePlayer(id).name ?: "?"

    private fun create(ctx: CommandContext<CommandSourceStack>): Int {
        val player = player(ctx) ?: return 0
        val name = StringArgumentType.getString(ctx, "name")
        when {
            plugin.clans.ofPlayer(player.uniqueId) != null -> plugin.messages.send(player, "already-in-clan")
            !nameRegex.matches(name) -> plugin.messages.send(player, "invalid-name")
            plugin.clans.byName(name) != null -> plugin.messages.send(player, "name-taken", "name" to name)
            else -> {
                val clan = Clan(
                    UUID.randomUUID(),
                    name,
                    player.uniqueId,
                    System.currentTimeMillis(),
                    maxMembers = plugin.config.getInt("clan.max-members", 10),
                    members = mutableMapOf(player.uniqueId to "leader"),
                    lastSeen = mutableMapOf(player.uniqueId to System.currentTimeMillis())
                )
                val event = ClanCreateEvent(clan, player.uniqueId)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return Command.SINGLE_SUCCESS
                plugin.clans.add(clan)
                plugin.messages.send(player, "created", "name" to name)
            }
        }
        return Command.SINGLE_SUCCESS
    }

    private fun disband(ctx: CommandContext<CommandSourceStack>): Int = member(ctx, leaderOnly = true) { player, clan ->
        val event = ClanDisbandEvent(clan, player.uniqueId)
        plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled) return@member
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
                val event = ClanRenameEvent(clan, clan.name, name)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
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
                val event = ClanColorEvent(clan, parsed.first)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
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
                val event = ClanJoinEvent(clan, id)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return Command.SINGLE_SUCCESS
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
            val event = ClanLeaveEvent(clan, player.uniqueId)
            plugin.server.pluginManager.callEvent(event)
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
                    plugin.messages.clanParsed(clan, if (delta > 0) "promoted" else "demoted", "player" to nameOf(target), "rank" to plugin.ranks.display(rank))
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
                val old = clan.leader
                plugin.clans.setOwner(clan, target, plugin.ranks.nonLeader().last())
                plugin.server.pluginManager.callEvent(ClanLeaderChangeEvent(clan, old, target, "command"))
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
                val name = nameOf(target)
                plugin.clans.removeMember(clan, target)
                plugin.server.pluginManager.callEvent(ClanKickEvent(clan, target, player.uniqueId))
                Bukkit.getPlayer(target)?.let { plugin.messages.send(it, "you-kicked", "name" to clan.name) }
                plugin.messages.clan(clan, "member-kicked", "player" to name)
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
                val event = ClanAllyEvent(clan, other, false)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
                plugin.clans.breakAlly(clan, other)
                plugin.messages.clan(clan, "ally-broken", "name" to other.name)
                plugin.messages.clan(other, "ally-broken", "name" to clan.name)
            }
            plugin.clans.hasAllyRequest(other, clan) -> {
                val event = ClanAllyEvent(clan, other, true)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
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
                val event = ClanEnemyEvent(clan, other, false)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
                plugin.clans.clearEnemy(clan, other)
                plugin.messages.clan(clan, "enemy-cleared", "name" to other.name)
            }
            else -> {
                val event = ClanEnemyEvent(clan, other, true)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
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
        val cents = Money.parse(StringArgumentType.getString(ctx, "amount"))
        when {
            cents == null || cents <= 0 -> plugin.messages.send(player, "invalid-amount")
            eco == null || !eco.available() -> plugin.messages.send(player, "no-economy")
            else -> {
                val event = ClanBankDepositEvent(clan, player.uniqueId, cents, clan.bankCents + cents)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
                if (!eco.withdraw(player, cents / 100.0)) {
                    plugin.messages.send(player, "insufficient-funds")
                    return@member
                }
                clan.bankCents += cents
                plugin.clans.addBankLog(clan, player.uniqueId, BankLog.Type.DEPOSIT, cents, clan.bankCents)
                plugin.clans.save(clan)
                plugin.messages.send(player, "deposited", "amount" to ClanManager.money(cents))
            }
        }
    }

    private fun withdraw(ctx: CommandContext<CommandSourceStack>, raw: String?): Int = member(ctx, leaderOnly = true) { player, clan ->
        val eco = plugin.economy
        val cents = if (raw == null || raw.equals("all", true)) clan.bankCents else Money.parse(raw)
        when {
            cents == null || cents <= 0 -> plugin.messages.send(player, "invalid-amount")
            eco == null || !eco.available() -> plugin.messages.send(player, "no-economy")
            cents > clan.bankCents -> plugin.messages.send(player, "bank-insufficient")
            else -> {
                val event = ClanBankWithdrawEvent(clan, player.uniqueId, cents, clan.bankCents - cents)
                plugin.server.pluginManager.callEvent(event)
                if (event.isCancelled) return@member
                if (!eco.deposit(player, cents / 100.0)) {
                    plugin.messages.send(player, "no-economy")
                    return@member
                }
                clan.bankCents -= cents
                plugin.clans.addBankLog(clan, player.uniqueId, BankLog.Type.WITHDRAW, cents, clan.bankCents)
                plugin.clans.save(clan)
                plugin.messages.send(player, "withdrawn", "amount" to ClanManager.money(cents))
            }
        }
    }

    private fun top(ctx: CommandContext<CommandSourceStack>, page: Int, requested: String?): Int {
        val sender = ctx.source.sender
        if (!plugin.config.getBoolean("leaderboard.enabled", true)) {
            plugin.messages.send(sender, "leaderboard-disabled")
            return 0
        }
        val type = if (requested == null) plugin.clans.sort else {
            val normalized = requested.lowercase()
            if (normalized !in ClanManager.TYPES) {
                plugin.messages.send(sender, "invalid-type", "type" to requested)
                return 0
            }
            normalized
        }
        val list = plugin.clans.top(type)
        val perPage = max(1, plugin.config.getInt("leaderboard.page-size", 10))
        val pages = max(1, ceil(list.size / perPage.toDouble()).toInt())
        val p = page.coerceIn(1, pages)
        plugin.messages.send(sender, "top-header", "page" to p.toString(), "pages" to pages.toString(), "type" to type)
        list.drop((p - 1) * perPage).take(perPage).forEachIndexed { i, clan ->
            plugin.messages.sendParsed(
                sender,
                "top-line",
                "rank" to ((p - 1) * perPage + i + 1).toString(),
                "name" to clan.coloredName(),
                "value" to plugin.clans.topValue(clan, type)
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun info(ctx: CommandContext<CommandSourceStack>, name: String?): Int {
        val sender = ctx.source.sender
        val clan = resolveClan(sender, name) ?: return 0
        plugin.messages.sendParsed(sender, "info-header", "name" to clan.coloredName())
        plugin.messages.send(sender, "info-leader", "player" to nameOf(clan.leader))
        if (clan.description.isNotBlank()) {
            plugin.messages.send(sender, "info-description", "description" to clan.description)
        }
        plugin.messages.send(
            sender,
            "info-members",
            "members" to clan.members.size.toString(),
            "max" to clan.maxMembers.toString()
        )
        plugin.messages.send(sender, "info-bank", "amount" to ClanManager.money(clan.bankCents))
        plugin.messages.send(sender, "info-allies", "count" to clan.allies.size.toString())
        plugin.messages.send(sender, "info-enemies", "count" to clan.enemies.size.toString())
        plugin.messages.send(sender, "info-created", "date" to TimeFormat.date(clan.createdAt))
        return Command.SINGLE_SUCCESS
    }

    private fun list(ctx: CommandContext<CommandSourceStack>, page: Int): Int {
        val sender = ctx.source.sender
        val all = plugin.clans.all().sortedBy { it.name.lowercase() }
        val perPage = max(1, plugin.config.getInt("clan.list-page-size", 10))
        val pages = max(1, ceil(all.size / perPage.toDouble()).toInt())
        val p = page.coerceIn(1, pages)
        plugin.messages.send(sender, "list-header", "page" to p.toString(), "pages" to pages.toString(), "total" to all.size.toString())
        all.drop((p - 1) * perPage).take(perPage).forEach { clan ->
            plugin.messages.sendParsed(
                sender,
                "list-line",
                "name" to clan.coloredName(),
                "members" to clan.members.size.toString(),
                "max" to clan.maxMembers.toString(),
                "bank" to ClanManager.money(clan.bankCents)
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun members(ctx: CommandContext<CommandSourceStack>, name: String?): Int {
        val sender = ctx.source.sender
        val clan = resolveClan(sender, name) ?: return 0
        val sorted = clan.members.entries.sortedWith(
            compareByDescending<Map.Entry<UUID, String>> { plugin.ranks.level(it.value) }
                .thenBy { nameOf(it.key).lowercase() }
        )
        plugin.messages.sendParsed(sender, "members-header", "name" to clan.coloredName(), "count" to clan.members.size.toString())
        sorted.forEach { entry ->
            val online = Bukkit.getPlayer(entry.key) != null
            val lastSeen = if (!lastSeenEnabled) "" else TimeFormat.ago(clan.lastSeen[entry.key] ?: 0L)
            plugin.messages.sendParsed(
                sender,
                if (lastSeenEnabled) "members-line" else "members-line-no-seen",
                "player" to nameOf(entry.key),
                "rank" to plugin.ranks.display(entry.value),
                "last_seen" to lastSeen,
                "status" to if (online) plugin.config.getString("clan.last-seen.online-text", "Online")!! else plugin.config.getString("clan.last-seen.offline-text", "Offline")!!
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun description(ctx: CommandContext<CommandSourceStack>, text: String?): Int {
        if (text == null) return member(ctx) { player, clan ->
            if (clan.description.isBlank()) {
                plugin.messages.send(player, "description-empty")
            } else {
                plugin.messages.send(player, "info-description", "description" to clan.description)
            }
        }
        return member(ctx, need = "description") { player, clan ->
            val value = text.trim()
            val maxLength = plugin.config.getInt("clan.description-max-length", 120)
            when {
                value.length > maxLength -> plugin.messages.send(player, "description-too-long", "max" to maxLength.toString())
                else -> {
                    val event = ClanDescriptionEvent(clan, value)
                    plugin.server.pluginManager.callEvent(event)
                    if (event.isCancelled) return@member
                    clan.description = value
                    plugin.clans.save(clan)
                    plugin.messages.send(player, "description-set")
                }
            }
        }
    }

    private fun logs(ctx: CommandContext<CommandSourceStack>, name: String?): Int {
        val sender = ctx.source.sender
        val player = sender as? Player
        if (name != null && player != null && !player.hasPermission("daclans.command.logs.others")) {
            val own = plugin.clans.ofPlayer(player.uniqueId)
            if (own == null || !own.name.equals(name, true)) {
                plugin.messages.send(sender, "no-permission")
                return 0
            }
        }
        val clan = resolveClan(sender, name) ?: return 0
        plugin.messages.sendParsed(sender, "banklog-header", "name" to clan.coloredName())
        if (clan.logs.isEmpty()) {
            plugin.messages.send(sender, "banklog-empty")
            return Command.SINGLE_SUCCESS
        }
        clan.logs.forEach { log ->
            val who = log.player?.let { nameOf(it) } ?: "Server"
            val signed = when (log.type) {
                BankLog.Type.DEPOSIT -> "+${Money.format(log.amountCents)}"
                else -> "-${Money.format(log.amountCents)}"
            }
            plugin.messages.send(
                sender,
                "banklog-line",
                "date" to TimeFormat.date(log.createdAt),
                "type" to log.type.name.lowercase(),
                "player" to who,
                "amount" to signed,
                "balance" to Money.format(log.balanceCents)
            )
        }
        return Command.SINGLE_SUCCESS
    }

    private fun resolveClan(sender: CommandSender, name: String?): Clan? {
        val clan = if (name != null) {
            plugin.clans.byName(name)
        } else {
            (sender as? Player)?.let { plugin.clans.ofPlayer(it.uniqueId) }
        }
        if (clan == null) {
            if (name != null) plugin.messages.send(sender, "no-such-clan", "name" to name)
            else plugin.messages.send(sender, "not-in-clan")
        }
        return clan
    }

    private fun version(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val meta = plugin.pluginMeta
        plugin.messages.send(sender, "version-plugin", "version" to meta.version)
        plugin.messages.send(sender, "version-server", "version" to plugin.server.version)
        plugin.messages.send(sender, "version-api", "version" to (meta.apiVersion ?: plugin.server.bukkitVersion))
        plugin.messages.send(sender, "version-author", "author" to meta.authors.joinToString(", ").ifBlank { "Unknown" })
        return Command.SINGLE_SUCCESS
    }

    private fun chat(ctx: CommandContext<CommandSourceStack>, ally: Boolean): Int {
        val player = player(ctx) ?: return 0
        val message = StringArgumentType.getString(ctx, "message")
        if (ally) plugin.chat.sendAlly(player, message) else plugin.chat.sendClan(player, message)
        return Command.SINGLE_SUCCESS
    }
}
