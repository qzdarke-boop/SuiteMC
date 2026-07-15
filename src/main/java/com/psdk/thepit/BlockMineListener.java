package com.psdk.thepit;

import com.psdk.PSDK;
import com.psdk.pitems.PSDKItems;
import com.psdk.vip.VipBonus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public class BlockMineListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public record MineableBlock(double coins, String colorHex, Sound sound) {}

    public static final Map<Material, MineableBlock> MINEABLE = new EnumMap<>(Material.class);
    static {
        MINEABLE.put(Material.DIAMOND_ORE,           new MineableBlock(15,  "<#55ffff>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.DEEPSLATE_DIAMOND_ORE, new MineableBlock(18,  "<#55ffff>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.GOLD_ORE,              new MineableBlock(10,  "<#efa600>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.DEEPSLATE_GOLD_ORE,    new MineableBlock(11,  "<#efa600>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.IRON_ORE,              new MineableBlock(5,   "<#a4a4a4>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.DEEPSLATE_IRON_ORE,    new MineableBlock(6,   "<#a4a4a4>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.EMERALD_ORE,           new MineableBlock(25,  "<#10fc46>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.DEEPSLATE_EMERALD_ORE, new MineableBlock(28,  "<#10fc46>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.LAPIS_ORE,             new MineableBlock(4,   "<#5555ff>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.DEEPSLATE_LAPIS_ORE,   new MineableBlock(5,   "<#5555ff>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.COAL_ORE,              new MineableBlock(2,   "<#404040>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.DEEPSLATE_COAL_ORE,    new MineableBlock(3,   "<#404040>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.REDSTONE_ORE,          new MineableBlock(3,   "<#ff0000>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.DEEPSLATE_REDSTONE_ORE,new MineableBlock(4,   "<#ff0000>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.NETHER_QUARTZ_ORE,     new MineableBlock(8,   "<#ffffff>", Sound.BLOCK_AMETHYST_BLOCK_BREAK));
        MINEABLE.put(Material.ANCIENT_DEBRIS,        new MineableBlock(50,  "<#ff55ff>", Sound.BLOCK_ANVIL_LAND));
    }

    private final PSDK plugin;

    public BlockMineListener(PSDK plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();

        MineableBlock blockData = MINEABLE.get(blockType);
        if (blockData == null) return;

        // Picareta Infernal gerencia seus próprios coins com bônus 1.3x
        if ("Picareta_Infernal".equals(PSDKItems.getItemTypeId(
                player.getInventory().getItemInMainHand()))) return;

        event.setDropItems(false);

        double money = blockData.coins();

        // Multiplicador de Fortuna: +0.25x por nível (F1=1.25x, F2=1.50x, F3=1.75x...).
        // Quanto mais Fortuna, maior o multiplicador — sem limite de nível.
        ItemStack tool = player.getInventory().getItemInMainHand();
        int fortuneLevel = tool.getEnchantmentLevel(Enchantment.FORTUNE);
        double fortuneMultiplier = 1.0 + (0.25 * fortuneLevel);

        // Tudo multiplica em conjunto: Fortuna x Admin Abuse x VIP — quanto mais
        // de cada, mais coins. Combinando os três dá pra alcançar vários "x".
        money *= fortuneMultiplier;
        money *= plugin.getAdminAbuseManager().getMiningMultiplier();
        money *= VipBonus.getMiningMultiplier(player);
        money = Math.round(money);   // valor inteiro: o que entra na conta é o mesmo que aparece
        plugin.getEconomyManager().addCoins(player.getUniqueId(), player.getName(), money);

        // Mostra o valor REALMENTE ganho (com bônus de tag/evento), na cor do minério.
        player.sendMessage(mm.deserialize(
                blockData.colorHex() + "<bold>+</bold>" + (long) money + " coins"));
        player.playSound(event.getBlock().getLocation(), blockData.sound(), 1f, 1f);
    }
}
