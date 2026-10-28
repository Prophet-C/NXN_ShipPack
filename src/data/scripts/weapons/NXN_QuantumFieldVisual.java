package data.scripts.weapons;

import java.awt.Color;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashSet;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 系统三「量子爆发-反转」的领域视觉 —— 星云漩涡版。
 *
 * 构成（自内向外）：
 *  1. 明亮核心（白绿小球）
 *  2. 螺旋星云：多条对数螺旋臂，由密集尘埃粒子 + 软贴图构成
 *  3. 外围暗色底色（压暗整个领域）
 *  4. 边界标志线（细亮圆环，标出生效范围）
 *  5. 收起涟漪
 *
 * 螺旋由粒子在 advance 中生成（沿曲线撒尘），贴图在 render 中叠加柔光。
 */
public class NXN_QuantumFieldVisual extends BaseCombatLayeredRenderingPlugin {

    /** 领域最大半径（su） */
    public static final float RADIUS = 2600f;
    /** 张开速度（su/秒） */
    private static final float OPEN_SPEED = 750f;
    /** 收缩基础速度 */
    private static final float CLOSE_BASE = 1500f;
    /** 总持续（与子系统一致） */
    private static final float DURATION = 20f;
    /** 倒计时音时长（秒）：在剩余这么久时开始播放，尾声正好对齐结束点 */
    private static final float COUNTDOWN_LEN = 4.02f;
    private boolean countdownPlayed = false;
    /** 淡入 / 淡出 */
    private static final float FADE_IN = 0.5f;
    private static final float FADE_OUT = 0.8f;

    // ===== 星云漩涡 =====
    /** 漩涡半径占领域比例 */
    private static final float VORTEX_RADIUS_FRAC = 0.62f;
    /** 螺旋臂数量 */
    private static final int SPIRAL_ARMS = 3;
    /** 螺旋扭转（弧度，越大越卷） */
    private static final float SPIRAL_TWIST = 3.4f;
    /** 基础转速（度/秒） */
    private static final float SPIRAL_SPIN = 42f;
    /** 每帧尘埃粒子数 / 寿命 / 尺寸 */
    private static final int DUST_PER_FRAME = 80;
    private static final float DUST_LIFE = 1.5f;
    private static final float DUST_SIZE_MIN = 3f;
    private static final float DUST_SIZE_MAX = 9f;

    /** 外圈星云：数量 / 所在半径比例 / 单块尺寸比例 / 透明度 */
    private static final int OUTER_NEBULA_COUNT = 10;
    private static final float OUTER_RING_FRAC = 0.86f;
    private static final float OUTER_BLOB_FRAC = 0.46f;
    private static final float OUTER_ALPHA = 95f;

    /** 领域内敌方时间倍率（0.67 = 慢 33%）*/
    private static final float TIME_MULT = 0.50f;   // 时间流速 -50%
    /** 减速效果的 stat id */
    private static final String SLOW_ID = "NXN_quantum_field_slow";
    /** 外框柔光：放大比例 / 透明度 */
    private static final float GLOW_SCALE = 1.06f;
    private static final float GLOW_ALPHA = 105f;
    /** 射弹/导弹进入领域时的速度倍率（-33%）*/
    private static final float PROJECTILE_SLOW = 0.67f;
    /** 向外推力（像素/秒²）：把进入领域的敌方射弹推开 */
    private static final float PUSH_ACCEL = 800f;      // 中心处最大推力（翻倍）
    private static final float PUSH_ACCEL_MISSILE = 800f;
    private static final float PUSH_MAX_SPEED = 2200f;

    /** 射弹扫描间隔 */
    private static final float PROJ_CHECK = 0.1f;

    /** 目标检测间隔 */
    private static final float CHECK_INTERVAL = 0.15f;

    private static final Color C_CORE = new Color(235, 255, 240, 255);
    private static final Color C_WISP = new Color(150, 255, 185, 255);
    private static final Color C_DUST = new Color(110, 245, 150, 255);
    private static final Color C_DUST_2 = new Color(200, 255, 215, 255);
    private static final Color C_BOUNDARY = new Color(180, 255, 200, 255);
    private static final Color DARK_BASE = new Color(4, 12, 8, 255);

