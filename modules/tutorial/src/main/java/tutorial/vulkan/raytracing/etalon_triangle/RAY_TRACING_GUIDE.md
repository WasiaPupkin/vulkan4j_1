# Vulkan Ray Tracing на Java — Полное руководство

> **Файл:** `Application.java`  
> **Технологии:** Vulkan 1.2 + Ray Tracing Pipeline, Java 25 FFM API (Project Panama), GLFW, VMA, shaderc  
> **Демо:** Отображение одного треугольника через трассировку лучей

---

## 1. Введение

Это приложение — минимальная демонстрация **аппаратного ray tracing** через Vulkan API на Java.

**Что вы видите на экране:** цветной треугольник, отрендеренный не через растеризацию (как в обычных играх), а через **трассировку лучей** — для каждого пикселя экрана генерируется луч, который проверяет пересечение с треугольником в 3D-пространстве.

**Почему это важно:** Ray tracing — основа современной фотореалистичной графики. Те же принципы лежат в основе NVIDIA RTX, path tracing в Cyberpunk 2077, и рендеринга в кино (Pixar, Weta Digital).

---

## 2. Теория: Ray Tracing vs Растеризация

### Классическая растеризация (OpenGL / традиционный Vulkan)

```
Вершины → Трансформация → Растеризация → Фрагментный шейдер → Пиксели
```

Каждый треугольник **проецируется** на экран и закрашивается. Быстро, но:
- Нет естественных отражений, теней, преломлений
- Все эффекты — "хаки" (shadow maps, screen-space reflections)

### Трассировка лучей

```
Для каждого пикселя:
  1. Создать луч из камеры через пиксель
  2. Найти ближайший объект на пути луча
  3. Вызвать шейдер для определения цвета
  4. (Опционально) Отразить/преломить луч и повторить
```

**Преимущества:**
- Физически корректные тени, отражения, преломления
- Единый алгоритм для всех эффектов
- Масштабируется до path tracing (сотни отскоков)

**Недостатки:** Тяжело для GPU — нужен аппаратный RT-core (NVIDIA RTX, AMD RDNA2+)

### Ключевые структуры Vulkan Ray Tracing

#### BLAS (Bottom Level Acceleration Structure)
Содержит **геометрию** — вершины и индексы треугольников. BLAS строится один раз и оптимизируется для быстрого поиска пересечений.

#### TLAS (Top Level Acceleration Structure)
Содержит **экземпляры** — ссылки на BLAS с матрицами трансформации. Позволяет размещать одну геометрию многократно с разными позициями/поворотами.

#### SBT (Shader Binding Table)
Таблица соответствия между стадиями луча и шейдерами:
- **Raygen** — генерирует луч для каждого пикселя
- **Miss** — выполняется, если луч ничего не задел
- **Closest Hit** — выполняется при попадании в объект

```
┌─────────────────────────────────────────────────────────────┐
│                    Shader Binding Table                      │
├──────────────┬──────────────┬───────────────────────────────┤
│  Raygen SBT  │  Miss SBT    │  Hit Group SBT                │
│  (адрес →    │  (адрес →    │  (адрес →                      │
│   shader)    │   shader)    │   closest-hit shader)          │
└──────────────┴──────────────┴───────────────────────────────┘
```

---

## 3. Архитектура приложения

### Диаграмма зависимостей ресурсов

