package com.psdk.lootchest;

import com.psdk.pitems.PSDKItems;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Geração de loot aleatório por raridade.
 *
 * Regra-mãe: armadura recebe PROTECTION, armas (espada/machado) recebem
 * SHARPNESS. NUNCA colocar Protection em espada.
 *
 * As porcentagens seguem o pedido e ficam centralizadas aqui (fáceis de ajustar).
 */
public final class LootTable {

    private LootTable() {}

    private static final long TRAP_DURATION = 604_800_000L; // 7 dias

    private static final Material[] DIAMOND_ARMOR = {
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
    };
    private static final Material[] NETHERITE_ARMOR = {
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS
    };
    private static final Material[] FILLER = {
            Material.COOKED_BEEF, Material.GOLDEN_CARROT, Material.ARROW,
            Material.EXPERIENCE_BOTTLE, Material.ENDER_PEARL
    };

    /** Gera a lista de itens de loot para uma raridade. */
    public static List<ItemStack> generate(LootRarity rarity) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<ItemStack> loot = new ArrayList<>();

        switch (rarity) {
            case NORMAL -> {
                int n = r.nextInt(3, 6); // 3-5
                for (int i = 0; i < n; i++) loot.add(normalItem(r));
            }
            case RARO -> {
                int n = r.nextInt(4, 7); // 4-6
                for (int i = 0; i < n; i++) loot.add(raroItem(r));
            }
            case EPICO -> {
                int n = r.nextInt(4, 7); // 4-6 (nerf: menos itens)
                for (int i = 0; i < n; i++) loot.add(epicoItem(r));
            }
            case LENDARIO -> {
                int n = r.nextInt(6, 10); // 6-9
                for (int i = 0; i < n; i++) loot.add(lendarioItem(r));
            }
        }
        return loot;
    }

    // ---------------- NORMAL: itens "podres" (P1 / Sharpness 1) ----------------
    private static ItemStack normalItem(ThreadLocalRandom r) {
        int roll = r.nextInt(100);
        if (roll < 40) return armor(DIAMOND_ARMOR[r.nextInt(4)], Enchantment.PROTECTION, 1, 0);
        if (roll < 60) return weapon(Material.DIAMOND_SWORD, 1, 0);
        if (roll < 75) return weapon(Material.DIAMOND_AXE, 1, 0);
        if (roll < 85) return bow(1);
        return filler(r);
    }

    // ---------------- RARO: P2 / Sharpness 2 (6% de subir para 3) --------------
    private static ItemStack raroItem(ThreadLocalRandom r) {
        int roll = r.nextInt(100);
        int unb = r.nextInt(100) < 30 ? 1 : 0;
        if (roll < 40) {
            int prot = r.nextInt(100) < 6 ? 3 : 2; // 6% P3
            return armor(DIAMOND_ARMOR[r.nextInt(4)], Enchantment.PROTECTION, prot, unb);
        }
        if (roll < 65) {
            int sharp = r.nextInt(100) < 6 ? 3 : 2; // 6% Sharp3
            return weapon(Material.DIAMOND_SWORD, sharp, unb);
        }
        if (roll < 80) {
            int sharp = r.nextInt(100) < 6 ? 3 : 2;
            return weapon(Material.DIAMOND_AXE, sharp, unb);
        }
        if (roll < 90) return bow(2);
        return filler(r);
    }

    // ---------------- ÉPICO (nerfado: menos OP) -------------------------------
    // 15% totem | 5% espada netherite S2 unb1 | 22% netherite P2 (ocas. unb1) |
    // 33% dima P3 unb1 | 13% dima sword S3 unb1 | 12% filler
    // Removido: P4 garantido, netherite P3 comum e a espada S3 — agora é "bom",
    // não "endgame".
    private static ItemStack epicoItem(ThreadLocalRandom r) {
        int roll = r.nextInt(100);
        if (roll < 15) return totem();
        if (roll < 20) return weapon(Material.NETHERITE_SWORD, 2, 1);
        if (roll < 42) {
            int unb = r.nextInt(100) < 40 ? 1 : 0;
            return armor(NETHERITE_ARMOR[r.nextInt(4)], Enchantment.PROTECTION, 2, unb);
        }
        if (roll < 75) return armor(DIAMOND_ARMOR[r.nextInt(4)], Enchantment.PROTECTION, 3, 1);
        if (roll < 88) return weapon(Material.DIAMOND_SWORD, 3, 1);
        return filler(r);
    }

    // ---------------- LENDÁRIO ------------------------------------------------
    // 1% trap | 8% maçã encantada | 16% maçã | 30% totem | 15% livro |
    // 30% gear (85% netherite P3/P4, 15% dima P4 unb3)
    private static ItemStack lendarioItem(ThreadLocalRandom r) {
        int roll = r.nextInt(100);
        if (roll < 1)  return PSDKItems.create(PSDKItems.ItemType.TRAP, TRAP_DURATION);
        if (roll < 9)  return new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
        if (roll < 25) return new ItemStack(Material.GOLDEN_APPLE, r.nextInt(1, 3));
        if (roll < 55) return totem();
        if (roll < 70) {
            // Livro: Protection IV ou Sharpness V
            return r.nextBoolean()
                    ? book(Enchantment.PROTECTION, 4)
                    : book(Enchantment.SHARPNESS, 5);
        }
        // gear restante (30%)
        if (r.nextInt(100) < 85) {
            // netherite
            if (r.nextInt(100) < 25) return weapon(Material.NETHERITE_SWORD, r.nextBoolean() ? 4 : 5, 3);
            int prot = r.nextInt(100) < 50 ? 4 : 3;
            return armor(NETHERITE_ARMOR[r.nextInt(4)], Enchantment.PROTECTION, prot, 3);
        }
        // 15% diamante P4 unbreaking 3
        return armor(DIAMOND_ARMOR[r.nextInt(4)], Enchantment.PROTECTION, 4, 3);
    }

    // ---------------- helpers de construção -----------------------------------

    private static ItemStack armor(Material mat, Enchantment ench, int level, int unbreaking) {
        ItemStack item = new ItemStack(mat);
        if (level > 0) item.addUnsafeEnchantment(ench, level);
        if (unbreaking > 0) item.addUnsafeEnchantment(Enchantment.UNBREAKING, unbreaking);
        return item;
    }

    /** Arma corpo-a-corpo: recebe SHARPNESS (jamais Protection). */
    private static ItemStack weapon(Material mat, int sharpness, int unbreaking) {
        ItemStack item = new ItemStack(mat);
        if (sharpness > 0) item.addUnsafeEnchantment(Enchantment.SHARPNESS, sharpness);
        if (unbreaking > 0) item.addUnsafeEnchantment(Enchantment.UNBREAKING, unbreaking);
        return item;
    }

    private static ItemStack bow(int power) {
        ItemStack item = new ItemStack(Material.BOW);
        if (power > 0) item.addUnsafeEnchantment(Enchantment.POWER, power);
        return item;
    }

    private static ItemStack totem() {
        return new ItemStack(Material.TOTEM_OF_UNDYING);
    }

    private static ItemStack book(Enchantment ench, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(ench, level, true);
            book.setItemMeta(meta);
        }
        return book;
    }

    private static ItemStack filler(ThreadLocalRandom r) {
        Material mat = FILLER[r.nextInt(FILLER.length)];
        int amount = switch (mat) {
            case ARROW -> r.nextInt(8, 33);
            case COOKED_BEEF, GOLDEN_CARROT -> r.nextInt(2, 9);
            case EXPERIENCE_BOTTLE -> r.nextInt(4, 17);
            default -> r.nextInt(1, 5);
        };
        return new ItemStack(mat, amount);
    }
}
