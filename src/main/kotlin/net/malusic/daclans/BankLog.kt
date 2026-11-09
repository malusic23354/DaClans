package net.malusic.daclans

import java.util.UUID

data class BankLog(
    val id: UUID = UUID.randomUUID(),
    val clanId: UUID,
    val player: UUID?,
    val type: Type,
    val amountCents: Long,
    val balanceCents: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    enum class Type { DEPOSIT, WITHDRAW, UPGRADE }
}
