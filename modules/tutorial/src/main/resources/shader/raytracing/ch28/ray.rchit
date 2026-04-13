#version 460
#extension GL_EXT_ray_tracing : require

struct RayPayload {
    vec3 color;
    vec2 texCoord;
};

layout(location = 0) rayPayloadInEXT RayPayload payload;
hitAttributeEXT vec2 barycentrics;

layout(binding = 2, set = 0) uniform sampler2D texSampler;

// UV storage buffer: для каждого треугольника 3 UV координаты подряд (vec2)
// Индекс: gl_PrimitiveID * 3 + [0,1,2]
layout(std430, set = 0, binding = 3) readonly buffer UVBuffer {
    vec2 uvs[];
};

layout(push_constant) uniform Constants {
    mat4 model;
    mat4 inverseProjection;
    mat4 inverseView;
} constants;

void main() {
    // Compute barycentric coordinates
    vec3 bary = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);

    // Получаем UV координаты трёх вершин треугольника из storage buffer
    uint baseIndex = gl_PrimitiveID * 3;
    vec2 uv0 = uvs[baseIndex];
    vec2 uv1 = uvs[baseIndex + 1];
    vec2 uv2 = uvs[baseIndex + 2];

    // Интерполируем UV с barycentric координатами
    vec2 texCoord = bary.x * uv0 + bary.y * uv1 + bary.z * uv2;

    // Sample texture and apply sRGB correction
    vec3 sampledColor = texture(texSampler, texCoord).rgb;
    payload.color = pow(sampledColor, vec3(1.0 / 2.2));
    payload.texCoord = texCoord;
}
