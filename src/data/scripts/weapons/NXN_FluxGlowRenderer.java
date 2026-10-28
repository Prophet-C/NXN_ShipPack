package data.scripts.weapons;

import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;

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

/**
 * 船体管线发光 + 法线光照。
 *
 * 遮罩：赋能越高越亮，**统一红色**，带轻微抖动。低于 START_T 完全不可见。
 *      层级 CRUISERS_LAYER（船体之上、装甲模块之下）。
 * 光源：GraphicsLib 点光源，随赋能增强，照亮附近带法线贴图的炮塔。
 */
public class NXN_FluxGlowRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final EnumSet<CombatEngineLayers> LAYERS =
            EnumSet.of(CombatEngineLayers.CRUISERS_LAYER);

    // ===== 遮罩强度 =====
    /** 开始发光的赋能阈值 */
    private static final float START_T = 0.12f;
    /** 最大透明度 */
    private static final float MAX_ALPHA = 245f;
    /** 低赋能：偏红的暖色（不是纯红，避免发死） */
    private static final Color GLOW_LOW = new Color(255, 62, 22);
    /** 高赋能：掺橙，更接近烧红的金属 */
    private static final Color GLOW_HIGH = new Color(255, 125, 45);

    // ===== 点光源 =====
    private static final float LIGHT_LOCAL_X = -71f;
    private static final float LIGHT_LOCAL_Y = 0f;
    private static final float LIGHT_SIZE = 200f;
    private static final float LIGHT_T = 0.15f;
    private static final float LIGHT_MAX = 0.5f;

    private final ShipAPI ship;
    private SpriteAPI mask;
    private boolean maskTried = false;
    private boolean maskBroken = false;

    private StandardLight light = null;

    private float time = 0f;

    public NXN_FluxGlowRenderer(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return LAYERS;
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
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        time += amount;
        updateLight();
    }

    // ---------------- 点光源 ----------------

    private void updateLight() {
        if (ship == null || !ship.isAlive()) return;

        float flux = ship.getFluxTracker().getFluxLevel();
        float intensity = 0f;
        if (flux > LIGHT_T) {
            intensity = Math.min(1f, (flux - LIGHT_T) / (1f - LIGHT_T)) * LIGHT_MAX;
        }

        if (light == null) {
            try {
                light = new StandardLight(new Vector2f(ship.getLocation()), new Vector2f(),
                        new Vector2f(), ship);
                light.makePermanent();
                light.setSize(LIGHT_SIZE);
                light.setIntensity(0f);
                LightShader.addLight(light);

            } catch (Throwable t) {

                light = null;
                return;
            }
        }

        if (light == null) return;

        try {
            double rad = Math.toRadians(ship.getFacing());
            float fx = (float) Math.cos(rad);
            float fy = (float) Math.sin(rad);
            Vector2f pos = new Vector2f(
                    ship.getLocation().x + fx * LIGHT_LOCAL_X - fy * LIGHT_LOCAL_Y,
                    ship.getLocation().y + fy * LIGHT_LOCAL_X + fx * LIGHT_LOCAL_Y);

            light.setLocation(pos);
            light.setSize(LIGHT_SIZE);

            float t = Math.min(1f, flux);
            light.setColor(1.0f, 0.10f, 0.05f);
            light.setIntensity(intensity);
        } catch (Throwable ignored) {
        }
    }

    // ---------------- 遮罩渲染 ----------------

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (ship == null || !ship.isAlive()) return;
        if (maskBroken) return;

        if (!maskTried) {
            maskTried = true;
            try {
                mask = Global.getSettings().getSprite("NXN_ships", "NXN_rumbling_glow");
                if (mask != null) mask.getWidth();
            } catch (Throwable t) {
                mask = null;
            }
            if (mask == null) {
                maskBroken = true;

                return;
            }
        }
        if (mask == null) return;

        SpriteAPI hull = ship.getSpriteAPI();
        if (hull == null) return;

        try {
            renderMask(hull);
        } catch (Throwable t) {
            maskBroken = true;

        }
    }

    /** 按赋能在图里插值颜色：低赋能偏红 -> 高赋能掺橙 */
    private static Color blendColor(float t, int alpha) {
        return new Color(
                (int) (GLOW_LOW.getRed() + (GLOW_HIGH.getRed() - GLOW_LOW.getRed()) * t),
                (int) (GLOW_LOW.getGreen() + (GLOW_HIGH.getGreen() - GLOW_LOW.getGreen()) * t),
                (int) (GLOW_LOW.getBlue() + (GLOW_HIGH.getBlue() - GLOW_LOW.getBlue()) * t),
                alpha);
    }

    private void renderMask(SpriteAPI hull) {
        float flux = ship.getFluxTracker().getFluxLevel();
        if (flux <= START_T) return;

        float t = Math.min(1f, (flux - START_T) / (1f - START_T));

        float boost = 0.62f + 0.80f * t;

        // 纯发光，不闪烁
        int alpha = (int) (MAX_ALPHA * t * boost);
        if (alpha < 6) return;
        if (alpha > 255) alpha = 255;

        // GL 状态保护：加法混合后必须恢复，否则整屏会被洗白
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        org.lwjgl.opengl.GL11.glPushMatrix();
        try {
            mask.setAdditiveBlend();
            mask.setAngle(ship.getFacing() - 90f);
            mask.setSize(hull.getWidth(), hull.getHeight());
            mask.setColor(blendColor(t, alpha));
            mask.renderAtCenter(ship.getLocation().x, ship.getLocation().y);
        } finally {
            try {
                mask.setNormalBlend();
                mask.setColor(Color.WHITE);
                mask.setAngle(ship.getFacing() - 90f);
                mask.setSize(hull.getWidth(), hull.getHeight());
            } catch (Throwable ignored) {
            }
            org.lwjgl.opengl.GL11.glPopMatrix();
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }
}