    private final EnumSet<CombatEngineLayers> layers = EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);
    private final ShipAPI ship;
    private final CombatEngineAPI engine;
    private final Vector2f center = new Vector2f();

    private final SpriteAPI halo;
    private final SpriteAPI arm;
    private final SpriteAPI core;
    private final SpriteAPI rippleSprite;

    private final NXN_FieldShader shader = new NXN_FieldShader();

    private final Set<ShipAPI> affected = new HashSet<ShipAPI>();
    private final Set<Object> slowedProjectiles =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
    private float projTimer = 0f;
    private float checkTimer = 0f;

    private float radius = 0f;
    private float age = 0f;
    private boolean active = true;
    private float rippleAge = -1f;

    public NXN_QuantumFieldVisual(ShipAPI ship) {
        this.ship = ship;
        this.engine = Global.getCombatEngine();
        if (ship != null) center.set(ship.getLocation());

        this.halo = cloud("NXN_fx", "dust");
        this.arm = cloud("NXN_fx", "nebula");
        this.core = sprite("glow_1");
        this.rippleSprite = sprite("shockwave_1");
    }

    private static SpriteAPI cloud(String category, String key) {
        try {
            return Global.getSettings().getSprite(category, key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static SpriteAPI sprite(String key) {
        try {
            return Global.getSettings().getSprite("NXN_nuke", key);
        } catch (Throwable ignored) {
            return null;
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
        if (!active && radius <= 1f) {
            clearSlowdown();
        }
        return !active && radius <= 1f && (rippleAge < 0f || rippleAge > 0.8f);
    }

    @Override
    public void advance(float amount) {
        if (engine == null || engine.isPaused()) return;
        age += amount;

        // 领域快结束时播放倒计时，尾声对齐结束时间点
        if (!countdownPlayed && age >= DURATION - COUNTDOWN_LEN) {
            countdownPlayed = true;
            try {
                Global.getSoundPlayer().playSound("NXN_field_countdown", 1f, 1f,
                        center, new Vector2f());
            } catch (Throwable ignored) {
            }
        }
        if (ship != null && ship.isAlive()) center.set(ship.getLocation());

        if (age <= DURATION) {
            radius = Math.min(RADIUS, radius + OPEN_SPEED * amount);
        } else {
            active = false;
            float progress = 1f - Math.min(1f, radius / RADIUS);
            float speed = CLOSE_BASE + progress * progress * 3500f;
            float old = radius;
            radius = Math.max(0f, radius - speed * amount);
            if (old > 0f && radius <= 0f && rippleAge < 0f) {
                rippleAge = 0f;
            }
        }

        if (rippleAge >= 0f) rippleAge += amount;

        // ===== 敌方减速（timeMult）=====
        if (age <= DURATION) {
            checkTimer += amount;
            if (checkTimer >= CHECK_INTERVAL) {
                checkTimer = 0f;
                updateAffectedShips();
            }

            projTimer += amount;
            if (projTimer >= PROJ_CHECK) {
                projTimer = 0f;
                slowProjectilesInField();
            }
        } else {
            clearSlowdown();
        }

        // ===== 领域内随机粒子（随机出现 / 随机移动 / 快速消失）=====
        boolean live = age <= DURATION && radius > 40f;
        if (!live) return;

        for (int i = 0; i < DUST_PER_FRAME; i++) {
            double ang = Math.random() * Math.PI * 2.0;
            float rr = radius * (float) Math.sqrt(Math.random());
            float px = center.x + (float) Math.cos(ang) * rr;
            float py = center.y + (float) Math.sin(ang) * rr;

            float spd = 30f + (float) Math.random() * 110f;
            double va = Math.random() * Math.PI * 2.0;
            Vector2f vel = new Vector2f((float) Math.cos(va) * spd, (float) Math.sin(va) * spd);

            engine.addHitParticle(new Vector2f(px, py), vel,
                    DUST_SIZE_MIN + (float) Math.random() * (DUST_SIZE_MAX - DUST_SIZE_MIN),
                    1.05f, 0.70f + (float) Math.random() * 0.60f,
                    Math.random() < 0.7f ? C_DUST : C_DUST_2);
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (radius <= 1f) return;

        float fade = 1f;
        if (age < FADE_IN) fade = age / FADE_IN;
        else if (age > DURATION) fade = Math.max(0f, 1f - (age - DURATION) / FADE_OUT);
        fade = Math.max(0f, Math.min(1f, fade));

        float size = radius * 2f;
        float vortexR = radius * VORTEX_RADIUS_FRAC;
        float vortexSize = vortexR * 2f;

        // ===== 着色器方案（首选）=====
        shader.render(center, radius, age, fade, C_WISP, viewport);
        boolean shaderOk = shader.isOk();

        // ===== 1) 暗色底色（贴图备用方案）=====
        if (!shaderOk && halo != null) {
            halo.setNormalBlend();
            halo.setSize(size, size);
            halo.setColor(new Color(DARK_BASE.getRed(), DARK_BASE.getGreen(), DARK_BASE.getBlue(),
                    (int) (52f * fade)));
            halo.renderAtCenter(center.x, center.y);
        }

        // ===== 2) 星云柔光（备用方案）=====
        for (int i = 0; !shaderOk && i < 3; i++) {
            if (arm == null) break;
            float spin = age * (18f + i * 9f) * ((i % 2 == 0) ? 1f : -0.65f);
            float sc = 1f + 0.06f * (float) Math.sin(age * 1.7f + i * 2.1f);
            int alpha = (int) ((i == 0 ? 120f : i == 1 ? 85f : 55f) * fade);
            arm.setAdditiveBlend();
            arm.setAngle(spin);
            arm.setSize(vortexSize * (1.15f - 0.15f * i) * sc, vortexSize * (1.15f - 0.15f * i) * sc);
            arm.setColor(new Color(C_WISP.getRed(), C_WISP.getGreen(), C_WISP.getBlue(),
                    Math.min(255, alpha)));
            arm.renderAtCenter(center.x, center.y);
        }

        // ===== 3) 明亮核心（备用方案）=====
        if (!shaderOk && core != null) {
            core.setAdditiveBlend();
            core.setAngle(age * 30f);
            core.setSize(vortexSize * 0.34f, vortexSize * 0.34f);
            core.setColor(new Color(C_CORE.getRed(), C_CORE.getGreen(), C_CORE.getBlue(),
                    (int) (185f * fade)));
            core.renderAtCenter(center.x, center.y);
        }

        // ===== 3.8) 被干扰目标的外框柔光 =====
        renderTargetGlow(fade);

        // ===== 4) 边界实线（GL_LINE_LOOP）=====
        drawBoundaryLine(fade);

        // ===== 5) 收起涟漪 =====
        if (rippleAge >= 0f && rippleAge <= 0.8f && rippleSprite != null) {
            float rt = rippleAge / 0.8f;
            float rSize = RADIUS * 2f * (0.2f + 0.8f * rt);
            rippleSprite.setAdditiveBlend();
            rippleSprite.setAngle(0f);
            rippleSprite.setSize(rSize, rSize);
            rippleSprite.setColor(new Color(200, 255, 215, (int) (170f * (1f - rt))));
            rippleSprite.renderAtCenter(center.x, center.y);
        }
    }

    /** 边界实线（外粗内细，形成辉光边缘） */
    private void drawBoundaryLine(float fade) {
        int seg = 180;
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);

        org.lwjgl.opengl.GL11.glLineWidth(5f);
        org.lwjgl.opengl.GL11.glColor4f(C_BOUNDARY.getRed() / 255f,
                C_BOUNDARY.getGreen() / 255f, C_BOUNDARY.getBlue() / 255f, 0.20f * fade);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
        for (int i = 0; i < seg; i++) {
            double a = (Math.PI * 2.0) * i / seg;
            org.lwjgl.opengl.GL11.glVertex2f(center.x + (float) Math.cos(a) * radius,
                    center.y + (float) Math.sin(a) * radius);
        }
        org.lwjgl.opengl.GL11.glEnd();

        org.lwjgl.opengl.GL11.glLineWidth(2f);
        org.lwjgl.opengl.GL11.glColor4f(C_BOUNDARY.getRed() / 255f,
                C_BOUNDARY.getGreen() / 255f, C_BOUNDARY.getBlue() / 255f, 0.85f * fade);
        org.lwjgl.opengl.GL11.glBegin(org.lwjgl.opengl.GL11.GL_LINE_LOOP);
        for (int i = 0; i < seg; i++) {
            double a = (Math.PI * 2.0) * i / seg;
            org.lwjgl.opengl.GL11.glVertex2f(center.x + (float) Math.cos(a) * radius,
                    center.y + (float) Math.sin(a) * radius);
        }
        org.lwjgl.opengl.GL11.glEnd();

        org.lwjgl.opengl.GL11.glPopAttrib();
    }

    /** 更新领域内的敌方舰船，施加/移除减速 */
    private void updateAffectedShips() {
        if (engine == null || ship == null) return;

        Set<ShipAPI> nowInside = new HashSet<ShipAPI>();
        for (ShipAPI s : engine.getShips()) {
            if (s == null || !s.isAlive() || s.isHulk()) continue;
            if (s.getOwner() == ship.getOwner()) continue;
            if (com.fs.starfarer.api.util.Misc.getDistance(s.getLocation(), center) > radius) continue;
            nowInside.add(s);
        }

        // 新进领域
        for (ShipAPI s : nowInside) {
            if (!affected.contains(s)) {
                try {
                    s.getMutableStats().getTimeMult().modifyMult(SLOW_ID, TIME_MULT);
                } catch (Throwable ignored) {
                }
            }
        }
        // 离开领域
        for (ShipAPI s : affected) {
            if (!nowInside.contains(s)) {
                try {
                    s.getMutableStats().getTimeMult().unmodify(SLOW_ID);
                } catch (Throwable ignored) {
                }
            }
        }

        affected.clear();
        affected.addAll(nowInside);


    }

    /** 清理所有减速 */
    private void clearSlowdown() {
        for (ShipAPI s : affected) {
            try {
                s.getMutableStats().getTimeMult().unmodify(SLOW_ID);
            } catch (Throwable ignored) {
            }
        }
        affected.clear();
        slowedProjectiles.clear();
    }

    /** 被干扰目标的外框柔光（放大贴图 + 加法混合）*/
    private void renderTargetGlow(float fade) {
        if (affected.isEmpty()) return;
        float pulse = 0.75f + 0.25f * (float) Math.sin(age * 3.4f);

        for (ShipAPI s : affected) {
            if (s == null || !s.isAlive()) continue;
            SpriteAPI spr = s.getSpriteAPI();
            if (spr == null) continue;
            try {
                float w = spr.getWidth() * GLOW_SCALE;
                float h = spr.getHeight() * GLOW_SCALE;
                spr.setAdditiveBlend();
                spr.setAngle(s.getFacing() - 90f);
                spr.setSize(w, h);
                spr.setColor(new Color(C_WISP.getRed(), C_WISP.getGreen(), C_WISP.getBlue(),
                        Math.max(0, Math.min(255, (int) (GLOW_ALPHA * fade * pulse)))));
                spr.renderAtCenter(s.getLocation().x, s.getLocation().y);
                // 恢复
                spr.setNormalBlend();
                spr.setColor(Color.WHITE);
                spr.setAngle(s.getFacing() - 90f);
                spr.setSize(w / GLOW_SCALE, h / GLOW_SCALE);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 领域内的射弹/导弹：首次进入减速 33%，之后持续受到向外推力 */
    private void slowProjectilesInField() {
        if (engine == null) return;
        float dt = PROJ_CHECK;

        try {
            for (com.fs.starfarer.api.combat.DamagingProjectileAPI p : engine.getProjectiles()) {
                if (p == null || p.isExpired()) continue;
                // 只影响敌方射弹
                if (p.getOwner() == ship.getOwner()) continue;
                Vector2f loc = p.getLocation();
                if (com.fs.starfarer.api.util.Misc.getDistance(loc, center) > radius) continue;

                Vector2f vel = p.getVelocity();
                if (!slowedProjectiles.contains(p)) {
                    vel.scale(PROJECTILE_SLOW);
                    slowedProjectiles.add(p);
                }
                pushOutward(loc, vel, PUSH_ACCEL, dt);
            }
        } catch (Throwable ignored) {
        }

        try {
            for (com.fs.starfarer.api.combat.MissileAPI m : engine.getMissiles()) {
                if (m == null || m.isExpired()) continue;
                if (m.getOwner() == ship.getOwner()) continue;
                Vector2f loc = m.getLocation();
                if (com.fs.starfarer.api.util.Misc.getDistance(loc, center) > radius) continue;

                Vector2f vel = m.getVelocity();
                if (!slowedProjectiles.contains(m)) {
                    vel.scale(PROJECTILE_SLOW);
                    slowedProjectiles.add(m);
                }
                pushOutward(loc, vel, PUSH_ACCEL_MISSILE, dt);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 沿"领域中心 -> 该点"方向施加推力：中心最大，边缘衰减到 0 */
    private void pushOutward(Vector2f loc, Vector2f vel, float accel, float dt) {
        float dx = loc.x - center.x;
        float dy = loc.y - center.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) len = 1f;

        // 距离衰减：中心 1.0 -> 边缘 0
        float t = Math.min(1f, len / Math.max(1f, radius));
        float falloff = 1f - t;
        float a = accel * falloff;
        float nx = dx / len;
        float ny = dy / len;

        // ① 改速度（多数射弹有效）
        vel.x += nx * a * dt;
        vel.y += ny * a * dt;

        // ② 同时直接位移（部分弹的 getVelocity() 返回副本，改速度无效）
        float shift = a * dt * dt * 2f;
        loc.x += nx * shift;
        loc.y += ny * shift;

        float sp = vel.length();
        if (sp > PUSH_MAX_SPEED && sp > 0.001f) {
            vel.scale(PUSH_MAX_SPEED / sp);
        }
    }
}
