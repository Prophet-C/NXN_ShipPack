package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 导弹巢手绘渲染器：把 21 帧画在 **CAPITAL 层**（低于 L2/R2 模块的 DESTROYER 层），
 * 因此会被翼板装甲挡住下半部分 —— 做出"藏在装甲里"的效果。
 */
public class NXN_NestRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final EnumSet<CombatEngineLayers> LAYERS =
            EnumSet.of(CombatEngineLayers.CAPITAL_SHIPS_LAYER);

    private static final int FRAMES = 21;
    private static final List<NXN_MissileNest> NESTS = new ArrayList<NXN_MissileNest>();
    private static int createdCombatHash = 0;

    private static SpriteAPI[] right = null;
    private static SpriteAPI[] left = null;

    public static void register(NXN_MissileNest nest) {
        if (!NESTS.contains(nest)) NESTS.add(nest);
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
        CombatEngineAPI engine = Global.getCombatEngine();
        return engine == null || engine != lastEngine;
    }

    private CombatEngineAPI lastEngine = null;

    @Override
    public void advance(float amount) {
        lastEngine = Global.getCombatEngine();
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (NESTS.isEmpty()) return;
        loadSprites();

        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        try {
            for (int i = NESTS.size() - 1; i >= 0; i--) {
                NXN_MissileNest nest = NESTS.get(i);
                WeaponAPI w = nest.getWeapon();
                ShipAPI s = nest.getModule();
                if (w == null || s == null || !s.isAlive()) {
                    NESTS.remove(i);
                    continue;
                }
                int frame = nest.getCurrentFrame();
                if (frame < 0) frame = 0;
                if (frame >= FRAMES) frame = FRAMES - 1;

                boolean isLeft = nest.isLeftSide();
                SpriteAPI[] set = isLeft ? left : right;
                if (set == null) continue;
                SpriteAPI spr = set[frame];
                if (spr == null) continue;

                try {
                    spr.setNormalBlend();
                    spr.setSize(57f, 170f);
                    spr.setAngle(w.getCurrAngle() - 90f);
                    spr.setColor(Color.WHITE);
                    spr.renderAtCenter(w.getLocation().x, w.getLocation().y);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }

    private static void loadSprites() {
        if (right != null && left != null) return;
        right = new SpriteAPI[FRAMES];
        left = new SpriteAPI[FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            String idx = (i < 10 ? "0" : "") + i;
            right[i] = load("graphics/weapons/nxn/nest_strip" + idx + ".png");
            left[i] = load("graphics/weapons/nxn/nest_strip_L" + idx + ".png");
        }
    }

    private static SpriteAPI load(String path) {
        try {
            SpriteAPI s = Global.getSettings().getSprite(path);
            if (s != null) {
                s.getWidth();
                return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
