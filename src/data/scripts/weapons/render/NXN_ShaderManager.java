/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fs.starfarer.api.Global
 *  org.apache.log4j.Logger
 *  org.lwjgl.opengl.GL20
 *  org.lwjgl.opengl.GLContext
 */
package data.scripts.weapons.render;

import com.fs.starfarer.api.Global;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public class NXN_ShaderManager {
    private static NXN_ShaderManager instance;
    private Map<String, Integer> shaders = new HashMap<String, Integer>();
    private boolean openGL20Supported = false;
    private static final Logger LOG;

    private NXN_ShaderManager() {
        this.checkOpenGLSupport();
    }

    public static NXN_ShaderManager getInstance() {
        if (instance == null) {
            instance = new NXN_ShaderManager();
        }
        return instance;
    }

    private void checkOpenGLSupport() {
        try {
            this.openGL20Supported = GLContext.getCapabilities().OpenGL20;
            LOG.info(("[MOCI_SHADER] OpenGL20Supported=" + this.openGL20Supported));
        }
        catch (Exception e) {
            this.openGL20Supported = false;
            LOG.warn("[MOCI_SHADER] OpenGL capability check failed", (Throwable)e);
        }
    }

    public int createShader(String vertPath, String fragPath, String shaderKey) {
        if (!this.openGL20Supported) {
            LOG.warn("[MOCI_SHADER] Skip createShader (OpenGL 2.0 not supported)");
            return 0;
        }
        if (this.shaders.containsKey(shaderKey)) {
            LOG.info(("[MOCI_SHADER] Reuse program key=" + shaderKey + " id=" + this.shaders.get(shaderKey)));
            return this.shaders.get(shaderKey);
        }
        try {
            String vertSource = Global.getSettings().loadText(vertPath);
            String fragSource = Global.getSettings().loadText(fragPath);
            LOG.info(("[MOCI_SHADER] Load sources vert=" + vertPath + " len=" + (vertSource != null ? vertSource.length() : -1) + ", frag=" + fragPath + " len=" + (fragSource != null ? fragSource.length() : -1)));
            int programID = this.createShaderProgram(vertSource, fragSource);
            if (programID != 0) {
                this.shaders.put(shaderKey, programID);
                LOG.info(("[MOCI_SHADER] Program linked key=" + shaderKey + " id=" + programID));
                return programID;
            }
            LOG.warn(("[MOCI_SHADER] Program link failed key=" + shaderKey));
            return 0;
        }
        catch (Exception e) {
            LOG.error(("[MOCI_SHADER] Exception while creating program key=" + shaderKey), (Throwable)e);
            return 0;
        }
    }

    public int getShader(String shaderKey) {
        Integer shader = this.shaders.get(shaderKey);
        return shader != null ? shader : 0;
    }

    public boolean isShaderValid(String shaderKey) {
        Integer shaderID = this.shaders.get(shaderKey);
        if (shaderID == null || shaderID == 0) {
            return false;
        }
        try {
            return GL20.glIsProgram((int)shaderID);
        }
        catch (Exception e) {
            return false;
        }
    }

    private int createShader(String source, int shaderType) {
        int shaderID = GL20.glCreateShader((int)shaderType);
        GL20.glShaderSource((int)shaderID, (CharSequence)source);
        GL20.glCompileShader((int)shaderID);
        if (GL20.glGetShaderi((int)shaderID, (int)35713) == 0) {
            String log = GL20.glGetShaderInfoLog((int)shaderID, (int)GL20.glGetShaderi((int)shaderID, (int)35716));
            LOG.error(("[MOCI_SHADER] Compile failed type=" + shaderType + " log=\n" + log));
            GL20.glDeleteShader((int)shaderID);
            return 0;
        }
        LOG.info(("[MOCI_SHADER] Compile OK type=" + shaderType + " id=" + shaderID));
        return shaderID;
    }

    private int createShaderProgram(String vertSource, String fragSource) {
        int programID = GL20.glCreateProgram();
        int vertShader = this.createShader(vertSource, 35633);
        int fragShader = this.createShader(fragSource, 35632);
        if (vertShader == 0 || fragShader == 0) {

            if (vertShader != 0) {
                GL20.glDeleteShader((int)vertShader);
            }
            if (fragShader != 0) {
                GL20.glDeleteShader((int)fragShader);
            }
            GL20.glDeleteProgram((int)programID);
            return 0;
        }
        GL20.glAttachShader((int)programID, (int)vertShader);
        GL20.glAttachShader((int)programID, (int)fragShader);
        GL20.glLinkProgram((int)programID);
        if (GL20.glGetProgrami((int)programID, (int)35714) == 0) {
            String error = GL20.glGetProgramInfoLog((int)programID, (int)GL20.glGetProgrami((int)programID, (int)35716));

            LOG.error(("[MOCI_SHADER] Link failed log=\n" + error));
            GL20.glDeleteProgram((int)programID);
            GL20.glDeleteShader((int)vertShader);
            GL20.glDeleteShader((int)fragShader);
            return 0;
        }
        LOG.info(("[MOCI_SHADER] Link OK program=" + programID + ", vert=" + vertShader + ", frag=" + fragShader));
        GL20.glDeleteShader((int)vertShader);
        GL20.glDeleteShader((int)fragShader);
        return programID;
    }

    public void cleanup() {
        for (Map.Entry<String, Integer> entry : this.shaders.entrySet()) {
            try {
                GL20.glDeleteProgram((int)entry.getValue());
            }
            catch (Exception exception) {}
        }
        this.shaders.clear();
    }

    public boolean isOpenGL20Supported() {
        return this.openGL20Supported;
    }

    public void printShaderStats() {
    }

    static {
        LOG = Global.getLogger(NXN_ShaderManager.class);
    }
}

