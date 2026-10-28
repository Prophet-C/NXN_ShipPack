package data.scripts.weapons;

import java.awt.Color;
import java.util.EnumSet;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 量子跃迁的双环粒子特效（快速 / 宽环 / 朝向对齐）。
 *
 * - 双环圆心沿船首方向偏移，形成 ∞ 形；两圆心间距随扩张逐渐缩短（比 2×半径 少 200 su）
 * - 环带宽度（径向抖动）加宽
 * - 总粒子数 12000，0.35 秒内生成完，粒子寿命 0.3 秒
 * - 半径 10 su → 300 su
 */
public class NXN_TeleportRings extends BaseCombatLayeredRenderingPlugin {

    private static final int TOTAL_PARTICLES = 12000;
    private static final float DURATION = 1.6f;
    private static final float PARTICLE_LIFE = 2.0f;   // 环存在约 2 秒（快速扩张后缓慢消散）
    private static final float START_SU = 10f;
    private static final float END_SU = 300f;
    private static final float SU_TO_PX = 3f;
    private static final int PER_FRAME = 125;

    /** 环带径向厚度（像素，越大环越"宽"） */
    private static final float BAND_THICKNESS = 27f;
    /** 两圆心间距在终点比 2×半径 少多少 su */
    private static final float END_SEPARATION_REDUCTION_SU = 200f;

    private static final Color C1 = new Color(70, 255, 120, 255);
    private static final Color C2 = new Color(150, 255, 180, 255);

    private final CombatEngineAPI engine;
    private final Vector2f center = new Vector2f();
    private final float facing;
    private float age = 0f;
    private int spawned = 0;

    public NXN_TeleportRings(Vector2f at, float facing) {
        if (at != null) center.set(at);
        this.facing = facing;
        this.engine = Global.getCombatEngine();
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.UNDER_SHIPS_LAYER);
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
        return age > DURATION + PARTICLE_LIFE + 0.2f;
    }

    @Override
    public void advance(float amount) {
        if (engine == null || engine.isPaused()) return;
        age += amount;
        if (age > DURATION) return;

        float t = Math.min(1f, age / DURATION);
        float radiusPx = (START_SU + (END_SU - START_SU) * t) * SU_TO_PX;

        // 圆心间距：起点 2r，终点 2r - 200su（不足则归零）
        float reductionPx = (END_SEPARATION_REDUCTION_SU * SU_TO_PX) * t;
        float offsetPx = Math.max(0f, radiusPx - reductionPx * 0.5f);

        // 沿船首方向向右旋转 90 度偏移
        double rad = Math.toRadians(facing - 90.0);
        float dirX = (float) Math.cos(rad);
        float dirY = (float) Math.sin(rad);

        for (int i = 0; i < PER_FRAME && spawned < TOTAL_PARTICLES; i++) {
            spawned++;
            int ring = spawned % 2;
            float sign = (ring == 0) ? -1f : 1f;

            float cx = center.x + dirX * offsetPx * sign;
            float cy = center.y + dirY * offsetPx * sign;

            double ang = Math.random() * Math.PI * 2.0;
            float band = (float) (Math.random() * 2.0 - 1.0) * BAND_THICKNESS;
            float r = radiusPx + band;

            float px = cx + (float) Math.cos(ang) * r;
            float py = cy + (float) Math.sin(ang) * r;

            float spd = 10f + (float) Math.random() * 40f;
            double va = Math.random() * Math.PI * 2.0;
            Vector2f vel = new Vector2f((float) Math.cos(va) * spd, (float) Math.sin(va) * spd);

            engine.addHitParticle(new Vector2f(px, py), vel,
                    5f + (float) Math.random() * 8f,
                    1.2f, PARTICLE_LIFE * (0.75f + (float) Math.random() * 0.25f),
                    Math.random() < 0.5f ? C1 : C2);
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        // 纯粒子表现
    }
}
