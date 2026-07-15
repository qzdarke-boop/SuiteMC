package com.psdk.crates;

import com.psdk.PSDK;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.logging.Level;

public class HologramManager {

    private final PSDK plugin;
    private final CrateManager crateManager;

    public HologramManager(PSDK plugin, CrateManager crateManager) {
        this.plugin = plugin;
        this.crateManager = crateManager;
    }

    public void spawnAll() {
        for (Crate crate : crateManager.getAllCrates()) {
            if (crate.getLocal() != null) {
                spawnHologram(crate);
            }
        }
    }

    public void despawnAll() {
        for (Crate crate : crateManager.getAllCrates()) {
            despawnHologram(crate);
        }
    }

    public void spawnHologram(Crate crate) {
        Location loc = crate.getLocal();
        if (loc == null || loc.getWorld() == null) return;

        // CRÍTICO: garante o chunk carregado ANTES de procurar as entidades salvas.
        // No onEnable o chunk pode não estar carregado e getEntity(uuid) retornaria null,
        // fazendo o código spawnar entidades NOVAS e deixar as antigas órfãs (acumulando
        // Interactions empilhadas a cada restart -> clique na órfã não abre nada).
        if (!loc.getChunk().isLoaded()) loc.getChunk().load();

        String bdUUID = crate.getBlockDisplayUUID();
        String intUUID = crate.getInteractionUUID();

        if (bdUUID != null && !bdUUID.isEmpty() && intUUID != null && !intUUID.isEmpty()) {
            try {
                Entity bdEntity = plugin.getServer().getEntity(UUID.fromString(bdUUID));
                Entity intEntity = plugin.getServer().getEntity(UUID.fromString(intUUID));

                if ((bdEntity instanceof BlockDisplay || bdEntity instanceof ItemDisplay)
                        && intEntity instanceof Interaction
                        && bdEntity.isValid() && intEntity.isValid()) {
                    return;
                }
                if (bdEntity != null) bdEntity.remove();
                if (intEntity != null) intEntity.remove();
            } catch (IllegalArgumentException e) {
                plugin.getLogger().log(Level.WARNING, "UUID invalido para holograma da crate: " + crate.getNome());
            }
        }

        // Remove qualquer holograma/hitbox órfão acumulado no local (de restarts antigos)
        // antes de criar um novo conjunto, evitando entidades duplicadas empilhadas.
        cleanupStrayEntities(loc);

        Crate.Visual visual = crate.getVisual();
        boolean isCustom = visual == Crate.Visual.CUSTOM;
        boolean isBau = visual == Crate.Visual.BAU;

        float scale = isBau ? 2f : 3f;
        double offset = isBau ? -0.5 : -1.0;

        Location displayLoc = loc.clone().add(offset, 0, offset);
        Entity displayEntity;

        if (isCustom && crate.getCustomHologramItem() != null) {
            ItemStack item = crate.getCustomHologramItem();
            Location itemLoc = loc.clone().add(0.5, 1.8, 0.5);
            displayEntity = loc.getWorld().spawn(itemLoc, ItemDisplay.class, entity -> {
                entity.setItemStack(item);
                entity.setPersistent(true);
                entity.setInvulnerable(true);
                entity.setBillboard(Display.Billboard.FIXED);
                Transformation t = new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(2.5f, 2.5f, 2.5f),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                entity.setTransformation(t);
            });
            scale = 2.5f;
        } else {
            Material material = isBau ? Material.CHEST : Material.ENDER_CHEST;
            final float finalScale = scale;
            displayEntity = loc.getWorld().spawn(displayLoc, BlockDisplay.class, entity -> {
                entity.setBlock(material.createBlockData());
                entity.setPersistent(true);
                entity.setInvulnerable(true);
                Transformation t = new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(finalScale, finalScale, finalScale),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                entity.setTransformation(t);
            });
        }

        Location interactionLoc = loc.clone().add(0.5, 0, 0.5);
        final float interactionScale = scale;
        Interaction interaction = loc.getWorld().spawn(interactionLoc, Interaction.class, entity -> {
            entity.setInteractionWidth(interactionScale);
            entity.setInteractionHeight(interactionScale);
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setResponsive(true);
        });

        crate.setBlockDisplayUUID(displayEntity.getUniqueId().toString());
        crate.setInteractionUUID(interaction.getUniqueId().toString());
        crateManager.saveCrate(crate);
    }

    public void despawnHologram(Crate crate) {
        removeByUUID(crate.getBlockDisplayUUID());
        removeByUUID(crate.getInteractionUUID());
    }

    private void removeByUUID(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty()) return;
        try {
            Entity entity = plugin.getServer().getEntity(UUID.fromString(uuidStr));
            if (entity != null) entity.remove();
        } catch (IllegalArgumentException ignored) {}
    }

    /**
     * Remove Interactions/Displays órfãos perto do local da crate (sobras de restarts
     * anteriores onde os UUIDs ficaram dessincronizados). Só roda quando vamos respawnar
     * um conjunto novo, então não mexe em hologramas válidos já existentes.
     */
    private void cleanupStrayEntities(Location loc) {
        if (loc.getWorld() == null) return;
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        for (Entity e : loc.getWorld().getNearbyEntities(center, 1.6, 2.0, 1.6)) {
            if (e instanceof Interaction || e instanceof BlockDisplay || e instanceof ItemDisplay) {
                e.remove();
            }
        }
    }
}
