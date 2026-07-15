package com.psdk.thepit;

import com.psdk.PSDK;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ThreadLocalRandom;

public class KitManager {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;

    public KitManager(PSDK plugin) {
        this.plugin = plugin;
    }

    public void give(Player p) {
        if (hasMeaningfulInventory(p)) return;

        p.getInventory().clear();

        giveArmor(p);
        giveWeapons(p);
        giveBow(p);
        giveEssentials(p);
        giveExtras(p);
        p.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));

        KitLootManager kl = plugin.getKitLootManager();
        if (kl != null) {
            for (int slot : kl.slotsWithLoot()) {
                int roll = ThreadLocalRandom.current().nextInt(100) + 1;
                int acc = 0;
                for (KitLootManager.LootEntry e : kl.getEntries(slot)) {
                    acc += e.chance();
                    if (roll <= acc) {
                        ItemStack copy = e.item().clone();
                        copy.setAmount(Math.max(1, e.amount()));
                        if (slot >= 0 && slot <= 40) {
                            p.getInventory().setItem(slot, copy);
                        }
                        break;
                    }
                }
            }
        }

        // ── Garantias do kit (rodam DEPOIS do loot, então nunca são sobrescritas) ──
        p.getInventory().setItem(5, new ItemStack(Material.IRON_PICKAXE)); // picareta de ferro garantida
        ensureWeapon(p);                                                   // sempre uma arma de melee (depois da picareta)
    }

    /** Não sobrescreve inventário se o jogador já trouxe itens do .dat (loot, EC, etc.). */
    private boolean hasMeaningfulInventory(Player p) {
        for (ItemStack item : p.getInventory().getStorageContents()) {
            if (item != null && !item.getType().isAir()) return true;
        }
        for (ItemStack item : p.getInventory().getArmorContents()) {
            if (item != null && !item.getType().isAir()) return true;
        }
        ItemStack offhand = p.getInventory().getItemInOffHand();
        return offhand != null && !offhand.getType().isAir();
    }

    /** Garante pelo menos uma arma de melee (espada/machado). Se não tiver, dá uma espada de ferro. */
    private void ensureWeapon(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it == null) continue;
            String n = it.getType().name();
            if (n.endsWith("_SWORD") || n.endsWith("_AXE")) return;   // já tem arma
        }
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        enchantWeapon(sword);
        ItemStack slot0 = p.getInventory().getItem(0);
        if (slot0 == null || slot0.getType().isAir()) p.getInventory().setItem(0, sword);
        else p.getInventory().addItem(sword);
    }

    private void giveArmor(Player p) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int roll = rng.nextInt(1000);

        Material helmet, chest, legs, boots;

        if (roll < 500) {
            helmet = Material.IRON_HELMET;
            chest = Material.CHAINMAIL_CHESTPLATE;
            legs = Material.CHAINMAIL_LEGGINGS;
            boots = Material.IRON_BOOTS;
        } else if (roll < 750) {
            helmet = Material.IRON_HELMET;
            chest = Material.IRON_CHESTPLATE;
            legs = Material.IRON_LEGGINGS;
            boots = Material.IRON_BOOTS;
        } else if (roll < 900) {
            helmet = Material.IRON_HELMET;
            chest = Material.IRON_CHESTPLATE;
            legs = Material.IRON_LEGGINGS;
            boots = Material.IRON_BOOTS;
            int diamondPiece = rng.nextInt(4);
            switch (diamondPiece) {
                case 0 -> helmet = Material.DIAMOND_HELMET;
                case 1 -> chest = Material.DIAMOND_CHESTPLATE;
                case 2 -> legs = Material.DIAMOND_LEGGINGS;
                case 3 -> boots = Material.DIAMOND_BOOTS;
            }
        } else if (roll < 960) {
            helmet = Material.DIAMOND_HELMET;
            chest = Material.DIAMOND_CHESTPLATE;
            legs = Material.DIAMOND_LEGGINGS;
            boots = Material.DIAMOND_BOOTS;
        } else {
            helmet = Material.CHAINMAIL_HELMET;
            chest = Material.CHAINMAIL_CHESTPLATE;
            legs = Material.CHAINMAIL_LEGGINGS;
            boots = Material.CHAINMAIL_BOOTS;
            int diamondPiece = rng.nextInt(4);
            switch (diamondPiece) {
                case 0 -> helmet = Material.DIAMOND_HELMET;
                case 1 -> chest = Material.DIAMOND_CHESTPLATE;
                case 2 -> legs = Material.DIAMOND_LEGGINGS;
                case 3 -> boots = Material.DIAMOND_BOOTS;
            }
        }

        if (rng.nextInt(10) == 0) {
            ItemStack turtleHelmet = new ItemStack(Material.TURTLE_HELMET);
            ItemMeta meta = turtleHelmet.getItemMeta();
            if (meta != null) {
                meta.displayName(mm.deserialize("<!italic><#10fc46>Capacete do Beta"));
                turtleHelmet.setItemMeta(meta);
            }
            enchantArmor(turtleHelmet);
            p.getInventory().setHelmet(turtleHelmet);
        } else {
            ItemStack h = new ItemStack(helmet);
            enchantArmor(h);
            p.getInventory().setHelmet(h);
        }

        ItemStack c = new ItemStack(chest);
        enchantArmor(c);
        p.getInventory().setChestplate(c);

        ItemStack l = new ItemStack(legs);
        enchantArmor(l);
        p.getInventory().setLeggings(l);

        ItemStack b = new ItemStack(boots);
        enchantArmor(b);
        p.getInventory().setBoots(b);
    }

    private void enchantArmor(ItemStack item) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int roll = rng.nextInt(100);

        if (roll < 50) return;
        else if (roll < 70) item.addUnsafeEnchantment(Enchantment.PROTECTION, 1);
        else if (roll < 82) item.addUnsafeEnchantment(Enchantment.PROTECTION, 2);
        else if (roll < 89) item.addUnsafeEnchantment(Enchantment.FIRE_PROTECTION, 1);
        else if (roll < 94) item.addUnsafeEnchantment(Enchantment.BLAST_PROTECTION, 1);
        else if (roll < 98) item.addUnsafeEnchantment(Enchantment.PROJECTILE_PROTECTION, 1);
        else item.addUnsafeEnchantment(Enchantment.THORNS, 1);
    }

    private void giveWeapons(Player p) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        Material[] weapons = {
                Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD,
                Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_AXE,
                Material.CROSSBOW
        };

        Material primary = weapons[rng.nextInt(weapons.length)];
        ItemStack weapon1 = new ItemStack(primary);
        enchantWeapon(weapon1);
        p.getInventory().setItem(0, weapon1);

        if (rng.nextInt(4) == 0) {
            Material secondary;
            do {
                secondary = weapons[rng.nextInt(weapons.length)];
            } while (secondary == primary);
            ItemStack weapon2 = new ItemStack(secondary);
            enchantWeapon(weapon2);
            p.getInventory().setItem(1, weapon2);
        }
    }

    private void enchantWeapon(ItemStack item) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Material mat = item.getType();
        boolean isSword = mat.name().contains("SWORD");
        boolean isCrossbow = mat == Material.CROSSBOW;

        int enchantCount = rng.nextInt(3);
        for (int i = 0; i < enchantCount; i++) {
            if (isCrossbow) {
                int pick = rng.nextInt(3);
                switch (pick) {
                    case 0 -> item.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, rng.nextInt(1, 3));
                    case 1 -> item.addUnsafeEnchantment(Enchantment.UNBREAKING, rng.nextInt(1, 3));
                    case 2 -> item.addUnsafeEnchantment(Enchantment.MULTISHOT, 1);
                }
            } else {
                int pick = rng.nextInt(100);
                if (pick < 35) {
                    item.addUnsafeEnchantment(Enchantment.SHARPNESS, rng.nextInt(1, 3));
                } else if (pick < 65) {
                    item.addUnsafeEnchantment(Enchantment.SMITE, rng.nextInt(1, 3));
                } else if (pick < 85 && isSword) {
                    item.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, rng.nextInt(1, 3));
                } else {
                    item.addUnsafeEnchantment(Enchantment.UNBREAKING, rng.nextInt(1, 3));
                }
            }
        }
    }

    private void giveBow(Player p) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.CROSSBOW) return;
        }

        ItemStack bow = new ItemStack(Material.BOW);
        int roll = rng.nextInt(100);
        if (roll < 30) bow.addUnsafeEnchantment(Enchantment.POWER, 1);
        else if (roll < 50) bow.addUnsafeEnchantment(Enchantment.POWER, 2);
        else if (roll < 65) bow.addUnsafeEnchantment(Enchantment.PUNCH, 1);
        else if (roll < 75) bow.addUnsafeEnchantment(Enchantment.FLAME, 1);

        p.getInventory().setItem(2, bow);
    }

    private void giveEssentials(Player p) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        p.getInventory().setItem(3, new ItemStack(Material.ARROW, rng.nextInt(16, 49)));
        p.getInventory().setItem(4, new ItemStack(Material.COOKED_BEEF, 32));
        p.getInventory().setItem(8, new ItemStack(Material.OAK_LOG, 64));
    }

    private void giveExtras(Player p) {
        if (ThreadLocalRandom.current().nextInt(100) == 0) {
            p.getInventory().setItem(6, new ItemStack(Material.TOTEM_OF_UNDYING));
        }
    }
}
