# Глава 21 — Ray Tracing: Индекс Буфер и Квадрат

## Цель Главы

Эта глава — ray tracing аналог главы **part06/ch21** из основного туториала. В part06/ch21 добавляется **индекс буфер** для рендеринга квадрата из 4 вершин (2 треугольника) вместо одного треугольника. Здесь мы делаем то же самое, но через **ray tracing pipeline**.

### Ключевые Отличия от ch20

| Аспект | ch20 | ch21 |
|--------|------|------|
| Вершины | 3 (один треугольник) | 4 (квадрат) |
| Индекс буфер | Нет | Да (6 индексов) |
| Треугольники | 1 | 2 |
| `primitiveCount` в BLAS | 1 | 2 |
| Вершинный stride | 12 байт (3 float) | 24 байта (6 float: pos + color) |

---

## Архитектура Приложения

```
Application.java
├── initWindow()                    → GLFW окно 1280×720
├── initVulkan()
│   ├── createInstance()            → Vulkan instance + validation layers
│   ├── setupDebugMessenger()       → Отладка (если включена)
│   ├── createSurface()             → Поверхность GLFW
│   ├── pickPhysicalDevice()        → Выбор GPU с поддержкой RT
│   ├── createLogicalDevice()       → Логическое устройство + RT фичи
│   ├── createVMA()                 → VMA аллокатор для буферов
│   ├── createCommandPool()         → Пул команд
│   ├── createSwapchain()           → Swapchain + изображения
│   ├── createImageViews()          → Views для swapchain изображений
│   ├── createOutputImage()         → Storage image для RT результата
│   ├── createVertexBuffer()        → 4 вершины (позиция + цвет)
│   ├── createIndexBuffer()         → 6 индексов (2 треугольника)
│   ├── createAccelerationStructures()
│   │   ├── createAndBuildBlas()    → Bottom-level AS (геометрия)
│   │   └── createAndBuildTlas()    → Top-level AS (инстансы)
│   ├── createDescriptorSetLayout() → Layout (storage image + AS)
│   ├── createDescriptorPool()      → Пул дескрипторов
│   ├── createDescriptorSet()       → Дескриптор для шейдеров
│   ├── createRayTracingPipeline()  → RT пайплайн (rgen + rchit + rmiss)
│   ├── createSyncObjects()         → Семафоры и фенсы
│   ├── createShaderBindingTable()  → SBT (указатели на шейдер-группы)
│   └── createCommandBuffers()      → Командные буферы
├── mainLoop()                      → Цикл рендеринга
└── cleanup()                       → Очистка ресурсов
```

---

## Ключевые Изменения по сравнению с ch20

### 1. Вершинный Буфер

В ch20 было **3 вершины** (один треугольник):
```java
float[] vertices = {
    0.0f,  2.0f, 0.0f,
   -2.0f, -2.0f, 0.0f,
    2.0f, -2.0f, 0.0f
};
```

В ch21 — **4 вершины** (квадрат) с цветами:
```java
float[] vertices = {
    // Position         // Color
    -2.0f, -2.0f, 0.0f,   1.0f, 0.0f, 0.0f,   // vertex 0: red,    bottom-left
     2.0f, -2.0f, 0.0f,   0.0f, 1.0f, 0.0f,   // vertex 1: green,  bottom-right
     2.0f,  2.0f, 0.0f,   0.0f, 0.0f, 1.0f,   // vertex 2: blue,   top-right
    -2.0f,  2.0f, 0.0f,   1.0f, 1.0f, 1.0f,   // vertex 3: white,  top-left
};
```

**Stride** изменён с 12 на 24 байта (6 float × 4 байта):
```java
private static final int VERTEX_STRIDE_BYTES = 24; // pos: 3 + color: 3
```

### 2. Индекс Буфер

**Новый метод** `createIndexBuffer()` создаёт буфер с 6 индексами:
```java
int[] indices = {
    0, 1, 2,    // Triangle 1: bottom-left → bottom-right → top-right
    2, 3, 0     // Triangle 2: top-right → top-left → bottom-left
};
```

Индекс буфер использует VMA для выделения памяти:
```java
var bufferPair = createVmaBuffer(vma, vmaAllocator, arena, size,
    VkBufferUsageFlags.INDEX_BUFFER | 
    VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | 
    VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
    VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
    VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT);
```

**Ключевые флаги использования:**
- `INDEX_BUFFER` — буфер индексов
- `SHADER_DEVICE_ADDRESS` — доступен для ray tracing шейдеров
- `ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR` — используется при построении AS

### 3. BLAS (Bottom-Level Acceleration Structure)

