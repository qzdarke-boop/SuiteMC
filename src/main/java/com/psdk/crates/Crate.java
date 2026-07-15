package com.psdk.crates;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Crate {

    /** Quantidade máxima de itens de recompensa (= nº de slots de recompensa na GUI). */
    public static final int MAX_ITENS = 10;

    public enum Tipo { NORMAL, EXCLUSIVA }
    public enum Visual { BAU, ENDERCHEST, CUSTOM }

    private final String nome;
    private Tipo tipo;
    private Visual visual;
    private ItemStack customHologramItem;
    private String cor = "<#fcc850>";
    private String tituloMenu;
    private int limiteGlobal;
    private Location local;
    private CrateKey itemChave;
    private List<ItemStack> itens;
    private String blockDisplayUUID;
    private String interactionUUID;
    private String nexoKeyId;
    private double precoToken = 100.0;

    public Crate(String nome) {
        this.nome = nome;
        this.itens = new ArrayList<>(Collections.nCopies(MAX_ITENS, null));
        this.limiteGlobal = -1;
    }

    public String getNome() { return nome; }

    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }

    public Visual getVisual() { return visual; }
    public void setVisual(Visual visual) { this.visual = visual; }

    public ItemStack getCustomHologramItem() { return customHologramItem; }
    public void setCustomHologramItem(ItemStack item) { this.customHologramItem = item; }

    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor != null ? cor : "<#fcc850>"; }

    public String getTituloMenu() { return tituloMenu; }
    public void setTituloMenu(String tituloMenu) { this.tituloMenu = tituloMenu; }

    public int getLimiteGlobal() { return limiteGlobal; }
    public void setLimiteGlobal(int limiteGlobal) { this.limiteGlobal = limiteGlobal; }

    public boolean isEsgotada() {
        return tipo == Tipo.EXCLUSIVA && limiteGlobal == 0;
    }

    public Location getLocal() { return local; }
    public void setLocal(Location local) { this.local = local; }

    public CrateKey getItemChave() { return itemChave; }
    public void setItemChave(CrateKey itemChave) { this.itemChave = itemChave; }

    public List<ItemStack> getItens() { return itens; }

    /** Normaliza qualquer lista de entrada para o tamanho fixo de {@link #MAX_ITENS} posições. */
    public void setItens(List<ItemStack> itens) {
        List<ItemStack> normalizado = new ArrayList<>(Collections.nCopies(MAX_ITENS, null));
        if (itens != null) {
            for (int i = 0; i < itens.size() && i < MAX_ITENS; i++) {
                normalizado.set(i, itens.get(i));
            }
        }
        this.itens = normalizado;
    }

    /** Coloca o item na primeira posição livre. Retorna o índice usado, ou -1 se cheia. */
    public int addItem(ItemStack item) {
        int idx = firstFreeIndex();
        if (idx < 0) return -1;
        this.itens.set(idx, item);
        return idx;
    }

    /** Define (ou substitui) o item em uma posição específica (0..MAX_ITENS-1). */
    public void setItemAt(int index, ItemStack item) {
        if (index < 0 || index >= MAX_ITENS) return;
        this.itens.set(index, item);
    }

    /** Remove o item de uma posição (vira slot vazio), sem deslocar os demais. */
    public void removeItemAt(int index) {
        if (index < 0 || index >= MAX_ITENS) return;
        this.itens.set(index, null);
    }

    /** Índice da primeira posição vazia, ou -1 se a caixa está cheia. */
    public int firstFreeIndex() {
        for (int i = 0; i < MAX_ITENS; i++) {
            ItemStack it = itens.get(i);
            if (it == null || it.getType().isAir()) return i;
        }
        return -1;
    }

    /** Quantidade de slots de recompensa ocupados. */
    public int countItens() {
        int n = 0;
        for (ItemStack it : itens) {
            if (it != null && !it.getType().isAir()) n++;
        }
        return n;
    }

    public boolean isFull() { return firstFreeIndex() < 0; }

    public String getBlockDisplayUUID() { return blockDisplayUUID; }
    public void setBlockDisplayUUID(String uuid) { this.blockDisplayUUID = uuid; }

    public String getInteractionUUID() { return interactionUUID; }
    public void setInteractionUUID(String uuid) { this.interactionUUID = uuid; }

    public String getNexoKeyId() { return nexoKeyId; }
    public void setNexoKeyId(String nexoKeyId) { this.nexoKeyId = nexoKeyId; }

    public double getPrecoToken() { return precoToken; }
    public void setPrecoToken(double precoToken) { this.precoToken = Math.max(1, precoToken); }
}