#version 460
#extension GL_EXT_ray_tracing : enable

struct RayPayload {
    vec3 color;
    float hitT;
    vec2 hitUV;
};

layout(location = 0) rayPayloadInEXT RayPayload payload;

void main() {
    // Black background for missed rays
    payload.color = vec3(0.0, 0.0, 0.0);
}