```
                    ┌─────────────────────┐
                    │   VkInstance        │  ← Связь с Vulkan loader
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │ VkPhysicalDevice    │  ← GPU (выбирается из списка)
                    └─────────┬───────────┘
                              │
                    ┌─────────▼───────────┐
                    │   VkDevice          │  ← Логическое устройство
                    └──┬─────┬─────┬─────┘
                       │     │     │
          ┌────────────┘     │     └─────────────┐
          ▼                  ▼                   ▼
┌─────────────────┐ ┌────────────────┐ ┌──────────────────┐
│  VkSwapchainKHR │ │  VmaAllocator  │ │ VkCommandPool    │
│  (цепочка       │ │  (управление   │ │ (аллокатор       │
│   изображений)  │ │   памятью)     │ │  command buffer) │
└────────┬────────┘ └───┬─────┬─────┘ └────────┬─────────┘
         │              │     │                 │
         │    ┌─────────┘     └──────┐          │
         │    ▼                      ▼          │
         │ ┌─────────────┐  ┌──────────────┐   │
         │ │ vertexBuffer│  │ blasBuffer   │   │
         │ │ (вершины)   │  │ tlasBuffer   │   │
         │ │ (VMA)       │  │ (AS storage) │   │
         │ └──────┬──────┘  └──────┬───────┘   │
         │        │                │            │
         │        ▼                ▼            │
         │   ┌────────────────────────┐        │
         │   │ BLAS (треугольник)     │        │
         │   │ TLAS (1 экземпляр)     │        │
         │   └───────────┬────────────┘        │
         │               │                     │
         │               ▼                     │
         │   ┌───────────────────┐            │
         │   │ outputImage       │            │
         │   │ (storage image)   │            │
         │   └────────┬──────────┘            │
         │            │                       │
         │            ▼                       │
         │   ┌───────────────────┐            │
         │   │ swapChainImages   │◄───────────┘
         │   │ (рендер → экран)  │   (cmdCopyImage)
         │   └───────────────────┘
         │
         ▼
┌────────────────────────────────────────────────────────────┐
│                    Render Loop                              │
│                                                             │
│  acquireNextImage → recordCommandBuffer → queueSubmit      │
│       ↓                   ↓                     ↓           │
│  swapchain image   traceRays → copyImage   presentKHR      │
│                                                             │
└────────────────────────────────────────────────────────────┘
```

### Порядок создания ресурсов и обратный порядок уничтожения

```
Создание (→)                              Уничтожение (→)
─────────────────────                     ─────────────────────
1.  VkInstance                      18.   GLFW
2.  VkDebugUtilsMessengerEXT       17.   VkDebugUtilsMessengerEXT
3.  VkSurfaceKHR                   16.   VkSurfaceKHR
4.  VkPhysicalDevice               15.   VkDevice
5.  VkDevice                       14.   VmaAllocator (skip)
6.  VkCommandPool                  13.   Sync objects
7.  VmaAllocator                   12.   Swapchain
8.  Shaderc compiler               11.   Output image
9.  VkSwapchainKHR                 10.   Vertex buffer
10. VkImageViews                    9.   instBuffer (already freed)
11. VkVertexBuffer                  8.   AS storage buffers
12. BLAS + TLAS                     7.   Acceleration structures
13. Output image                    6.   Descriptor set/layout/pool
14. Descriptor set layout           5.   Pipeline + layout
15. Descriptor pool + set           4.   SBT buffer
16. Ray tracing pipeline            3.   Command pool
17. Sync objects (fences/semaph.)   2.   Shaderc
18. SBT buffer                      1.   VkInstance
19. Command buffers
```

> **Правило Vulkan:** ресурс можно уничтожить только после всех зависящих от него ресурсов. Поэтому порядок — строго обратный.

---

## 4. Фаза инициализации

Метод `initVulkan()` вызывает 18 функций в строгом порядке. Каждая создаёт критически важный ресурс.

### 4.1. Загрузка Vulkan (`loadStaticCommands`, `loadEntryCommands`)

**Что происходит:** Vulkan использует систему указателей на функции. Перед вызовом любой функции Vulkan нужно получить её адрес через `vkGetInstanceProcAddr`.

```
vkGetInstanceProcAddr(null, "vkCreateInstance")  → vkCreateInstance
vkGetInstanceProcAddr(instance, "vkCreateDevice") → vkCreateDevice
```

`VkStaticCommands` — функции, не требующие VkInstance (загрузчик, слои).  
`VkEntryCommands` — функции уровня Instance (создание device, перечисление GPU).

### 4.2. Создание VkInstance (`createInstance`)

