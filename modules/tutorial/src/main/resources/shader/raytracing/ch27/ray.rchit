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

    // UV coordinates для каждой вершины куба (как в графическом ch27):
    // Front face (z=0): vertices 0-3
    //   0: (-0.5,-0.5, 0)  UV(1,0)
    //   1: ( 0.5,-0.5, 0)  UV(0,0)
    //   2: ( 0.5, 0.5, 0)  UV(0,1)
    //   3: (-0.5, 0.5, 0)  UV(1,1)
    // Back face (z=-0.5): vertices 4-7
    //   4: (-0.5,-0.5,-0.5) UV(1,0)
    //   5: ( 0.5,-0.5,-0.5) UV(0,0)
    //   6: ( 0.5, 0.5,-0.5) UV(0,1)
    //   7: (-0.5, 0.5,-0.5) UV(1,1)
    vec2 uv[8];
    uv[0] = vec2(1.0, 0.0);
    uv[1] = vec2(0.0, 0.0);
    uv[2] = vec2(0.0, 1.0);
    uv[3] = vec2(1.0, 1.0);
    uv[4] = vec2(1.0, 0.0);
    uv[5] = vec2(0.0, 0.0);
    uv[6] = vec2(0.0, 1.0);
    uv[7] = vec2(1.0, 1.0);

    // Indices: (0,1,2), (2,3,0), (4,5,6), (6,7,4)
    // Map gl_PrimitiveID to vertex indices
    int v0, v1, v2;
    if (gl_PrimitiveID == 0)      { v0 = 0; v1 = 1; v2 = 2; }
    else if (gl_PrimitiveID == 1) { v0 = 2; v1 = 3; v2 = 0; }
    else if (gl_PrimitiveID == 2) { v0 = 4; v1 = 5; v2 = 6; }
    else                          { v0 = 6; v1 = 7; v2 = 4; }

    // Interpolate UV
    vec2 texCoord = bary.x * uv[v0] + bary.y * uv[v1] + bary.z * uv[v2];

    // Sample texture and apply sRGB correction
    vec3 sampledColor = texture(texSampler, texCoord).rgb;
    payload.color = pow(sampledColor, vec3(1.0 / 2.2));
    payload.texCoord = texCoord;
}
