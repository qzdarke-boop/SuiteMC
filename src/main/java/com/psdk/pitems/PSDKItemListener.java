package com.psdk.pitems;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import com.psdk.thepit.BlockMineListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class PSDKItemListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    /** Duração das teias da TRAP, em ticks (7 segundos). */
    private static final long TRAP_WEB_TICKS = 140L;

    private final PSDK plugin;
    private final NamespacedKey keyTrapProjectile;
    /** Locais das teias da TRAP ainda ativas — não podem ser quebradas. */
    private final java.util.Set<org.bukkit.Location> trapWebs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public PSDKItemListener(PSDK plugin) {
        this.plugin = plugin;
        this.keyTrapProjectile = new NamespacedKey(plugin, "trap_projectile");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        EquipmentSlot slot = findTotemSlot(player);
        if (slot == null) return;

        ItemStack totem = player.getInventory().getItem(slot);
        if (totem == null) return;

        String typeId = PSDKItems.getItemTypeId(totem);
        if (!"Totem_INFERNAL".equals(typeId)) return;

        if (PSDKItems.isExpired(totem)) return;

        event.setCancelled(false);

        int uses = PSDKItems.getTotemUses(totem);
        uses--;

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 1, 0));

        if (uses > 0) {
            PSDKItems.setTotemUses(totem, uses);
            player.getInventory().setItem(slot, totem);
        } else {
            player.getInventory().setItem(slot, null);
        }

        player.sendMessage(mm.deserialize("<#FF0000>Você usou seu totem infernal! <#FF0000>Usos restantes: <#10fc46>" + Math.max(uses, 0)));
    }

    private EquipmentSlot findTotemSlot(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isCustomTotem(mainHand)) return EquipmentSlot.HAND;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isCustomTotem(offHand)) return EquipmentSlot.OFF_HAND;

        return null;
    }

    private boolean isCustomTotem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return "Totem_INFERNAL".equals(PSDKItems.getItemTypeId(item));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // Teia da TRAP é INQUEBRÁVEL enquanto ativa (prende o jogador pelos 7s).
        if (event.getBlock().getType() == Material.COBWEB
                && trapWebs.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();

        String typeId = PSDKItems.getItemTypeId(tool);
        if (!"Picareta_Infernal".equals(typeId)) return;

        Block broken = event.getBlock();
        // Captura o material ANTES de qualquer quebra (depois vira AR e o bônus se perde).
        Material oreMaterial = broken.getType();
        if (!isOre(oreMaterial)) return;
        event.setDropItems(false);   // dá COINS no lugar do drop físico (senão era coin + minério = exploit)

        if (PSDKItems.isExpired(tool)) {
            event.setCancelled(true);
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(mm.deserialize("<#FF0000>Sua <gradient:#FF4500:#FF0000:#8B0000>Picareta Infernal</gradient> <#FF0000>expirou!"));
            return;
        }

        // Sem vein mining: quebra apenas o bloco clicado (o evento segue seu fluxo normal).
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        double fortuneBonus = switch (fortuneLevel) {
            case 1 -> 2; case 2 -> 5; case 3 -> 10; case 4 -> 18; case 5 -> 25; default -> 0;
        };

        BlockMineListener.MineableBlock mineData = BlockMineListener.MINEABLE.get(oreMaterial);
        if (mineData != null) {
            double totalCoins = (mineData.coins() + fortuneBonus) * 1.3;
            plugin.getEconomyManager().addCoins(player.getUniqueId(), player.getName(), totalCoins);
            player.sendMessage(mm.deserialize("<#FF4500><bold>+</bold>" + String.format("%.0f", totalCoins) + " coins <#848c94>(x1.3 Picareta Infernal)"));
        }
    }

    private boolean isOre(Material material) {
        return material.name().contains("ORE");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        String typeId = PSDKItems.getItemTypeId(item);
        if (!"Netherite_Golden_Apple".equals(typeId)) return;

        if (PSDKItems.isExpired(item)) {
            event.setCancelled(true);
            int slot = player.getInventory().first(item);
            if (slot >= 0) player.getInventory().setItem(slot, null);
            player.sendMessage(mm.deserialize("<#FF0000>Sua <gradient:#FFD700:#FF8C00>Maçã de Netherite</gradient> <#FF0000>expirou!"));
            return;
        }

        event.setCancelled(true);

        int slot = player.getInventory().first(item);
        if (slot >= 0) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    player.getInventory().setItem(slot, null);
                }
            }
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 4800, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 12000, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 12000, 0));

        player.sendMessage(mm.deserialize("<#FFD700>Maçã de netherite consumida! Boa sorte!"));
    }

    // ==================== TRAP (bola de neve que gera teias) ====================

    // Lançamento via PlayerLaunchProjectileEvent (Paper): permite NÃO consumir o item
    // quando a tentativa é inválida (fora da arena / expirado), igual ao Troque de Posição.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTrapLaunch(PlayerLaunchProjectileEvent event) {
        ItemStack thrown = event.getItemStack();
        if (!"TRAP".equals(PSDKItems.getItemTypeId(thrown))) return;
        if (!(event.getProjectile() instanceof Snowball snowball)) return;

        Player player = event.getPlayer();

        // Versão temporária expirada: não ativa nem consome (a task remove em seguida).
        if (PSDKItems.isExpired(thrown)) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            return;
        }

        // Só pode ser usada na área ATIVA de PvP (dentro da arena E com PvP liberado).
        // Tentativa inválida não consome o item.
        if (!isPvpArena(player.getLocation())) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            player.sendActionBar(mm.deserialize("<#e22c27>Você só pode usar este item na arena de PvP!"));
            return;
        }

        // Marca o projétil para identificá-lo no impacto.
        snowball.getPersistentDataContainer().set(keyTrapProjectile, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrapHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!snowball.getPersistentDataContainer().has(keyTrapProjectile, PersistentDataType.BYTE)) return;

        // Centro da armadilha: o bloco de AR adjacente à face atingida (ou onde o
        // projétil parou). Assim a teia nasce no espaço livre, não dentro do bloco.
        Block center;
        if (event.getHitBlock() != null && event.getHitBlockFace() != null) {
            center = event.getHitBlock().getRelative(event.getHitBlockFace());
        } else {
            center = snowball.getLocation().getBlock();
        }

        // Revalida a REGIÃO no impacto: a teia só nasce se o centro estiver na área
        // ATIVA de PvP (o projétil pode ter saído da arena depois do lançamento).
        if (!isPvpArena(center.getLocation())) return;

        placeWebTrap(center);
    }

    /** Área ATIVA de PvP = dentro dos limites da arena E com PvP liberado (não é zona segura). */
    private boolean isPvpArena(Location loc) {
        if (loc == null) return false;
        return plugin.getArenaManager().isInsideArena(loc)
                && plugin.getRegionManager().isAllowed(loc, RegionFlag.PVP);
    }

    /**
     * Cria um quadrado 3x3 de teias no plano horizontal em volta de {@code center}.
     * Só substitui blocos de AR — paredes/blocos existentes ficam intactos ("do
     * tamanho que conseguir") — e remove as teias colocadas após 7 segundos.
     */
    private void placeWebTrap(Block center) {
        List<Block> placed = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                if (b.getType().isAir()) {
                    b.setType(Material.COBWEB, false);
                    trapWebs.add(b.getLocation());   // marca a teia como inquebrável
                    placed.add(b);
                }
            }
        }
        if (placed.isEmpty()) return;

        // Remove apenas as teias que nós colocamos e que ainda forem teia.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Block b : placed) {
                trapWebs.remove(b.getLocation());   // volta a poder quebrar
                if (b.getType() == Material.COBWEB) b.setType(Material.AIR, false);
            }
        }, TRAP_WEB_TICKS);
    }
}
