/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fs.starfarer.api.Global
 *  com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin
 *  com.fs.starfarer.api.combat.CombatEngineAPI
 *  com.fs.starfarer.api.combat.CombatEngineLayers
 *  com.fs.starfarer.api.combat.CombatEntityAPI
 *  com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
 *  com.fs.starfarer.api.combat.ShipAPI
 *  com.fs.starfarer.api.combat.ViewportAPI
 *  com.fs.starfarer.api.combat.WeaponAPI
 *  com.fs.starfarer.api.graphics.SpriteAPI
 *  lunalib.lunaSettings.LunaSettings
 *  org.dark.shaders.util.ShaderLib
 *  org.lwjgl.BufferUtils
 *  org.lwjgl.opengl.GL11
 *  org.lwjgl.opengl.GL13
 *  org.lwjgl.opengl.GL15
 *  org.lwjgl.opengl.GL20
 *  org.lwjgl.util.vector.Vector2f
 */
package data.scripts.weapons.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import data.hullmods.NXN_GNFieldDefense_Script;
import data.hullmods.NXN_GNShield_Script;
import data.scripts.weapons.render.NXN_ShaderManager;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

public class NXN_GNShieldRendering_Shader
extends BaseCombatLayeredRenderingPlugin {
    public static final String RENDER_KEY = "NXN_GNShieldRendering_Shader";
    private final CombatEngineAPI engine = Global.getCombatEngine();
    private int shader = 0;
    private int[] uniform;
    private boolean shaderInitialized = false;
    private NXN_ShaderManager shaderManager;
    private static int currentShader = -1;
    private static boolean shaderStateInitialized = false;
    private static int shaderBindCount = 0;
    private static int uniformUpdateCount = 0;
    private float cachedType = -999.0f;
    private float cachedSpreadLevel = -999.0f;
    private float cachedMaxSize = -999.0f;
    private float cachedCombinedAlpha = -999.0f;
    private float cachedFluxLevel = -999.0f;
    private static final float UNIFORM_EPSILON = 1.0E-4f;
    private int shieldQuadVBO = -1;
    private boolean vboInitialized = false;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final String SHADER_VERT_PATH = "data/shaders/NXN_GNShield.vert";
    private static final String SHADER_FRAG_PATH = "data/shaders/NXN_GNShield.frag";
    private static final String SHADER_KEY = "NXN_GNShield";
    private final Map<ShipAPI, Float> shieldSpreadElapsed = new HashMap<ShipAPI, Float>();
    private static final float SPREAD_CYCLE_TIME = 10.0f;
    private int currentFighterCount = 0;

    public NXN_GNShieldRendering_Shader() {
        this.initializeShader();
    }

    private void initializeShader() {
        try {
            this.shaderManager = NXN_ShaderManager.getInstance();
            if (!this.shaderManager.isOpenGL20Supported()) {
                return;
            }
            this.shader = this.shaderManager.createShader(SHADER_VERT_PATH, SHADER_FRAG_PATH, SHADER_KEY);
            if (this.shader != 0 && this.shaderManager.isShaderValid(SHADER_KEY)) {
                GL20.glUseProgram((int)this.shader);
                this.uniform = new int[]{GL20.glGetUniformLocation((int)this.shader, (CharSequence)"shieldTex"), GL20.glGetUniformLocation((int)this.shader, (CharSequence)"fxTex"), GL20.glGetUniformLocation((int)this.shader, (CharSequence)"type"), GL20.glGetUniformLocation((int)this.shader, (CharSequence)"state"), GL20.glGetUniformLocation((int)this.shader, (CharSequence)"gnColor"), GL20.glGetUniformLocation((int)this.shader, (CharSequence)"fluxLevel")};
                boolean uniformsValid = true;
                for (int i = 0; i < this.uniform.length; ++i) {
                    if (this.uniform[i] != -1) continue;
                    uniformsValid = false;
                }
                if (uniformsValid) {
                    GL20.glUseProgram((int)0);
                    this.shaderInitialized = true;
                    this.warmupShader();
                } else {
                    this.shaderInitialized = false;
                }
            } else {
                this.shaderInitialized = false;
            }
            this.shaderManager.printShaderStats();
        }
        catch (Exception e) {
            this.shaderInitialized = false;
        }
    }

    public static NXN_GNShieldRendering_Shader getInstance() {
        if (Global.getCombatEngine() != null && Global.getCombatEngine().getCustomData().containsKey(RENDER_KEY)) {
            return (NXN_GNShieldRendering_Shader)(Global.getCombatEngine().getCustomData().get(RENDER_KEY));
        }
        NXN_GNShieldRendering_Shader renderer = new NXN_GNShieldRendering_Shader();
        if (Global.getCombatEngine() != null) {
            Global.getCombatEngine().addLayeredRenderingPlugin((CombatLayeredRenderingPlugin)renderer);
            Global.getCombatEngine().getCustomData().put(RENDER_KEY, renderer);
        }
        return renderer;
    }

    public void init(CombatEntityAPI entity) {
    }

    public void cleanup() {
        if (this.vboInitialized && this.shieldQuadVBO != -1) {
            GL15.glDeleteBuffers((int)this.shieldQuadVBO);
            this.shieldQuadVBO = -1;
            this.vboInitialized = false;
        }
    }

    public boolean isExpired() {
        return false;
    }

    public void advance(float amount) {
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        this.updateFighterCount();
        this.updateShieldSpread(amount);
    }

    private void updateFighterCount() {
        this.currentFighterCount = 0;
        for (ShipAPI ship : this.engine.getShips()) {
            if (!ship.isFighter()) continue;
            ++this.currentFighterCount;
        }
    }

    private void updateShieldSpread(float amount) {
        NXN_GNFieldDefense_Script script;
        boolean shieldActive;
        boolean hasGNShield;
        for (ShipAPI ship : this.engine.getShips()) {
            hasGNShield = NXN_GNShield_Script.hasShield(ship) || NXN_GNFieldDefense_Script.hasShield(ship);
            if (!hasGNShield) continue;
            shieldActive = false;
            if (ship.getShield() != null && ship.getShield().isOn()) {
                shieldActive = true;
            } else if (NXN_GNFieldDefense_Script.hasShield(ship) && (script = NXN_GNFieldDefense_Script.getInstance(ship)) != null && !script.getShield().isShieldBroken()) {
                shieldActive = true;
            }
            if (!shieldActive) continue;
            Float elapsedObj = this.shieldSpreadElapsed.get(ship);
            float elapsed = elapsedObj != null ? elapsedObj.floatValue() : 0.0f;
            if ((elapsed += amount) >= 10.0f) {
                elapsed -= 10.0f;
            }
            this.shieldSpreadElapsed.put(ship, Float.valueOf(elapsed));
        }
        for (ShipAPI ship : new ArrayList<ShipAPI>(this.shieldSpreadElapsed.keySet())) {
            boolean bl = hasGNShield = NXN_GNShield_Script.hasShield(ship) || NXN_GNFieldDefense_Script.hasShield(ship);
            if (!hasGNShield) {
                this.shieldSpreadElapsed.remove(ship);
                continue;
            }
            shieldActive = false;
            if (ship.getShield() != null && ship.getShield().isOn()) {
                shieldActive = true;
            } else if (NXN_GNFieldDefense_Script.hasShield(ship) && (script = NXN_GNFieldDefense_Script.getInstance(ship)) != null && !script.getShield().isShieldBroken()) {
                shieldActive = true;
            }
            if (shieldActive) continue;
            this.shieldSpreadElapsed.remove(ship);
        }
    }

    private float getShieldSpreadLevel(ShipAPI ship) {
        Float elapsed = this.shieldSpreadElapsed.get(ship);
        if (elapsed == null) {
            return 0.0f;
        }
        return elapsed.floatValue() / 10.0f;
    }

    private void bindShaderOptimized() {
        if (!this.shaderInitialized) {
            return;
        }
        if (currentShader != this.shader) {
            GL20.glUseProgram((int)this.shader);
            currentShader = this.shader;
            ++shaderBindCount;
            if (!shaderStateInitialized) {
                GL20.glUniform1i((int)this.uniform[0], (int)0);
                GL20.glUniform1i((int)this.uniform[1], (int)1);
                shaderStateInitialized = true;
            }
        }
    }

    private static void unbindShader() {
        if (currentShader != 0) {
            GL20.glUseProgram((int)0);
            currentShader = 0;
        }
    }

    private void warmupShader() {
        if (!this.shaderInitialized) {
            return;
        }
        try {
            this.bindShaderOptimized();
            if (shaderStateInitialized) {
                GL20.glUniform1f((int)this.uniform[2], (float)0.0f);
                GL20.glUniform4f((int)this.uniform[3], (float)0.0f, (float)256.0f, (float)256.0f, (float)1.0f);
                GL20.glUniform3f((int)this.uniform[4], (float)0.2f, (float)0.4f, (float)1.0f);
                GL20.glUniform1f((int)this.uniform[5], (float)0.0f);
            }
            NXN_GNShieldRendering_Shader.unbindShader();
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    private float calculateTotalShipSize(ShipAPI ship) {
        float maxSize = Math.max(ship.getSpriteAPI().getHeight(), ship.getSpriteAPI().getWidth());
        if (ship.getChildModulesCopy() != null) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.getSpriteAPI() == null) continue;
                float moduleSize = Math.max(module.getSpriteAPI().getHeight(), module.getSpriteAPI().getWidth());
                float distance = (float)Math.sqrt(Math.pow(module.getLocation().x - ship.getLocation().x, 2.0) + Math.pow(module.getLocation().y - ship.getLocation().y, 2.0));
                maxSize = Math.max(maxSize, (distance + moduleSize) * 1.2f);
            }
        }
        return maxSize;
    }

    private boolean shouldRenderWeapon(WeaponAPI weapon) {
        if (weapon.getSprite() == null) {
            return false;
        }
        if (weapon.isDisabled()) {
            return false;
        }
        return !weapon.getSlot().isHidden() && (weapon.getSlot().isBuiltIn() || weapon.getSlot().isDecorative());
    }

    private Map<Integer, ArrayList<WeaponAPI>> groupWeaponsByTexture(ShipAPI ship) {
        HashMap<Integer, ArrayList<WeaponAPI>> weaponsByTexture = new HashMap<Integer, ArrayList<WeaponAPI>>();
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getSprite() == null || !this.shouldRenderWeapon(weapon)) continue;
            int textureId = weapon.getSprite().getTextureId();
            ArrayList<WeaponAPI> weaponList = (ArrayList<WeaponAPI>)weaponsByTexture.get(textureId);
            if (weaponList == null) {
                weaponList = new ArrayList<WeaponAPI>();
                weaponsByTexture.put(textureId, weaponList);
            }
            weaponList.add(weapon);
        }
        return weaponsByTexture;
    }

    private Map<Integer, ArrayList<ShipAPI>> groupModulesByTexture(ShipAPI ship) {
        HashMap<Integer, ArrayList<ShipAPI>> modulesByTexture = new HashMap<Integer, ArrayList<ShipAPI>>();
        if (ship.getChildModulesCopy() != null) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.getSpriteAPI() == null) continue;
                int textureId = module.getSpriteAPI().getTextureId();
                ArrayList<ShipAPI> moduleList = (ArrayList<ShipAPI>)modulesByTexture.get(textureId);
                if (moduleList == null) {
                    moduleList = new ArrayList<ShipAPI>();
                    modulesByTexture.put(textureId, moduleList);
                }
                moduleList.add(module);
            }
        }
        return modulesByTexture;
    }

    private void setUniform1fCached(int location, float value, float cachedValue) {
        if (Math.abs(value - cachedValue) > 1.0E-4f) {
            GL20.glUniform1f((int)location, (float)value);
            ++uniformUpdateCount;
        }
    }

    private void setUniform4fCached(int location, float x, float y, float z, float w, float cachedX, float cachedY, float cachedZ, float cachedW) {
        if (Math.abs(x - cachedX) > 1.0E-4f || Math.abs(y - cachedY) > 1.0E-4f || Math.abs(z - cachedZ) > 1.0E-4f || Math.abs(w - cachedW) > 1.0E-4f) {
            GL20.glUniform4f((int)location, (float)x, (float)y, (float)z, (float)w);
            ++uniformUpdateCount;
        }
    }

    private void initShieldQuadVBO() {
        if (this.vboInitialized) {
            return;
        }
        try {
            float[] quadVertices = new float[]{-0.5f, -0.5f, 0.0f, 0.0f, -0.5f, 0.5f, 0.0f, 1.0f, 0.5f, 0.5f, 1.0f, 1.0f, 0.5f, -0.5f, 1.0f, 0.0f};
            FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer((int)quadVertices.length);
            vertexBuffer.put(quadVertices);
            vertexBuffer.flip();
            this.shieldQuadVBO = GL15.glGenBuffers();
            GL15.glBindBuffer((int)34962, (int)this.shieldQuadVBO);
            GL15.glBufferData((int)34962, (FloatBuffer)vertexBuffer, (int)35044);
            GL15.glBindBuffer((int)34962, (int)0);
            this.vboInitialized = true;
        }
        catch (Exception e) {
            this.vboInitialized = false;
        }
    }

    private void renderShieldQuadVBO(float scaleX, float scaleY) {
        if (!this.vboInitialized) {
            this.initShieldQuadVBO();
            if (!this.vboInitialized) {
                return;
            }
        }
        GL15.glBindBuffer((int)34962, (int)this.shieldQuadVBO);
        GL11.glEnableClientState((int)32884);
        GL11.glEnableClientState((int)32888);
        GL11.glVertexPointer((int)2, (int)5126, (int)16, (long)0L);
        GL11.glTexCoordPointer((int)2, (int)5126, (int)16, (long)8L);
        GL11.glScalef((float)scaleX, (float)scaleY, (float)1.0f);
        GL11.glDrawArrays((int)7, (int)0, (int)4);
        GL11.glDisableClientState((int)32888);
        GL11.glDisableClientState((int)32884);
        GL15.glBindBuffer((int)34962, (int)0);
    }

    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    public float getRenderRadius() {
        return 1000000.0f;
    }

    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (layer == CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER) {
            this.bindShaderOptimized();
            for (ShipAPI ship : this.engine.getShips()) {
                boolean hasGNShield;
                if (this.shouldSkipFighterRendering(ship) || !(hasGNShield = NXN_GNShield_Script.hasShield(ship) || NXN_GNFieldDefense_Script.hasShield(ship)) || ship.isStationModule()) continue;
                if (ship.getShield() != null) {
                    Vector2f shieldCenter = ship.getShield().getLocation();
                    float offsetDistance = Vector2f.sub((Vector2f)shieldCenter, (Vector2f)ship.getLocation(), null).length();
                    float extendedRadius = ship.getCollisionRadius() + offsetDistance;
                    if (!ShaderLib.isOnScreen((Vector2f)ship.getLocation(), (float)(extendedRadius * 2.0f))) continue;
                    this.processShield(ship);
                    continue;
                }
                if (!ShaderLib.isOnScreen((Vector2f)ship.getLocation(), (float)(ship.getCollisionRadius() * 2.0f))) continue;
                this.processShield(ship);
            }
            NXN_GNShieldRendering_Shader.unbindShader();
        }
    }

    protected void processShield(ShipAPI ship) {
        float f;
        float f2;
        Vector2f shieldCenter;
        NXN_GNFieldDefense_Script script;
        boolean hasVanillaShield = ship.getShield() != null;
        boolean hasGNFieldDefense = NXN_GNFieldDefense_Script.hasShield(ship);
        if (hasVanillaShield && !ship.getShield().isOn()) {
            return;
        }
        if (hasGNFieldDefense && (script = NXN_GNFieldDefense_Script.getInstance(ship)).getShield().isShieldBroken()) {
            return;
        }
        SpriteAPI shield = Global.getSettings().getSprite("fx", SHADER_KEY);
        SpriteAPI hitFx = Global.getSettings().getSprite("fx", "NXN_GNShield_fog");
        float max = Math.max(ship.getSpriteAPI().getHeight(), ship.getSpriteAPI().getWidth());
        float offsetDistance = 0.0f;
        if (hasVanillaShield) {
            shieldCenter = ship.getShield().getLocation();
            offsetDistance = Vector2f.sub((Vector2f)shieldCenter, (Vector2f)ship.getLocation(), null).length();
        } else {
            shieldCenter = ship.getLocation();
        }
        max = Math.max(512.0f, max + offsetDistance * 2.0f);
        Vector2f size = new Vector2f(max *= 1.1f, max);
        Vector2f uv = new Vector2f(1.0f, 1.0f);
        float combinedShieldAlphaBase = ship.getAlphaMult() * ship.getExtraAlphaMult() * ship.getExtraAlphaMult2();
        float combinedShieldAlpha = combinedShieldAlphaBase * 1.0f;
        float spreadLevel = this.getShieldSpreadLevel(ship);
        float fluxLevel = ship.getFluxLevel();
        GL11.glPushAttrib((int)1048575);
        GL11.glEnable((int)3553);
        GL11.glEnable((int)2960);
        GL11.glDisable((int)2929);
        GL11.glColorMask((boolean)false, (boolean)false, (boolean)false, (boolean)false);
        GL11.glStencilFunc((int)519, (int)1, (int)255);
        GL11.glStencilOp((int)7680, (int)7680, (int)7681);
        GL11.glClear((int)1024);
        GL11.glEnable((int)3008);
        GL11.glAlphaFunc((int)516, (float)0.2f);
        if (this.shaderInitialized) {
            this.setUniform1fCached(this.uniform[2], 3.0f, this.cachedType);
            if (Math.abs(3.0f - this.cachedType) > 1.0E-4f) {
                this.cachedType = 3.0f;
            }
        }
        SpriteAPI ss = ship.getSpriteAPI();
        GL13.glActiveTexture((int)33984);
        GL11.glBindTexture((int)3553, (int)ss.getTextureId());
        ss.renderAtCenter(ship.getLocation().x, ship.getLocation().y);
        Map<Integer, ArrayList<ShipAPI>> modulesByTexture = this.groupModulesByTexture(ship);
        for (Map.Entry<Integer, ArrayList<ShipAPI>> entry : modulesByTexture.entrySet()) {
            GL11.glBindTexture((int)3553, (int)entry.getKey());
            for (ShipAPI module : entry.getValue()) {
                SpriteAPI moduleSpr = module.getSpriteAPI();
                moduleSpr.renderAtCenter(module.getLocation().x, module.getLocation().y);
            }
        }
        Map<Integer, ArrayList<WeaponAPI>> weaponsByTexture = this.groupWeaponsByTexture(ship);
        for (Map.Entry<Integer, ArrayList<WeaponAPI>> entry : weaponsByTexture.entrySet()) {
            GL11.glBindTexture((int)3553, (int)entry.getKey());
            for (WeaponAPI weapon : entry.getValue()) {
                SpriteAPI weaponSpr = weapon.getSprite();
                weaponSpr.renderAtCenter(weapon.getLocation().x, weapon.getLocation().y);
            }
        }
        GL11.glStencilFunc((int)514, (int)1, (int)255);
        GL11.glStencilOp((int)7680, (int)7680, (int)7680);
        GL11.glColorMask((boolean)true, (boolean)true, (boolean)true, (boolean)true);
        GL11.glAlphaFunc((int)516, (float)0.0f);
        GL11.glDisable((int)3008);
        GL11.glPushMatrix();
        GL13.glActiveTexture((int)33984);
        GL11.glBindTexture((int)3553, (int)shield.getTextureId());
        GL13.glActiveTexture((int)33985);
        GL11.glBindTexture((int)3553, (int)hitFx.getTextureId());
        GL11.glTranslatef((float)shieldCenter.x, (float)shieldCenter.y, (float)0.0f);
        GL11.glRotatef((float)ship.getFacing(), (float)0.0f, (float)0.0f, (float)1.0f);
        GL11.glEnable((int)3042);
        GL11.glBlendFunc((int)770, (int)771);
        if (this.shaderInitialized) {
            this.setUniform1fCached(this.uniform[2], 0.0f, this.cachedType);
            if (Math.abs(0.0f - this.cachedType) > 1.0E-4f) {
                this.cachedType = 0.0f;
            }
            f2 = Math.max(ship.getSpriteAPI().getHeight(), ship.getSpriteAPI().getWidth());
            f = Math.max(128.0f, f2);
            this.setUniform4fCached(this.uniform[3], spreadLevel, f2, f, combinedShieldAlpha, this.cachedSpreadLevel, this.cachedSpreadLevel, this.cachedMaxSize, this.cachedCombinedAlpha);
            if (Math.abs(spreadLevel - this.cachedSpreadLevel) > 1.0E-4f || Math.abs(f2 - this.cachedMaxSize) > 1.0E-4f || Math.abs(combinedShieldAlpha - this.cachedCombinedAlpha) > 1.0E-4f) {
                this.cachedSpreadLevel = spreadLevel;
                this.cachedMaxSize = f2;
                this.cachedCombinedAlpha = combinedShieldAlpha;
            }
            GL20.glUniform3f((int)this.uniform[4], (float)0.2f, (float)0.4f, (float)1.0f);
            this.setUniform1fCached(this.uniform[5], fluxLevel, this.cachedFluxLevel);
            if (Math.abs(fluxLevel - this.cachedFluxLevel) > 1.0E-4f) {
                this.cachedFluxLevel = fluxLevel;
            }
        } else {
            f2 = 0.2f + fluxLevel * 0.8f;
            f = 0.4f - fluxLevel * 0.1f;
            float b = 1.0f - fluxLevel * 0.8f;
            GL11.glColor4f((float)f2, (float)f, (float)b, (float)combinedShieldAlpha);
        }
        if (!this.vboInitialized) {
            this.initShieldQuadVBO();
        }
        if (this.vboInitialized) {
            this.renderShieldQuadVBO(size.x, size.y);
        } else {
            f2 = size.x * 0.5f;
            f = size.y * 0.5f;
            GL11.glBegin((int)7);
            GL11.glTexCoord2f((float)0.0f, (float)0.0f);
            GL11.glVertex2f((float)(-f2), (float)(-f));
            GL11.glTexCoord2f((float)0.0f, (float)uv.y);
            GL11.glVertex2f((float)(-f2), (float)f);
            GL11.glTexCoord2f((float)uv.x, (float)uv.y);
            GL11.glVertex2f((float)f2, (float)f);
            GL11.glTexCoord2f((float)uv.x, (float)0.0f);
            GL11.glVertex2f((float)f2, (float)(-f));
            GL11.glEnd();
        }
        GL11.glPopMatrix();
        this.renderHitEffects(ship, shield, hitFx, combinedShieldAlphaBase);
        GL11.glDisable((int)2960);
        GL11.glStencilFunc((int)519, (int)0, (int)255);
        GL11.glEnable((int)2929);
        GL11.glPopAttrib();
    }

    private void renderHitEffects(ShipAPI ship, SpriteAPI shield, SpriteAPI hitFx, float combinedShieldAlphaBase) {
        List<NXN_GNShield_Script.NXN_GNShieldHitData> hitDatas = null;
        if (NXN_GNShield_Script.hasShield(ship)) {
            NXN_GNShield_Script s = NXN_GNShield_Script.getInstance(ship);
            if (s != null) {
                hitDatas = s.getHitDatas();
            }
        }
        if (hitDatas == null || hitDatas.isEmpty()) {
            return;
        }
        GL11.glPushMatrix();
        GL13.glActiveTexture((int)33984);
        GL11.glBindTexture((int)3553, (int)shield.getTextureId());
        GL13.glActiveTexture((int)33985);
        GL11.glBindTexture((int)3553, (int)hitFx.getTextureId());
        GL11.glTranslatef((float)ship.getLocation().x, (float)ship.getLocation().y, (float)0.0f);
        GL11.glRotatef((float)ship.getFacing(), (float)0.0f, (float)0.0f, (float)1.0f);
        GL11.glEnable((int)3042);
        GL11.glBlendFunc((int)770, (int)771);
        for (NXN_GNShield_Script.NXN_GNShieldHitData hitObj : hitDatas) {
            Vector2f rel = hitObj.getRel();
            float hitSize = hitObj.getSize();
            float hitLevel = hitObj.getLevel();
            if (this.shaderInitialized) {
                GL20.glUniform1f((int)this.uniform[2], (float)(1.0f + combinedShieldAlphaBase));
                GL20.glUniform4f((int)this.uniform[3], (float)hitLevel, (float)hitSize, (float)(rel.x / 256.0f), (float)(rel.y / 256.0f));
                GL20.glUniform3f((int)this.uniform[4], (float)0.2f, (float)0.4f, (float)1.0f);
                GL20.glUniform1f((int)this.uniform[5], (float)ship.getFluxLevel());
            }
            Vector2f size = new Vector2f(hitSize, hitSize);
            Vector2f relPos = rel;
            GL11.glBegin((int)7);
            GL11.glTexCoord2f((float)0.0f, (float)0.0f);
            GL11.glVertex2f((float)(-size.x + relPos.x), (float)(-size.y + relPos.y));
            GL11.glTexCoord2f((float)0.0f, (float)1.0f);
            GL11.glVertex2f((float)(-size.x + relPos.x), (float)(size.y + relPos.y));
            GL11.glTexCoord2f((float)1.0f, (float)1.0f);
            GL11.glVertex2f((float)(size.x + relPos.x), (float)(size.y + relPos.y));
            GL11.glTexCoord2f((float)1.0f, (float)0.0f);
            GL11.glVertex2f((float)(size.x + relPos.x), (float)(-size.y + relPos.y));
            GL11.glEnd();
        }
        GL11.glPopMatrix();
    }

    public boolean isShaderInitialized() {
        return this.shaderInitialized;
    }

    private boolean shouldSkipFighterRendering(ShipAPI ship) {
        if (!ship.isFighter()) {
            return false;
        }
        boolean skipFighter = this.getLunaSettingBoolean("NXN_gnShieldSkipFighter", true);
        boolean enableLimit = this.getLunaSettingBoolean("NXN_gnShieldEnableLimit", false);
        int fighterLimit = this.getLunaSettingInt("NXN_gnShieldFighterLimit", 150);
        if (skipFighter) {
            return true;
        }
        return enableLimit && this.currentFighterCount > fighterLimit;
    }

    private boolean getLunaSettingBoolean(String key, boolean defaultValue) {
        return defaultValue;
    }

    private int getLunaSettingInt(String key, int defaultValue) {
        return defaultValue;
    }

    public static String getPerformanceStats() {
        return String.format("Shader Performance - Binds: %d, Uniform Updates: %d", shaderBindCount, uniformUpdateCount);
    }

    public static void resetPerformanceStats() {
        shaderBindCount = 0;
        uniformUpdateCount = 0;
    }
}

