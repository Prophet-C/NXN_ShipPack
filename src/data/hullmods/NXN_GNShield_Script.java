/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fs.starfarer.api.Global
 *  com.fs.starfarer.api.combat.ArmorGridAPI
 *  com.fs.starfarer.api.combat.BeamAPI
 *  com.fs.starfarer.api.combat.CombatEntityAPI
 *  com.fs.starfarer.api.combat.DamageAPI
 *  com.fs.starfarer.api.combat.DamageType
 *  com.fs.starfarer.api.combat.DamagingProjectileAPI
 *  com.fs.starfarer.api.combat.EmpArcEntityAPI
 *  com.fs.starfarer.api.combat.ShipAPI
 *  com.fs.starfarer.api.combat.listeners.AdvanceableListener
 *  com.fs.starfarer.api.combat.listeners.DamageTakenModifier
 *  com.fs.starfarer.api.util.Misc
 *  com.fs.starfarer.api.util.TimeoutTracker
 *  org.dark.shaders.distortion.DistortionAPI
 *  org.dark.shaders.distortion.DistortionShader
 *  org.dark.shaders.distortion.RippleDistortion
 *  org.lazywizard.lazylib.MathUtils
 *  org.lazywizard.lazylib.VectorUtils
 *  org.lwjgl.util.vector.ReadableVector2f
 *  org.lwjgl.util.vector.Vector2f
 */
package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.TimeoutTracker;
import data.scripts.NXN_GNShieldUI;
import data.scripts.util.NXN_ColorData;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.dark.shaders.distortion.DistortionAPI;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.ReadableVector2f;
import org.lwjgl.util.vector.Vector2f;

