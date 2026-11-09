package net.malusic.daclans.api

import net.malusic.daclans.Clan
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

abstract class ClanEvent(open val clan: Clan) : Event()

private fun handlers(): HandlerList = HandlerList()

class ClanCreateEvent(override val clan: Clan, val creator: UUID) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanDisbandEvent(override val clan: Clan, val actor: UUID) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanRenameEvent(override val clan: Clan, val oldName: String, val newName: String) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanJoinEvent(override val clan: Clan, val player: UUID) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanLeaveEvent(override val clan: Clan, val player: UUID) : ClanEvent(clan) {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanKickEvent(override val clan: Clan, val player: UUID, val actor: UUID) : ClanEvent(clan) {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanLeaderChangeEvent(
    override val clan: Clan,
    val oldLeader: UUID,
    val newLeader: UUID,
    val reason: String
) : ClanEvent(clan) {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanColorEvent(override val clan: Clan, val color: String) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanDescriptionEvent(override val clan: Clan, val description: String) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanBankDepositEvent(
    override val clan: Clan,
    val player: UUID,
    val amountCents: Long,
    val balanceCents: Long
) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanBankWithdrawEvent(
    override val clan: Clan,
    val player: UUID,
    val amountCents: Long,
    val balanceCents: Long
) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanAllyEvent(override val clan: Clan, val other: Clan, val allied: Boolean) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanEnemyEvent(override val clan: Clan, val other: Clan, val enemy: Boolean) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class ClanChatEvent(
    override val clan: Clan,
    val player: UUID,
    val message: String,
    val ally: Boolean
) : ClanEvent(clan), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        private val HANDLERS = handlers()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
