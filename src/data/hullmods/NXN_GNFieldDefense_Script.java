package data.hullmods;

import com.fs.starfarer.api.combat.ShipAPI;
import java.util.ArrayList;
import java.util.List;

/**
 * 桩类：原 Moci_GNFieldDefense_Script 是独立的"力场防御"系统，
 * 地鸣只用 GN 护盾，这里只保留 GN 护盾渲染器引用到的接口，全部返回"没有"。
 */
public class NXN_GNFieldDefense_Script {

    public static boolean hasShield(ShipAPI ship) {
        return false;
    }

    public static NXN_GNFieldDefense_Script getInstance(ShipAPI ship) {
        return null;
    }

    public Shield getShield() {
        return new Shield();
    }

    public List<HitData> getHitDatas() {
        return new ArrayList<HitData>();
    }

    /** 护盾状态桩 */
    public static class Shield {
        public boolean isShieldBroken() { return false; }
        public float getShieldLevel() { return 1f; }
    }

    /** 命中数据桩 */
    public static class HitData {
        public float getLevel() { return 0f; }
        public float getSize() { return 0f; }
        public org.lwjgl.util.vector.Vector2f getRel() { return new org.lwjgl.util.vector.Vector2f(); }
    }
}
