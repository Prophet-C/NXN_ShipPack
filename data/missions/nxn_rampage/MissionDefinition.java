package data.missions.nxn_rampage;

import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;

/**
 * \u5730\u9e23\u6d4b\u8bd5\u573a\uff1a\u6211\u65b9\u5355\u8230\u5730\u9e23\u7ea7 vs \u654c\u65b9\u4e09\u8258\u653b\u52bf\u7ea7\u3002
 * \u7528\u4e8e\u6d4b\u8bd5 NXN_rumbling \u7684\u4e3b\u70ae\u5207\u6362 / \u51fa\u529b\u89e3\u7981 / \u6a21\u5757\u5c55\u5f00\u3002
 */
public class MissionDefinition implements MissionDefinitionPlugin {

    @Override
    public void defineMission(MissionDefinitionAPI api) {

        api.initFleet(FleetSide.PLAYER, "NXN", FleetGoal.ATTACK, false);
        api.initFleet(FleetSide.ENEMY, "HSS", FleetGoal.ATTACK, true);

        api.setFleetTagline(FleetSide.PLAYER, "\u5730\u9e23\u7ea7 \u5355\u8230");
        api.setFleetTagline(FleetSide.ENEMY, "\u653b\u52bf\u7ea7 x3");

        api.addBriefingItem("\u6211\u65b9\u4ec5\u6709\u5730\u9e23\u7ea7\u4e00\u8258\uff0c\u654c\u65b9\u4e3a\u4e09\u8258\u653b\u52bf\u7ea7\u6218\u5217\u8230\u3002");
        api.addBriefingItem("\u6d4b\u8bd5\u8981\u70b9\uff1a\u4e3b\u70ae\u5b9e\u5f39/\u80fd\u91cf\u5207\u6362\u3001\u51fa\u529b\u89e3\u7981\u65f6\u7684\u6a21\u5757\u5c55\u5f00\u4e0e\u7c92\u5b50\u3001\u574d\u7f29\u7089\u4e0e\u6a21\u5757\u5316\u88c5\u7532\u3002");
        api.addBriefingItem("\u79fb\u9664\u300c\u5b9e\u5f39\u6a21\u5f0f\u300d\u6216\u300c\u80fd\u91cf\u6a21\u5f0f\u300d\u8239\u63d2\u5373\u53ef\u5207\u6362\u4e3b\u70ae\u3002");

        // \u6211\u65b9\uff1a\u5730\u9e23\u7ea7\uff08\u542b\u5168\u90e8\u6a21\u5757\uff09
        api.addToFleet(FleetSide.PLAYER, "NXN_rumbling_base", FleetMemberType.SHIP, "NXN \u5730\u9e23", true);

        // \u654c\u65b9\uff1a\u4e09\u8258\u653b\u52bf\u7ea7
        api.addToFleet(FleetSide.ENEMY, "onslaught_Standard", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, "onslaught_Standard", FleetMemberType.SHIP, false);
        api.addToFleet(FleetSide.ENEMY, "onslaught_Standard", FleetMemberType.SHIP, false);

        // \u5730\u56fe\uff1a10000 x 10000\uff0c\u53cc\u65b9\u6b63\u9762\u63a5\u654c\uff0c\u65e0\u5e72\u6270\u76ee\u6807
        float width = 10000f;
        float height = 10000f;
        api.initMap(-width / 2f, width / 2f, -height / 2f, height / 2f);
    }
}
