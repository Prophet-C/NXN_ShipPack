package data.scripts.weapons;

import java.nio.ByteBuffer;
import java.util.Random;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * 程序生成的 3D 噪声贴图（供领域 shader 使用）。
 *
 * 用三个八度的值噪声（value noise）叠加，生成 GL_TEXTURE_3D。
 * 因为采样时 z 用 fract(time)，所以不需要每帧更新贴图。
 */
public class NXN_Noise3D {

    public static final int SIZE = 64;

    /** 创建并返回 3D 噪声纹理 ID（需在 GL 上下文中调用） */
    public static int create() {
        int size = SIZE;
        float[] acc = new float[size * size * size];

        int[] lattice = {4, 8, 16};
        float[] weights = {0.5f, 0.3f, 0.2f};

        Random rnd = new Random(20261023L);

        for (int o = 0; o < lattice.length; o++) {
            int lat = lattice[o];
            float[] grid = new float[lat * lat * lat];
            for (int i = 0; i < grid.length; i++) {
                grid[i] = rnd.nextFloat();
            }

            for (int z = 0; z < size; z++) {
                float fz = (float) z * lat / size;
                for (int y = 0; y < size; y++) {
                    float fy = (float) y * lat / size;
                    for (int x = 0; x < size; x++) {
                        float fx = (float) x * lat / size;
                        float v = trilinear(grid, lat, fx, fy, fz);
                        acc[(z * size + y) * size + x] += weights[o] * v;
                    }
                }
            }
        }

        // 写入 RGB 缓冲
        ByteBuffer buf = BufferUtils.createByteBuffer(size * size * size * 3);
        for (int i = 0; i < acc.length; i++) {
            float v = acc[i];
            if (v < 0f) v = 0f;
            if (v > 1f) v = 1f;
            byte b = (byte) (int) (v * 255f);
            buf.put(b).put(b).put(b);
        }
        buf.flip();

        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL12.GL_TEXTURE_3D, tex);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL11.GL_REPEAT);
        GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGB8, size, size, size, 0,
                GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buf);

        return tex;
    }

    /** 三线性插值取样（带环绕） */
    private static float trilinear(float[] grid, int lat, float x, float y, float z) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float tx = x - x0;
        float ty = y - y0;
        float tz = z - z0;

        float c000 = at(grid, lat, x0, y0, z0);
        float c100 = at(grid, lat, x0 + 1, y0, z0);
        float c010 = at(grid, lat, x0, y0 + 1, z0);
        float c110 = at(grid, lat, x0 + 1, y0 + 1, z0);
        float c001 = at(grid, lat, x0, y0, z0 + 1);
        float c101 = at(grid, lat, x0 + 1, y0, z0 + 1);
        float c011 = at(grid, lat, x0, y0 + 1, z0 + 1);
        float c111 = at(grid, lat, x0 + 1, y0 + 1, z0 + 1);

        float x00 = c000 + (c100 - c000) * tx;
        float x10 = c010 + (c110 - c010) * tx;
        float x01 = c001 + (c101 - c001) * tx;
        float x11 = c011 + (c111 - c011) * tx;

        float y0v = x00 + (x10 - x00) * ty;
        float y1v = x01 + (x11 - x01) * ty;

        return y0v + (y1v - y0v) * tz;
    }

    private static float at(float[] grid, int lat, int x, int y, int z) {
        int xx = ((x % lat) + lat) % lat;
        int yy = ((y % lat) + lat) % lat;
        int zz = ((z % lat) + lat) % lat;
        return grid[(zz * lat + yy) * lat + xx];
    }
}
