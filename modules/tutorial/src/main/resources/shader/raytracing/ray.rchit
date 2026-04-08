#version 460
#extension GL_EXT_ray_tracing : require

struct RayPayload {
    vec3 color;
};

layout(location = 0) rayPayloadInEXT RayPayload payload;
layout(binding = 0, set = 0, rgba8) uniform image2D resultImage;

void main() {
    // Hit shader вызвался! Записываем ЗЕЛЁНЫЙ цвет в payload
    // Координаты пикселя можно получить из ray origin/direction
    // Но проще - просто установить цвет, raygen сам запишет его
    
    payload.color = vec3(0.0, 1.0, 0.0); // Зелёный для попаданий
}