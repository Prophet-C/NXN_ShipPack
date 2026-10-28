package data.scripts.weapons;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * F 系统激活期间的船体拖影（画在船体下方）。
 *
 * - 只在舰船移动时记录，形成"行进轨迹"
 * - 本体 + 所有模块都参与
 * - 普通混合 + 半透明蓝青，渲染后恢复贴图状态
 */
public class NXN_AfterimageRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final float RECORD_INTERVAL = 0.20f;
    private static final int MAX_GHOSTS = 16;
    private static final float GHOST_LIFE = 2.52f;
    private static final int GHOST_ALPHA = 128;   // 50% 不透明
    private static final Color GHOST_TINT = new Color(150, 255, 175);   // 淡绿色
    /** 移动小于该距离（像素）不再记录，避免静止时重叠 */
    private static final float MIN_MOVE = 1.5f;

    private static final class Part {
        final SpriteAPI sprite;
        final Vector2f pos = new Vector2f();
        final float facing;

        Part(SpriteAPI sprite, Vector2f pos, float facing) {
            this.sprite = sprite;
            this.pos.set(pos);
            this.facing = facing;
        }
    }

    private static final class Ghost {
        final List<Part> parts = new ArrayList<Part>();
        float age = 0f;
    }

    private final EnumSet<CombatEngineLayers> layers = EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);
    private final ShipAPI ship;
    private final List<Ghost> ghosts = new ArrayList<Ghost>();
    private final Vector2f lastPos = new Vector2f();
    private float recordTimer = 0f;

    public NXN_AfterimageRenderer(ShipAPI ship) {
        this.ship = ship;
        if (ship != null) lastPos.set(ship.getLocation());
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
        return false;   // 常驻
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.isPaused()) return;
        if (ship == null || !ship.isAlive()) {
            ghosts.clear();
            return;
        }

        for (int i = ghosts.size() - 1; i >= 0; i--) {
            Ghost g = ghosts.get(i);
            g.age += amount;
            if (g.age >= GHOST_LIFE) ghosts.remove(i);
        }

        ShipSystemAPI system = ship.getSystem();
        boolean active = system != null && system.isActive();
        if (!active) return;

        recordTimer += amount;
        if (recordTimer < RECORD_INTERVAL) return;
        recordTimer = 0f;

        // 只在移动时记录
        float moved = Vector2f.sub(ship.getLocation(), lastPos, new Vector2f()).length();
        if (moved < MIN_MOVE) return;
        lastPos.set(ship.getLocation());

        Ghost g = new Ghost();
        collectParts(g, ship);
        for (ShipAPI module : ship.getChildModulesCopy()) {
            if (module != null && module.isAlive()) collectParts(g, module);
        }
        if (ghosts.size() >= MAX_GHOSTS) ghosts.remove(0);
        ghosts.add(g);
    }

    private static void collectParts(Ghost g, ShipAPI s) {
        SpriteAPI spr = s.getSpriteAPI();
        if (spr == null) return;
        g.parts.add(new Part(spr, s.getLocation(), s.getFacing()));
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (ghosts.isEmpty()) return;
        org.lwjgl.opengl.GL11.glPushAttrib(org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS);
        try {

        for (Ghost g : ghosts) {
            float t = 1f - (g.age / GHOST_LIFE);
            if (t <= 0f) continue;
            int alpha = (int) (GHOST_ALPHA * t * t);
            if (alpha <= 3) continue;

            for (Part p : g.parts) {
                try {
                    p.sprite.setNormalBlend();
                    p.sprite.setAngle(p.facing - 90f);
                    p.sprite.setColor(new Color(GHOST_TINT.getRed(), GHOST_TINT.getGreen(),
                            GHOST_TINT.getBlue(), alpha));
                    p.sprite.renderAtCenter(p.pos.x, p.pos.y);
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            restore(ship);
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module != null) restore(module);
            }
        } catch (Throwable ignored) {
        }
        } finally {
            org.lwjgl.opengl.GL11.glPopAttrib();
        }
    }

    private static void restore(ShipAPI s) {
        if (s == null) return;
        SpriteAPI spr = s.getSpriteAPI();
        if (spr == null) return;
        spr.setNormalBlend();
        spr.setColor(Color.WHITE);
        spr.setAngle(s.getFacing() - 90f);
    }
}
