package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 炮口冲击波：开火瞬间在炮口生成一个快速收缩的加色光环（复刻 UAF 赫雷克萨 609 的 MagicRender 冲击波）。
 *
 * UAF 原参数：起始尺寸 L*0.4 → 结束尺寸 L*0.25，持续 particleDuration，加色混合。
 */
public class NXN_ShockwaveFX extends BaseCombatLayeredRenderingPlugin {

    private static class Wave {
        float x, y, startSize, endSize, life, maxLife;
        float angle;
        Color color;
    }

    private static final List<Wave> WAVES = new ArrayList<Wave>();
    private static SpriteAPI sprite = null;
    private static boolean registered = false;
    private static final EnumSet<CombatEngineLayers> LAYERS =
            EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return LAYERS;
    }

    @Override
    public float getRenderRadius() {
        return 100000f;
    }

    @Override
    public void advance(float amount) {
        if (WAVES.isEmpty()) return;
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave w = it.next();
            w.life -= amount;
            if (w.life <= 0f) it.remove();
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (WAVES.isEmpty()) return;
        load();
        if (sprite == null) return;

        for (Wave w : WAVES) {
            float t = 1f - Math.max(0f, w.life) / w.maxLife;      // 0 → 1
            float size = w.startSize + (w.endSize - w.startSize) * t;
            float alpha = 1f - t;
            try {
                sprite.setAdditiveBlend();
                sprite.setSize(size, size);
                sprite.setAngle(w.angle);
                Color c = w.color;
                sprite.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(),
                        (int) Math.max(0, Math.min(255, c.getAlpha() * alpha))));
                sprite.renderAtCenter(w.x, w.y);
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static void load() {
        if (sprite != null) return;
        try {
            sprite = Global.getSettings().getSprite("graphics/weapons/nxn/shiliu_shockwave.png");
            if (sprite != null) sprite.getWidth();
        } catch (Throwable ignored) {
            sprite = null;
        }
    }

    /** 生成一个冲击波 */
    public static void spawn(CombatEngineAPI engine, float x, float y,
                             float startSize, float endSize, float duration,
                             float angle, Color color) {
        if (engine == null) return;
        Wave w = new Wave();
        w.x = x;
        w.y = y;
        w.startSize = startSize;
        w.endSize = endSize;
        w.life = duration;
        w.maxLife = Math.max(0.001f, duration);
        w.angle = angle;
        w.color = color == null ? Color.WHITE : color;
        WAVES.add(w);

        if (!registered) {
            registered = true;
            try {
                engine.addLayeredRenderingPlugin(new NXN_ShockwaveFX());
            } catch (Throwable ignored) {
            }
        }
    }
}
