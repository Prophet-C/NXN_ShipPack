#version 330 compatibility

uniform vec2 center;
uniform float radius;
uniform float time;
uniform float intensity;
uniform vec3 tint;
uniform sampler3D noiseTexture;

in vec2 texCoord;
out vec4 fragColor;

float sampleNoise(vec2 pos, float timeOffset) {
    vec3 coord = vec3(pos.x / 2.0, pos.y / 2.0, fract(time * 0.03 + timeOffset));
    float n = texture(noiseTexture, coord).r;
    return (n - 0.5) * 2.4;
}

void main() {
    vec2 px = gl_FragCoord.xy;
    vec2 d = px - center;
    float R = length(d);
    float r = R / max(radius, 1.0);

    // 圆外丢弃 + 边缘柔化
    float edgeSoftness = 1.5 / max(radius, 1.0);
    float mask = 1.0 - smoothstep(1.0, 1.0 + edgeSoftness, r);
    if (mask <= 0.001) discard;

    vec2 vR = d / max(radius, 1.0);
    float rad = length(vR);
    vec2 v = d / max(radius, 1.0);
    vec2 dir = normalize(d + vec2(1e-6));
    vec2 tangent = vec2(-dir.y, dir.x);

    float birthIntensity = smoothstep(0.75, 0.95, rad);
    float fadeToCenter = smoothstep(0.05, 0.9, rad);

    float timePhase = time * 0.03;
    vec2 phasePos = v * 3.0;
    float n1 = sampleNoise(phasePos, 0.0);
    float n2 = sampleNoise(phasePos + vec2(1.7, 2.3), 0.1);

    // 旋流：切向 + 向内 + curl 噪声
    vec2 curlFlow = vec2(n2, n1) * 0.20;
    vec2 radialFlow = dir * (-0.30) * timePhase * (0.2 + birthIntensity * 0.8);
    vec2 tangentFlow = tangent * n1 * 0.22;
    vec2 combinedFlow = radialFlow + tangentFlow + curlFlow;
    vec2 mistTravel = v - combinedFlow;

    // 螺旋：角度随半径变化（对数螺旋）
    float spiralPhase = 0.9 - rad * 3.4 + time * 0.12;
    float c = cos(spiralPhase);
    float s = sin(spiralPhase);
    vec2 rotatedPos = vec2(mistTravel.x * c - mistTravel.y * s,
                           mistTravel.x * s + mistTravel.y * c);

    // 噪声扭曲
    vec2 noiseWarp = vec2(sampleNoise(rotatedPos * 1.8, 0.2),
                          sampleNoise(rotatedPos * 2.2, 0.25));
    vec2 mistPos = rotatedPos + noiseWarp * 0.14;

    // 三层噪声叠加
    float m1 = sampleNoise(mistPos * 2.6, 0.3);
    float m2 = sampleNoise(mistPos * 4.4, 0.35);
    float m3 = sampleNoise(mistPos * 8.0, 0.4);
    float mistBase = m1 * 0.5 + m2 * 0.3 + m3 * 0.2;

    float depthIntensity = smoothstep(0.95, 0.25, rad);
    float density = mistBase * fadeToCenter;
    density *= (0.7 + birthIntensity * 0.7 + depthIntensity * 0.5);
    density *= smoothstep(-1.2, 0.3, mistBase);
    density = clamp(density, 0.0, 1.0);

    // ===== 外圈星云带（只在 0.58~1.0 半径之间，程序生成）=====
    float band = smoothstep(0.26, 0.42, rad) * (1.0 - smoothstep(0.70, 0.86, rad));
    float bandDensity = 0.0;
    if (band > 0.01) {
        vec2 bandPos = v * 1.7;
        float b1 = sampleNoise(bandPos, 0.55);
        float b2 = sampleNoise(bandPos * 2.1 + vec2(3.1, 1.7), 0.62);
        float b3 = sampleNoise(bandPos * 4.3 + vec2(0.7, 2.9), 0.71);
        float bandNoise = b1 * 0.5 + b2 * 0.3 + b3 * 0.2;
        bandDensity = smoothstep(-0.35, 0.95, bandNoise) * band;
    }

    // 中心亮度
    float core = smoothstep(0.5, 0.0, rad);

    vec3 col = tint * (density * 1.7) + vec3(0.78, 1.0, 0.85) * core * 0.85;
    col += tint * bandDensity * 1.5;
    float alpha = (density * 0.90 + core * 0.55 + bandDensity * 0.70) * mask * intensity;

    fragColor = vec4(col, clamp(alpha, 0.0, 1.0));
}
