package com.psdk.boss;

import com.psdk.PSDK;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Ponte por reflection com o ModelEngine R4 (com.ticxo.modelengine.api).
 *
 * Compila sem o jar do ModelEngine; se o plugin não estiver presente, degrada
 * (o boss funciona, só fica sem o modelo 3D). É tolerante a pequenas variações
 * de assinatura entre versões — procura os métodos por nome + número de args.
 */
public final class ModelEngineHook {

    private ModelEngineHook() {}

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("ModelEngine");
    }

    /**
     * Cria o ModeledEntity para a entidade e aplica o blueprint (modelId).
     * Retorna o objeto ActiveModel (pra tocar animações depois), ou null se falhar.
     */
    public static Object applyModel(Entity entity, String modelId) {
        if (!isAvailable()) return null;
        Logger log = PSDK.getInstance().getLogger();
        try {
            Class<?> api = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");

            // createModeledEntity(Entity) -> ModeledEntity  (fallback getOrCreate)
            Object modeled = invokeStatic(api, "createModeledEntity", entity);
            if (modeled == null) modeled = invokeStatic(api, "getOrCreateModeledEntity", entity);
            if (modeled == null) {
                log.warning("[Boss] ModelEngine: createModeledEntity retornou null.");
                return null;
            }

            // createActiveModel(String) -> ActiveModel
            Object active = invokeStatic(api, "createActiveModel", modelId);
            if (active == null) {
                log.warning("[Boss] ModelEngine: modelo '" + modelId + "' não existe (importou o blueprint e deu /meg reload?).");
                return null;
            }

            // modeled.addModel(active, true)
            Method addModel = findMethod(modeled.getClass(), "addModel", 2);
            if (addModel != null) {
                addModel.setAccessible(true);
                addModel.invoke(modeled, active, true);
            } else {
                log.warning("[Boss] ModelEngine: método addModel não encontrado.");
            }

            log.info("[Boss] ModelEngine: modelo '" + modelId + "' aplicado com sucesso.");
            return active;
        } catch (Throwable t) {
            log.warning("[Boss] ModelEngine falhou ao aplicar '" + modelId + "': "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
            return null;
        }
    }

    /** Tenta aplicar o PRIMEIRO modelId que existir (resiliente a variações de nome do blueprint). */
    public static Object applyModelAny(Entity entity, String... modelIds) {
        for (String id : modelIds) {
            Object m = applyModel(entity, id);
            if (m != null) return m;
        }
        return null;
    }

    /** Escala o modelo (deixa maior/menor). Tolerante a variações de assinatura do R4. */
    public static void setScale(Object activeModel, double scale) {
        if (activeModel == null) return;
        try {
            for (String name : new String[]{"setScale", "setBaseScale", "scale"}) {
                for (Method m : activeModel.getClass().getMethods()) {
                    if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
                    Class<?> pt = m.getParameterTypes()[0];
                    m.setAccessible(true);
                    if (pt == float.class)  { m.invoke(activeModel, (float) scale); return; }
                    if (pt == double.class) { m.invoke(activeModel, scale);         return; }
                }
            }
        } catch (Throwable ignored) { }
    }

    private static volatile boolean warnedNoPlay = false;

    /** Toca uma animação (pelo nome do bbmodel) no ActiveModel. */
    public static void playAnimation(Object activeModel, String anim, double lerpIn, double lerpOut, double speed, boolean force) {
        if (activeModel == null) return;
        try {
            // O getter do handler muda de nome entre versões: tenta os mais comuns.
            Object handler = null;
            for (String getter : new String[]{"getAnimationHandler", "getAnimation", "getAnimationManager"}) {
                Method g = findMethod(activeModel.getClass(), getter, 0);
                if (g != null) {
                    g.setAccessible(true);
                    handler = g.invoke(activeModel);
                    if (handler != null) break;
                }
            }
            if (handler == null) return;

            // playAnimation(String, double, double, double, boolean)
            Method play = findMethod(handler.getClass(), "playAnimation", 5);
            if (play != null) {
                play.setAccessible(true);
                play.invoke(handler, anim, lerpIn, lerpOut, speed, force);
            } else if (!warnedNoPlay) {
                warnedNoPlay = true;
                PSDK.getInstance().getLogger().warning(
                        "[Boss] ModelEngine: playAnimation(5 args) não encontrado em "
                        + handler.getClass().getName() + " — animações não vão trocar. Me avise.");
            }
        } catch (Throwable ignored) { }
    }

    // ───────────────────────── helpers ─────────────────────────

    private static Object invokeStatic(Class<?> owner, String name, Object arg) {
        try {
            for (Method m : owner.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) {
                    m.setAccessible(true);
                    return m.invoke(null, arg);
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static Method findMethod(Class<?> owner, String name, int paramCount) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        for (Method m : owner.getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        return null;
    }
}
