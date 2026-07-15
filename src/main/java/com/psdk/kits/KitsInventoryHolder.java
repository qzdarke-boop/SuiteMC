package com.psdk.kits;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifica menus de kits sem depender do título do inventário. */
public final class KitsInventoryHolder implements InventoryHolder {

    public enum Type {
        MAIN,
        BASICOS,
        VIP,
        WEBSITE,
        BASIC_PREVIEW,
        VIP_PREVIEW
    }

    private final Type type;
    private final Kit basicKit;
    private final VipKit vipKit;
    private Inventory inventory;

    private KitsInventoryHolder(Type type, Kit basicKit, VipKit vipKit) {
        this.type = type;
        this.basicKit = basicKit;
        this.vipKit = vipKit;
    }

    public static KitsInventoryHolder main() {
        return new KitsInventoryHolder(Type.MAIN, null, null);
    }

    public static KitsInventoryHolder basicos() {
        return new KitsInventoryHolder(Type.BASICOS, null, null);
    }

    public static KitsInventoryHolder vip() {
        return new KitsInventoryHolder(Type.VIP, null, null);
    }

    public static KitsInventoryHolder website() {
        return new KitsInventoryHolder(Type.WEBSITE, null, null);
    }

    public static KitsInventoryHolder basicPreview(Kit kit) {
        return new KitsInventoryHolder(Type.BASIC_PREVIEW, kit, null);
    }

    public static KitsInventoryHolder vipPreview(VipKit kit) {
        return new KitsInventoryHolder(Type.VIP_PREVIEW, null, kit);
    }

    public Type getType() {
        return type;
    }

    public Kit getBasicKit() {
        return basicKit;
    }

    public VipKit getVipKit() {
        return vipKit;
    }

    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
