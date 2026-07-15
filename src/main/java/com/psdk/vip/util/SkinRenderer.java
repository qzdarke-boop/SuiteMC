package com.psdk.vip.util;

import com.psdk.vip.VipConfig;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Renderiza a cabeça (8x8) do jogador no chat usando {@link VipConfig#HEAD_CHAR}.
 *
 * Tenta vários serviços de avatar por nome (com fallback): se um estiver fora do
 * ar ou bloqueado pelo firewall do servidor, passa para o próximo. Se NENHUM
 * responder (servidor sem internet de saída), volta linhas em branco.
 */
public class SkinRenderer {

    /** Serviços de avatar por nome, na ordem de tentativa (todos devolvem PNG 8x8). */
    private static final String[] SOURCES = {
            "https://mc-heads.net/avatar/%s/8",
            "https://minotar.net/helm/%s/8",
            "https://minotar.net/avatar/%s/8"
    };

    public static CompletableFuture<List<String>> getSkinLines(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            BufferedImage image = null;
            for (String src : SOURCES) {
                image = fetch(String.format(src, playerName));
                if (image != null) break;
            }
            if (image == null) return getEmptyLines();
            return render(image);
        });
    }

    /** Baixa uma imagem da URL; devolve null se falhar (timeout, bloqueio, etc.). */
    private static BufferedImage fetch(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (PSDK)");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            if (conn.getResponseCode() != 200) return null;
            return ImageIO.read(conn.getInputStream());
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** Converte os 8x8 pixels da cabeça em 8 linhas MiniMessage coloridas. */
    private static List<String> render(BufferedImage image) {
        List<String> lines = new ArrayList<>();
        String pixelChar = VipConfig.HEAD_CHAR;
        int w = Math.min(8, image.getWidth());
        int h = Math.min(8, image.getHeight());
        for (int y = 0; y < h; y++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < w; x++) {
                Color c = new Color(image.getRGB(x, y), true);
                String hex = String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
                line.append("<").append(hex).append(">").append(pixelChar);
            }
            line.append("<white> ");
            lines.add(line.toString());
        }
        // Garante 8 linhas mesmo se a imagem vier menor.
        while (lines.size() < 8) lines.add("        ");
        return lines;
    }

    private static List<String> getEmptyLines() {
        List<String> spaces = new ArrayList<>();
        for (int i = 0; i < 8; i++) spaces.add("        ");
        return spaces;
    }
}
