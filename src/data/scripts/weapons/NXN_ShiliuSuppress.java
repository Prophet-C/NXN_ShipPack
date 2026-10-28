package data.scripts.weapons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 石流「压制」机制。
 *
 * 每次命中护盾叠 1 层（上限 12 层），5 秒内没有新命中则清空：
 *   ① 护盾受到伤害 +3% / 层（满层 +36%）
 *   ② 护盾维持幅能 +5% / 层（满层 +60%）
 *   ③ 最高速度 -3% / 层（最多 -30%，即"锚定"）
 *   满 12 层触发「护盾破口」：0.6 秒内护盾吸收减半，之后层数清零，30 秒冷却。
 */
public final class NXN_ShiliuSuppress {

    private static final String ID = "NXN_shiliu_suppress";

    private static final int MAX_STACKS = 12;
    private static final float DECAY_TIME = 5f;
    private static final float DMG_PER_STACK = 0.03f;
    private static final float UPKEEP_PER_STACK = 0.05f;
    private static final float SPEED_PER_STACK = 0.03f;
    private static final float SPEED_FLOOR = 0.70f;

    private static final float BREAK_TIME = 0.6f;
    private static final float BREAK_COOLDOWN = 30f;

    private static class State {
        int stacks = 0;
        float age = 0f;          // 距离上次命中
        float breakLeft = 0f;    // 破口剩余时间
        float cooldown = 0f;     // 破口冷却
    }

    private static final Map<ShipAPI, State> STATES = new HashMap<ShipAPI, State>();

    private NXN_ShiliuSuppress() {
    }

    /** 命中护盾时调用 */
    public static void apply(ShipAPI ship) {
        if (ship == null) return;
        State st = STATES.get(ship);
        if (st == null) {
            st = new State();
            STATES.put(ship, st);
        }
        st.age = 0f;
        if (st.stacks < MAX_STACKS) st.stacks++;

        // 满层 → 触发破口
        if (st.stacks >= MAX_STACKS && st.cooldown <= 0f && st.breakLeft <= 0f) {
            st.breakLeft = BREAK_TIME;
            st.cooldown = BREAK_COOLDOWN;
            st.stacks = 0;
        }
        refresh(ship, st);
    }

    /** 每帧推进（从石流武器特效里调用）*/
    public static void advance(CombatEngineAPI engine, float amount) {
        if (STATES.isEmpty()) return;

        Iterator<Map.Entry<ShipAPI, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ShipAPI, State> e = it.next();
            ShipAPI ship = e.getKey();
            State st = e.getValue();

            if (ship == null || !ship.isAlive()) {
                clear(ship);
                it.remove();
                continue;
            }

            st.age += amount;
            if (st.cooldown > 0f) st.cooldown -= amount;

            if (st.breakLeft > 0f) {
                st.breakLeft -= amount;
                if (st.breakLeft <= 0f) {
                    st.breakLeft = 0f;
                    refresh(ship, st);
                }
            }

            if (st.age > DECAY_TIME && st.stacks > 0) {
                st.stacks = 0;
                refresh(ship, st);
            }

            if (st.stacks == 0 && st.breakLeft <= 0f && st.cooldown <= 0f) {
                it.remove();
            }
        }
    }

    /** 把当前层数写入属性 */
    private static void refresh(ShipAPI ship, State st) {
        try {
            float speedMult = Math.max(SPEED_FLOOR, 1f - SPEED_PER_STACK * st.stacks);

            ship.getMutableStats().getShieldDamageTakenMult()
                    .modifyMult(ID, 1f + DMG_PER_STACK * st.stacks);
            ship.getMutableStats().getShieldUpkeepMult()
                    .modifyMult(ID, 1f + UPKEEP_PER_STACK * st.stacks);
            ship.getMutableStats().getMaxSpeed().modifyMult(ID, speedMult);

            if (st.breakLeft > 0f) {
                ship.getMutableStats().getShieldAbsorptionMult().modifyMult(ID, 0.5f);
            } else {
                ship.getMutableStats().getShieldAbsorptionMult().unmodify(ID);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 清掉某个目标身上的所有效果 */
    private static void clear(ShipAPI ship) {
        if (ship == null) return;
        try {
            ship.getMutableStats().getShieldDamageTakenMult().unmodify(ID);
            ship.getMutableStats().getShieldUpkeepMult().unmodify(ID);
            ship.getMutableStats().getMaxSpeed().unmodify(ID);
            ship.getMutableStats().getShieldAbsorptionMult().unmodify(ID);
        } catch (Throwable ignored) {
        }
    }

    /** 战斗结束清理（可选调用）*/
    public static void reset() {
        List<ShipAPI> ships = new ArrayList<ShipAPI>(STATES.keySet());
        for (ShipAPI s : ships) clear(s);
        STATES.clear();
    }
}
