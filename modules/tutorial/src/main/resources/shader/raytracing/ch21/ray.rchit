#version 460
#extension GL_EXT_ray_tracing : require

struct RayPayload {
    vec3 color;
    float hitT;
    vec2 hitUV;
};

layout(location = 0) rayPayloadInEXT RayPayload payload;
hitAttributeEXT vec2 barycentrics;

void main() {
    vec3 bary = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);

    vec3 colorRed   = vec3(1.0, 0.0, 0.0);
    vec3 colorGreen = vec3(0.0, 1.0, 0.0);
    vec3 colorBlue  = vec3(0.0, 0.0, 1.0);

    payload.color = bary.x * colorRed +
                    bary.y * colorGreen +
                    bary.z * colorBlue;
}
