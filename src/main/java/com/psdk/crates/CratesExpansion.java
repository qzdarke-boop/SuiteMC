package com.psdk.crates;

import com.psdk.PSDK;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CratesExpansion extends PlaceholderExpansion {

    private final PSDK plugin;

    public CratesExpansion(PSDK plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "crates"; }
    @Override public @NotNull String getAuthor() { return "PSDK"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("tokens") && player != null) {
            return com.psdk.util.NumberUtil.abbrev(plugin.getEconomyManager().getTokens(player.getUniqueId()));
        }

        if (params.startsWith("saldo_") && player instanceof Player p) {
            String crateId = params.substring(6);
            // getSaldoCached: NÃO toca no banco (placeholders rodam em thread assíncrona).
            int saldo = plugin.getCrateManager().getSaldoCached(p.getUniqueId(), crateId);
            Crate crate = plugin.getCrateManager().getCrate(crateId);
            if (crate != null) {
                return crate.getCor() + saldo;
            }
            return String.valueOf(saldo);
        }

        // Igual ao saldo_, mas SEM a cor da caixa (só o número) — ideal para hologramas
        // onde você quer controlar a cor manualmente.
        if (params.startsWith("qtd_") && player instanceof Player p) {
            String crateId = params.substring(4);
            return String.valueOf(plugin.getCrateManager().getSaldoCached(p.getUniqueId(), crateId));
        }

        if (params.startsWith("restantes_")) {
            String crateId = params.substring(10);
            Crate crate = plugin.getCrateManager().getCrate(crateId);
            if (crate == null) return "0";
            int restantes = crate.getLimiteGlobal();
            return restantes < 0 ? "∞" : String.valueOf(Math.max(0, restantes));
        }

        if (params.startsWith("tipo_")) {
            String crateId = params.substring(5);
            Crate crate = plugin.getCrateManager().getCrate(crateId);
            if (crate == null || crate.getTipo() == null) return "?";
            return crate.getTipo().name();
        }

        return null;
    }
}