**Зачем:** VkInstance — это "подключение" приложения к драйверу Vulkan. Без него нельзя ничего сделать.

**Что передаётся:**
- `VkApplicationInfo` — имя приложения, engine, версия API (1.2)
- `enabledExtensionCount` + `ppEnabledExtensionNames` — расширения (VK_KHR_get_physical_device_properties2, VK_EXT_debug_utils)
- `pNext` → `VkDebugUtilsMessengerCreateInfoEXT` — если включены validation layers

**Валидация:** Проверяет, что слой `VK_LAYER_KHRONOS_validation` доступен (если запрошен).

### 4.3. Debug Messenger (`setupDebugMessenger`)

**Зачем:** Validation layers шлют сообщения об ошибках через callback. Без него вы не увидите ошибки Vulkan.

**Уровни серьёзности:**
- `ERROR` — критическая ошибка (некорректные параметры, утечки)
- `WARNING` — предупреждение (неоптимальное использование API)
- `INFO` — информационные сообщения
- `VERBOSE` — детальная отладка

**Callback:** `Application.debugCallback()` печатает сообщения в `System.err`.

### 4.4. Создание поверхности (`createSurface`)

**Зачем:** VkSurfaceKHR — платформо-зависимое окно (Windows: HWND + HINSTANCE, Linux: X11/Wayland). Создаётся через GLFW.

### 4.5. Выбор GPU (`pickPhysicalDevice`)

**Алгоритм выбора:**
1. Перечислить все физические устройства (GPU)
2. Для каждого проверить:
   - Есть ли нужные семейства очередей (graphics + present)
   - Поддерживает ли все требуемые расширения:
     - `VK_KHR_swapchain` — рендеринг в окно
     - `VK_KHR_ray_tracing_pipeline` — ray tracing pipeline
     - `VK_KHR_acceleration_structure` — BLAS/TLAS
     - `VK_KHR_deferred_host_operations` — отложенные операции
     - `VK_AMD_device_coherent_memory` — когерентная память (AMD-specific)
3. Взять первый подходящий GPU

### 4.6. Логическое устройство (`createLogicalDevice`)

**Зачем:** VkDevice — интерфейс к GPU для создания ресурсов (буферы, изображения, пайплайны).

**Feature flags (что включаем):**

| Feature | Зачем |
|---------|-------|
| `descriptorIndexing` | Динамическая индексация дескрипторов |
| `shaderSampledImageArrayNonUniformIndexing` | Неравномерная индексация текстур |
| `bufferDeviceAddress` | Получение GPU-адреса буфера (нужно для AS build) |
| `bufferDeviceAddressCaptureReplay` | Повторное использование адресов |
| `accelerationStructure` | Поддержка BLAS/TLAS |
| `rayTracingPipeline` | Ray tracing pipeline |
| `deviceCoherentMemory` | AMD когерентная память |

### 4.7. Command Pool (`createCommandPool`)

**Зачем:** VkCommandPool — аллокатор командных буферов. Все `VkCommandBuffer` создаются из пула.

Флаг `RESET_COMMAND_BUFFER` — позволяет перезаписывать буферы каждый кадр.

### 4.8. VMA Allocator (`createVMA`)

**Зачем:** `VkDeviceMemory` — это "сырая" GPU-память. Вручную управлять ей сложно (типы памяти, выравнивание, fragmentation). VMA упрощает:

```java
// Без VMA (ручной):
vkGetBufferMemoryRequirements() → vkAllocateMemory() → vkBindBufferMemory()

// С VMA (автоматический):
vmaCreateBuffer() → готово (память выбрана автоматически)
```

### 4.9. Shader Compiler (`createShaderCompiler`)

**Зачем:** Shaderc компилирует GLSL-код в SPIR-V байткод (в рантайме). Альтернатива — pre-compiled `.spv` файлы.

Целевая среда: Vulkan 1.2.

### 4.10. Swapchain (`createSwapchain`)

**Зачем:** VkSwapchainKHR — цепочка изображений для двойной/тройной буферизации.

