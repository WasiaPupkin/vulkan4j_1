#version 460
#extension GL_EXT_ray_tracing : require

struct RayPayload {
    vec3 color;
    vec2 texCoord;
};

layout(location = 0) rayPayloadInEXT RayPayload payload;
hitAttributeEXT vec2 barycentrics;

layout(binding = 2, set = 0) uniform sampler2D texSampler;

layout(push_constant) uniform Constants {
    mat4 model;
    mat4 inverseProjection;
    mat4 inverseView;
} constants;

void main() {
    // Compute barycentric coordinates
    vec3 bary = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);

    // UV coordinates для каждой вершины квада:
    // vertex 0: (0, 1) - bottom-left
    // vertex 1: (1, 1) - bottom-right  
    // vertex 2: (1, 0) - top-right
    // vertex 3: (0, 0) - top-left
    vec2 uv0 = vec2(0.0, 1.0);  // vertex 0
    vec2 uv1 = vec2(1.0, 1.0);  // vertex 1
    vec2 uv2 = vec2(1.0, 0.0);  // vertex 2
    vec2 uv3 = vec2(0.0, 0.0);  // vertex 3

    // Интерполируем UV по барицентрическим координатам
    vec2 texCoord;
    if (gl_PrimitiveID == 0) {
        // Triangle 0,1,2
        texCoord = bary.x * uv0 + bary.y * uv1 + bary.z * uv2;
    } else {
        // Triangle 2,3,0
        texCoord = bary.x * uv2 + bary.y * uv3 + bary.z * uv0;
    }

    // Сэмплируем текстуру и корректируем яркость
    vec3 sampledColor = texture(texSampler, texCoord).rgb;
    // SRGB correction: linear -> sRGB для корректного отображения
    // (output image в UNORM, swapchain может быть SRGB)
    payload.color = pow(sampledColor, vec3(1.0 / 2.2));
    payload.texCoord = texCoord;
}
