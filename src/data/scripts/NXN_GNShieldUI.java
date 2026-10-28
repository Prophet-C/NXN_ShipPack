/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fs.starfarer.api.GameState
 *  com.fs.starfarer.api.Global
 *  com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
 *  com.fs.starfarer.api.combat.CombatEngineAPI
 *  com.fs.starfarer.api.combat.CombatUIAPI
 *  com.fs.starfarer.api.combat.EveryFrameCombatPlugin
 *  com.fs.starfarer.api.combat.ShipAPI
 *  com.fs.starfarer.api.combat.ViewportAPI
 *  com.fs.starfarer.api.input.InputEventAPI
 *  org.lazywizard.lazylib.ui.FontException
 *  org.lazywizard.lazylib.ui.LazyFont
 *  org.lazywizard.lazylib.ui.LazyFont$DrawableString
 *  org.lazywizard.lazylib.ui.LazyFont$TextAnchor
 *  org.lwjgl.opengl.GL11
 *  org.lwjgl.util.vector.Vector2f
 */
package data.scripts;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import data.hullmods.NXN_GNShield_Script;
import java.awt.Color;
import java.util.List;
import org.lazywizard.lazylib.ui.FontException;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

public class NXN_GNShieldUI
extends BaseEveryFrameCombatPlugin {
    private static final String UI_KEY = "NXN_GNShieldUI";
    private static final float INDICATOR_SIZE = 8.0f;
    private static final Color FIELD_ON_COLOR = new Color(100, 200, 255, 255);
    private static final Color FIELD_OFF_COLOR = new Color(255, 50, 50, 255);
    private static LazyFont labelFont = null;
    private static boolean fontInitialized = false;

    public static void register() {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null && !engine.getCustomData().containsKey(UI_KEY)) {
            NXN_GNShieldUI ui = new NXN_GNShieldUI();
            engine.addPlugin((EveryFrameCombatPlugin)ui);
            engine.getCustomData().put(UI_KEY, ui);
        }
    }

    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCurrentState().equals(GameState.TITLE)) {
            return;
        }
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) {
            return;
        }
        if (!engine.isUIShowingHUD() || engine.isUIShowingDialog()) {
            return;
        }
        ViewportAPI viewport = engine.getViewport();
        if (viewport == null) {
            return;
        }
        for (ShipAPI ship : engine.getShips()) {
            if (ship.isFighter() || !ship.isAlive() || ship.isHulk() || ship.isStationModule() || !NXN_GNShield_Script.hasShield(ship)) continue;
            boolean isShieldOn = ship.getShield() != null && ship.getShield().isOn();
            this.renderStatusText(ship, isShieldOn, viewport, engine);
        }
    }

    private int getStatusDisplayMode(ShipAPI ship, CombatEngineAPI engine) {
        float threshold;
        ShipAPI playerShip;
        CombatUIAPI combatUI = engine.getCombatUI();
        if (combatUI != null) {
            if (combatUI.isShowingCommandUI()) {
                return -1;
            }
            if (combatUI.isShowingDeploymentDialog()) {
                return -1;
            }
        }
        if ((playerShip = engine.getPlayerShip()) == null) {
            return -1;
        }
        ShipAPI playerTarget = playerShip.getShipTarget();
        if (playerTarget == ship) {
            return 0;
        }
        if (ship == playerShip) {
            return 1;
        }
        ViewportAPI vp = engine.getViewport();
        Vector2f mouseWorld = new Vector2f(vp.convertScreenXToWorldX((float)Global.getSettings().getMouseX()), vp.convertScreenYToWorldY((float)Global.getSettings().getMouseY()));
        float dist = Vector2f.sub((Vector2f)mouseWorld, (Vector2f)ship.getLocation(), null).length();
        if (dist < (threshold = 50.0f + ship.getCollisionRadius())) {
            return 1;
        }
        if (ship.getOwner() == playerShip.getOwner() && vp.isNearViewport(ship.getLocation(), -(ship.getCollisionRadius() * 1.25f))) {
            return 1;
        }
        return -1;
    }

    private void renderStatusText(ShipAPI ship, boolean isFieldOn, ViewportAPI viewport, CombatEngineAPI engine) {
        float maxRadius;
        float viewMult;
        boolean isSmallMode;
        int displayMode = this.getStatusDisplayMode(ship, engine);
        if (displayMode < 0) {
            return;
        }
        boolean bl = isSmallMode = displayMode == 1;
        if (!viewport.isNearViewport(ship.getLocation(), ship.getCollisionRadius() + 500.0f)) {
            return;
        }
        float shipX = ship.getLocation().x;
        float shipY = ship.getLocation().y;
        float screenX = viewport.convertWorldXtoScreenX(shipX);
        float screenY = viewport.convertWorldYtoScreenY(shipY);
        float shipRadius = ship.getCollisionRadius();
        float screenRadius = shipRadius / (viewMult = viewport.getViewMult());
        if (screenRadius > (maxRadius = 150.0f)) {
            screenRadius = maxRadius;
        }
        if (screenRadius < 50.0f && !isSmallMode) {
            screenRadius = 50.0f;
        }
        if (isSmallMode) {
            screenRadius *= 0.66f;
        }
        float offsetX = screenRadius * 0.6f * 1.6666f;
        float offsetY = screenRadius * 0.6f * 1.6666f;
        float lineHeight = 11.0f;
        float panelX = screenX + offsetX;
        float panelY = screenY + offsetY + 3.0f;
        float baseY = panelY + lineHeight + 7.0f;
        float baseX = panelX + 5.0f;
        int intBaseX = (int)baseX;
        int intBaseY = (int)baseY;
        Color statusColor = isFieldOn ? FIELD_ON_COLOR : FIELD_OFF_COLOR;
        float alpha = ship.getAlphaMult();
        float screenHeight = Global.getSettings().getScreenHeight();
        this.renderIndicator(intBaseX, intBaseY, isFieldOn, statusColor, alpha, screenHeight);
        float textOffsetX = 11.0f;
        float textOffsetY = 2.0f;
        this.renderFieldStatusText((float)intBaseX + textOffsetX, (float)intBaseY + textOffsetY, isFieldOn, statusColor, alpha, screenHeight);

        // 量子槽：画在“力场”这一行上方
        float qValue = data.hullmods.NXN_QuantumGauge.getValue(ship);
        this.renderQuantumGauge((float)intBaseX, (float)intBaseY + 17f, qValue, 100f, alpha, screenHeight);
    }

    private static void initFont() {
        if (fontInitialized) {
            return;
        }
        fontInitialized = true;
        try {
            labelFont = LazyFont.loadFont((String)"graphics/fonts/victor10.fnt");
        }
        catch (FontException e) {
            labelFont = null;
        }
    }

    private void renderIndicator(float x, float y, boolean isOn, Color statusColor, float alpha, float screenHeight) {
        GL11.glPushAttrib((int)1048575);
        GL11.glMatrixMode((int)5889);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho((double)0.0, (double)Global.getSettings().getScreenWidth(), (double)0.0, (double)screenHeight, (double)-1.0, (double)1.0);
        GL11.glMatrixMode((int)5888);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable((int)3553);
        GL11.glEnable((int)3042);
        GL11.glBlendFunc((int)770, (int)771);
        float indicatorX = x - 1.0f;
        float indicatorY = y + 2.0f;
        Color indicatorColor = new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), (int)(255.0f * alpha));
        if (isOn) {
            GL11.glColor4ub((byte)((byte)indicatorColor.getRed()), (byte)((byte)indicatorColor.getGreen()), (byte)((byte)indicatorColor.getBlue()), (byte)((byte)indicatorColor.getAlpha()));
            GL11.glBegin((int)7);
            GL11.glVertex2f((float)indicatorX, (float)indicatorY);
            GL11.glVertex2f((float)indicatorX, (float)(indicatorY + 8.0f));
            GL11.glVertex2f((float)(indicatorX + 8.0f), (float)(indicatorY + 8.0f));
            GL11.glVertex2f((float)(indicatorX + 8.0f), (float)indicatorY);
            GL11.glEnd();
        } else {
            GL11.glColor4ub((byte)((byte)indicatorColor.getRed()), (byte)((byte)indicatorColor.getGreen()), (byte)((byte)indicatorColor.getBlue()), (byte)((byte)indicatorColor.getAlpha()));
            GL11.glLineWidth((float)1.5f);
            GL11.glBegin((int)2);
            GL11.glVertex2f((float)indicatorX, (float)indicatorY);
            GL11.glVertex2f((float)indicatorX, (float)(indicatorY + 8.0f));
            GL11.glVertex2f((float)(indicatorX + 8.0f), (float)(indicatorY + 8.0f));
            GL11.glVertex2f((float)(indicatorX + 8.0f), (float)indicatorY);
            GL11.glEnd();
        }
        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5889);
        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5888);
        GL11.glPopAttrib();
    }

    private void renderFieldStatusText(float x, float y, boolean isOn, Color statusColor, float alpha, float screenHeight) {
        if (!fontInitialized) {
            NXN_GNShieldUI.initFont();
        }
        if (labelFont == null) {
            return;
        }
        Color textColor = new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), (int)(255.0f * alpha));
        GL11.glMatrixMode((int)5889);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho((double)0.0, (double)Global.getSettings().getScreenWidth(), (double)0.0, (double)screenHeight, (double)-1.0, (double)1.0);
        GL11.glMatrixMode((int)5888);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        String text = isOn ? "\u529b\u573a\uff1a\u5f00\u542f" : "\u529b\u573a\uff1a\u5173\u95ed";
        float fontSize = labelFont.getBaseHeight();
        float charX = x - 1.0f;
        float charY = y + fontSize;
        for (int i = 0; i < text.length(); ++i) {
            String ch = text.substring(i, i + 1);
            LazyFont.DrawableString drawable = labelFont.createText(ch, textColor, fontSize);
            drawable.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            drawable.draw(charX, charY);
            float charWidth = labelFont.calcWidth(ch, fontSize);
            charX += charWidth * 0.8f;
        }
        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5889);
        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5888);
    }

    /** 量子槽：一条横向能量槽 + 数值文字 */
    private void renderQuantumGauge(float x, float y, float value, float max, float alpha, float screenHeight) {
        float w = 62f;
        float h = 5f;
        float frac = max > 0f ? Math.max(0f, Math.min(1f, value / max)) : 0f;

        GL11.glPushAttrib((int)1048575);
        GL11.glMatrixMode((int)5889);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho((double)0.0, (double)Global.getSettings().getScreenWidth(), (double)0.0, (double)screenHeight, (double)-1.0, (double)1.0);
        GL11.glMatrixMode((int)5888);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glDisable((int)3553);
        GL11.glEnable((int)3042);
        GL11.glBlendFunc((int)770, (int)771);

        // 槽底
        GL11.glColor4ub((byte)30, (byte)30, (byte)36, (byte)((int)(200.0f * alpha)));
        GL11.glBegin((int)7);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();

        // 填充
        if (frac > 0f) {
            GL11.glColor4ub((byte)120, (byte)200, (byte)255, (byte)((int)(235.0f * alpha)));
            GL11.glBegin((int)7);
            GL11.glVertex2f(x, y);
            GL11.glVertex2f(x, y + h);
            GL11.glVertex2f(x + w * frac, y + h);
            GL11.glVertex2f(x + w * frac, y);
            GL11.glEnd();
        }

        // 外框
        GL11.glColor4ub((byte)150, (byte)205, (byte)255, (byte)((int)(170.0f * alpha)));
        GL11.glLineWidth((float)1.0f);
        GL11.glBegin((int)2);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();

        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5889);
        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5888);
        GL11.glPopAttrib();

        String text = String.format("%d / %d", Math.round(value), Math.round(max));
        this.renderPlainText(x + w + 3f, y, text, new Color(150, 210, 255), alpha, screenHeight);
    }

    /** 通用小字（LazyFont，屏幕空间） */
    private void renderPlainText(float x, float y, String text, Color color, float alpha, float screenHeight) {
        if (!fontInitialized) {
            NXN_GNShieldUI.initFont();
        }
        if (labelFont == null) {
            return;
        }
        GL11.glMatrixMode((int)5889);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho((double)0.0, (double)Global.getSettings().getScreenWidth(), (double)0.0, (double)screenHeight, (double)-1.0, (double)1.0);
        GL11.glMatrixMode((int)5888);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        Color c = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int)(255.0f * alpha));
        float fontSize = labelFont.getBaseHeight();
        float charX = x;
        float charY = y + fontSize;
        for (int i = 0; i < text.length(); ++i) {
            String ch = text.substring(i, i + 1);
            LazyFont.DrawableString drawable = labelFont.createText(ch, c, fontSize);
            drawable.setAnchor(LazyFont.TextAnchor.TOP_LEFT);
            drawable.draw(charX, charY);
            charX += labelFont.calcWidth(ch, fontSize) * 0.8f;
        }
        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5889);
        GL11.glPopMatrix();
        GL11.glMatrixMode((int)5888);
    }
}
