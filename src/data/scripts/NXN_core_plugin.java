package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import org.dark.shaders.light.LightData;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;

public class NXN_core_plugin extends BaseModPlugin {

    private static void initShaderLib() {
        ShaderLib.init();
        LightData.readLightDataCSV("data/lights/NXN_light_data.csv");
        TextureData.readTextureDataCSV("data/lights/NXN_texture_data.csv");
    }

    @Override
    public void onApplicationLoad() throws Exception {
        initShaderLib();
    }
}
