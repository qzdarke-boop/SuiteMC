package com.psdk.pitems;

import com.psdk.PSDK;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PSDKItems {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final Pattern TIME_PATTERN = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    private static NamespacedKey keyItemType;
    private static NamespacedKey keyExpireTime;
    private static NamespacedKey keyTotemUses;

    public static void init() {
        PSDK plugin = PSDK.getInstance();
        keyItemType   = new NamespacedKey(plugin, "item_type");
        keyExpireTime = new NamespacedKey(plugin, "expire_time");
        keyTotemUses  = new NamespacedKey(plugin, "totem_uses");
    }

    public enum ItemType {
        TOTEM_INFERNAL("Totem_INFERNAL", Material.TOTEM_OF_UNDYING, "totem_triplo",
                "<!italic><gradient:#FF0000:#AA0000>Totem Infernal</gradient>",
                "<#e22c27>Evite a morte mais de uma vez."),

        PICARETA_INFERNAL("Picareta_Infernal", Material.NETHERITE_PICKAXE, "picareta_infernal",
                "<!italic><gradient:#FF4500:#FF0000:#8B0000>Picareta Infernal</gradient>",
                "<#FF4500>Quebra veia inteira! <#fcc850>+30% coins em minérios."),

        NETHERITE_GOLDEN_APPLE("Netherite_Golden_Apple", Material.ENCHANTED_GOLDEN_APPLE, "netherite_apple",
                "<!italic><gradient:#FFD700:#FF8C00>Netherite Golden Apple</gradient>",
                "<#FFD700>Te dá poderes absurdos!"),

        TRAP("TRAP", Material.SNOWBALL, null,
                "<!italic><gradient:#ff0000:#000000><bold>Cadeia</bold></gradient>",
                "<#a4a4a4>Prende inimigos em uma armadilha de teias."),

        JAULA("jaula", Material.RED_STAINED_GLASS, null,
                "<!italic><gradient:#ff0000:#000000><bold>Jaula</bold></gradient>",
                "<#a4a4a4>Prende os inimigos em uma jaula de vidro."),

        TROCA_POSICAO("troca_posicao", Material.EGG, null,
                "<!italic><gradient:#ff0000:#000000><bold>Troque de Posição</bold></gradient>",
                "<#a4a4a4>Troque de lugar com o seu inimigo.");

        private final String id;
        private final Material material;
        // ← final agora, igual ao SSDK que funciona
        private final String nexoId;
        private final String displayName;
        private final String description;

        ItemType(String id, Material material, String nexoId, String displayName, String description) {
            this.id          = id;
            this.material    = material;
            this.nexoId      = nexoId;
            this.displayName = displayName;
            this.description = description;
        }

        public String getId()          { return id; }
        public Material getMaterial()  { return material; }
        public String getNexoId()      { return nexoId; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }

        public static ItemType fromId(String id) {
            for (ItemType type : values()) {
                if (type.id.equalsIgnoreCase(id)) return type;
            }
            return null;
        }
    }

    // -----------------------------------------------------------------
    //  create()  —  igual à lógica do SSDK que funciona:
    //  1. tenta pegar o ItemStack do Nexo (preserva modelo/textura)
    //  2. pega o meta JÁ existente (não cria um meta limpo em cima)
    //  3. aplica displayName, enchants, PDC e lore por cima
    // -----------------------------------------------------------------
    /** Cria a versão PERMANENTE do item (sem expiração, sem "ITEM EXCLUSIVO", sem tempo restante). */
    public static ItemStack create(ItemType type) {
        return create(type, 0L);
    }

    /**
     * Cria o item. Se {@code durationMillis > 0} a versão é TEMPORÁRIA (grava o timestamp de
     * expiração no PDC e mostra "EXPIRA EM"/"ITEM EXCLUSIVO"); caso contrário é PERMANENTE
     * (apenas o {@code item_type} é gravado — o item nunca expira e a lore não mostra tempo).
     * As duas versões compartilham o mesmo {@code item_type}, então a habilidade funciona igual.
     */
    public static ItemStack create(ItemType type, long durationMillis) {
        ItemStack item = tryCreateFromNexo(type);
        if (item == null) {
            item = new ItemStack(type.getMaterial());
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(mm.deserialize(type.getDisplayName()));

        boolean temporary = durationMillis > 0;
        long expireAt = temporary ? System.currentTimeMillis() + durationMillis : 0L;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keyItemType, PersistentDataType.STRING, type.getId());
        if (temporary) {
            pdc.set(keyExpireTime, PersistentDataType.LONG, expireAt);
        }

        if (type == ItemType.TOTEM_INFERNAL) {
            pdc.set(keyTotemUses, PersistentDataType.INTEGER, 3);
            meta.lore(buildLore(type, expireAt, 3));
        } else {
            meta.lore(buildLore(type, expireAt));
        }

        if (type == ItemType.PICARETA_INFERNAL) {
            Enchantment fortune    = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune"));
            Enchantment efficiency = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
            Enchantment unbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            Enchantment mending    = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("mending"));
            if (fortune    != null) meta.addEnchant(fortune,    3, true);
            if (efficiency != null) meta.addEnchant(efficiency, 5, true);
            if (unbreaking != null) meta.addEnchant(unbreaking, 3, true);
            if (mending    != null) meta.addEnchant(mending,    1, true);
        }

        if (type == ItemType.NETHERITE_GOLDEN_APPLE) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            Enchantment luck = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("luck_of_the_sea"));
            if (luck != null) meta.addEnchant(luck, 1, true);
        }

        // Itens especiais de habilidade: brilho encantado sem encantamento funcional
        // (mesma identidade visual da Caixa Eye), sem exibir a linha de encantamentos.
        if (type == ItemType.TRAP || type == ItemType.JAULA || type == ItemType.TROCA_POSICAO) {
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        }

        item.setItemMeta(meta);
        return item;
    }

    // -----------------------------------------------------------------
    //  tryCreateFromNexo() / buildNexoItem()  —  delegam ao util único
    //  com.psdk.util.NexoUtil (integração robusta e tolerante a versão).
    // -----------------------------------------------------------------
    public static ItemStack tryCreateFromNexo(ItemType type) {
        return com.psdk.util.NexoUtil.buildItem(type.getNexoId());
    }

    public static ItemStack buildNexoItem(String nexoId) {
        return com.psdk.util.NexoUtil.buildItem(nexoId);
    }

    public static List<Component> buildLore(ItemType type, long expireAt) {
        return buildLore(type, expireAt, -1);
    }

    public static List<Component> buildLore(ItemType type, long expireAt, int uses) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<!italic>  " + type.getDescription()));
        lore.add(Component.empty());

        if (type == ItemType.TOTEM_INFERNAL) {
            lore.add(mm.deserialize("<!italic>  <bold><#e22c27>Efeitos recebidos:"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Cancele sua morte"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Completa sua fome"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Regeneração I (5s)"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Absorção III (10s)"));
            lore.add(Component.empty());
            int displayUses = uses >= 0 ? uses : 3;
            lore.add(mm.deserialize("<!italic>  <#fcc850>Você possui <#cbd1d7>" + displayUses + " <#fcc850>usos restantes."));
            lore.add(Component.empty());
        }

        if (type == ItemType.PICARETA_INFERNAL) {
            lore.add(mm.deserialize("<!italic>  <bold><#e22c27>Habilidades:"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ <#FF4500>Te fornece x1.3 coins <#a4a4a4>ao minerar minérios"));
            lore.add(Component.empty());
        }

        if (type == ItemType.TRAP) {
            lore.add(mm.deserialize("<!italic>  <bold><#e22c27>Habilidade:"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Arremesse para criar um <#cbd1d7>3x3 <#a4a4a4>de teias"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ As teias desaparecem após <#fcc850>7 segundos"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<!italic>  <#cbd1d7>Somente na arena de PvP"));
            lore.add(mm.deserialize("<!italic>  <#fcc850>Recarga: <#cbd1d7>7 segundos"));
            lore.add(Component.empty());
        }

        if (type == ItemType.JAULA) {
            lore.add(mm.deserialize("<!italic>  <bold><#e22c27>Habilidade:"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Cria uma jaula de vidro vermelho <#cbd1d7>(8x8x8)"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Prende quem estiver dentro dela"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Os presos escapam com <#fcc850>5 <#a4a4a4>tentativas de quebra"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Some sozinha após <#fcc850>5 minutos"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<!italic>  <#cbd1d7>Somente na arena de PvP"));
            lore.add(mm.deserialize("<!italic>  <#fcc850>Recarga: <#cbd1d7>5 minutos"));
            lore.add(Component.empty());
        }

        if (type == ItemType.TROCA_POSICAO) {
            lore.add(mm.deserialize("<!italic>  <bold><#e22c27>Habilidade:"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Lance o ovo em um inimigo para <#cbd1d7>trocar de posição"));
            lore.add(mm.deserialize("<!italic>  <#a4a4a4>▸ Coloca os dois em <#cbd1d7>Combat Log"));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<!italic>  <#cbd1d7>Somente na arena de PvP"));
            lore.add(mm.deserialize("<!italic>  <#fcc850>Recarga: <#cbd1d7>5 segundos"));
            lore.add(Component.empty());
        }

        // Bloco de expiração APENAS na versão temporária (expireAt > 0). A versão
        // permanente não mostra "EXPIRA EM" nem "ITEM EXCLUSIVO".
        if (expireAt > 0) {
            long remaining = expireAt - System.currentTimeMillis();
            lore.add(mm.deserialize("<!italic>  <bold><#cbd1d7>EXPIRA EM:"));
            lore.add(mm.deserialize("<!italic>  <#cbd1d7>" + formatTime(remaining)));
            lore.add(Component.empty());
            lore.add(mm.deserialize("<!italic>  <bold><#e22c27>ITEM EXCLUSIVO"));
        }

        // Remove linhas em branco no final (a versão permanente termina no bloco da habilidade).
        while (!lore.isEmpty() && lore.get(lore.size() - 1).equals(Component.empty())) {
            lore.remove(lore.size() - 1);
        }

        return lore;
    }

    public static String formatTime(long millis) {
        if (millis <= 0) return "<#e22c27>Expirado";

        long totalSeconds = millis / 1000;
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (days > 0)    return days + "d " + hours + "h";
        if (hours > 0)   return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return "Menos de um minuto";
    }

    public static String getItemTypeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(keyItemType, PersistentDataType.STRING);
    }

    public static Long getExpireTime(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(keyExpireTime, PersistentDataType.LONG);
    }

    public static boolean isExpired(ItemStack item) {
        Long expire = getExpireTime(item);
        return expire != null && System.currentTimeMillis() >= expire;
    }

    public static int getTotemUses(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer uses = item.getItemMeta().getPersistentDataContainer()
                .get(keyTotemUses, PersistentDataType.INTEGER);
        return uses != null ? uses : 0;
    }

    public static void setTotemUses(ItemStack item, int uses) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyTotemUses, PersistentDataType.INTEGER, uses);

        Long expireAt = meta.getPersistentDataContainer().get(keyExpireTime, PersistentDataType.LONG);
        if (expireAt != null) {
            meta.lore(buildLore(ItemType.TOTEM_INFERNAL, expireAt, uses));
        }
        item.setItemMeta(meta);
    }

    public static long parseTime(String input) {
        if (input == null || input.isBlank()) return -1;
        Matcher m = TIME_PATTERN.matcher(input.toLowerCase().trim());
        if (!m.matches()) return -1;

        long total = 0;
        if (m.group(1) != null) total += Long.parseLong(m.group(1)) * 86400000L;
        if (m.group(2) != null) total += Long.parseLong(m.group(2)) * 3600000L;
        if (m.group(3) != null) total += Long.parseLong(m.group(3)) * 60000L;
        if (m.group(4) != null) total += Long.parseLong(m.group(4)) * 1000L;

        return total > 0 ? total : -1;
    }

    public static NamespacedKey getKeyItemType()    { return keyItemType; }
    public static NamespacedKey getKeyExpireTime()  { return keyExpireTime; }
    public static NamespacedKey getKeyTotemUses()   { return keyTotemUses; }
}