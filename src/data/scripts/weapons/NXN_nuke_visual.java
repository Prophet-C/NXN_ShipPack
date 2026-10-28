package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 半音符式命中特效（尺寸参考 UAF 的 renderMinibreveNuke，颜色改为外橙红内白）。
 *
 * 时间线：白闪 → 橙红冲击波 ×3 → 内白外橙的火球 → 橙红残留 + 碎片
 * 贴图来自本 mod 的 "NXN_nuke" 分类（拷自 UAF semibreve_gfx）。
 */
public class NXN_nuke_visual extends BaseCombatLayeredRenderingPlugin {

    // ===== 配色 =====
    private static final Color WHITE = new Color(255, 255, 255, 255);
    private static final Color WHITE_WARM = new Color(255, 242, 220, 255);
    private static final Color ORANGE = new Color(255, 120, 45, 255);
    private static final Color ORANGE_DEEP = new Color(240, 75, 25, 255);
    private static final Color ORANGE_DIM = new Color(190, 70, 30, 255);

    private static final class Fx {
        final SpriteAPI sprite;
        final float delay;
        final float duration;
        final float size0;
        final float size1;
        final int alpha0;
        final int alpha1;
        final float rotate;
        final float rotateSpeed;
        final float px;
        final float py;
        final Color color;

        Fx(SpriteAPI sprite, float delay, float duration, float size0, float size1,
           int alpha0, int alpha1, float rotate, float rotateSpeed, float px, float py, Color color) {
            this.sprite = sprite;
            this.delay = delay;
            this.duration = duration;
            this.size0 = size0;
            this.size1 = size1;
            this.alpha0 = alpha0;
            this.alpha1 = alpha1;
            this.rotate = rotate;
            this.rotateSpeed = rotateSpeed;
            this.px = px;
            this.py = py;
            this.color = color;
        }
    }

    private final EnumSet<CombatEngineLayers> layers =
            EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    private final List<Fx> fx = new ArrayList<>();
    private final Vector2f center = new Vector2f();
    private float t = 0f;
    private float maxT = 0f;

    public NXN_nuke_visual(Vector2f at) {
        if (at != null) center.set(at);

        // ===== 内层：白 =====
        add("glow_1",      0.00f, 0.16f,  140f,  420f, 255, 0,   0f,  90f, 0f, 0f, WHITE);
        add("explosion_1", 0.02f, 0.50f,  180f,  430f, 255, 0,   0f,  30f, 0f, 0f, WHITE_WARM);
        add("glow_2",      0.18f, 0.55f,  150f,  420f, 210, 0,   0f, -60f, 0f, 0f, WHITE);

        // ===== 外层：橙红冲击波 =====
        add("shockwave_1", 0.00f, 0.32f,   60f,  300f, 235, 0,   0f,  40f, 0f, 0f, ORANGE);
        add("shockwave_2", 0.06f, 0.46f,   90f,  420f, 210, 0,   0f,  25f, 0f, 0f, ORANGE);
        add("shockwave_3", 0.12f, 0.62f,  120f,  560f, 180, 0,   0f,  15f, 0f, 0f, ORANGE_DEEP);
        add("explosion_2", 0.12f, 0.75f,  220f,  520f, 200, 0,   0f, -20f, 0f, 0f, ORANGE);

        // ===== 残留 =====
        add("aftermath_1", 0.48f, 1.50f,  300f,  520f, 150, 0,   0f,   8f, 0f, 0f, ORANGE_DIM);
        add("aftermath_2", 0.78f, 1.80f,  260f,  460f, 120, 0,   0f,  -6f, 0f, 0f, ORANGE_DIM);
        add("glow_3",      0.55f, 0.90f,  160f,  320f,  90, 0,   0f, 120f, 0f, 0f, ORANGE);

        // ===== 碎片 =====
        for (int i = 0; i < 8; i++) {
            float ang = (float) (Math.random() * 360.0);
            float dist = 70f + (float) Math.random() * 220f;
            float px = (float) Math.cos(Math.toRadians(ang)) * dist;
            float py = (float) Math.sin(Math.toRadians(ang)) * dist;
            String key = Math.random() < 0.5 ? "particle_1" : "particle_2";
            add(key, 0.05f + (float) Math.random() * 0.28f, 0.42f,
                    36f + (float) Math.random() * 24f, 90f,
                    255, 0, (float) (Math.random() * 360.0), (float) (Math.random() * 300.0 - 150.0),
                    px, py, Math.random() < 0.5 ? ORANGE : WHITE_WARM);
        }
    }

    private void add(String key, float delay, float duration, float size0, float size1,
                     int alpha0, int alpha1, float rotate, float rotateSpeed,
                     float px, float py, Color color) {
        SpriteAPI sprite = null;
        try {
            sprite = Global.getSettings().getSprite("NXN_nuke", key);
        } catch (Throwable ignored) {
        }
        if (sprite == null) return;
        fx.add(new Fx(sprite, delay, duration, size0, size1, alpha0, alpha1, rotate, rotateSpeed, px, py, color));
        maxT = Math.max(maxT, delay + duration);
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
        return t > maxT;
    }

    @Override
    public void advance(float amount) {
        if (Global.getCombatEngine() == null || Global.getCombatEngine().isPaused()) return;
        t += amount;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (fx.isEmpty() || t > maxT) return;

        for (Fx f : fx) {
            float lt = t - f.delay;
            if (lt < 0f || lt > f.duration) continue;

            float p = lt / f.duration;
            float size = f.size0 + (f.size1 - f.size0) * p;
            int alpha = (int) (f.alpha0 + (f.alpha1 - f.alpha0) * p);
            if (alpha <= 0 || size <= 0f) continue;

            f.sprite.setAdditiveBlend();
            f.sprite.setAngle(f.rotate + f.rotateSpeed * lt);
            f.sprite.setSize(size, size);
            f.sprite.setColor(new Color(f.color.getRed(), f.color.getGreen(), f.color.getBlue(),
                    Math.min(255, alpha)));
            f.sprite.renderAtCenter(center.x + f.px, center.y + f.py);
        }
    }
}