**Параметры выбираются автоматически:**
- Формат: R8G8B8A8_UNORM (sRGB) — стандартный 8-bit цвет
- Present mode: MAILBOX (если доступен) — тройная буферизация без tearing, FIFO — fallback
- Extent: размер окна, с учётом min/max от GPU
- Image count: `minImageCount + 1` (дополнительный буфер для плавности)

Usage flags: `COLOR_ATTACHMENT | TRANSFER_DST` — swapchain image будет и целью рендеринга, и получателем копии.

### 4.11. Image Views (`createImageViews`)

**Зачем:** VkImage "сырое" изображение. VkImageView — "окно" в него с определённым форматом,_aspect mask, слоями. Pipeline требует image views, не raw images.

### 4.12. Vertex Buffer (`createVertexBuffer`)

**Данные:** Один треугольник из 3 вершин (x, y, z):

```
     (0.0, 1.67, 3.0)    ← Вершина сверху
          ▲
         / \
        /   \
       /     \
(-1.0,-0.33,3.0)──(1.0,-0.33,3.0)  ← База треугольника
```

Треугольник находится на Z=3 (перед камерой, которая на Z=5) и центрирован по Y.

**Создание через VMA:**
- Usage: `SHADER_DEVICE_ADDRESS | ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR`
- VMA flags: `HOST_ACCESS_SEQUENTIAL_WRITE` — CPU пишет один раз
- Memory: `HOST_VISIBLE | HOST_COHERENT` — видна CPU, без явного flush

### 4.13. Acceleration Structures (`createAccelerationStructures`)

Самый сложный этап. Разделён на два подметода:

#### 4.13.1. BLAS (`createAndBuildBlas`)

**Что такое BLAS:** Ускоренная структура, содержащая треугольники для поиска пересечений.

**Шаги:**
1. **Создать индексный буфер** — `[0, 1, 2]` (3 индекса)
2. **Создать transform буфер** — identity matrix 3×4 (12 float)
3. **deviceWaitIdle()** — убедиться, что CPU-записи завершены
4. **Настроить геометрию:**
   ```
   VkAccelerationStructureGeometryTrianglesDataKHR:
     vertexFormat = R32G32B32_SFLOAT  (3 floats на вершину)
     vertexData   = адрес vertexBuffer
     vertexStride = 12 bytes           (3 × 4)
     maxVertex    = 2                  (последний индекс)
     indexType    = UINT32
     indexData    = адрес indexBuffer
     transformData = 0                 (NULL = без трансформации)
   ```
5. **Получить размеры:** `getAccelerationStructureBuildSizesKHR()` → `accelerationStructureSize`, `buildScratchSize`
6. **Выделить буфер:** `createAccelerationStructureBuffer()` с ручным `VkDeviceMemory`
7. **Создать AS:** `createAccelerationStructureKHR()`
8. **Построить AS:** `cmdBuildAccelerationStructuresKHR()` в transient command buffer
9. **Очистить временные буферы:** index, transform, scratch

#### 4.13.2. TLAS (`createAndBuildTlas`)

**Что такое TLAS:** Ускоренная структура, содержащая **экземпляры** BLAS с трансформациями.

**VkAccelerationStructureInstanceKHR (64 байта):**
```
Offset  Bytes  Поле
──────────────────────────────────────────────
  0      48     transform[12]  — 3×4 матрица трансформации
 48       4     packed: instanceCustomIndex(24 бита) | mask(8 бит)
 52       4     packed: instanceShaderBindingTableRecordOffset(24 бита) | flags(8 бит)
 56       8     accelerationStructureReference — device address BLAS
```

**Наш экземпляр:**
- transform = identity (без смещения)
- customIndex = 0, mask = 0xFF (виден для всех ray mask)
- SBT offset = 0, flags = TRIANGLE_FACING_CULL_DISABLE
- blasAddress = device address BLAS (получен через `getAccelerationStructureDeviceAddressKHR`)

### 4.14. Output Image (`createOutputImage`)

**Зачем:** Storage image, в которую raygen shader пишет цвет каждого пикселя.

