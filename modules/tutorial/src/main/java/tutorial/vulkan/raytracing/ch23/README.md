# Chapter 23 — Ray Tracing: Rotating Colored Quad

## Обзор

Это приложение рендерит **вращающийся цветной квадрат** с использованием **ray tracing** вместо растеризации. Оно является ray tracing-аналогом графического пайплайна из `part07/ch23/Main.java` — тот же объект, те же трансформации, та же камера, но совершенно другой подход к рендерингу.

> **Ключевое отличие**: вместо вершинного шейдера → растеризатора → фрагментного шейдера, здесь мы генерируем лучи для каждого пикселя и проверяем их пересечение с геометрией через acceleration structures.

---

## Архитектура приложения

```
┌─────────────────────────────────────────────────────────────────┐
│                         Application                             │
├─────────────────────────────────────────────────────────────────┤
│  Window (GLFW) → Vulkan Instance → Physical Device → Device    │
│        ↓                                                        │
│  Swapchain → Output Image → Sync Objects                       │
│        ↓                                                        │
│  Ray Tracing Pipeline:                                         │
│    Vertex/Index Buffers (quad geometry)                        │
│    BLAS (Bottom-Level Acceleration Structure)                  │
│    TLAS (Top-Level Acceleration Structure)                     │
│    Ray Tracing Pipeline (rgen → rmiss → rchit)                │
│    Shader Binding Table (SBT)                                  │
│    Descriptor Set (storage image + acceleration structure)     │
│        ↓                                                        │
│  Draw Loop: trace rays → outputImage → swapchain               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Инициализация Vulkan

### 1. Создание Instance

Создаётся `VkInstance` с **Vulkan 1.2** — это минимальная версия для ray tracing extensions. Включаются расширения:
- `VK_KHR_get_physical_device_properties2` — обязательно для ray tracing
- `VK_EXT_debug_utils` — для валидационных слоёв (опционально)

### 2. Выбор физического устройства

Ищется GPU, поддерживающий **все** необходимые расширения:
- `VK_KHR_swapchain` — для вывода на экран
- `VK_KHR_ray_tracing_pipeline` — сам ray tracing пайплайн
- `VK_KHR_acceleration_structure` — acceleration structures
- `VK_KHR_deferred_host_operations` — для отложенных операций
- `VK_KHR_buffer_device_address` — для получения адресов буферов
- `VK_AMD_device_coherent_memory` — для когерентной памяти

Также запрашиваются фичи через `VkPhysicalDeviceVulkan12Features` и специфичные для ray tracing структуры:
- `bufferDeviceAddress` + `bufferDeviceAddressCaptureReplay`
- `accelerationStructure` + `accelerationStructureCaptureReplay`
- `rayTracingPipeline` + `rayTracingPipelineShaderGroupHandleCaptureReplay`
- `deviceCoherentMemory`

После создания устройства извлекаются **ray tracing properties**:
- `shaderGroupHandleSize` — размер handle группы шейдеров (обычно 32 байта)
- `shaderGroupHandleAlignment` — выравнивание handle (обычно 32)
- `shaderGroupBaseAlignment` — базовое выравнивание SBT записи (обычно 256)

Эти значения критичны для корректного построения Shader Binding Table.

### 3. Создание логического устройства

Создаётся `VkDevice` с ray tracing extension-ами и фичами. Получаются указатели на графическую и present очереди.

### 4. VMA (Vulkan Memory Allocator)

Инициализируется VMA с флагом `BUFFER_DEVICE_ADDRESS` — это позволяет получать GPU-адреса буферов, что необходимо для acceleration structures.

---

## Геометрия

### Вершинный буфер

Создаётся квадрат из **4 вершин** с данными `vec2 pos + vec3 color` (stride = 20 байт):

```
  (-0.5, 0.5) white ●────────────● blue (0.5, 0.5)
                  │            /  │
                  │          /    │
                  │        /      │
                  │      /        │
                  │    /          │
                  │  /            │
  (-0.5,-0.5)red ●────────────● green (0.5,-0.5)
