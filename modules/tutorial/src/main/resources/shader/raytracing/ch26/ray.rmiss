#version 460
#extension GL_EXT_ray_tracing : enable

struct RayPayload {
    vec3 color;
    vec2 texCoord;
};

layout(location = 0) rayPayloadInEXT RayPayload payload;

void main() {
    // Black background for missed rays
    payload.color = vec3(0.0, 0.0, 0.0);
    payload.texCoord = vec2(0.0);
}
