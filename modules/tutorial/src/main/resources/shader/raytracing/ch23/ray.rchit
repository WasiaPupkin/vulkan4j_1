#version 460
#extension GL_EXT_ray_tracing : require

struct RayPayload {
    vec3 color;
    vec3 barycentrics;
};

layout(location = 0) rayPayloadInEXT RayPayload payload;
hitAttributeEXT vec2 barycentrics;

layout(push_constant) uniform Constants {
    mat4 model;
    mat4 inverseProjection;
    mat4 inverseView;
} constants;

void main() {
    // Compute barycentric coordinates
    vec3 bary = vec3(1.0 - barycentrics.x - barycentrics.y, barycentrics.x, barycentrics.y);
    payload.barycentrics = bary;

    // Our quad has 2 triangles with these vertex colors:
    // Triangle 0 (indices 0,1,2): red, green, blue
    // Triangle 1 (indices 2,3,0): blue, white, red
    //
    // We use gl_HitKindKHR to determine which triangle was hit.
    // gl_HitKindKHR encodes the instance/custom index which we set to 0.
    // Instead, we can determine the triangle by checking the barycentric winding.
    
    // For a counter-clockwise quad split diagonally:
    // If bary.x + bary.y <= 1.0 and we're in the first triangle (0,1,2)
    // The vertex colors are: v0=red, v1=green, v2=blue, v3=white
    
    // Simple approach: use barycentric coordinates to interpolate
    // Triangle 1: red(0), green(1), blue(2)
    // Triangle 2: blue(2), white(3), red(0)
    
    // We can distinguish by checking if bary.z (third bary) corresponds to vertex 2 or 0
    // In practice for a quad, the barycentrics naturally interpolate
    
    vec3 color0 = vec3(1.0, 0.0, 0.0);  // vertex 0: red
    vec3 color1 = vec3(0.0, 1.0, 0.0);  // vertex 1: green  
    vec3 color2 = vec3(0.0, 0.0, 1.0);  // vertex 2: blue
    vec3 color3 = vec3(1.0, 1.0, 1.0);  // vertex 3: white

    // For triangle 0,1,2: interpolate red, green, blue
    // For triangle 2,3,0: interpolate blue, white, red
    // The barycentrics from VkAccelerationStructureGeometryTrianglesDataKHR 
    // are relative to the triangle being hit.
    
    // Since both triangles share the same barycentric interpolation pattern,
    // we use the fact that gl_PrimitiveID tells us which primitive was hit
    if (gl_PrimitiveID == 0) {
        // Triangle 0,1,2: red, green, blue
        payload.color = bary.x * color0 + bary.y * color1 + bary.z * color2;
    } else {
        // Triangle 2,3,0: blue, white, red
        payload.color = bary.x * color2 + bary.y * color3 + bary.z * color0;
    }
}
