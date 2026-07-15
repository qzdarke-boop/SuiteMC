package com.psdk.kits;

import com.psdk.PSDK;
import com.psdk.clan.ClanColorKeyManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Kits VIP com layout fixo em slots de inventário 54. */
public enum VipKit {

    PREMIUM_1D("Kit Premium 1D", "premium_1d",
            TimeUnit.DAYS.toMillis(1),
            slots(
                    premArmor(13, Material.IRON_HELMET, 2),
                    premTool(14, Material.IRON_SWORD, Map.of(
                            Enchantment.SHARPNESS, 1, Enchantment.UNBREAKING, 1)),
                    premExtra(21, Material.COOKED_BEEF, 16),
                    premArmor(22, Material.IRON_CHESTPLATE, 2),
                    premTool(23, Material.IRON_PICKAXE, Map.of()),
                    premExtra(30, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 1)),
                    premArmor(31, Material.IRON_LEGGINGS, 2),
                    premTool(32, Material.IRON_AXE, Map.of(Enchantment.EFFICIENCY, 2)),
                    premArmor(40, Material.IRON_BOOTS, 2)
            )),

    PREMIUM_7D("Kit Premium 7D", "premium_7d",
            TimeUnit.DAYS.toMillis(7),
            slots(
                    premArmor(13, Material.IRON_HELMET, 4),
                    premTool(14, Material.IRON_AXE, Map.of(Enchantment.EFFICIENCY, 3)),
                    premExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 1)),
                    premArmor(22, Material.IRON_CHESTPLATE, 4),
                    premTool(23, Material.IRON_PICKAXE, Map.of(Enchantment.EFFICIENCY, 3)),
                    premExtra(30, Material.COOKED_BEEF, 32),
                    premArmor(31, Material.IRON_LEGGINGS, 4),
                    premTool(32, Material.IRON_SWORD, Map.of(
                            Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 3)),
                    premExtra(39, Material.GOLDEN_APPLE, 1),
                    premArmor(40, Material.IRON_BOOTS, 4)
            )),

    PREMIUM_30D("Kit Premium 30D", "premium_30d",
            TimeUnit.DAYS.toMillis(30),
            slots(
                    premArmor(13, Material.DIAMOND_HELMET, 4),
                    premTool(14, Material.DIAMOND_PICKAXE, Map.of(
                            Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3)),
                    premExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 2)),
                    premArmor(22, Material.DIAMOND_CHESTPLATE, 4),
                    premTool(23, Material.DIAMOND_AXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 3)),
                    premExtra(30, Material.COOKED_BEEF, 64),
                    premArmor(31, Material.DIAMOND_LEGGINGS, 4),
                    premTool(32, Material.DIAMOND_SWORD, Map.of(
                            Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 3)),
                    premExtra(39, Material.GOLDEN_APPLE, 8),
                    premArmor(40, Material.DIAMOND_BOOTS, 4)
            )),

    ELITE_1D("Kit Elite 1D", "elite_1d",
            TimeUnit.DAYS.toMillis(1),
            slots(
                    eliteArmor(13, Material.DIAMOND_HELMET, Map.of(
                            Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 1)),
                    eliteTool(14, Material.DIAMOND_PICKAXE, Map.of()),
                    eliteExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 2)),
                    eliteArmor(22, Material.DIAMOND_CHESTPLATE, Map.of(
                            Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 1)),
                    eliteTool(23, Material.DIAMOND_AXE, Map.of(Enchantment.EFFICIENCY, 2)),
                    eliteExtra(30, Material.GOLDEN_CARROT, 32),
                    eliteArmor(31, Material.DIAMOND_LEGGINGS, Map.of(
                            Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 1)),
                    eliteTool(32, Material.DIAMOND_SWORD, Map.of(Enchantment.SHARPNESS, 2)),
                    eliteExtra(39, Material.GOLDEN_APPLE, 1),
                    eliteArmor(40, Material.DIAMOND_BOOTS, Map.of(
                            Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 1))
            )),

    ELITE_7D("Kit Elite 7D", "elite_7d",
            TimeUnit.DAYS.toMillis(7),
            slots(
                    eliteArmor(13, Material.DIAMOND_HELMET, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 2)),
                    eliteTool(14, Material.DIAMOND_AXE, Map.of(Enchantment.EFFICIENCY, 3)),
                    eliteExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 2)),
                    eliteArmor(22, Material.DIAMOND_CHESTPLATE, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 2)),
                    eliteTool(23, Material.DIAMOND_PICKAXE, Map.of(Enchantment.EFFICIENCY, 3)),
                    eliteExtra(30, Material.GOLDEN_APPLE, 8),
                    eliteArmor(31, Material.DIAMOND_LEGGINGS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 2)),
                    eliteTool(32, Material.DIAMOND_SWORD, Map.of(
                            Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 2)),
                    eliteExtra(39, Material.GOLDEN_CARROT, 16),
                    eliteArmor(40, Material.DIAMOND_BOOTS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 2))
            )),

    ELITE_30D("Kit Elite 30D", "elite_30d",
            TimeUnit.DAYS.toMillis(30),
            slots(
                    eliteArmor(13, Material.NETHERITE_HELMET, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    eliteTool(14, Material.DIAMOND_PICKAXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 2)),
                    eliteExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 2)),
                    eliteArmor(22, Material.DIAMOND_CHESTPLATE, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    eliteTool(23, Material.DIAMOND_AXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 2)),
                    eliteExtra(30, Material.GOLDEN_CARROT, 64),
                    eliteArmor(31, Material.DIAMOND_LEGGINGS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    eliteTool(32, Material.DIAMOND_SWORD, Map.of(
                            Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3)),
                    eliteExtra(41, Material.GOLDEN_APPLE, 16),
                    eliteArmor(40, Material.NETHERITE_BOOTS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3))
            )),

    ETERNAL_1D("Kit Eternal 1D", "eternal_1d",
            TimeUnit.DAYS.toMillis(1),
            slots(
                    eternArmor(13, Material.DIAMOND_HELMET, 2),
                    eternTool(14, Material.NETHERITE_PICKAXE, Map.of()),
                    eternExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 3)),
                    eternArmor(22, Material.NETHERITE_CHESTPLATE, 2),
                    eternTool(23, Material.NETHERITE_AXE, Map.of(Enchantment.EFFICIENCY, 2)),
                    eternExtra(24, Material.TOTEM_OF_UNDYING, 1),
                    eternExtra(30, Material.GOLDEN_APPLE, 5),
                    eternArmor(31, Material.NETHERITE_LEGGINGS, 2),
                    eternTool(32, Material.NETHERITE_SWORD, Map.of(Enchantment.SHARPNESS, 2)),
                    eternExtra(39, Material.GOLDEN_APPLE, 1),
                    eternArmor(40, Material.DIAMOND_BOOTS, 2)
            )),

    ETERNAL_7D("Kit Eternal 7D", "eternal_7d",
            TimeUnit.DAYS.toMillis(7),
            slots(
                    pkt(12, ClanColorKeyManager.PacketType.QUALQUER),
                    eternArmor(13, Material.NETHERITE_HELMET, 2),
                    eternTool(14, Material.NETHERITE_PICKAXE, Map.of(Enchantment.EFFICIENCY, 3)),
                    eternExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 3)),
                    eternArmor(22, Material.NETHERITE_CHESTPLATE, 2),
                    eternTool(23, Material.NETHERITE_AXE, Map.of(Enchantment.EFFICIENCY, 3)),
                    eternExtra(24, Material.TOTEM_OF_UNDYING, 1),
                    eternExtra(30, Material.GOLDEN_APPLE, 10),
                    eternArmor(31, Material.NETHERITE_LEGGINGS, 2),
                    eternTool(32, Material.NETHERITE_SWORD, Map.of(
                            Enchantment.SHARPNESS, 3, Enchantment.FIRE_ASPECT, 2)),
                    eternExtra(39, Material.ENCHANTED_GOLDEN_APPLE, 5),
                    eternArmor(40, Material.NETHERITE_BOOTS, 2),
                    pkt(41, ClanColorKeyManager.PacketType.ANIMADA)
            )),

    ETERNAL_30D("Kit Eternal 30D", "eternal_30d",
            TimeUnit.DAYS.toMillis(30),
            slots(
                    pkt(12, ClanColorKeyManager.PacketType.QUALQUER),
                    eternArmor(13, Material.NETHERITE_HELMET, 3),
                    eternTool(14, Material.NETHERITE_PICKAXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 3)),
                    eternExtra(21, Material.SHIELD, 1),
                    eternArmor(22, Material.NETHERITE_CHESTPLATE, 3),
                    eternTool(23, Material.NETHERITE_AXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 3)),
                    eternExtra(24, Material.GOLDEN_APPLE, 1),
                    eternExtra(30, Material.GOLDEN_APPLE, 24),
                    eternArmor(31, Material.NETHERITE_LEGGINGS, 3),
                    eternTool(32, Material.NETHERITE_SWORD, Map.of(
                            Enchantment.SHARPNESS, 4, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3)),
                    eternExtra(39, Material.ENCHANTED_GOLDEN_APPLE, 2),
                    eternArmor(40, Material.NETHERITE_BOOTS, 3),
                    pkt(41, ClanColorKeyManager.PacketType.ANIMADA)
            )),

    SUITE_1D("Kit Suite 1D", "suite_1d",
            TimeUnit.DAYS.toMillis(1),
            slots(
                    pkt(12, ClanColorKeyManager.PacketType.QUALQUER, 2),
                    suiteArmor(13, Material.NETHERITE_HELMET, Map.of(
                            Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
                            TrimPattern.SENTRY, TrimMaterial.GOLD),
                    suiteTool(14, Material.NETHERITE_PICKAXE, Map.of(
                            Enchantment.EFFICIENCY, 5, Enchantment.MENDING, 1)),
                    suiteExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 4)),
                    suiteArmor(22, Material.NETHERITE_CHESTPLATE, Map.of(
                            Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
                            TrimPattern.SENTRY, TrimMaterial.GOLD),
                    suiteTool(23, Material.NETHERITE_AXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.MENDING, 1)),
                    suiteExtra(30, Material.GOLDEN_APPLE, 8),
                    suiteArmor(31, Material.NETHERITE_LEGGINGS, Map.of(
                            Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
                            TrimPattern.SENTRY, TrimMaterial.GOLD),
                    suiteTool(32, Material.NETHERITE_SWORD, Map.of(
                            Enchantment.SHARPNESS, 3, Enchantment.FIRE_ASPECT, 2,
                            Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    splash(38, PotionType.SWIFTNESS),
                    suiteExtra(39, Material.GOLDEN_CARROT, 64),
                    suiteArmor(40, Material.NETHERITE_BOOTS, Map.of(
                            Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
                            TrimPattern.SENTRY, TrimMaterial.GOLD),
                    pkt(41, ClanColorKeyManager.PacketType.ANIMADA, 2),
                    splash(42, PotionType.STRENGTH)
            )),

    SUITE_7D("Kit Suite 7D", "suite_7d",
            TimeUnit.DAYS.toMillis(7),
            slots(
                    pkt(12, ClanColorKeyManager.PacketType.QUALQUER, 2),
                    suiteArmor(13, Material.NETHERITE_HELMET, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.DIAMOND),
                    suiteTool(14, Material.NETHERITE_PICKAXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.MENDING, 1)),
                    suiteExtra(21, Material.SHIELD, 1, Map.of(Enchantment.UNBREAKING, 4)),
                    suiteArmor(22, Material.NETHERITE_CHESTPLATE, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.DIAMOND),
                    suiteTool(23, Material.NETHERITE_AXE, Map.of(
                            Enchantment.EFFICIENCY, 3, Enchantment.MENDING, 1)),
                    splash(29, PotionType.SWIFTNESS),
                    suiteExtra(30, Material.GOLDEN_APPLE, 15),
                    suiteArmor(31, Material.NETHERITE_LEGGINGS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.DIAMOND),
                    suiteTool(32, Material.NETHERITE_SWORD, Map.of(
                            Enchantment.SHARPNESS, 3, Enchantment.FIRE_ASPECT, 2,
                            Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    splash(33, PotionType.STRENGTH),
                    splash(38, PotionType.SWIFTNESS),
                    suiteExtra(39, Material.GOLDEN_CARROT, 64),
                    suiteArmor(40, Material.NETHERITE_BOOTS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 4, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.DIAMOND),
                    pkt(41, ClanColorKeyManager.PacketType.ANIMADA, 2),
                    splash(42, PotionType.STRENGTH)
            )),

    SUITE_30D("Kit Suite 30D", "suite_30d",
            TimeUnit.DAYS.toMillis(30),
            slots(
                    splash(11, PotionType.SWIFTNESS),
                    pkt(12, ClanColorKeyManager.PacketType.QUALQUER, 2),
                    suiteArmor(13, Material.NETHERITE_HELMET, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.AMETHYST),
                    suiteTool(14, Material.NETHERITE_PICKAXE, Map.of(
                            Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    splash(15, PotionType.STRENGTH),
                    splash(20, PotionType.SWIFTNESS),
                    suiteExtra(21, Material.SHIELD, 1),
                    suiteArmor(22, Material.NETHERITE_CHESTPLATE, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.AMETHYST),
                    suiteTool(23, Material.NETHERITE_AXE, Map.of(
                            Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    splash(24, PotionType.STRENGTH),
                    splash(29, PotionType.SWIFTNESS),
                    suiteExtra(30, Material.ENCHANTED_GOLDEN_APPLE, 32),
                    suiteArmor(31, Material.NETHERITE_LEGGINGS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.AMETHYST),
                    suiteTool(32, Material.NETHERITE_SWORD, Map.of(
                            Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2,
                            Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    splash(33, PotionType.STRENGTH),
                    splash(38, PotionType.SWIFTNESS),
                    suiteExtra(39, Material.GOLDEN_CARROT, 64),
                    suiteArmor(40, Material.NETHERITE_BOOTS, Map.of(
                            Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 5, Enchantment.MENDING, 1),
                            TrimPattern.SILENCE, TrimMaterial.AMETHYST),
                    pkt(41, ClanColorKeyManager.PacketType.ANIMADA, 2),
                    splash(42, PotionType.STRENGTH)
            ));

    private final String displayName;
    private final String configKey;
    private final long cooldownMs;
    private final Map<Integer, KitSlot> slots;

    VipKit(String displayName, String configKey, long cooldownMs, Map<Integer, KitSlot> slots) {
        this.displayName = displayName;
        this.configKey   = configKey;
        this.cooldownMs  = cooldownMs;
        this.slots       = Collections.unmodifiableMap(slots);
    }

    public String getDisplayName() { return displayName; }
    public String getConfigKey()   { return configKey; }
    public String getPermission()  { return "psdk.kit." + configKey; }
    public long getCooldownMs()  { return cooldownMs; }

    public Map<Integer, ItemStack> resolveSlotItems(PSDK plugin) {
        ClanColorKeyManager keyManager = new ClanColorKeyManager(plugin);
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        for (var entry : slots.entrySet()) {
            result.put(entry.getKey(), entry.getValue().resolve(keyManager));
        }
        applySwordSharpnessToAxeAndShield(result);
        return Collections.unmodifiableMap(result);
    }

    private static void applySwordSharpnessToAxeAndShield(Map<Integer, ItemStack> items) {
        int swordSharp = 0;
        for (ItemStack it : items.values()) {
            if (it != null && it.getType().name().endsWith("_SWORD")) {
                int lvl = it.getEnchantmentLevel(Enchantment.SHARPNESS);
                if (lvl > swordSharp) swordSharp = lvl;
            }
        }
        if (swordSharp <= 0) return;
        for (ItemStack it : items.values()) {
            if (it == null) continue;
            boolean isAxe = it.getType().name().endsWith("_AXE");
            boolean isShield = it.getType() == Material.SHIELD;
            if (!isAxe && !isShield) continue;
            ItemMeta meta = it.getItemMeta();
            if (meta == null) continue;
            meta.addEnchant(Enchantment.SHARPNESS, swordSharp, true);
            it.setItemMeta(meta);
        }
    }

    public String getCooldownLabel() {
        long days = TimeUnit.MILLISECONDS.toDays(cooldownMs);
        if (days == 1) return "1 dia";
        return days + " dias";
    }

    public static VipKit atMenuSlot(int slot) {
        return switch (slot) {
            case KitsVipGUI.SLOT_PREMIUM_1D  -> PREMIUM_1D;
            case KitsVipGUI.SLOT_PREMIUM_7D  -> PREMIUM_7D;
            case KitsVipGUI.SLOT_PREMIUM_30D -> PREMIUM_30D;
            case KitsVipGUI.SLOT_ELITE_1D    -> ELITE_1D;
            case KitsVipGUI.SLOT_ELITE_7D    -> ELITE_7D;
            case KitsVipGUI.SLOT_ELITE_30D   -> ELITE_30D;
            case KitsVipGUI.SLOT_ETERNAL_1D  -> ETERNAL_1D;
            case KitsVipGUI.SLOT_ETERNAL_7D  -> ETERNAL_7D;
            case KitsVipGUI.SLOT_ETERNAL_30D -> ETERNAL_30D;
            case KitsVipGUI.SLOT_SUITE_1D    -> SUITE_1D;
            case KitsVipGUI.SLOT_SUITE_7D    -> SUITE_7D;
            case KitsVipGUI.SLOT_SUITE_30D   -> SUITE_30D;
            default -> null;
        };
    }

    // ── Definição de slots ───────────────────────────────────────────────────

    private interface KitSlot {
        ItemStack resolve(ClanColorKeyManager keyManager);
    }

    private record MaterialSlot(Material material, int amount) implements KitSlot {
        @Override
        public ItemStack resolve(ClanColorKeyManager keyManager) {
            return new ItemStack(material, amount);
        }
    }

    private record CustomItemSlot(ItemStack item) implements KitSlot {
        @Override
        public ItemStack resolve(ClanColorKeyManager keyManager) {
            return item.clone();
        }
    }

    private record PacketSlot(ClanColorKeyManager.PacketType type, int amount) implements KitSlot {
        @Override
        public ItemStack resolve(ClanColorKeyManager keyManager) {
            return keyManager.createPacketItem(type, amount);
        }
    }

    private record SplashPotionSlot(PotionType type, int amount) implements KitSlot {
        @Override
        public ItemStack resolve(ClanColorKeyManager keyManager) {
            ItemStack item = new ItemStack(Material.SPLASH_POTION, amount);
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null) {
                meta.setBasePotionType(type);
                item.setItemMeta(meta);
            }
            return item;
        }
    }

    private static Map<Integer, KitSlot> slots(SlotEntry... entries) {
        Map<Integer, KitSlot> map = new LinkedHashMap<>();
        for (SlotEntry e : entries) map.put(e.slot, e.slotDef);
        return map;
    }

    private record SlotEntry(int slot, KitSlot slotDef) {}

    private static SlotEntry mat(int slot, Material material, int amount) {
        return new SlotEntry(slot, new MaterialSlot(material, amount));
    }

    private static SlotEntry item(int slot, ItemStack stack) {
        return new SlotEntry(slot, new CustomItemSlot(stack));
    }

    private static SlotEntry premArmor(int slot, Material mat, int protection) {
        return item(slot, named(mat, 1, VipKitStyles.PREMIUM_ARMOR, Map.of(Enchantment.PROTECTION, protection)));
    }

    private static SlotEntry premTool(int slot, Material mat, Map<Enchantment, Integer> enchants) {
        return item(slot, named(mat, 1, VipKitStyles.PREMIUM_TOOL, enchants));
    }

    private static SlotEntry premExtra(int slot, Material mat, int amount) {
        return item(slot, named(mat, amount, VipKitStyles.PREMIUM_EXTRA, Map.of()));
    }

    private static SlotEntry premExtra(int slot, Material mat, int amount,
                                       Map<Enchantment, Integer> enchants) {
        return item(slot, named(mat, amount, VipKitStyles.PREMIUM_EXTRA, enchants));
    }

    private static SlotEntry eliteArmor(int slot, Material mat, Map<Enchantment, Integer> enchants) {
        return item(slot, named(mat, 1, VipKitStyles.ELITE_ARMOR, enchants));
    }

    private static SlotEntry eliteTool(int slot, Material mat, Map<Enchantment, Integer> enchants) {
        return item(slot, named(mat, 1, VipKitStyles.ELITE_TOOL, enchants));
    }

    private static SlotEntry eliteExtra(int slot, Material mat, int amount) {
        return item(slot, named(mat, amount, VipKitStyles.ELITE_EXTRA, Map.of()));
    }

    private static SlotEntry eliteExtra(int slot, Material mat, int amount,
                                        Map<Enchantment, Integer> enchants) {
        return item(slot, named(mat, amount, VipKitStyles.ELITE_EXTRA, enchants));
    }

    private static SlotEntry eternArmor(int slot, Material mat, int protection) {
        return item(slot, named(mat, 1, VipKitStyles.ETERNAL_ARMOR, Map.of(Enchantment.PROTECTION, protection)));
    }

    private static SlotEntry eternTool(int slot, Material mat, Map<Enchantment, Integer> enchants) {
        return item(slot, named(mat, 1, VipKitStyles.ETERNAL_TOOL, enchants));
    }

    private static SlotEntry eternExtra(int slot, Material mat, int amount) {
        return item(slot, named(mat, amount, VipKitStyles.ETERNAL_EXTRA, Map.of()));
    }

    private static SlotEntry eternExtra(int slot, Material mat, int amount,
                                        Map<Enchantment, Integer> enchants) {
        return item(slot, named(mat, amount, VipKitStyles.ETERNAL_EXTRA, enchants));
    }

    private static SlotEntry suiteArmor(int slot, Material mat, Map<Enchantment, Integer> enchants,
                                        TrimPattern trim, TrimMaterial trimMat) {
        return item(slot, buildNamed(mat, 1, VipKitStyles.SUITE_ARMOR, enchants, trim, trimMat));
    }

    private static SlotEntry suiteTool(int slot, Material mat, Map<Enchantment, Integer> enchants) {
        return item(slot, buildNamed(mat, 1, VipKitStyles.SUITE_TOOL, enchants, null, null));
    }

    private static SlotEntry suiteExtra(int slot, Material mat, int amount) {
        return item(slot, buildNamed(mat, amount, VipKitStyles.SUITE_EXTRA, Map.of(), null, null));
    }

    private static SlotEntry suiteExtra(int slot, Material mat, int amount,
                                       Map<Enchantment, Integer> enchants) {
        return item(slot, buildNamed(mat, amount, VipKitStyles.SUITE_EXTRA, enchants, null, null));
    }

    private static ItemStack named(Material mat, int amount, String displayName,
                                   Map<Enchantment, Integer> enchants) {
        return buildNamed(mat, amount, displayName, enchants, null, null);
    }

    private static ItemStack buildNamed(Material mat, int amount, String displayName,
                                        Map<Enchantment, Integer> enchants,
                                        TrimPattern trim, TrimMaterial trimMat) {
        ItemStack item = new ItemStack(mat, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(MiniMessage.miniMessage().deserialize("<!italic>" + displayName));
        enchants.forEach((ench, level) -> meta.addEnchant(ench, level, true));
        if (meta instanceof ArmorMeta armorMeta && trim != null && trimMat != null) {
            armorMeta.setTrim(new ArmorTrim(trimMat, trim));
            item.setItemMeta(armorMeta);
        } else {
            item.setItemMeta(meta);
        }
        return item;
    }

    private static SlotEntry pkt(int slot, ClanColorKeyManager.PacketType type) {
        return pkt(slot, type, 1);
    }

    private static SlotEntry pkt(int slot, ClanColorKeyManager.PacketType type, int amount) {
        return new SlotEntry(slot, new PacketSlot(type, amount));
    }

    private static SlotEntry splash(int slot, PotionType type) {
        return splash(slot, type, 1);
    }

    private static SlotEntry splash(int slot, PotionType type, int amount) {
        return new SlotEntry(slot, new SplashPotionSlot(type, amount));
    }
}
