package data.scripts.weapons;

import java.awt.Color;
import java.util.EnumSet;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 量子跃迁序列：
 *
 *  0.0 ~ 0.8s  准备：舰船抖动，带淡入淡出
 *  0.8s        瞬移（只在起点播放粒子双环）
 *  0.8 ~ 1.15s 落点抖动，带淡入淡出
 */
public class NXN_TeleportSequence extends BaseCombatLayeredRenderingPlugin {

    /** 准备时间 */
    private static final float PREP = 0.8f;
    /** 落点抖动持续 */
    private static final float POST = 0.7f;
    /** 抖动副本数 / 偏移半径 / 最大透明度 */
    private static final int COPIES = 5;
    private static final float JITTER_RANGE = 22f;
    private static final int JITTER_ALPHA = 120;
    /** 淡入淡出时间 */
    private static final float FADE = 0.2f;
    private static final Color JITTER_TINT = new Color(100, 190, 255);

    private final EnumSet<CombatEngineLayers> layers = EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);
    private final ShipAPI ship;
    private final CombatEngineAPI engine;
    private final Vector2f to = new Vector2f();
    private final ShipAPI faceTarget;

    private float t = 0f;
    private boolean teleported = false;

    public NXN_TeleportSequence(ShipAPI ship, Vector2f target) {
        this(ship, target, null);
    }

    public NXN_TeleportSequence(ShipAPI ship, Vector2f target, ShipAPI faceTarget) {
        this.ship = ship;
        this.engine = Global.getCombatEngine();
        this.faceTarget = faceTarget;
        if (target != null) this.to.set(target);

        // 准备阶段播放传送声效
        try {
            Global.getSoundPlayer().playSound("NXN_teleport_shift", 1f, 1f,
                    ship.getLocation(), Misc.ZERO);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return layers;
    }

    @Override
    public float getRenderRadius() {
        return 100000f;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
    }

    public boolean isExpired() {
        return t > PREP + POST;
    }

    @Override
    public void advance(float amount) {
        if (engine == null || engine.isPaused()) return;
        if (ship == null || !ship.isAlive()) return;

        t += amount;
        if (!teleported && t >= PREP) {
            teleported = true;
            doTeleport();
        }
    }

    private void doTeleport() {
        Vector2f from = new Vector2f(ship.getLocation());

        // 瞬移
        ship.getLocation().set(to);
        ship.getVelocity().set(0f, 0f);

        // 落地后船头直接对准目标敌舰
        if (faceTarget != null && faceTarget.isAlive()) {
            Vector2f d = new Vector2f(faceTarget.getLocation().x - to.x,
                    faceTarget.getLocation().y - to.y);
            float ang = (float) Math.toDegrees(Math.atan2(d.y, d.x));
            ship.setFacing(ang);
        }

        // 双环改为落地时触发（跟随落地后的船首方向）
        engine.addLayeredRenderingPlugin(new NXN_TeleportRings(to, ship.getFacing()));


    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float alphaFactor;
        if (t <= PREP) {
            // 准备阶段：淡入 + 末尾淡出
            float in = Math.min(1f, t / FADE);
            float out = Math.min(1f, Math.max(0f, (PREP - t) / FADE));
            alphaFactor = Math.min(in, out);
        } else {
            // 落点阶段：淡出
            alphaFactor = Math.max(0f, 1f - (t - PREP) / POST);
        }
        if (alphaFactor <= 0.01f) return;

        int baseAlpha = (int) (JITTER_ALPHA * alphaFactor);

        // 抖动幅度：准备阶段 0 -> 1 逐渐变大；落点后 1 -> 0 逐渐减小
        float shake;
        if (t <= PREP) {
            shake = Math.max(0.12f, t / PREP);
        } else {
            shake = Math.max(0f, 1f - (t - PREP) / POST);
        }
        float range = JITTER_RANGE * shake;

        drawJitter(ship, baseAlpha, range);
        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (module != null && module.isAlive()) drawJitter(module, baseAlpha, range);
        }
    }

    private void drawJitter(ShipAPI s, int baseAlpha, float range) {
        SpriteAPI spr = s.getSpriteAPI();
        if (spr == null) return;
        for (int i = 0; i < COPIES; i++) {
            float ox = (float) (Math.random() * 2.0 - 1.0) * range;
            float oy = (float) (Math.random() * 2.0 - 1.0) * range;
            int alpha = baseAlpha - i * 15;
            if (alpha <= 0) continue;
            try {
                spr.setAdditiveBlend();
                spr.setAngle(s.getFacing() - 90f);
                spr.setColor(new Color(JITTER_TINT.getRed(), JITTER_TINT.getGreen(),
                        JITTER_TINT.getBlue(), alpha));
                spr.renderAtCenter(s.getLocation().x + ox, s.getLocation().y + oy);
            } catch (Throwable ignored) {
            }
        }
        try {
            spr.setNormalBlend();
            spr.setColor(Color.WHITE);
            spr.setAngle(s.getFacing() - 90f);
        } catch (Throwable ignored) {
        }
    }
}
