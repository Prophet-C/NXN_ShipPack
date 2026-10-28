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
import com.fs.starfarer.api.combat.ShipEngineControllerAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 引擎绿色喷射粒子。
 *
 * 与船中段装饰武器喷烟同样的做法：**沿引擎喷口方向喷射**，不是原地生成。
 * 只在引擎工作时喷。
 *
 * 速度 = 船速 × 0.55 + 喷口方向 × (150~230) + 小抖动
 * 抖动很小，形成一条清晰的绿粒子流。
 */
public class NXN_EngineParticles extends BaseCombatLayeredRenderingPlugin {

    private static final float SPAWN_CHANCE = 0.6f;

    private static final float SIZE_MIN = 3f;
    private static final float SIZE_RANGE = 4f;

    private static final float DURATION_MIN = 0.35f;
    private static final float DURATION_RANGE = 0.35f;

    private static final float EXHAUST_MIN = 150f;
    private static final float EXHAUST_RANGE = 80f;

    private static final float VEL_INHERIT = 0.55f;

    private static final float VEL_JITTER = 14f;
    private static final float POS_JITTER = 4f;
    private static final float NOZZLE_OFFSET = 6f;

    private static final Color C_OUTER = new Color(120, 255, 165, 160);
    private static final Color C_INNER = new Color(220, 255, 235, 190);

    private final ShipAPI ship;

    public NXN_EngineParticles(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);
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
        return ship == null || !ship.isAlive();
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        // 只发粒子
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        if (ship == null || !ship.isAlive()) return;

        ShipEngineControllerAPI ctrl = ship.getEngineController();
        if (ctrl == null) return;

        for (ShipEngineControllerAPI.ShipEngineAPI e : ctrl.getShipEngines()) {
            if (e == null) continue;
            if (!e.isActive() || e.isDisabled()) continue;
            if (Math.random() > SPAWN_CHANCE) continue;

            Vector2f loc = e.getLocation();
            if (loc == null) continue;

            float angleDeg = ship.getFacing();
            if (e.getEngineSlot() != null) {
                angleDeg += e.getEngineSlot().getAngle();
            }
            float rad = (float) Math.toRadians(angleDeg);
            float dirX = (float) Math.cos(rad);
            float dirY = (float) Math.sin(rad);

            Vector2f p = new Vector2f(
                    loc.x + dirX * NOZZLE_OFFSET + rand() * POS_JITTER,
                    loc.y + dirY * NOZZLE_OFFSET + rand() * POS_JITTER);

            float exhaust = EXHAUST_MIN + (float) Math.random() * EXHAUST_RANGE;
            Vector2f vel = new Vector2f(
                    ship.getVelocity().x * VEL_INHERIT + dirX * exhaust + rand() * VEL_JITTER,
                    ship.getVelocity().y * VEL_INHERIT + dirY * exhaust + rand() * VEL_JITTER);

            float size = SIZE_MIN + (float) Math.random() * SIZE_RANGE;
            float dur = DURATION_MIN + (float) Math.random() * DURATION_RANGE;

            engine.addHitParticle(p, vel, size, 1.05f, dur, C_OUTER);
            if (Math.random() < 0.35f) {
                engine.addHitParticle(p, new Vector2f(vel.x * 0.96f, vel.y * 0.96f),
                        size * 0.55f, 1.2f, dur * 0.8f, C_INNER);
            }
        }
    }

    private static float rand() {
        return (float) Math.random() * 2f - 1f;
    }
}