- Формат: `R8G8B8A8_UNORM` (8 бит на канал)
- Usage: `STORAGE | TRANSFER_SRC | TRANSFER_DST`
  - STORAGE — shader пишет в него
  - TRANSFER_SRC — копирует из него в swapchain
  - TRANSFER_DST — можно очищать

### 4.15. Descriptor Set Layout (`createDescriptorSetLayout`)

**Определяет, какие ресурсы доступны шейдерам:**

| Binding | Type | Stage | Что |
|---------|------|-------|-----|
| 0 | STORAGE_IMAGE | RAYGEN, CLOSEST_HIT | outputImage |
| 1 | ACCELERATION_STRUCTURE | RAYGEN, CLOSEST_HIT | TLAS |

### 4.16-4.18. Descriptor Pool, Set, Pipeline

**Descriptor Pool:** Аллокатор для descriptor set'ов. Размер: `MAX_FRAMES_IN_FLIGHT × 2` для каждого типа.

**Descriptor Set:** Фактическое связывание ресурсов:
- Binding 0 → `outputImageView` (storage image)
- Binding 1 → `tlas` (acceleration structure)

**Ray Tracing Pipeline:**
- 3 shader stage: raygen, miss, closest-hit
- 3 shader group: GENERAL(raygen), GENERAL(miss), TRIANGLES_HIT_GROUP(closest-hit)
- Pipeline layout: 1 descriptor set + push constants (128 байт)

### 4.19-4.21. Sync, SBT, Command Buffers

**Sync Objects:** 2 кадра в полёте → 2 × (imageAvailable + renderFinished + fence)

**SBT (Shader Binding Table):**
```
┌──────────────────────────────────────────────┐
│  Raygen Region  │  Miss Region  │ Hit Region │
│  (32 bytes)     │  (32 bytes)   │ (32 bytes) │
│  handle[0]      │  handle[1]    │ handle[2]  │
└──────────────────────────────────────────────┘
    ↑                  ↑                ↑
raygenAddress     missAddress      hitAddress
```

Каждый handle — 32 байта (`shaderGroupHandleSize`). Регионы выровнены на 64 байта (`shaderGroupBaseAlignment`).

---

## 5. Фаза рендеринга (drawFrame)

Каждый кадр проходит через конвейер синхронизации:

```
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  CPU: Frame N    │    │  CPU: Frame N+1  │    │  CPU: Frame N+2  │
│  (record cmds)   │───▶│  (record cmds)   │───▶│  (wait for fence)│
└────────┬─────────┘    └────────┬─────────┘    └────────┬─────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  GPU: Frame N    │    │  GPU: Frame N+1  │    │  (ждёт fence N)  │
│  (trace+copy)    │    │  (trace+copy)    │    │                  │
└──────────────────┘    └──────────────────┘    └──────────────────┘
```

**MAX_FRAMES_IN_FLIGHT = 2:** Пока GPU обрабатывает кадр N, CPU уже готовит кадр N+1.

### Пошаговый разбор drawFrame()

```
Шаг 1: waitForFences()       — Ждём, пока GPU завершил этот frame index
Шаг 2: acquireNextImageKHR()  — Получаем следующий swapchain image
Шаг 3: resetFences()          — Сбрасываем fence для нового кадра
Шаг 4: beginCommandBuffer()   — Начинаем записывать команды
Шаг 5: recordCommandBuffer()  — Записываем ray tracing команды (см. раздел 6)
Шаг 6: endCommandBuffer()     — Заканчиваем запись
Шаг 7: queueSubmit()          — Отправляем на GPU (сигнал: renderFinishedSemaphore)
Шаг 8: queuePresentKHR()      — Показываем на экране (ждёт: renderFinishedSemaphore)
Шаг 9: currentFrame = (currentFrame + 1) % 2  — Следующий frame index
```

### Что происходит при resize

