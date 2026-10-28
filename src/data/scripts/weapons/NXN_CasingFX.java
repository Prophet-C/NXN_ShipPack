package data.scripts.weapons;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 抛壳特效：每次开火从炮塔后方甩出弹壳，带抛物线 + 自旋 + 淡出。
 *
 * 用法：NXN_CasingFX.spawn(engine, 位置, 速度, 初始角度, 自旋速度, 寿命);
 * 渲染：ABOVE_SHIPS 层，自己绘制，不依赖游戏实体。
 */
public class NXN_CasingFX extends BaseCombatLayeredRenderingPlugin {

    private static class Casing {
        float x, y, vx, vy, ang, spin, life, maxLife;
        float size;
    }

    private static final List<Casing> CASINGS = new ArrayList<Casing>();
    private static SpriteAPI sprite = null;
    private static final EnumSet<CombatEngineLayers> LAYERS =
            EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);

    /** 重力（像素/秒²），让弹壳往下掉 */
    private static final float GRAVITY = 120f;

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
        if (CASINGS.isEmpty()) return;
        Iterator<Casing> it = CASINGS.iterator();
        while (it.hasNext()) {
            Casing c = it.next();
            c.x += c.vx * amount;
            c.y += c.vy * amount;
            c.vy -= GRAVITY * amount;
            c.vx *= (1f - Math.min(1f, amount * 1.2f));
            c.vy *= (1f - Math.min(1f, amount * 0.6f));
            c.ang += c.spin * amount;
            c.life -= amount;
            if (c.life <= 0f) it.remove();
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (CASINGS.isEmpty()) return;
        load();
        if (sprite == null) return;
        for (Casing c : CASINGS) {
            float alpha = Math.min(1f, c.life / (c.maxLife * 0.4f));
            try {
                // 先画一层加色亮芯（保证在暗背景/烟雾里也看得见）
                sprite.setAdditiveBlend();
                sprite.setSize(c.size * 1.15f, c.size * 1.85f);
                sprite.setAngle(c.ang);
                sprite.setColor(new java.awt.Color(255, 225, 170, (int) Math.max(0, Math.min(255, 120 * alpha))));
                sprite.renderAtCenter(c.x, c.y);

                sprite.setNormalBlend();
                sprite.setSize(c.size, c.size * 1.6f);
                sprite.setAngle(c.ang);
                sprite.setColor(new java.awt.Color(255, 255, 255,
                        (int) Math.max(0, Math.min(255, alpha * 255f))));
                sprite.renderAtCenter(c.x, c.y);
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private static void load() {
        if (sprite != null) return;
        try {
            sprite = Global.getSettings().getSprite("graphics/weapons/nxn/shiliu_casing.png");
            if (sprite != null) sprite.getWidth();
        } catch (Throwable ignored) {
            sprite = null;
        }
    }

    /** 甩出一个弹壳 */
    public static void spawn(com.fs.starfarer.api.combat.CombatEngineAPI engine,
                             Vector2f pos, Vector2f vel, float angle, float spin,
                             float life, float size) {
        if (engine == null || pos == null) return;
        Casing c = new Casing();
        c.x = pos.x;
        c.y = pos.y;
        c.vx = vel == null ? 0f : vel.x;
        c.vy = vel == null ? 0f : vel.y;
        c.ang = angle;
        c.spin = spin;
        c.life = life;
        c.maxLife = life;
        c.size = size;
        CASINGS.add(c);

        // 确保渲染插件存在（每个战斗各注册一次）
        if (!registered || engine != lastEngine) {
            registered = true;
            lastEngine = engine;
            CASINGS.clear();
            try {
                engine.addLayeredRenderingPlugin(new NXN_CasingFX());
            } catch (Throwable ex) {
                // 注册失败静默
            }
        }
    }

    private static boolean registered = false;
    private static com.fs.starfarer.api.combat.CombatEngineAPI lastEngine = null;

    /** 战斗结束/换场时清理 */
    public static void reset() {
        CASINGS.clear();
        registered = false;
        sprite = null;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
    }
}
