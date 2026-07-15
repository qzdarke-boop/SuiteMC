package com.psdk.crates;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class KeyActivateListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;
    private final java.util.Map<java.util.UUID, Long> lastUse = new java.util.concurrent.ConcurrentHashMap<>();

    public KeyActivateListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack itemNaMao = player.getInventory().getItemInMainHand();
        if (itemNaMao.getType().isAir()) return;

        String crateName = plugin.getKeyManager().getCrateNameFromKey(itemNaMao);
        if (crateName == null) return;

        Crate crate = plugin.getCrateManager().getCrate(crateName);
        if (crate == null) return;
        if (!plugin.getKeyManager().isValidKey(itemNaMao, crate)) return;

        event.setCancelled(true);

        // Cooldown anti clique-duplo (evita ativar a mesma chave 2x no mesmo instante).
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < 300) return;

        boolean isSneaking = player.isSneaking();
        int amountToActivate = isSneaking ? itemNaMao.getAmount() : 1;

        int saldoAntes = plugin.getCrateManager().getSaldo(player.getUniqueId(), crateName);
        plugin.getCrateManager().addSaldo(player.getUniqueId(), crateName, amountToActivate);
        int novoSaldo = plugin.getCrateManager().getSaldo(player.getUniqueId(), crateName);
        if (novoSaldo < saldoAntes + amountToActivate) {
            player.sendActionBar(mm.deserialize("<#FF0000>Erro ao ativar a chave. Tente novamente."));
            return;
        }

        // Cooldown só após sucesso confirmado.
        lastUse.put(player.getUniqueId(), now);

        if (isSneaking || itemNaMao.getAmount() == 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            itemNaMao.setAmount(itemNaMao.getAmount() - 1);
        }

        player.getWorld().spawnParticle(Particle.ENCHANT,
                player.getLocation().add(0, 1.2, 0), 20, 0.3, 0.3, 0.3, 0.5);

        player.sendActionBar(mm.deserialize(crate.getCor() + "Você ativou " + amountToActivate + " chave(s) da caixa " + crateName));
    }
}