```
1. glfw.setFramebufferSizeCallback → framebufferResized = true
2. drawFrame: queuePresentKHR возвращает SUBOPTIMAL_KHR или OUT_OF_DATE_KHR
3. needsSwapchainRecreation = true
4. mainLoop: recreateSwapChain()
   ├── deviceWaitIdle()              — GPU должен завершить всё
   ├── cleanupSwapChain()            — Уничтожить старые ресурсы
   │   ├── destroyImageView() × N    — Image views
   │   ├── destroyImage()            — Output image
   │   ├── destroySwapchainKHR()     — Swapchain
   │   └── swapchainArena.close()    — Освободить arena-память
   ├── createSwapchain()             — Новый swapchain (новый размер)
   ├── createImageViews()            — Новые image views
   ├── createOutputImage()           — Новый output image
   ├── transitionOutputImageToGeneral()
   └── recreateDescriptorPool() + createDescriptorSet() — Новые дескрипторы
```

---

## 6. recordCommandBuffer — Детальный разбор

Это "сердце" рендеринга. Каждая команда записывается в command buffer для выполнения GPU.

### 6.1. Определение SBT регионов

```java
var raygenRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
        .deviceAddress(raygenAddress)   // Где в GPU-памяти лежит raygen handle
        .stride(sbtRecordSize)          // Размер одной записи (32 байта)
        .size(sbtRecordSize);           // Общий размер региона
```

GPU использует эти адреса, чтобы найти нужный shader для каждой стадии луча.

### 6.2. Barrier 1: outputImage GENERAL → GENERAL

```java
var rayTraceBarrier = createImageBarrier(arena, outputImage,
        VkImageLayout.GENERAL, VkImageLayout.GENERAL,
        VkAccessFlags.SHADER_WRITE, VkAccessFlags.SHADER_WRITE);
```

**Зачем:** Синхронизация между кадрами. Ждём пока предыдущий кадр закончил писать в outputImage, прежде чем писать снова.

### 6.3. Bind Pipeline + Descriptor Set

```java
deviceCommands.cmdBindPipeline(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, rayTracingPipeline);
deviceCommands.cmdBindDescriptorSets(cmd, ..., descriptorSet, ...);
```

Говорим GPU: "используй этот pipeline и эти ресурсы (outputImage + TLAS)".

### 6.4. Вычисление матриц камеры

```
Растеризация:        vertex × projection × view = screen_pixel
Трассировка лучей:   screen_pixel × invProjection × invView = ray_direction
```

**Почему инвертированные?** В растеризации мы проецируем 3D → 2D. В ray tracing мы делаем обратное: для каждого 2D пикселя находим 3D направление луча.

```java
Matrix4f projection = new Matrix4f().setPerspective(fovRadians, aspectRatio, 0.1f, 100.0f, true);
// near=0.1, far=100.0 — лучи трейсятся только в этом диапазоне
// true = left-handed (Vulkan: Z растёт от камеры в экран)

Matrix4f view = new Matrix4f();
view.lookAt(
    new Vector3f(0.0f, 0.0f, 5.0f),   // eye: камера на Z=5
    new Vector3f(0.0f, 0.0f, 0.0f),   // center: смотрит в начало координат
    new Vector3f(0.0f, 1.0f, 0.0f)    // up: Y — вверх
);

Matrix4f invProjection = new Matrix4f(projection).invert();
Matrix4f invView = new Matrix4f(view).invert();
```

### 6.5. Push Constants

```java
float[] pushConstants = new float[32];
invProjection.get(pushConstants, 0);       // [0..15]  — inverse projection
invView.get(pushConstants, 16);           // [16..31] — inverse view

// Копируем в нативную память (FFM требует MemorySegment)
var nativeMemory = arena.allocate(ValueLayout.JAVA_FLOAT, 32);
for (int i = 0; i < 32; i++) {
    nativeMemory.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, pushConstants[i]);
}

deviceCommands.cmdPushConstants(cmd, pipelineLayout,
    VkShaderStageFlags.RAYGEN_KHR, 0, 128, nativeMemory);
```