Метод `createAndBuildBlas()` теперь принимает **два адреса** — вершин и индексов:
```java
private long createAndBuildBlas(Arena arena, long vertexAddress, long indexAddress, int alignment)
```

**Изменения:**
```java
// ch20: 1 треугольник, локальный индекс буфер
int[] indices = {0, 1, 2};
.primitiveCount(1)
.maxVertex(2)

// ch21: 2 треугольника, индекс буфер передаётся извне
int primitiveCount = 2;
int maxVertex = 3;  // 4 вершины → максимальный индекс = 3
```

**Треугольная геометрия:**
```java
var triangles = VkAccelerationStructureGeometryTrianglesDataKHR.allocate(arena)
    .vertexFormat(VkFormat.R32G32B32_SFLOAT)
    .vertexData(vd -> vd.deviceAddress(vertexAddress))
    .vertexStride(VERTEX_STRIDE_BYTES)    // 24 байта (не 12!)
    .maxVertex(maxVertex)                  // 3 (не 2!)
    .indexType(VkIndexType.UINT32)
    .indexData(vd -> vd.deviceAddress(indexAddress))  // Внешний буфер!
    .transformData(vd -> vd.deviceAddress(0));
```

**Важно:** Мы больше не создаём локальный индекс буфер внутри `createAndBuildBlas()` — вместо этого используем `indexAddress`, переданный из `createIndexBuffer()`. Это экономит память и упрощает код.

### 4. Инициализация

В `initVulkan()` добавлен новый шаг:
```java
createOutputImage();
createVertexBuffer();
createIndexBuffer();              // ← НОВОЕ
createAccelerationStructures();
```

### 5. Очистка

В `cleanup()` добавлена очистка индекс буфера:
```java
if (indexBuffer != null) {
    vma.destroyBuffer(vmaAllocator, indexBuffer, indexBufferAllocation);
    indexBuffer = null;
    indexBufferAllocation = null;
}
```

---

## Ray Tracing Пайплайн

### Шейдерные Группы

Пайплайн состоит из 3 шейдерных групп:

| Группа | Тип | Шейдер | Описание |
|--------|-----|--------|----------|
| 0 | `GENERAL` | `ray.rgen` | Генерация лучей для каждого пикселя |
| 1 | `GENERAL` | `ray.rmiss` | Фон для лучей, не попавших в геометрию |
| 2 | `TRIANGLES_HIT_GROUP` | `ray.rchit` | Обработка попадания в треугольник |

### Shader Binding Table (SBT)

SBT хранит указатели на скомпилированные группы шейдеров:
```
raygenAddress  → Группа 0 (ray generation)
missAddress    → Группа 1 (miss)
hitAddress     → Группа 2 (closest hit)
```

Каждая запись выровнена по `shaderGroupBaseAlignment` (обычно 32 или 64 байта).

---

## Шейдеры

### ray.rgen (Ray Generation)

```glsl
void main() {
    // Нормализуем координаты пикселя в [-1, 1]
    vec2 pixelCoord = (vec2(gl_LaunchIDEXT.xy) / vec2(gl_LaunchSizeEXT.xy)) * 2.0 - 1.0;
    pixelCoord.y = -pixelCoord.y;  // Flip Y для Vulkan

    // Луч из камеры (z = 5.0)
    vec3 rayOrigin = vec3(0.0, 0.0, 5.0);
    
    // Направление луча с учётом FOV
    float fovScale = tan(radians(60.0 * 0.5));
    vec3 rayDirView = normalize(vec3(pixelCoord * fovScale, -1.0));
    vec3 rayDirection = normalize((constants.inverseView * vec4(rayDirView, 0.0)).xyz);

    // Запускаем луч через TLAS
    traceRayEXT(tlas, gl_RayFlagsOpaqueEXT, 0xFF, 0, 0, 0,
                rayOrigin, 0.0, rayDirection, 1000.0, 0);

    // Сохраняем результат в storage image
    imageStore(resultImage, ivec2(gl_LaunchIDEXT.xy), vec4(payload.color, 1.0));
}
```

### ray.rchit (Closest Hit)

```glsl
layout(location = 0) rayPayloadInEXT RayPayload payload;
hitAttributeEXT vec2 barycentrics;

void main() {
    // Барицентрические координаты → веса интерполяции
    vec3 bary = vec3(1.0 - barycentrics.x - barycentrics.y, 
                     barycentrics.x, barycentrics.y);

    // Цвета вершин (соответствуют данным в vertexBuffer)
    vec3 colorRed   = vec3(1.0, 0.0, 0.0);
    vec3 colorGreen = vec3(0.0, 1.0, 0.0);
    vec3 colorBlue  = vec3(0.0, 0.0, 1.0);

    // Интерполируем цвет по барицентрическим координатам
    payload.color = bary.x * colorRed + 
                    bary.y * colorGreen + 
                    bary.z * colorBlue;
}
```

