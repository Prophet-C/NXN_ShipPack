package data.scripts.weapons;

import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

/**
 * 石流弹丸命中：只有打到护盾时才叠「压制」。
 */
public class NXN_ShiliuHit implements OnHitEffectPlugin {

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        // 命中处的重力扭曲（比炮口更大）
        try {
            RippleDistortion r = new RippleDistortion(new Vector2f(point), new Vector2f());
            r.setSize(20f);
            r.setMaxSize(320f);
            r.setIntensity(9f);
            r.setMaxIntensity(9f);
            r.setLifetime(0.65f);
            r.setArc(360f, 360f);
            r.setFrameRate(2f);
            r.setAutoFadeIntensityTime(0.45f);
            r.setAutoFadeSizeTime(0.65f);
            DistortionShader.addDistortion(r);
        } catch (Throwable ignored) {
        }

        if (!shieldHit) return;
        if (!(target instanceof ShipAPI)) return;
        NXN_ShiliuSuppress.apply((ShipAPI) target);
    }
}