public class NXN_GNShield_Script
implements DamageTakenModifier,
AdvanceableListener {
    private ShipAPI ship;
    public static final String KEY = "NXN_GNShield_Absorb";
    private final Color DMG_TEXT = new Color(15, 15, 245, 225);
    private TimeoutTracker<BeamAPI> effected = new TimeoutTracker();
    private List<NXN_GNShieldHitData> hitDatas = new ArrayList<NXN_GNShieldHitData>();
    private TimeoutTracker<BeamAPI> beamController = new TimeoutTracker();
    public static final Color TEXT_COLOR = new Color(255, 155, 255, 255);
    public static final Color JITTER_COLOR = new Color(155, 155, 255, 75);
    public static final Color JITTER_UNDER_COLOR = new Color(155, 155, 255, 155);
    private float[][] armorGridCopy = null;
    private Boolean lastShieldOn = null;
    /** 护盾状态最小驻留时间（毫秒），用于压制原版 AI 的高频开关 */
    private static final long MIN_DWELL_MS = 500L;
    private long lastToggleMs = 0L;

    public static NXN_GNShield_Script getInstance(ShipAPI ship) {
        if (NXN_GNShield_Script.hasShield(ship)) {
            return (NXN_GNShield_Script)ship.getListeners(NXN_GNShield_Script.class).get(0);
        }
        NXN_GNShield_Script l = new NXN_GNShield_Script(ship);
        ship.addListener(l);
        return l;
    }

    public static boolean hasShield(ShipAPI ship) {
        return ship.hasListenerOfClass(NXN_GNShield_Script.class);
    }

    public NXN_GNShield_Script(ShipAPI ship) {
        this.ship = ship;
        this.init(ship);
    }

    public void init(ShipAPI ship) {
        this.saveArmor();
        if (ship.getShield() != null) {
            ship.getShield().setArc(0.0f);
            ship.getShield().setRadius(Math.max(10.0f, ship.getCollisionRadius() - (float)ship.getHullSize().ordinal() * 10.0f));
            ship.getShield().setSkipRendering(true);
            ship.getShield().setInnerColor(NXN_ColorData.NONE);
            ship.getShield().setRingColor(NXN_ColorData.NONE);
        }
        NXN_GNShieldUI.register();
    }

    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        damage.getModifier().unmodify(KEY);
        if (this.ship.isStationModule()) {
            float damageTaken;
            ShipAPI parent = this.ship.getParentStation();
            if (parent == null || parent.getShield() == null || parent.getShield() != null && !parent.getShield().isOn()) {
                return null;
            }
            ParamType type = this.getType(param, damage);
            ShipAPI source = null;
            float factor = parent.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed() * parent.getMutableStats().getShieldAbsorptionMult().getModifiedValue() * parent.getMutableStats().getShieldDamageTakenMult().getModifiedValue();
            boolean isBeam = false;
            switch (type) {
                case BEAM: {
                    source = ((BeamAPI)param).getSource();
                    isBeam = true;
                    break;
                }
                case PROJ: {
                    source = ((DamagingProjectileAPI)param).getSource();
                    if (((DamagingProjectileAPI)param).getProjectileSpec() != null) break;
                    factor = 0.0f;
                    break;
                }
                case SHIP: {
                    break;
                }
            }
            if (damage.isDps()) {
                isBeam = true;
            }
            float f = damageTaken = factor == 0.0f ? 0.0f : this.computeDamageToShield(damage, source, isBeam) * factor;
            if (damage.isForceHardFlux()) {
                isBeam = false;
            }
            if (damageTaken >= 0.0f) {
                BeamAPI beam;
                if (type == ParamType.BEAM && !this.beamController.contains((beam = (BeamAPI)param)) && beam.getBrightness() > 0.0f) {
                    this.beamController.add(beam, 1.0f);
                }
                if (type == ParamType.PROJ) {
                    this.createHitRipple(point, parent.getVelocity(), damageTaken, damage.getType(), VectorUtils.getFacing((Vector2f)VectorUtils.getDirectionalVector((Vector2f)parent.getLocation(), (Vector2f)point)), parent.getCollisionRadius());
                }
                parent.getFluxTracker().increaseFlux(damageTaken, !isBeam);
                Global.getCombatEngine().addFloatingDamageText(point, damageTaken, TEXT_COLOR, (CombatEntityAPI)this.ship, damage.getStats() != null ? damage.getStats().getEntity() : null);
                damage.getModifier().modifyMult(KEY, 1.0E-4f);
                if (type == ParamType.BEAM) {
                    if (this.effected.contains(((BeamAPI)param))) {
                        this.effected.set(((BeamAPI)param), 1.1f);
                    } else {
                        this.effected.add(((BeamAPI)param), 1.1f);
                    }
                }
                return KEY;
            }
        } else {
            if (this.ship.getShield() == null || this.ship.getShield() != null && !this.ship.getShield().isOn()) {
                return null;
            }
            ParamType type = this.getType(param, damage);
            ShipAPI source = null;
            float factor = this.ship.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed() * this.ship.getMutableStats().getShieldAbsorptionMult().getModifiedValue() * this.ship.getMutableStats().getShieldDamageTakenMult().getModifiedValue();
            boolean isBeam = false;
            switch (type) {
                case BEAM: {
                    source = ((BeamAPI)param).getSource();
                    isBeam = true;
                    break;
                }
                case PROJ: {
                    source = ((DamagingProjectileAPI)param).getSource();
                    break;
                }
                case SHIP: {
                    break;
                }
            }
            if (damage.isDps()) {
                isBeam = true;
            }
            float damageTaken = this.computeDamageToShield(damage, source, isBeam) * factor;
            if (damage.isForceHardFlux()) {
                isBeam = false;
            }
            if (damageTaken > 0.0f) {
                BeamAPI beam;
                if (type == ParamType.BEAM && !this.beamController.contains((beam = (BeamAPI)param)) && beam.getBrightness() > 0.0f) {
                    this.beamController.add(beam, 1.0f);
                }
                if (type == ParamType.PROJ) {
                    this.createHitRipple(point, this.ship.getVelocity(), damageTaken, damage.getType(), VectorUtils.getFacing((Vector2f)VectorUtils.getDirectionalVector((Vector2f)this.ship.getLocation(), (Vector2f)point)), this.ship.getCollisionRadius());
                    this.hitDatas.add(new NXN_GNShieldHitData(this.ship, point, 1.0f, MathUtils.clamp((float)(damageTaken / 4.0f), (float)10.0f, (float)40.0f)));
                }
                if (type == ParamType.BEAM) {
                    this.hitDatas.add(new NXN_GNShieldHitData(this.ship, point, 0.8f, MathUtils.clamp((float)(damageTaken / 2.0f), (float)10.0f, (float)40.0f)));
                }
                this.ship.getFluxTracker().increaseFlux(damageTaken, !isBeam);
                Global.getCombatEngine().addFloatingDamageText(point, damageTaken, TEXT_COLOR, (CombatEntityAPI)this.ship, damage.getStats() != null ? damage.getStats().getEntity() : null);
                damage.getModifier().modifyMult(KEY, 1.0E-4f);
                if (type == ParamType.BEAM) {
                    if (this.effected.contains(((BeamAPI)param))) {
                        this.effected.set(((BeamAPI)param), 1.1f);
                    } else {
                        this.effected.add(((BeamAPI)param), 1.1f);
                    }
                }
                return KEY;
            }
        }
        return null;
    }

    public void advance(float amount) {
        if (this.ship == null || !this.ship.isAlive() || this.ship.isHulk()) {
            this.ship.removeListener(this);
            return;
        }
        if (!this.effected.getItems().isEmpty()) {
            for (BeamAPI b : this.effected.getItems()) {
                if (!(this.effected.getRemaining(b) < 1.0f)) continue;
                b.getDamage().getModifier().unmodify(KEY);
            }
        }
        ArrayList<NXN_GNShieldHitData> toRemoveHits = new ArrayList<NXN_GNShieldHitData>();
        for (NXN_GNShieldHitData data : this.hitDatas) {
            data.advance(amount);
            if (!data.shouldExpire()) continue;
            toRemoveHits.add(data);
        }
        this.hitDatas.removeAll(toRemoveHits);
        this.effected.advance(1.0f);
        this.beamController.advance(amount);
        if (Global.getCombatEngine().isPaused()) {
            return;
        }
        if (this.ship.isStationModule()) {
            ShipAPI shipAPI = this.ship.getParentStation();
        } else {
            if (this.ship.getShield() != null) {
                // NXN：弧角设为 360（全向），护盾开关完全交给原版 ShieldAI / 玩家操作，
                // 这里只做状态记录，不再干预 toggle。
                boolean nowOn = this.ship.getShield().isOn();
                if (lastShieldOn == null || lastShieldOn.booleanValue() != nowOn) {
                    lastShieldOn = Boolean.valueOf(nowOn);
                }
                if (this.ship.getShield().isOn()) {
                    this.ship.getShield().forceFacing(Misc.normalizeAngle((float)(this.ship.getFacing() + 180.0f)));
                    this.retainArmor();
                    this.ship.getMutableStats().getEmpDamageTakenMult().modifyMult(NXN_GNShield_Script.getKey(), 0.0f);
                    if (this.ship == Global.getCombatEngine().getPlayerShip()) {
                        Global.getCombatEngine().maintainStatusForPlayerShip("NXN_GNShield", "graphics/icons/hullsys/fortress_shield.png", "GN\u62a4\u76fe", "\u6fc0\u6d3b", false);
                    }
                } else {
                    if (this.ship == Global.getCombatEngine().getPlayerShip()) {
                        Global.getCombatEngine().maintainStatusForPlayerShip("NXN_GNShield", "graphics/icons/hullsys/fortress_shield.png", "GN\u62a4\u76fe", "\u5173\u95ed", true);
                    }
                    this.ship.getMutableStats().getEmpDamageTakenMult().unmodify(NXN_GNShield_Script.getKey());
                }
            }
            this.saveArmor();
        }
    }

    protected ParamType getType(Object param, DamageAPI damage) {
        if (param instanceof DamagingProjectileAPI) {
            return ParamType.PROJ;
        }
        if (param instanceof BeamAPI) {
            return ParamType.BEAM;
        }
        if (param instanceof EmpArcEntityAPI) {
            return ParamType.ARC;
        }
        return ParamType.SHIP;
    }

    private float computeDamageToShield(DamageAPI damage, ShipAPI source, boolean isBeam) {
        float base = damage.getDamage();
        if (isBeam && damage.getDamage() > 0.0f) {
            base = damage.getDpsDuration() * damage.getDamage();
        }
        if ((base *= damage.getType().getShieldMult()) == 0.0f) {
            return 0.0f;
        }
        float mult = 1.0f;
        if (source != null) {
            switch (this.ship.getHullSize()) {
                case DEFAULT: {
                    break;
                }
                case FIGHTER: {
                    mult *= source.getMutableStats().getDamageToFighters().getModifiedValue();
                    break;
                }
                case FRIGATE: {
                    mult *= source.getMutableStats().getDamageToFrigates().getModifiedValue();
                    break;
                }
                case DESTROYER: {
                    mult *= source.getMutableStats().getDamageToDestroyers().getModifiedValue();
                    break;
                }
                case CRUISER: {
                    mult *= source.getMutableStats().getDamageToCruisers().getModifiedValue();
                    break;
                }
                case CAPITAL_SHIP: {
                    mult *= source.getMutableStats().getDamageToCapital().getModifiedValue();
                }
            }
        }
        switch (damage.getType()) {
            case KINETIC: {
                mult *= this.ship.getMutableStats().getKineticDamageTakenMult().getModifiedValue();
                break;
            }
            case HIGH_EXPLOSIVE: {
                mult *= this.ship.getMutableStats().getHighExplosiveDamageTakenMult().getModifiedValue();
                break;
            }
            case FRAGMENTATION: {
                mult *= this.ship.getMutableStats().getFragmentationDamageTakenMult().getModifiedValue();
                mult *= 0.25f;
                break;
            }
            case ENERGY: {
                mult *= this.ship.getMutableStats().getEnergyDamageTakenMult().getModifiedValue();
                break;
            }
        }
        mult = isBeam ? (mult *= this.ship.getMutableStats().getBeamDamageTakenMult().getModifiedValue()) : (mult *= this.ship.getMutableStats().getProjectileDamageTakenMult().getModifiedValue());
        return base * mult;
    }

    protected boolean checkSurvival() {
        return !this.ship.isAlive();
    }

    public void retainArmor() {
        if (this.armorGridCopy == null) {
            this.saveArmor();
        } else {
            ArmorGridAPI a = this.ship.getArmorGrid();
            if (a == null) {
                return;
            }
            float[][] cag = a.getGrid();
            if (cag.length == 0) {
                return;
            }
            for (int i = 0; i < cag.length; ++i) {
                for (int j = 0; j < cag[i].length; ++j) {
                    a.setArmorValue(i, j, Math.max(this.armorGridCopy[i][j], cag[i][j]));
                }
            }
        }
    }

    public void saveArmor() {
        ArmorGridAPI a = this.ship.getArmorGrid();
        if (a == null) {
            return;
        }
        float[][] cag = a.getGrid();
        if (cag.length == 0) {
            return;
        }
        this.armorGridCopy = new float[cag.length][cag[0].length];
        for (int i = 0; i < cag.length; ++i) {
            for (int j = 0; j < cag[i].length; ++j) {
                this.armorGridCopy[i][j] = cag[i][j];
            }
        }
    }

    public void immuneEMPEffects(float level) {
        float EMP_REDUCTION = 0.0f;
        if (level < 0.25f) {
            EMP_REDUCTION = (0.25f - level) * 4.0f;
        }
        this.ship.getMutableStats().getEmpDamageTakenMult().modifyMult(KEY, EMP_REDUCTION);
    }

    private void createHitRipple(Vector2f location, Vector2f velocity, float damage, DamageType type, float direction, float shieldRadius) {
        float end2;
        float start2;
        float end1;
        float dmg = damage;
        if (type == DamageType.FRAGMENTATION) {
            dmg *= 0.25f;
        }
        if (type == DamageType.HIGH_EXPLOSIVE) {
            dmg *= 0.5f;
        }
        if (type == DamageType.KINETIC) {
            dmg *= 2.0f;
        }
        if (dmg < 75.0f) {
            return;
        }
        float fadeTime = (float)Math.pow(dmg, 0.25) * 0.1f;
        float size = (float)Math.pow(dmg, 0.3333333) * 8.0f;
        float ratio = Math.min(size / shieldRadius, 1.0f);
        float arc = 90.0f - ratio * 14.54136f;
        float start1 = direction - arc;
        if (start1 < 0.0f) {
            start1 += 360.0f;
        }
        if ((end1 = direction + arc) >= 360.0f) {
            end1 -= 360.0f;
        }
        if ((start2 = direction + arc) < 0.0f) {
            start2 += 360.0f;
        }
        if ((end2 = direction - arc) >= 360.0f) {
            end2 -= 360.0f;
        }
        RippleDistortion ripple = new RippleDistortion(location, velocity);
        ripple.setSize(size);
        ripple.setIntensity(size * 0.2f);
        ripple.setFrameRate(60.0f / fadeTime);
        ripple.fadeInSize(fadeTime * 1.2f);
        ripple.fadeOutIntensity(fadeTime);
        ripple.setSize(size * 0.2f);
        ripple.setArc(start1, end1);
        DistortionShader.addDistortion((DistortionAPI)ripple);
        ripple = new RippleDistortion(location, velocity);
        ripple.setSize(size);
        ripple.setIntensity(size * 0.05f);
        ripple.setFrameRate(60.0f / fadeTime);
        ripple.fadeInSize(fadeTime * 1.2f);
        ripple.fadeOutIntensity(fadeTime);
        ripple.setSize(size * 0.2f);
        ripple.setArc(start2, end2);
        DistortionShader.addDistortion((DistortionAPI)ripple);
    }

    public static String getKey() {
        return KEY;
    }

    public ShipAPI getShip() {
        return this.ship;
    }

    public List<NXN_GNShieldHitData> getHitDatas() {
        return this.hitDatas;
    }

    public static enum ParamType {
        ARC,
        SHIP,
        PROJ,
        BEAM;

    }

    public static class NXN_GNShieldHitData {
        private Vector2f rel;
        private float time;
        private final float size;
        private final float orgT;

        public NXN_GNShieldHitData(ShipAPI ship, Vector2f location, float time, float size) {
            this.rel = Vector2f.sub((Vector2f)location, (Vector2f)ship.getLocation(), null);
            VectorUtils.rotate((Vector2f)this.rel, (float)(-ship.getFacing()));
            this.orgT = time;
            this.time = time;
            this.size = size;
        }

        public void advance(float amount) {
            this.time -= amount;
        }

        public boolean shouldExpire() {
            return this.time <= 0.0f;
        }

        public float getSize() {
            return this.size;
        }

        public Vector2f getRel() {
            return new Vector2f((ReadableVector2f)this.rel);
        }

        public float getLevel() {
            return this.orgT == 0.0f ? 0.0f : this.time / this.orgT;
        }
    }
}

