package com.psdk.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Lê e ESCREVE inventário e ender chest de jogadores offline direto do playerdata (NBT).
 *
 * <p>Mapeamento de slots do inventário (NBT &harr; array de 41 posições estilo Bukkit):
 * <ul>
 *   <li>0–35: hotbar + mochila (igual)</li>
 *   <li>100→36 botas, 101→37 calças, 102→38 peitoral, 103→39 capacete</li>
 *   <li>-106→40 offhand</li>
 * </ul>
 *
 * <p>A escrita é feita de forma SEGURA: grava num arquivo temporário, valida lendo de
 * volta, e só então substitui o arquivo original (move atômico). Se qualquer passo
 * falhar, o arquivo original NUNCA é tocado.
 */
public final class OfflinePlayerDataUtil {

    private OfflinePlayerDataUtil() {}

    public static ItemStack[] loadInventory(UUID uuid) {
        return loadItems(uuid, "Inventory", 41);
    }

    /**
     * Resolves server-only state on the primary thread, then reads and parses
     * playerdata asynchronously. Future callbacks run on the async thread.
     */
    public static CompletableFuture<ItemStack[]> loadInventoryAsync(Plugin plugin, UUID uuid) {
        CompletableFuture<ItemStack[]> result = new CompletableFuture<>();
        final File dat;
        final Object registryAccess;
        try {
            dat = datFile(uuid);
            registryAccess = registryAccess();
        } catch (Throwable t) {
            logFailure("preparar leitura", "Inventory", t);
            result.complete(null);
            return result;
        }

        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                    result.complete(loadItems(dat, registryAccess, "Inventory", 41)));
        } catch (Throwable t) {
            logFailure("agendar leitura", "Inventory", t);
            result.complete(null);
        }
        return result;
    }

    public static ItemStack[] loadEnderChest(UUID uuid) {
        return loadItems(uuid, "EnderItems", 27);
    }

    public static boolean saveInventory(UUID uuid, ItemStack[] contents) {
        return saveItems(uuid, "Inventory", contents);
    }

    public static boolean saveEnderChest(UUID uuid, ItemStack[] contents) {
        return saveItems(uuid, "EnderItems", contents);
    }

    private static File datFile(UUID uuid) {
        return findPlayerDataFile(uuid);
    }

    private static File findPlayerDataFile(UUID uuid) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            File candidate = new File(world.getWorldFolder(), "playerdata/" + uuid + ".dat");
            if (candidate.isFile()) return candidate;
        }
        return new File(Bukkit.getWorlds().get(0).getWorldFolder(), "playerdata/" + uuid + ".dat");
    }

    // ─────────────────────────────── LEITURA ───────────────────────────────

    private static ItemStack[] loadItems(UUID uuid, String listKey, int size) {
        try {
            File dat = datFile(uuid);
            Object registryAccess = registryAccess();
            return loadItems(dat, registryAccess, listKey, size);
        } catch (Throwable t) {
            logFailure("ler", listKey, t);
            return null;
        }
    }

    private static ItemStack[] loadItems(File dat, Object registryAccess, String listKey, int size) {
        try {
            if (!dat.isFile()) return null;

            Object root = readRoot(dat);
            if (root == null) return null;

            Object itemList = getListTag(root, listKey);
            if (itemList == null) return new ItemStack[size];
            int listSize = (int) itemList.getClass().getMethod("size").invoke(itemList);
            if (listSize == 0) return new ItemStack[size];

            Class<?> nmsItem = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> provider = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> tag = Class.forName("net.minecraft.nbt.Tag");
            Method parse = nmsItem.getMethod("parse", provider, tag);

            String cbPkg = Bukkit.getServer().getClass().getPackage().getName();
            Class<?> craftItem = Class.forName(cbPkg + ".inventory.CraftItemStack");
            Method asBukkitCopy = craftItem.getMethod("asBukkitCopy", nmsItem);

            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < listSize; i++) {
                Object itemTag = getCompoundAt(itemList, i);
                if (itemTag == null) continue;
                int rawSlot = getByteTag(itemTag, "Slot", i);
                int slot = mapNbtSlotToArray(listKey, rawSlot, size);
                if (slot < 0 || slot >= size) continue;

                Object optional = parse.invoke(null, registryAccess, itemTag);
                Object nms = (optional instanceof java.util.Optional<?> o) ? o.orElse(null) : optional;
                if (nms == null) continue;
                Object bukkit = asBukkitCopy.invoke(null, nms);
                if (bukkit instanceof ItemStack is) contents[slot] = is;
            }
            return contents;
        } catch (Throwable t) {
            logFailure("ler", listKey, t);
            return null;
        }
    }

    // ─────────────────────────────── ESCRITA ───────────────────────────────

    private static boolean saveItems(UUID uuid, String listKey, ItemStack[] contents) {
        try {
            File dat = datFile(uuid);
            if (!dat.exists()) return false;

            Object root = readRoot(dat);
            if (root == null) return false;

            Object registryAccess = registryAccess();

            Class<?> nmsItemCls = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> provider = Class.forName("net.minecraft.core.HolderLookup$Provider");
            Class<?> tagCls = Class.forName("net.minecraft.nbt.Tag");
            Class<?> compoundCls = Class.forName("net.minecraft.nbt.CompoundTag");
            Class<?> listCls = Class.forName("net.minecraft.nbt.ListTag");

            String cbPkg = Bukkit.getServer().getClass().getPackage().getName();
            Class<?> craftItem = Class.forName(cbPkg + ".inventory.CraftItemStack");
            Method asNMSCopy = craftItem.getMethod("asNMSCopy", ItemStack.class);

            // ItemStack.save(Provider, Tag) ou save(Provider)
            Method save2 = null, save1 = null;
            try { save2 = nmsItemCls.getMethod("save", provider, tagCls); } catch (NoSuchMethodException ignored) {}
            if (save2 == null) {
                try { save1 = nmsItemCls.getMethod("save", provider); } catch (NoSuchMethodException ignored) {}
            }
            if (save2 == null && save1 == null) {
                Bukkit.getLogger().warning("[offline-data] não encontrei ItemStack.save(...) — abortando escrita.");
                return false;
            }

            Object listTag = listCls.getDeclaredConstructor().newInstance();
            Method putByte = compoundCls.getMethod("putByte", String.class, byte.class);

            for (int idx = 0; idx < contents.length; idx++) {
                ItemStack it = contents[idx];
                if (it == null || it.getType().isAir()) continue;
                int nbtSlot = mapArrayToNbtSlot(listKey, idx);
                if (nbtSlot == Integer.MIN_VALUE) continue;

                Object nms = asNMSCopy.invoke(null, it);
                Object compound = compoundCls.getDeclaredConstructor().newInstance();
                Object saved = (save2 != null)
                        ? save2.invoke(nms, registryAccess, compound)
                        : save1.invoke(nms, registryAccess);
                if (saved == null) continue;
                putByte.invoke(saved, "Slot", (byte) nbtSlot);
                listTagAdd(listTag, saved, tagCls);
            }

            // root.put(listKey, listTag)
            compoundCls.getMethod("put", String.class, tagCls).invoke(root, listKey, listTag);

            // Escrita SEGURA: grava temp -> valida -> move atômico.
            File tmp = new File(dat.getParentFile(), uuid + ".dat.psdk-tmp");
            writeRoot(root, tmp);
            if (readRoot(tmp) == null) {   // validação: precisa reler sem erro
                tmp.delete();
                Bukkit.getLogger().warning("[offline-data] validação do NBT falhou — escrita abortada (arquivo original intacto).");
                return false;
            }
            try {
                Files.move(tmp.toPath(), dat.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Throwable atomicFail) {
                Files.move(tmp.toPath(), dat.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Throwable t) {
            logFailure("escrever", listKey, t);
            return false;
        }
    }

    private static void logFailure(String operation, String listKey, Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        Bukkit.getLogger().log(Level.WARNING,
                "[offline-data] falha ao " + operation + " " + listKey + ": "
                        + cause.getClass().getSimpleName() + " - " + cause.getMessage(), failure);
    }

    // ──────────────────────────── NBT helpers ────────────────────────────

    private static Object registryAccess() throws Exception {
        Object mcServer = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        return mcServer.getClass().getMethod("registryAccess").invoke(mcServer);
    }

    private static Object readRoot(File dat) throws Exception {
        Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo");
        Class<?> accCls = Class.forName("net.minecraft.nbt.NbtAccounter");
        Object accounter = accCls.getMethod("unlimitedHeap").invoke(null);
        try {
            Method m = nbtIo.getMethod("readCompressed", java.nio.file.Path.class, accCls);
            return m.invoke(null, dat.toPath(), accounter);
        } catch (NoSuchMethodException ns) {
            try (java.io.InputStream in = new java.io.FileInputStream(dat)) {
                Method m = nbtIo.getMethod("readCompressed", java.io.InputStream.class, accCls);
                return m.invoke(null, in, accounter);
            }
        }
    }

    private static void writeRoot(Object root, File out) throws Exception {
        Class<?> nbtIo = Class.forName("net.minecraft.nbt.NbtIo");
        Class<?> compoundCls = Class.forName("net.minecraft.nbt.CompoundTag");
        try {
            Method m = nbtIo.getMethod("writeCompressed", compoundCls, java.nio.file.Path.class);
            m.invoke(null, root, out.toPath());
        } catch (NoSuchMethodException ns) {
            try (java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                Method m = nbtIo.getMethod("writeCompressed", compoundCls, java.io.OutputStream.class);
                m.invoke(null, root, os);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void listTagAdd(Object listTag, Object tag, Class<?> tagCls) throws Exception {
        if (listTag instanceof java.util.List) {
            ((java.util.List<Object>) listTag).add(tag);
            return;
        }
        try {
            int size = (int) listTag.getClass().getMethod("size").invoke(listTag);
            listTag.getClass().getMethod("add", int.class, tagCls).invoke(listTag, size, tag);
            return;
        } catch (NoSuchMethodException ignored) {}
        listTag.getClass().getMethod("add", tagCls).invoke(listTag, tag);
    }

    private static Object getListTag(Object compound, String key) throws Exception {
        try { return compound.getClass().getMethod("getListOrEmpty", String.class).invoke(compound, key); }
        catch (NoSuchMethodException ignored) { }
        try { return compound.getClass().getMethod("getList", String.class, int.class).invoke(compound, key, 10); }
        catch (NoSuchMethodException ignored) { }
        Object r = compound.getClass().getMethod("getList", String.class).invoke(compound, key);
        return (r instanceof java.util.Optional<?> o) ? o.orElse(null) : r;
    }

    private static Object getCompoundAt(Object listTag, int i) throws Exception {
        try { return listTag.getClass().getMethod("getCompoundOrEmpty", int.class).invoke(listTag, i); }
        catch (NoSuchMethodException ignored) { }
        Object r = listTag.getClass().getMethod("getCompound", int.class).invoke(listTag, i);
        return (r instanceof java.util.Optional<?> o) ? o.orElse(null) : r;
    }

    private static int getByteTag(Object compound, String key, int fallback) {
        try {
            try {
                Object b = compound.getClass().getMethod("getByteOr", String.class, byte.class)
                        .invoke(compound, key, (byte) 0);
                return ((Number) b).intValue();
            } catch (NoSuchMethodException ignored) { }
            Object b = compound.getClass().getMethod("getByte", String.class).invoke(compound, key);
            if (b instanceof java.util.Optional<?> o) b = o.orElse(null);
            if (b == null) return fallback;
            return ((Number) b).intValue();
        } catch (Throwable t) { return fallback; }
    }

    // ──────────────────────────── slot mapping ────────────────────────────

    /** NBT slot -> índice no array (estilo Bukkit). -1 = ignorar. */
    private static int mapNbtSlotToArray(String listKey, int nbtSlot, int size) {
        if ("Inventory".equals(listKey)) {
            if (nbtSlot >= 0 && nbtSlot <= 35) return nbtSlot;
            return switch (nbtSlot) {
                case 100 -> 36;   // botas
                case 101 -> 37;   // calças
                case 102 -> 38;   // peitoral
                case 103 -> 39;   // capacete
                case -106 -> 40;  // offhand
                default -> -1;
            };
        }
        return (nbtSlot >= 0 && nbtSlot < size) ? nbtSlot : -1;
    }

    /** Índice no array (estilo Bukkit) -> NBT slot. {@link Integer#MIN_VALUE} = ignorar. */
    private static int mapArrayToNbtSlot(String listKey, int arrayIndex) {
        if ("Inventory".equals(listKey)) {
            if (arrayIndex >= 0 && arrayIndex <= 35) return arrayIndex;
            return switch (arrayIndex) {
                case 36 -> 100;
                case 37 -> 101;
                case 38 -> 102;
                case 39 -> 103;
                case 40 -> -106;
                default -> Integer.MIN_VALUE;
            };
        }
        return arrayIndex;
    }
}
