# Chapter 28 — Ray Tracing: Textured Rotating Viking Room

## Обзор

Эта глава демонстрирует рендеринг сложной 3D модели (viking_room.obj) с использованием ray tracing пайплайна Vulkan, полностью эквивалентный графическому пайплайну из `part09/ch28/Main.java`.

## Ключевые Изменения по Сравнению с ch27

### 1. Загрузка Модели из OBJ Файла

**ch27:** Хардкодный куб с 8 вершинами и 12 индексами (4 треугольника).

**ch28:** Модель `viking_room.obj` загружается с помощью библиотеки `de.javagl.obj`. Логика вынесена в утилиту `VulkanUtil.loadObjModel()`.

Структура вершины осталась прежней (8 floats):
- `vec3` position (3 floats)
- `vec3` color (3 floats) — всегда белый (1.0, 1.0, 1.0)
- `vec2` texCoord (2 floats) — UV координаты из OBJ файла с инверсией V

### 2. Индексный Буфер

**ch27:** `short[]` (UINT16) с 12 индексами.

**ch28:** `int[]` (UINT32) с `indices.length / 3` треугольниками. Это потребовало изменения `indexType` в BLAS с `UINT16` на `UINT32`.

### 3. UV Storage Buffer

В ray tracing нет аппаратной интерполяции атрибутов вершин, как в растеризации. Для корректной выборки текстуры создан отдельный `uvBuffer`:

- **Структура:** "Развёрнутый" массив UV координат — для каждого треугольника 3 vec2 пары записаны подряд.
- **Индексация в шейдере:** `uvs[gl_PrimitiveID * 3 + i]` — позволяет избежать индексного буфера в шейдере.
- **Binding:** Descriptor set binding 3 (`STORAGE_BUFFER`, stage: `CLOSEST_HIT_KHR`).

### 4. Текстура

**ch27:** `texture.png` — маленькая процедурная текстура.

**ch28:** `viking_room.png` — текстура из OBJ модели.

### 5. Параметры Камеры

**ch27:**
- Позиция: `(0, 0, 3)`
- LookAt: `(0, 0, 0)`
- Up: `(0, 1, 0)`
- Ось вращения: **X axis**

**ch28:**
- Позиция: `(2, 2, 2)` — классический вид на модель из угла
- LookAt: `(0, 0, 0)`
- Up: `(0, 0, 1)` — Z вверх (как в OBJ файле)
- Ось вращения: **Z axis**

### 6. BLAS (Bottom-Level Acceleration Structure)

Основные изменения в `createAndBuildBlas`:

| Параметр | ch27 | ch28 |
|----------|------|------|
| `primitiveCount` | 4 (хардкод) | `indices.length / 3` (динамически) |
| `maxVertex` | 7 (8 вершин куба) | `vertices.length / 8 - 1` |
| `indexType` | `UINT16` | `UINT32` |

### 7. Push Constants и Генерация Лучей

**ch27:** 3 матрицы (model, invProj, invView), лучи генерируются вручную через `tan(fov/2)`.

**ch28:** 4 матрицы (model, invProj, invView, **invModel**). Лучи генерируются через `inverseProjection`, что гарантирует идентичный ракурс с graphics pipeline:

```glsl
vec4 clipSpace = vec4(pixelCoord, -1.0, 1.0);
vec4 rayDirView4 = constants.inverseProjection * clipSpace;
vec3 rayDirView = normalize(rayDirView4.xyz);
```

Инвертная матрица модели (`invModel`) передаётся явно, а не вычисляется через `transpose()`.

## Архитектура Ray Tracing Пайплайна

### Инициализация

```
initVulkan:
  ...
  createOutputImage()        // 1280x720 STORAGE image для ray tracing
  loadModel()                // Загрузка viking_room.obj (через VulkanUtil)
  createTextureImage()       // viking_room.png → GPU
  createTextureImageView()   // Image view для texture
  createTextureSampler()     // Sampler с linear filtering + anisotropy
  createVertexBuffer()       // Vertices: pos(3) + color(3) + uv(2)
  createIndexBuffer()        // Indices: UINT32
  createUvBuffer()           // UV storage buffer (3 vec2 per triangle)
  createAccelerationStructures()  // BLAS + TLAS
  ...
```

### Рендеринг (drawFrame)