**Push constants** — быстрый способ передать данные шейдеру (до 128 байт). Дешевле descriptor set, но маленький размер.

### 6.6. cmdTraceRaysKHR — Запуск лучей!

```java
deviceCommands.cmdTraceRaysKHR(cmd,
    raygenRegion,   // Raygen shader: генерирует лучи
    missRegion,     // Miss shader: что делать при промахе
    hitRegion,      // Hit shader: что делать при попадании
    callableRegion, // Callable shaders: не используются (0,0,0)
    width, height, 1);  // 1 ray per pixel = width × height rays
```

**Что происходит "под капотом":**
```
Для каждого пикселя (x, y):
  1. Raygen shader:
     ├── Прочитать invProjection и invView из push constants
     ├── Нормализовать (x, y) в [-1, 1] (NDC координаты)
     ├── Умножить на invProjection → direction в camera space
     ├── Умножить на invView → direction в world space
     └── Запустить луч: origin = camera position, direction = computed

  2. Аппаратный RT-core:
     ├── Пройти TLAS → найти экземпляр
     ├── Пройти BLAS → найти ближайший треугольник
     └── Вернуть t (расстояние), barycentric coords, geometry info

  3. Closest Hit shader (если попало):
     ├── Вычислить цвет (в нашем демо — красный)
     └── Записать в outputImage[x][y]

  4. Miss shader (если промах):
     ├── Вернуть фоновый цвет (в нашем демо — чёрный)
     └── Записать в outputImage[x][y]
```

### 6.7. Копирование в swapchain

После ray tracing результат в outputImage (GENERAL layout). Нужно скопировать в swapchain image:

```
Barrier 2: outputImage GENERAL → TRANSFER_SRC_OPTIMAL  (готовим к чтению)
Barrier 3: swapchainImage UNDEFINED → TRANSFER_DST_OPTIMAL (готовим к записи)
cmdCopyImage: outputImage → swapchainImage
Barrier 4: swapchainImage TRANSFER_DST → PRESENT_SRC     (готовим к показу)
Barrier 5: outputImage TRANSFER_SRC → GENERAL            (готовим к следующему кадру)
```

---

## 7. Синхронизация

### Fences (CPU ↔ GPU)

```
CPU: submit(frame 0) → signal(fence 0) → wait(fence 0) → submit(frame 0 again)
GPU:                    [executing...]  → fence signaled
```

Fence сигнализирует, когда GPU завершил работу над кадром. CPU ждёт перед записью нового кадра на тот же index.

### Semaphores (GPU ↔ GPU)

```
imageAvailableSemaphore:  swapchain image готов → можно рендерить
renderFinishedSemaphore:  рендеринг завершён → можно показывать
```

Semaphores работают полностью на GPU — CPU не ждёт.

### Frame-in-Flight Ring Buffer

```
Frame 0:  [CPU: record] → [GPU: execute] → [fence signaled] → [CPU: reuse]
Frame 1:              [CPU: record] → [GPU: execute] → [fence signaled]
Frame 0:                                                   [CPU: record again]
```

MAX_FRAMES_IN_FLIGHT = 2 означает: пока GPU обрабатывает frame 0, CPU готовит frame 1.

---

## 8. Управление памятью

### Три уровня управления памятью

| Уровень | Что | Когда |
|---------|-----|-------|
| **VkDeviceMemory** | Сырая GPU-память | Для AS buffers (ручной контроль) |
| **VMA** | Высокоуровневый аллокатор | Для vertex buffer, SBT, output image |
| **Arena (FFM)** | Временная нативная память | Для структур Vulkan (CreateInfo и т.д.) |

### Arena в FFM API

```java
// Arena.ofConfined() — создаёт временную арену, освобождается при close()
try (var arena = Arena.ofConfined()) {
    var info = VkBufferCreateInfo.allocate(arena);  // Память в арене
    // ... использовать info
} // arena.close() → вся память освобождена

// Arena.ofShared() — можно закрывать явно (используется для swapchain)
private Arena swapchainArena = Arena.ofShared();

// При recreate swapchain:
swapchainArena.close();          // Освободить старые указатели
swapchainArena = Arena.ofShared();  // Создать новую
```

