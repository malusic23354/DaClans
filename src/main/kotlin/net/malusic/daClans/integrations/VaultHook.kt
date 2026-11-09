package net.malusic.daclans.integrations

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer

class VaultHook {

    private fun eco(): Economy? = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider

    fun available(): Boolean = eco() != null

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean =
        eco()?.withdrawPlayer(player, amount)?.transactionSuccess() == true

    fun deposit(player: OfflinePlayer, amount: Double): Boolean =
        eco()?.depositPlayer(player, amount)?.transactionSuccess() == true
}
