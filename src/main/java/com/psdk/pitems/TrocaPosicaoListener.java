package com.psdk.pitems;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.psdk.PSDK;
import com.psdk.region.RegionFlag;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

/**
 * Habilidade do item {@link TrocaPosicaoItem}: arremessar o Ovo especial e acertar
 * DIRETAMENTE outro jogador na arena de PvP troca a posição dos dois.
 *
 * <p>Reaproveita os sistemas já existentes do plugin:
 * <ul>
 *   <li><b>Projétil por PDC</b> (igual ao {@code AdminAbuseItemListener}): o projétil
 *       é tagueado no lançamento com um marcador e o UUID do lançador.</li>
 *   <li><b>Cooldown estilo escudo</b> (igual ao {@code SafeTntListener}): recarga visual
 *       via {@link Player#setCooldown} + um mapa por jogador (independe de slot/unidade).</li>
 *   <li><b>Regiões</b> ({@code RegionManager}) e <b>arena</b> ({@code ArenaManager}) para
 *       validar a área ATIVA de PvP no lançamento e no impacto.</li>
 *   <li><b>Combat Log</b> ({@code CombatManager#registerHit}) — os dois entram em combate
 *       exatamente como quando um causa dano no outro.</li>
 *   <li><b>Devolução de item</b> no padrão do projeto (addItem + drop do overflow).</li>
 * </ul>
 *
 * <p><b>Consumo:</b> o item só é gasto de fato numa troca bem-sucedida. Bloqueios de
 * região não consomem (Paper {@code setShouldConsume(false)}) e falhas no impacto
 * devolvem a unidade e limpam o cooldown.
 */
public class TrocaPosicaoListener implements Listener {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private final PSDK plugin;
    private final NamespacedKey projKey;      // marca o projétil como do item especial
    private final NamespacedKey throwerKey;   // UUID de quem lançou o projétil
    private final NamespacedKey expireKey;    // expiração do item (só se for temporário)

    public TrocaPosicaoListener(PSDK plugin) {
        this.plugin = plugin;
        this.projKey = new NamespacedKey(plugin, "troca_posicao_proj");
        this.throwerKey = new NamespacedKey(plugin, "troca_posicao_thrower");
        this.expireKey = new NamespacedKey(plugin, "troca_posicao_expire");
    }

    // ── Lançamento ──────────────────────────────────────────────────────────────
    // PlayerLaunchProjectileEvent (Paper) permite NÃO consumir o item ao cancelar
    // (setShouldConsume), evitando o bug/duplicação do ProjectileLaunchEvent.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(PlayerLaunchProjectileEvent event) {
        ItemStack hand = event.getItemStack();
        if (!TrocaPosicaoItem.isSwapItem(plugin, hand)) return;
        if (!(event.getProjectile() instanceof Egg egg)) return;

        Player player = event.getPlayer();