```

| Индекс | Позиция | Цвет |
|--------|---------|------|
| 0 | (-0.5, -0.5) | (1, 0, 0) — красный |
| 1 | (0.5, -0.5) | (0, 1, 0) — зелёный |
| 2 | (0.5, 0.5) | (0, 0, 1) — синий |
| 3 | (-0.5, 0.5) | (1, 1, 1) — белый |

Квадрат разбит на 2 треугольника через индексный буфер (UINT16):
- Треугольник 0: индексы `0, 1, 2` (красный → зелёный → синий)
- Треугольник 1: индексы `2, 3, 0` (синий → белый → красный)

Буфер создаётся через **VMA** с флагами:
- `VERTEX_BUFFER` — использование как буфера вершин
- `SHADER_DEVICE_ADDRESS` — для получения GPU-адреса в BLAS
- `ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR` — вход для построения AS

### Индексный буфер

Аналогично вершинному, содержит 6 индексов типа `uint16`. Флаги те же, плюс `INDEX_BUFFER`.

---

## Acceleration Structures

Ray tracing в Vulkan требует построения **иерархических структур ускорения** (Acceleration Structures), которые GPU использует для быстрого поиска пересечений лучей с геометрией.

### BLAS (Bottom-Level Acceleration Structure)

BLAS содержит **сырую геометрию** — вершины и индексы квада. Процесс построения:

1. Получаем GPU-адреса вершинного и индексного буферов через `getBufferDeviceAddress`
2. Заполняем `VkAccelerationStructureGeometryTrianglesDataKHR`:
   - `vertexFormat = R32G32_SFLOAT` (vec2 позиция)
   - `vertexStride = 20` (байт между вершинами)
   - `maxVertex = 3` (максимальный индекс в буфере)
   - `indexType = UINT16`
3. Создаём `VkAccelerationStructureBuildGeometryInfoKHR` с типом `BOTTOM_LEVEL`
4. Запрашиваем **размеры** через `getAccelerationStructureBuildSizesKHR` — Vulkan говорит, сколько памяти нужно для самой структуры и scratch-буфера
5. Создаём буфер для BLAS через VMA с `ACCELERATION_STRUCTURE_STORAGE_KHR`
6. Создаём scratch-буфер (временная память для построения)
7. Запускаем `cmdBuildAccelerationStructuresKHR` — GPU строит BLAS
8. Получаем адрес BLAS через `getAccelerationStructureDeviceAddressKHR`

### TLAS (Top-Level Acceleration Structure)

TLAS содержит **инстансы** BLAS с трансформациями. В нашем случае — один инстанс с identity матрицей (квадрат не смещён и не повёрнут в мировом пространстве; вращение применяется через push constants в шейдере).

Процесс:
1. Создаём `VkAccelerationStructureInstanceKHR` (64 байта):
   - `transform` — 3×4 matrix identity (12 floats)
   - `customIndexAndMask` — custom index 0, mask 0xFF (все лучи проходят)
   - `sbtOffsetAndFlags` — SBT offset 0, флаг `TRIANGLE_FACING_CULL_DISABLE`
   - `accelerationStructureReference` — адрес BLAS
2. Буфер инстанса создаётся **вручную** (не через VMA), т.к. нужен гарантированный 16-байтовый выравни для device address
3. Заполняем `VkAccelerationStructureGeometryInstancesDataKHR`
4. Строим TLAS аналогично BLAS через `cmdBuildAccelerationStructuresKHR`

---

## Ray Tracing Pipeline

### Шейдеры

Ray tracing pipeline состоит из трёх типов шейдеров:

#### 1. Ray Generation (`ray.rgen`)
Выполняется **один раз на каждый пиксель**. Его задача — сгенерировать луч из камеры и запустить трассировку.

```glsl
// Push constants содержат:
//   model — матрица вращения квада
//   inverseProjection — обратная матрица проекции
//   inverseView — обратная матрица вида
```

Алгоритм:
1. Нормализуем координаты пикселя в [-1, 1]
2. Вычисляем направление луча в space камеры с учётом FOV (45°)
3. Трансформируем направление в мировой空间 через `inverseView`
4. **Трансформируем луч в локальное пространство объекта** через `transpose(model)` — это создаёт эффект вращения геометрии
5. Вызываем `traceRayEXT(tlas, ...)` — GPU трассирует луч через TLAS/BLAS
6. Результат (цвет) записываем в `resultImage` через `imageStore`

#### 2. Miss Shader (`ray.rmiss`)
Вызывается когда луч **ни во что не попал**. Возвращает чёрный цвет.

#### 3. Closest Hit Shader (`ray.rchit`)
Вызывается когда луч **попал в ближайший треугольник**. Его задача — вычислить цвет в точке попадания.

Алгоритм:
1. Получаем барицентрические координаты из `hitAttributeEXT vec2 barycentrics`
2. Вычисляем полные барицентрические координаты: `bary = (1-u-v, u, v)`
3. Определяем, в какой треугольник попали через `gl_PrimitiveID`:
   - `gl_PrimitiveID == 0` → интерполируем красный, зелёный, синий
   - `gl_PrimitiveID == 1` → интерполируем синий, белый, красный
4. Записываем интерполированный цвет в `payload.color`

### Pipeline Layout

Создаётся `VkPipelineLayout` с:
- **1 descriptor set layout** — 2 binding-а:
  - binding 0: `STORAGE_IMAGE` — изображение для записи результата (rgen + rchit)
  - binding 1: `ACCELERATION_STRUCTURE_KHR` — TLAS (rgen + rchit)
- **1 push constant range** — 48 floats (3 × mat4) для raygen шейдера

### Создание пайплайна

1. Компилируем GLSL → SPIR-V через `glslangValidator` (runtime)
2. Создаём `VkPipelineShaderStageCreateInfo` для каждого шейдера:
   - Stage 0: `RAYGEN_KHR` → ray.rgen
   - Stage 1: `MISS_KHR` → ray.rmiss
   - Stage 2: `CLOSEST_HIT_KHR` → ray.rchit
3. Создаём `VkRayTracingShaderGroupCreateInfoKHR` — 3 группы:
   - Группа 0: GENERAL, generalShader=0 (raygen)
   - Группа 1: GENERAL, generalShader=1 (miss)
   - Группа 2: TRIANGLES_HIT_GROUP, closestHitShader=2 (rchit)
4. Вызываем `createRayTracingPipelinesKHR` с флагом `CAPTURE_REPLAY`

---

## Shader Binding Table (SBT)

SBT — это таблица, связывающая shader groups с их данными. Для нашего пайплайна:

```
┌──────────────────────────────────────────────┐
│           Shader Binding Table               │
├──────────────────────────────────────────────┤
│ Raygen Record  │ handle[32] │ padding...    │ ← shaderGroupBaseAlignment (256 байт)
├──────────────────────────────────────────────┤
│ Miss Record    │ handle[32] │ padding...    │ ← shaderGroupBaseAlignment (256 байт)
├──────────────────────────────────────────────┤
│ Hit Record     │ handle[32] │ padding...    │ ← shaderGroupBaseAlignment (256 байт)
└──────────────────────────────────────────────┘
```

Процесс:
1. Получаем handles через `getRayTracingShaderGroupHandlesKHR` — Vulkan возвращает сырые байты для каждой группы
2. Выравниваем размер каждого handle до `shaderGroupHandleAlignment`
3. Создаём SBT-буфер через VMA с флагом `SHADER_BINDING_TABLE_KHR`
4. Записываем handles в буфер с отступом `shaderGroupBaseAlignment` между записями
5. Получаем device address для каждой записи: `raygenAddress`, `missAddress`, `hitAddress`

---

## Descriptor Set

Descriptor set связывает ресурсы с шейдерами:

| Binding | Тип | Ресурс | Используется в |
|---------|-----|--------|---------------|
| 0 | STORAGE_IMAGE | `outputImage` (R8G8B8A8_UNORM) | rgen, rchit |
| 1 | ACCELERATION_STRUCTURE_KHR | TLAS | rgen, rchit |

Storage image — это изображение, в которое ray generation шейдер записывает цвет каждого пикселя. Оно создаётся как `VkImage` с usage `STORAGE | TRANSFER_SRC | TRANSFER_DST` и форматом `R8G8B8A8_UNORM`.

---

## Камера и трансформации

### Параметры камеры
- **FOV**: 45°
- **Позиция**: (2, 2, 2)
- **Направление взгляда**: (0, 0, 0)
- **Вектор вверх**: (0, 0, 1) — Vulkan Z-up
- **Ближняя плоскость**: 0.1
- **Дальняя плоскость**: 10.0

### Матрицы в push constants

Каждый кадр вычисляются три матрицы:

1. **Model** — вращение вокруг оси Z на 90°/сек:
   ```java
   float angleRad = Math.toRadians(90.0f * time);
   Matrix4f model = new Matrix4f().rotate(angleRad, new Vector3f(0, 0, 1));
   ```

2. **Inverse Projection** — обратная матрица проекции:
   ```java
   Matrix4f proj = new Matrix4f().perspective(fov, aspect, near, far, true);
   proj.m11(-proj.m11()); // Flip Y для Vulkan
   Matrix4f invProj = proj.invert();
   ```

3. **Inverse View** — обратная матрица вида:
   ```java
   Matrix4f view = new Matrix4f().lookAt(pos, lookAt, up);
   Matrix4f invView = view.invert();
   ```

В ray generation шейдере:
- `invView[3].xyz` — извлекается позиция камеры (4-й столбец обратной view матрицы)
- `invView * rayDirView` — трансформация направления луча из view space в world space
- `transpose(model)` — трансформация луча в локальное пространство объекта (для вращения)

---

## Рендеринг: Draw Frame

Каждый кадр проходит через цикл:

### 1. Acquire swapchain image
```
acquireNextImageKHR → imageIndex
```

### 2. Record command buffer

#### Dispatch ray tracing:
```
Barrier: outputImage GENERAL → GENERAL
cmdBindPipeline(RAY_TRACING_KHR)
cmdBindDescriptorSets
cmdPushConstants(model, invProj, invView)
cmdTraceRaysKHR(raygen, miss, hit, callable, width, height, 1)
```

#### Image transfers:
```
Barrier: outputImage GENERAL → TRANSFER_SRC
Barrier: swapchain UNDEFINED → TRANSFER_DST
cmdCopyImage(outputImage → swapchainImage)
Barrier: swapchain TRANSFER_DST → PRESENT_SRC
Barrier: outputImage TRANSFER_SRC → GENERAL
```

### 3. Submit и Present
```
queueSubmit(graphicsQueue, cmdBuffer, fence)
queuePresentKHR(presentQueue, swapchainImage)
```

---

## Swapchain и Output Image

### Почему не напрямую в swapchain?

Ray tracing шейдер **не может** напрямую записывать в swapchain изображения, т.к.:
1. Swapchain изображения имеют формат, оптимальный для present (часто B8G8R8A8_SRGB)
2. Ray tracing шейдеру нужен `STORAGE` usage, а swapchain изображения его не имеют
3. Нужно контролировать layout transitions

### Решение: Output Image

Создаётся промежуточное изображение `R8G8B8A8_UNORM` с usage `STORAGE | TRANSFER_SRC | TRANSFER_DST`:
1. Ray generation шейдер записывает в него пиксели
2. Копируется в swapchain изображение через `cmdCopyImage`
3. Swapchain изображение переходит в `PRESENT_SRC`

### Layout transitions

Изображения проходят через цепочку layout-переходов каждый кадр:

```
outputImage:  UNDEFINED → GENERAL → TRANSFER_SRC → GENERAL
swapchain:    UNDEFINED → TRANSFER_DST → PRESENT_SRC
```

Каждый переход — это `VkImageMemoryBarrier` с правильными `srcAccessMask` и `dstAccessMask`.

---

## Синхронизация

Используется **двойная буферизация** (`MAX_FRAMES_IN_FLIGHT = 2`):

| Объект | Назначение |
|--------|-----------|
| `imageAvailableSemaphores[i]` | Сигнализирует, что swapchain image готов |
| `renderFinishedSemaphores[i]` | Сигнализирует, что рендеринг завершён |
| `inFlightFences[i]` | CPU ждёт, пока GPU закончит работу с предыдущим кадром |

Цикл:
```
WaitForFence(inFlightFences[currentFrame]) → CPU не обгоняет GPU
ResetFence(inFlightFences[currentFrame])
AcquireNextImage → signal: imageAvailableSemaphore
Submit → wait: imageAvailable → signal: renderFinished
Present → wait: renderFinished
currentFrame = (currentFrame + 1) % 2
```

---

## Обработка resize окна

При изменении размера окна:
1. `framebufferResized = true` (через callback GLFW)
2. `mainLoop` выставляет `needsSwapchainRecreation = true`
3. `recreateSwapChain()` выполняет:
   - `deviceWaitIdle` — ждём завершения GPU
   - `cleanupSwapChain` — уничтожаем старые изображения
   - `createSwapchain` → `createImageViews` → `createOutputImage`
   - `transitionOutputImageToGeneral` —初始 layout
   - Пересоздаём descriptor pool и set (т.к. изображение изменилось)

---

## Связь с графическим пайплайном (part07/ch23)

| Аспект | Графический (ch23) | Ray Tracing (ch23) |
|--------|-------------------|-------------------|
| Геометрия | 4 вершины, vec2+vec3 | Те же 4 вершины |
| Индексы | 6 × uint16 | Те же 6 индексов |
| Цвета вершин | красный, зелёный, синий, белый | Те же |
| Модель | Вращение 90°/сек вокруг Z | То же (через push constants) |
| Камера | lookAt(2,2,2 → 0,0,0) | Та же |
| FOV | 45° | 45° |
| Near/Far | 0.1 / 10.0 | Те же |
| Интерполяция цвета | Растеризатор (вершинный → фрагментный) | Барицентрические координаты в rchit |

---

## Структура шейдеров

```
ray.rgen (Ray Generation)
    │
    ├─ Вычисляет луч из камеры через пиксель
    ├─ Трансформирует луч в model space
    └─ traceRayEXT(tlas) ─────────────┐
                                      │
    ray.rmiss ◄───── (луч промахнулся) │
    │                                 │
    └─ payload.color = чёрный         │
                                      │
    ray.rchit ◄───── (луч попал) ◄────┘
    │
    ├─ Барицентрические координаты
    ├─ По gl_PrimitiveID выбираем цвета
    └─ payload.color = интерполированный цвет

    │
    ▼