**Как работает интерполяция:**
- `bary.x` — вес для вершины 0 (красная)
- `bary.y` — вес для вершины 1 (зелёная)
- `bary.z` — вес для вершины 2 (синяя)

Для **первого треугольника** (индексы 0, 1, 2) это даёт градиент красный→зелёный→синий.

Для **второго треугольника** (индексы 2, 3, 0) Vulkan автоматически маппит вершины 2, 3, 0 на bary.z, bary.y, bary.x соответственно, что даёт плавный переход цветов через оба треугольника.

### ray.rmiss (Miss)

```glsl
layout(location = 0) rayPayloadInEXT RayPayload payload;

void main() {
    // Чёрный фон для лучей, не попавших в геометрию
    payload.color = vec3(0.0, 0.0, 0.0);
}
```

---

## Поток Рендеринга (recordCommandBuffer)

1. **Барьер 1**: `outputImage GENERAL → GENERAL` (подготовка к RT)
2. **Bind RT пайплайна**: `cmdBindPipeline(RAY_TRACING_KHR)`
3. **Bind дескрипторов**: storage image + TLAS
4. **Push constants**: `inverseProjection` + `inverseView` матрицы
5. **traceRaysKHR**: Запуск RT для каждого пикселя
6. **Барьер 2**: `outputImage GENERAL → TRANSFER_SRC`
7. **Барьер 3**: `swapchain UNDEFINED → TRANSFER_DST`
8. **copyImage**: Копирование из output image в swapchain
9. **Барьер 4**: `swapchain TRANSFER_DST → PRESENT_SRC`
10. **Барьер 5**: `outputImage TRANSFER_SRC → GENERAL`

---

## Связь с part06/ch21

| part06/ch21 (Graphics Pipeline) | raytracing/ch21 (RT Pipeline) |
|--------------------------------|-------------------------------|
| Vertex buffer + Index buffer | Vertex buffer + Index buffer |
| `cmdBindVertexBuffers` | `vertexAddress` → BLAS |
| `cmdBindIndexBuffer` | `indexAddress` → BLAS |
| `cmdDrawIndexed(6, 1)` | `traceRaysKHR(width, height, 1)` |
| Graphics pipeline с шейдерами | RT pipeline с rgen/rchit/rmiss |
| Рендеринг в framebuffer | Рендеринг в storage image → copy в swapchain |
| Интерполяция цветов через фрагментный шейдер | Интерполяция через барицентрические координаты |

**Ключевая идея**: В обоих случаях мы рисуем квадрат из 4 вершин и 6 индексов с градиентной раскраской. Разница только в способе рендеринга — rasterization vs ray tracing.

---

## Структура Памяти

### Vertex Buffer Layout (24 байта на вершину)

```
Vertex 0: [-2.0, -2.0, 0.0]  [1.0, 0.0, 0.0]   ← red
Vertex 1: [ 2.0, -2.0, 0.0]  [0.0, 1.0, 0.0]   ← green
Vertex 2: [ 2.0,  2.0, 0.0]  [0.0, 0.0, 1.0]   ← blue
Vertex 3: [-2.0,  2.0, 0.0]  [1.0, 1.0, 1.0]   ← white
```

### Index Buffer Layout (6 индексов, UINT32)

```
Index 0-2: [0, 1, 2]  ← Triangle 1
Index 3-5: [2, 3, 0]  ← Triangle 2
```

### Геометрия Квадрата

```
  white (3) ─────── blue (2)
     │                │
     │      △ 2       │
     │    ↗    ↖      │
     │  ↗        ↖    │
     │ ↗   diag   ↖   │
     │ ↗            ↖ │
     │  ↖        ↗    │
     │    ↖    ↗      │
     │      △ 1       │
     │                │
   red (0) ─────── green (1)
```

---

## Запуск

```bash
# Компиляция
mvn compile -pl tutorial -am

# Запуск
mvn exec:java -pl tutorial \
  -Dexec.mainClass="tutorial.vulkan.raytracing.ch21.Application" \
  -Dexec.cleanupDaemonThreads=false

# С validation layers
mvn exec:java -pl tutorial \
  -Dexec.mainClass="tutorial.vulkan.raytracing.ch21.Application" \
  -Dexec.cleanupDaemonThreads=false \
  -Dvalidation=true
```

**Требования:**
- GPU с поддержкой Vulkan Ray Tracing (RTX 2000+, RX 6000+)
- Vulkan SDK (для `glslangValidator`)
- VMA библиотека
- GLFW