        // Item temporário expirado: não ativa a habilidade e não é consumido como ovo comum.
        // (o PSDKItemExpireTask remove o item do inventário em seguida.)
        if (PSDKItems.isExpired(hand)) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            return;
        }

        // Só pode lançar na área ATIVA de PvP (dentro da arena E com PvP ligado).
        if (!isPvpArena(player.getLocation())) {
            event.setShouldConsume(false); // não gasta o item
            event.setCancelled(true);
            block(player, "<#e22c27>Você só pode usar este item na arena de PvP!");
            return;
        }

        // Cooldown de 5s (padrão visual do escudo, centralizado). Bloqueio NÃO consome e
        // NÃO envia mensagem — a recarga já aparece no próprio item da hotbar.
        AbilityCooldownManager cd = plugin.getAbilityCooldownManager();
        if (!cd.isReady(player, AbilityCooldownManager.Ability.TROCA_POSICAO)) {
            event.setShouldConsume(false);
            event.setCancelled(true);
            return;
        }

        // Lançamento válido: tagueia o projétil e inicia o cooldown (anti-spam / anti-simultâneo).
        PersistentDataContainer projPdc = egg.getPersistentDataContainer();
        projPdc.set(projKey, PersistentDataType.BYTE, (byte) 1);
        projPdc.set(throwerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        // Preserva a expiração do item temporário para devolver a MESMA versão em caso de falha
        // (evita que jogar num alvo inválido converta um item temporário em permanente).
        Long expireAt = PSDKItems.getExpireTime(hand);
        if (expireAt != null) {
            projPdc.set(expireKey, PersistentDataType.LONG, expireAt);
        }
        cd.start(player, AbilityCooldownManager.Ability.TROCA_POSICAO);
    }

    // ── Impacto ───────────────────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Egg egg)) return;
        PersistentDataContainer pdc = egg.getPersistentDataContainer();
        if (!pdc.has(projKey, PersistentDataType.BYTE)) return;

        // Processa o projétil UMA única vez e o remove (sem dados presos).
        pdc.remove(projKey);
        String throwerId = pdc.get(throwerKey, PersistentDataType.STRING);
        Long expireAt = pdc.get(expireKey, PersistentDataType.LONG); // null = item permanente
        egg.remove();

        Player thrower = resolvePlayer(throwerId);
        if (thrower == null) return; // lançador saiu: nada a devolver

        // Precisa acertar DIRETAMENTE um jogador real e válido.
        if (!(event.getHitEntity() instanceof Player target)) {
            fail(thrower, expireAt, "<#e22c27>Você errou o alvo!");
            return;
        }
        if (!isValidTarget(thrower, target)) {
            fail(thrower, expireAt, "<#e22c27>Você não pode trocar de posição com esse alvo!");
            return;
        }

        // Revalida a REGIÃO DOS DOIS no momento do impacto (o alvo pode ter se movido).
        if (!isPvpArena(thrower.getLocation()) || !isPvpArena(target.getLocation())) {
            fail(thrower, expireAt, "<#e22c27>O alvo precisa estar na arena de PvP!");
            return;
        }

        // Posições no instante EXATO do impacto.
        Location fromThrower = thrower.getLocation().clone();
        Location fromTarget = target.getLocation().clone();
        if (!isSafeSwapLocation(fromThrower) || !isSafeSwapLocation(fromTarget)) {
            fail(thrower, expireAt, "<#e22c27>Não foi possível concluir a troca!");
            return;
        }

        // A troca não pode atravessar a fronteira de uma Jaula ativa
        // (dentro↔fora). Consulta centralizada no gerenciador da Jaula.
        if (!plugin.getCageManager().canTeleportBetween(fromThrower, fromTarget)) {
            fail(thrower, expireAt, "<#e22c27>Você não pode trocar de posição através de uma Jaula!");
            return;
        }

        // Troca ATÔMICA: se qualquer teleporte falhar (ex.: cancelado por outro plugin),
        // reverte e devolve o item — nunca teleporta só um dos dois.
        if (!thrower.teleport(fromTarget)) {
            fail(thrower, expireAt, "<#e22c27>Não foi possível concluir a troca!");
            return;
        }
        if (!target.teleport(fromThrower)) {
            thrower.teleport(fromThrower); // reverte o lançador
            fail(thrower, expireAt, "<#e22c27>Não foi possível concluir a troca!");
            return;
        }

        // Sucesso: Combat Log nos DOIS (mesmo sistema do dano) + efeitos discretos.
        // Sem mensagem/ActionBar/título de sucesso — o próprio Combat Log avisa o que precisar.
        plugin.getCombatManager().registerHit(target, thrower);
        swapEffect(fromThrower);
        swapEffect(fromTarget);
    }

    // ── Ovo não choca ────────────────────────────────────────────────────────────
    // O Ovo especial nunca deve gerar galinhas ao atingir algo (é só o projétil da
    // habilidade). Identificado pelo PDC do lançador, gravado no lançamento.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEggThrow(PlayerEggThrowEvent event) {
        if (event.getEgg().getPersistentDataContainer().has(throwerKey, PersistentDataType.STRING)) {
            event.setHatching(false);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Área ATIVA de PvP = dentro dos limites da arena E com PvP liberado (não é zona segura). */
    private boolean isPvpArena(Location loc) {
        if (loc == null) return false;
        return plugin.getArenaManager().isInsideArena(loc)
                && plugin.getRegionManager().isAllowed(loc, RegionFlag.PVP);
    }

    /** Só jogadores reais, vivos, online, no mesmo mundo e sem proteção especial. */
    private boolean isValidTarget(Player thrower, Player target) {
        if (target == null || target.equals(thrower)) return false;      // nunca o próprio
        if (!target.isOnline() || target.isDead()) return false;         // desconectado/morto
        if (target.getGameMode() == GameMode.SPECTATOR) return false;    // espectador
        if (target.isInvulnerable()) return false;                       // invulnerável (respawn/god)
        if (target.hasMetadata("NPC")) return false;                     // NPCs (Citizens etc.)
        if (!target.getWorld().equals(thrower.getWorld())) return false; // mundos diferentes
        if (plugin.getAfkManager().isInAfkWorld(target)) return false;   // protegido/AFK
        return target.getHealth() > 0;
    }

    /** Evita cair no vazio por uma posição inválida; o destino é onde um jogador já está de pé. */
    private boolean isSafeSwapLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return loc.getY() >= loc.getWorld().getMinHeight()
                && loc.getY() <= loc.getWorld().getMaxHeight();
    }

    private Player resolvePlayer(String uuid) {
        if (uuid == null) return null;
        try {
            Player p = plugin.getServer().getPlayer(UUID.fromString(uuid));
            return (p != null && p.isOnline()) ? p : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Bloqueio no lançamento: só avisa (o item não foi consumido). */
    private void block(Player player, String message) {
        player.sendActionBar(mm.deserialize(message));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
    }

    /** Falha no impacto: devolve o item, limpa o cooldown e avisa. */
    private void fail(Player thrower, Long expireAt, String message) {
        giveBack(thrower, expireAt);
        // Uso inválido no impacto: cancela o cooldown (não penaliza a tentativa falha).
        plugin.getAbilityCooldownManager().clear(thrower, AbilityCooldownManager.Ability.TROCA_POSICAO);
        thrower.sendActionBar(mm.deserialize(message));
        thrower.playSound(thrower.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
    }

    /**
     * Devolve exatamente o item especial original (mesmo tratamento de inventário cheio do projeto).
     * Preserva a versão: permanente ({@code expireAt == null}) ou temporária (recria com o tempo
     * restante). Se o item temporário já expirou, nada é devolvido.
     */
    private void giveBack(Player player, Long expireAt) {
        ItemStack item;
        if (expireAt == null) {
            item = TrocaPosicaoItem.create(plugin); // permanente
        } else {
            long remaining = expireAt - System.currentTimeMillis();
            if (remaining <= 0) return; // temporário já expirou: não devolve
            item = TrocaPosicaoItem.create(plugin, remaining);
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private void swapEffect(Location loc) {
        if (loc.getWorld() == null) return;
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
    }
}