imageStore(resultImage, payload.color)
```

---

## Ключевые Vulkan API вызовы

| Вызов | Назначение |
|-------|-----------|
| `createRayTracingPipelinesKHR` | Создание RT пайплайна |
| `createAccelerationStructureKHR` | Создание BLAS/TLAS |
| `cmdBuildAccelerationStructuresKHR` | Построение AS на GPU |
| `getAccelerationStructureBuildSizesKHR` | Запрос размеров для AS |
| `getRayTracingShaderGroupHandlesKHR` | Получение handles для SBT |
| `cmdTraceRaysKHR` | Запуск ray tracing |
| `getBufferDeviceAddress` | GPU-адрес буфера (для AS) |
| `getAccelerationStructureDeviceAddressKHR` | GPU-адрес AS |

---

## Расширения Vulkan

| Расширение | Зачем |
|-----------|------|
| `VK_KHR_ray_tracing_pipeline` | Ray tracing пайплайн |
| `VK_KHR_acceleration_structure` | Acceleration structures |
| `VK_KHR_deferred_host_operations` | Deferred операции |
| `VK_KHR_buffer_device_address` | Адреса буферов для AS |
| `VK_AMD_device_coherent_memory` | Когерентная память |
| `VK_KHR_swapchain` | Swapchain для вывода |
| `VK_KHR_get_physical_device_properties2` | Свойства PD для RT |

---

## Отладка

При запуске с флагом `-Dvalidation`:
```
mvn exec:java -pl tutorial -Dexec.mainClass="..." -Dvalidation
```

Включаются validation layers (`VK_LAYER_KHRONOS_validation`), которые проверяют:
- Корректность использования API
- Состояния sync и barriers
- Валидность handles и адресов

Для AMD GPU также устанавливаются переменные:
- `AMD_FORCE_VULKAN_RAY_TRACING=1`
- `AMD_RAY_TRACING_DEBUG=1`

---

## Зависимости

- **vulkan4j** — биндинги Vulkan через Java 22 FFM API
- **GLFW** — создание окна и обработка ввода
- **VMA** (Vulkan Memory Allocator) — управление памятью GPU
- **JOML** (Java OpenGL Math Library) — матрицы и векторы
- **glslangValidator** — компиляция GLSL → SPIR-V (runtime, из Vulkan SDK)

---

## Запуск

```powershell
# Компиляция
cd modules
mvn compile -pl tutorial -am -q

# Запуск
mvn exec:java -pl tutorial -Dexec.mainClass="tutorial.vulkan.raytracing.ch23.Application" -Dexec.cleanupDaemonThreads=false
```

Для запуска с validation layers:
```powershell
mvn exec:java -pl tutorial -Dexec.mainClass="tutorial.vulkan.raytracing.ch23.Application" -Dexec.cleanupDaemonThreads=false -Dvalidation
```

---

## Дальнейшее развитие

Этот пример — базовый ray tracing с простой геометрией. Следующие шаги:
- **Текстурирование** — загрузка текстур и сэмплирование в rchit
- **Освещение** — добавление источников света и моделей освещения
- **Отражения** — рекурсивная трассировка отражённых лучей
- **Тени** — трассировка лучей от точки попадания к источнику света
- **Загрузка моделей** — импорт сложных mesh-ей (glTF, OBJ)