1. **Ray Tracing Dispatch:**
   - Bind ray tracing pipeline
   - Bind descriptor set (storage image + TLAS + texture sampler + UV buffer)
   - Push constants: model, invProj, invView, invModel (4 матрицы = 256 байт)
   - `cmdTraceRaysKHR` — запуск лучей для каждого пикселя 1280x720

2. **Image Transfer:**
   - Barrier: outputImage GENERAL → TRANSFER_SRC
   - Barrier: swapchain UNDEFINED → TRANSFER_DST
   - `cmdBlitImage` с letterboxing (сохранение aspect ratio)
   - Barrier: swapchain → PRESENT_SRC
   - Barrier: outputImage → GENERAL (для следующего кадра)

### Структура Вершины

```
Offset  Size    Field
------  ----    -----
0       12      Position (vec3)
12      12      Color (vec3) — всегда белый
24      8       TexCoord (vec2) — UV из OBJ
------
Total   32 bytes (8 floats)
```

### Descriptor Set Layout

| Binding | Type | Stage |
|---------|------|-------|
| 0 | STORAGE_IMAGE (rgba8) | RAYGEN_KHR |
| 1 | ACCELERATION_STRUCTURE_KHR | RAYGEN_KHR |
| 2 | COMBINED_IMAGE_SAMPLER | CLOSEST_HIT_KHR |
| 3 | STORAGE_BUFFER (UV data) | CLOSEST_HIT_KHR |

### Push Constants

64 floats (256 bytes), 4 матрицы:
- `mat4 model` — матрица вращения модели (Z axis, 90°/сек)
- `mat4 inverseProjection` — обратная матрица проекции (для генерации лучей)
- `mat4 inverseView` — обратная матрица вида (позиция камеры)
- `mat4 inverseModel` — обратная матрица модели (для трансформации лучей в object space)

## Рефакторинг в VulkanUtil

По сравнению с ch27, часть общей логики вынесена в `VulkanUtil`:

| Метод | Описание |
|-------|----------|
| `loadObjModel(InputStream)` | Загрузка OBJ модели → `ObjModelData(vertices, indices)` |
| `compileGlslShader(Arena, source, stage, filename)` | Компиляция GLSL → SPIR-V через glslangValidator |
| `compileShaderFromClass(Arena, Class, filename, stage)` | Загрузка ресурса + компиляция |

## Сравнение с Графическим Пайплайном

| Аспект | Graphics (part09/ch28) | Ray Tracing (ch28) |
|--------|------------------------|---------------------|
| Модель | viking_room.obj | viking_room.obj |
| Текстура | viking_room.png | viking_room.png |
| Вращение | Z axis, 90°/сек | Z axis, 90°/сек |
| Камера | (2,2,2) → (0,0,0) | (2,2,2) → (0,0,0) |
| Вершины | pos(3)+color(3)+uv(2) | pos(3)+color(3)+uv(2) |
| Индексы | UINT32 | UINT32 |
| Рендеринг | Rasterization (cmdDrawIndexed) | Ray Tracing (cmdTraceRaysKHR) |
| Depth Testing | Да (depth buffer) | Нет (opaque rays) |
| UV Интерполяция | Аппаратная (vertex shader → fragment shader) | Ручная (barycentric в closest hit shader через UV storage buffer) |
| Генерация лучей/камера | Perspective projection matrix | `inverseProjection * clipSpace` |
| Push constants | 3 матрицы | 4 матрицы (+ invModel) |

## Запуск

```bash
mvn exec:java -pl tutorial -Dexec.mainClass="tutorial.vulkan.raytracing.ch28.Application" -Dexec.cleanupDaemonThreads=false
```

Для включения validation layers:

```bash
mvn exec:java -pl tutorial -Dexec.mainClass="tutorial.vulkan.raytracing.ch28.Application" -Dexec.cleanupDaemonThreads=false -Dvalidation
```

## Требования

- Vulkan 1.2+ GPU
- Поддержка ray tracing (VK_KHR_ray_tracing_pipeline)
- **Push constants ≥ 256 байт** (минимум по спецификации — 128 байт, но ch28 использует 4 матрицы = 256 байт)
- glslangValidator в PATH (для компиляции шейдеров at runtime)
- viking_room.obj и viking_room.png в resources

> **Примечание:** Если на вашем GPU лимит push constants < 256 байт, приложение завершится с понятной ошибкой. Решение: уберите `invModel` из push constants и используйте `transpose(model)` в шейдере (как в ch27).
