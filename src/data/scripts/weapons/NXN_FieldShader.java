package data.scripts.weapons;

import java.awt.Color;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 领域着色器（人联 absorption_field 同款架构）。
 *
 * - 3D 噪声贴图（NXN_Noise3D 程序生成）
 * - fragment shader 里做极坐标螺旋 + 噪声扭曲 + 三层噪声
 * - 世界坐标四边形 + gl_ModelViewProjectionMatrix
 *
 * 失败时静默降级（isOk() 返回 false，调用方回退到贴图方案）。
 */
public class NXN_FieldShader {

    private static final String VERT = "data/shaders/nxn_field.vert";
    private static final String FRAG = "data/shaders/nxn_field.frag";

    private int program = 0;
    private int noiseTex = 0;
    private boolean ok = false;
    private boolean initialized = false;
    private String error = null;

    private int uCenter = -1;
    private int uRadius = -1;
    private int uTime = -1;
    private int uIntensity = -1;
    private int uTint = -1;
    private int uNoise = -1;

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    /** 第一次调用时真正创建 GL 资源（必须在渲染线程） */
    private void ensureInit() {
        if (initialized) return;
        initialized = true;
        try {
            int vs = compile(VERT, GL20.GL_VERTEX_SHADER);
            int fs = compile(FRAG, GL20.GL_FRAGMENT_SHADER);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vs);
            GL20.glAttachShader(program, fs);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new RuntimeException("链接失败: " + GL20.glGetProgramInfoLog(program, 2048));
            }
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);

            uCenter = GL20.glGetUniformLocation(program, "center");
            uRadius = GL20.glGetUniformLocation(program, "radius");
            uTime = GL20.glGetUniformLocation(program, "time");
            uIntensity = GL20.glGetUniformLocation(program, "intensity");
            uTint = GL20.glGetUniformLocation(program, "tint");
            uNoise = GL20.glGetUniformLocation(program, "noiseTexture");

            noiseTex = NXN_Noise3D.create();
            ok = true;
        } catch (Throwable t) {
            ok = false;
            error = String.valueOf(t.getMessage());

        }
    }

    private static int compile(String path, int type) {
        int shader = GL20.glCreateShader(type);
        String src;
        try {
            src = Global.getSettings().loadText(path);
        } catch (Throwable t) {
            throw new RuntimeException("读取着色器失败: " + path);
        }
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("编译失败(" + path + "): " + GL20.glGetShaderInfoLog(shader, 2048));
        }
        return shader;
    }

    /** 在世界坐标绘制领域 */
    public void render(Vector2f center, float radius, float time, float intensity,
                       Color tint, ViewportAPI viewport) {
        ensureInit();
        if (!ok || program == 0 || radius <= 1f) return;

        float uiMult = 1f;
        try {
            uiMult = Global.getSettings().getScreenScaleMult();
        } catch (Throwable ignored) {
        }

        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();

            GL20.glUseProgram(program);

            float screenCx = viewport.convertWorldXtoScreenX(center.x) * uiMult;
            float screenCy = viewport.convertWorldYtoScreenY(center.y) * uiMult;
            float screenR = radius / Math.max(0.001f, viewport.getViewMult()) * uiMult;

            if (uCenter != -1) GL20.glUniform2f(uCenter, screenCx, screenCy);
            if (uRadius != -1) GL20.glUniform1f(uRadius, screenR);
            if (uTime != -1) GL20.glUniform1f(uTime, time);
            if (uIntensity != -1) GL20.glUniform1f(uIntensity, intensity);
            if (uTint != -1) GL20.glUniform3f(uTint,
                    tint.getRed() / 255f, tint.getGreen() / 255f, tint.getBlue() / 255f);

            if (uNoise != -1 && noiseTex != 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL12.GL_TEXTURE_3D, noiseTex);
                GL20.glUniform1i(uNoise, 0);
            }

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            float left = center.x - radius;
            float right = center.x + radius;
            float bottom = center.y - radius;
            float top = center.y + radius;

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0f, 0f);
            GL11.glVertex2f(left, bottom);
            GL11.glTexCoord2f(1f, 0f);
            GL11.glVertex2f(right, bottom);
            GL11.glTexCoord2f(1f, 1f);
            GL11.glVertex2f(right, top);
            GL11.glTexCoord2f(0f, 1f);
            GL11.glVertex2f(left, top);
            GL11.glEnd();

            GL20.glUseProgram(0);

            GL11.glPopMatrix();
            GL11.glPopAttrib();
        } catch (Throwable t) {
            ok = false;
            error = String.valueOf(t.getMessage());

        }
    }
}
