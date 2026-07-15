package com.psdk.clan;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adaptador de exibição das cores de clan para a GUI nova.
 *
 * Não substitui o sistema de cores existente ({@link ClanCommand} + {@link ColorManager}):
 * apenas expõe as cores sólidas/gradientes/animadas como {@link ClanColorEntry} no formato
 * que a {@link ClanGUI} consome (gradiente, permissão, material e displayName já prontos
 * para MiniMessage). A identidade de cada cor continua sendo o {@code hex} (mesmo valor
 * salvo em {@code clans.color}).
 */
public class ClanColorManager {

    /** Entrada de cor pronta para a GUI. {@code gradiente} = identidade salva no clan. */
    public record ClanColorEntry(String id, String displayName, String gradiente,
                                 String permissao, Material material) {}

    private final Map<String, ClanColorEntry> colors = new LinkedHashMap<>();

    public ClanColorManager() {
        reload();
    }

    public void reload() {
        colors.clear();
        addAll(ClanCommand.CLAN_COLORS, false);
        addAll(ClanCommand.CLAN_GRADIENTS, false);
        addAll(ClanCommand.CLAN_ANIMATED, true);
    }

    private void addAll(java.util.List<ClanCommand.ClanColor> list, boolean animated) {
        for (ClanCommand.ClanColor c : list) {
            Material mat;
            try { mat = Material.valueOf(c.material().toUpperCase()); }
            catch (IllegalArgumentException e) { mat = Material.WHITE_DYE; }

            String label = "Cor " + capitalize(c.name());

            String display;
            if (c.isGradient()) {
                display = c.hex() + label + "</gradient>";
            } else if (animated) {
                // Usa cor representativa derivada do nome; o hex real é sentinela do motor de animação.
                display = "<" + deriveAnimatedPreview(c.name()) + ">" + label;
            } else {
                display = "<" + c.hex() + ">" + label;
            }

            colors.put(c.name(), new ClanColorEntry(c.name(), display, c.hex(), c.permission(), mat));
        }
    }

    private static String deriveAnimatedPreview(String name) {
        String l = name.toLowerCase();
        if (l.contains("silver") || l.contains("shimmer"))             return "#C0C0C0";
        if (l.contains("gray") || l.contains("grey"))                  return "#808080";
        if (l.contains("shadow") || l.contains("ying yang")
                || l.contains("yinyang") || l.contains("vampire")
                || l.contains("midnight") || l.contains("night sky")
                || l.contains("devils") || l.contains("black"))        return "#555555";
        if (l.contains("white") || l.contains("ivory")
                || l.contains("ghost") || l.contains("glowing")
                || l.contains("winter") || l.contains("dreamy cloud")) return "#EEEEEE";
        if (l.contains("orange waving"))                               return "#FF8C00";
        if (l.contains("orange"))                                      return "#FF8C00";
        if (l.contains("rainbow"))                                     return "#FF4B4B";
        if (l.contains("gold"))                                        return "#FFD700";
        if (l.contains("yellow") || l.contains("sunshine"))           return "#FFD700";
        if (l.contains("lime"))                                        return "#32CD32";
        if (l.contains("green") || l.contains("forest")
                || l.contains("mead") || l.contains("brasil")
                || l.contains("christmas tree") || l.contains("witch brew")
                || l.contains("spring garden"))                        return "#228B22";
        if (l.contains("mint"))                                        return "#98FF98";
        if (l.contains("blue") || l.contains("ocean")
                || l.contains("water") || l.contains("azure")
                || l.contains("cookie monster") || l.contains("america")) return "#4169E1";
        if (l.contains("electric") || l.contains("icy aurora")
                || l.contains("mtn dew"))                              return "#00FFFF";
        if (l.contains("aurora") || l.contains("cyan"))               return "#00C9FF";
        if (l.contains("purple") || l.contains("violet")
                || l.contains("wizard") || l.contains("crystal pulse")) return "#8A2BE2";
        if (l.contains("pink") || l.contains("candy")
                || l.contains("sweet") || l.contains("cherry")
                || l.contains("blush") || l.contains("easter")
                || l.contains("lunar") || l.contains("starlit")
                || l.contains("poinsettia") || l.contains("daisy")
                || l.contains("cotton") || l.contains("peach")
                || l.contains("spring light") || l.contains("pale rainbow")) return "#FF69B4";
        if (l.contains("red") || l.contains("scarlet")
                || l.contains("volcano") || l.contains("flame")
                || l.contains("fire") || l.contains("halloween")
                || l.contains("witch poison") || l.contains("piscando")) return "#FF4444";
        if (l.contains("pumpkin"))                                     return "#FF7518";
        if (l.contains("dreamteam"))                                   return "#00BFFF";
        if (l.contains("cosmic"))                                      return "#9966CC";
        if (l.contains("earth"))                                       return "#8B6914";
        return "#fcc850"; // padrão: dourado indicando animação especial
    }

    /** Deixa só a primeira letra maiúscula (ex.: "rosa-quente" -> "Rosa-quente"). */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public Map<String, ClanColorEntry> getAll() {
        return colors;
    }

    /** Procura a entrada pela identidade (hex/gradiente) atualmente salva no clan. */
    public ClanColorEntry byGradiente(String gradiente) {
        if (gradiente == null) return null;
        for (ClanColorEntry e : colors.values()) {
            if (e.gradiente().equals(gradiente)) return e;
        }
        return null;
    }
}