### Почему swapchainArena = Arena.ofShared()?

`Arena.ofAuto()` управляется GC и **не поддерживает close()**. При resize старые указатели (swapChainImages, swapChainExtent) остаются в памяти до GC. `Arena.ofShared()` позволяет явное освобождение.

---

## 9. Шейдеры

### Ray Generation Shader (`ray.rgen`)

```glsl
// Вход: push constants (invProjection[16] + invView[16])
// Выход: запись цвета в outputImage

void main() {
    // 1. Получить координату пикселя в NDC [-1, 1]
    vec2 pixel = vec2(gl_LaunchIDEXT.xy) / vec2(gl_LaunchSizeEXT.xy) * 2.0 - 1.0;

    // 2. Направление луча в camera space
    vec4 direction = invProjection * vec4(pixel, 0.0, 1.0);
    direction = invView * direction;

    // 3. Запустить луч
    rayPayloadEXT payload;
    traceRayEXT(tlas, ..., origin, direction, ...);

    // 4. Записать результат в storage image
    imageStore(outputImage, ivec2(gl_LaunchIDEXT.xy), payload.color);
}
```

### Miss Shader (`ray.rmiss`)

```glsl
// Вызывается, если луч ничего не задел
void main() {
    rayPayloadEXT payload;
    payload.color = vec4(0.0, 0.0, 0.0, 1.0);  // Чёрный фон
}
```

### Closest Hit Shader (`ray.rchit`)

```glsl
// Вызывается при попадании в ближайший объект
void main() {
    rayPayloadEXT payload;
    // В нашем демо — просто красный цвет
    payload.color = vec4(1.0, 0.0, 0.0, 1.0);
}
```

---

## 10. Камера и матрицы

### Текущая камера (статичная)

```
Позиция: (0, 0, 5)     ← На оси Z, перед треугольником
Цель:    (0, 0, 0)     ← Смотрит в начало координат
Вверх:   (0, 1, 0)     ← Y — вверх
FOV:     60°           ← Стандартный угол обзора
Near:    0.1           ← Ближняя плоскость отсечения
Far:     100.0         ← Дальняя плоскость
```

**Почему изменение lookAt не видно:** Камера пересоздаётся каждый кадр в `recordCommandBuffer()`. Чтобы двигать камеру, нужно:
1. Добавить поля `cameraPosition`, `cameraTarget` в класс
2. Обновлять их из GLFW callbacks (клавиатура/мышь)
3. Использовать в `view.lookAt(cameraPosition, cameraTarget, cameraUp)`

### Как работают матрицы

```
Мировые координаты ──────▶ Camera space ──────▶ Clip space ──────▶ NDC
           (view)                 (projection)       (/w)

Обратный процесс (ray tracing):
NDC ──────▶ Clip space ──────▶ Camera space ──────▶ Мировые координаты
         (× invProjection)          (× invView)
```

---

## 11. Полезные ссылки

- [Vulkan Specification](https://www.khronos.org/vulkan/) — официальная спецификация
- [Vulkan Ray Tracing Final Specification](https://www.khronos.org/blog/ray-tracing-in-vulkan) — спецификация RT pipeline
- [Sascha Willems Examples](https://github.com/SASCHAWILLEMS/Vulkan) — эталонные примеры на C++
- [Vulkan Tutorial](https://vulkan-tutorial.com/) — пошаговое обучение
- [JOML Documentation](https://joml-ci.github.io/JOML/) — математическая библиотека

---

## 12. Запуск

```bash
# Базовый запуск
mvn exec:java -Dexec.mainClass="tutorial.vulkan.raytracing.Application"

# С validation layers
mvn exec:java -Dexec.mainClass="tutorial.vulkan.raytracing.Application" -Dvalidation
```

> **Требуется:** JDK 25+, GPU с поддержкой Vulkan ray tracing (NVIDIA RTX, AMD RDNA2+)

---
