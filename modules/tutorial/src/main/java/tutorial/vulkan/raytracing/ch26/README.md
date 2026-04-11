# Глава 26 — Ray Tracing: Текстурированный Вращающийся Квад

## Обзор

В этой главе мы добавляем **текстурирование** в ray tracing пайплайн. Результат идентичен графическому пайплайну из `part08/ch26/Main.java` — текстурированный вращающийся квадрат — но достигается через трассировку лучей вместо растеризации.

## Сравнение с Главой 23

### Что изменилось

| Аспект | Глава 23 (ch23) | Глава 26 (ch26) |
|--------|-----------------|-----------------|
| **Вершины** | `vec2 pos + vec3 color` (20 байт) | `vec2 pos + vec3 color + vec2 UV` (28 байт) |
| **Текстура** | Отсутствует | `texture.png` загружается через ImageIO |
| **Сэмплирование** | Нет | `VkSampler` с анизотропией |
| **Descriptor Set** | 2 bindings (STORAGE_IMAGE + ACCELERATION_STRUCTURE) | 3 bindings (+ COMBINED_IMAGE_SAMPLER) |
| **Closest Hit шейдер** | Интерполяция цветов по барицентрике | Сэмплирование текстуры по интерполированным UV |
| **Device Features** | Базовые | + `samplerAnisotropy` |

### Что осталось прежним

- **Геометрия**: Тот же квад (4 вершины, 2 треугольника, 6 индексов)
- **Вращение**: 90°/с вокруг оси Z
- **Камера**: Позиция (2,2,2), FOV 45°, смотрит в (0,0,0)
- **BLAS/TLAS**: Та же структура acceleration structures
- **Push Constants**: model + invProj + invView (48 floats)
- **Ray Generation и Miss шейдеры**: Практически идентичны

---

## Детали Реализации

### 1. Загрузка Текстуры

Текстура загружается из `/texture/texture.png` через стандартный Java ImageIO:

```java
BufferedImage image = ImageIO.read(Application.class.getResourceAsStream("/texture/texture.png"));
```

**Попиксельная конвертация**: Каждый пиксель преобразуется в RGBA байты:
```java
var color = new Color(image.getRGB(x, y), true);
seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getRed());
seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getGreen());
seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getBlue());
seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getAlpha());
```

Это критически важно — в отличие от простого копирования массива байтов, `BufferedImage.getRGB()` возвращает цвет в формате ARGB, и `Color.getRGB()` с флагом `true` (hasalpha) корректно извлекает компоненты.

### 2. Создание VkImage для Текстуры

Текстура создаётся через VMA (Vulkan Memory Allocator):

```java
var imageInfo = VkImageCreateInfo.allocate(arena)
        .imageType(VkImageType._2D)
        .format(VkFormat.R8G8B8A8_SRGB)    // SRGB формат для корректной цветопередачи
        .extent(VkExtent3D.allocate(arena).width(width).height(height).depth(1))
        .mipLevels(1)
        .arrayLayers(1)
        .samples(VkSampleCountFlags._1)
        .tiling(VkImageTiling.OPTIMAL)     // Оптимальный тайлинг для GPU
        .usage(VkImageUsageFlags.TRANSFER_DST | VkImageUsageFlags.SAMPLED)  // Для копирования и сэмплирования
        .initialLayout(VkImageLayout.UNDEFINED);
```

**Формат R8G8B8A8_SRGB**: Важно использовать SRGB, а не UNORM, чтобы текстура отображалась с правильной гамма-коррекцией.

### 3. Transitions Image Layouts

После создания текстуры нужно выполнить два layout transition:

1. **UNDEFINED → TRANSFER_DST_OPTIMAL**: Для копирования данных из staging buffer
2. **TRANSFER_DST_OPTIMAL → SHADER_READ_ONLY_OPTIMAL**: Для чтения текстуры в шейдере

Каждый transition требует `VkImageMemoryBarrier` с правильными `srcAccessMask` и `dstAccessMask`:

```java
// Для TRANSFER_DST_OPTIMAL
srcAccessMask = 0                               // Из чего переходим — не важно
dstAccessMask = VkAccessFlags.TRANSFER_WRITE    // Готовимся к TRANSFER WRITE

// Для SHADER_READ_ONLY_OPTIMAL  
srcAccessMask = VkAccessFlags.TRANSFER_WRITE    // После TRANSFER WRITE
dstAccessMask = VkAccessFlags.SHADER_READ       // Готовимся к SHADER READ
```

### 4. VkSampler с Анизотропией

Сэмплер создаётся с максимальным уровнем анизотропии, поддерживаемым устройством:

```java
var props = VkPhysicalDeviceProperties.allocate(arena);
instanceCommands.getPhysicalDeviceProperties(physicalDevice, props);

var samplerInfo = VkSamplerCreateInfo.allocate(arena)
        .magFilter(VkFilter.LINEAR)
        .minFilter(VkFilter.LINEAR)
        .addressModeU(VkSamplerAddressMode.REPEAT)
        .addressModeV(VkSamplerAddressMode.REPEAT)
        .anisotropyEnable(VkConstants.TRUE)
        .maxAnisotropy(props.limits().maxSamplerAnisotropy())  // Максимальная анизотропия GPU
        ...
```

**Важно**: Для использования анизотропии нужно включить `samplerAnisotropy` в `VkPhysicalDeviceFeatures` при создании logical device:

```java
var physicalDeviceFeatures = VkPhysicalDeviceFeatures.allocate(arena)
        .samplerAnisotropy(VkConstants.TRUE);
```

### 5. Descriptor Set Layout — Новый Binding

Добавляем третий binding для `COMBINED_IMAGE_SAMPLER`:

```java
var bindings = VkDescriptorSetLayoutBinding.allocate(arena, 3)
        .at(0, b -> b.binding(0).descriptorType(VkDescriptorType.STORAGE_IMAGE)...)       // Output image
        .at(1, b -> b.binding(1).descriptorType(VkDescriptorType.ACCELERATION_STRUCTURE_KHR)...) // TLAS
        .at(2, b -> b.binding(2).descriptorType(VkDescriptorType.COMBINED_IMAGE_SAMPLER)  // Текстура
                .stageFlags(VkShaderStageFlags.CLOSEST_HIT_KHR));  // Только в closest hit
```

**Почему только CLOSEST_HIT_KHR?** Текстура сэмплируется только в `rchit` шейдере. `rgen` не нуждается в доступе к текстуре — он только записывает результат в output image.

### 6. Descriptor Pool — Новый Тип

Добавляем `COMBINED_IMAGE_SAMPLER` в пул:

```java
private VkDescriptorPoolSize.Ptr populateDescriptorPoolSizes(Arena arena) {
    return VkDescriptorPoolSize.allocate(arena, 3)
            .at(0, s -> s.type(VkDescriptorType.STORAGE_IMAGE).descriptorCount(...))
            .at(1, s -> s.type(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(...))
            .at(2, s -> s.type(VkDescriptorType.COMBINED_IMAGE_SAMPLER).descriptorCount(...));  // <--
}
```

### 7. Обновление Descriptor Set

Привязываем текстуру и сэмплер:

```java
var textureInfo = VkDescriptorImageInfo.allocate(arena)
        .imageLayout(VkImageLayout.SHADER_READ_ONLY_OPTIMAL)  // Важно!
        .imageView(textureImageView)
        .sampler(textureSampler);

var writes = VkWriteDescriptorSet.allocate(arena, 3)
        ...
        .at(2, w -> w.dstSet(descriptorSet).dstBinding(2)
                .descriptorType(VkDescriptorType.COMBINED_IMAGE_SAMPLER)
                .pImageInfo(textureInfo));
```

**Важно**: `imageLayout` должен быть `SHADER_READ_ONLY_OPTIMAL`, иначе валидация выдаст ошибку.

### 8. Vertex Buffer с UV Координатами

Вершины теперь содержат UV координаты:

```java
float[] vertices = {
        // Position    // Color       // UV
        -0.5f, -0.5f,  1.0f, 0.0f, 0.0f,  0.0f, 1.0f,   // vertex 0: red,    bottom-left,  UV(0,1)
         0.5f, -0.5f,  0.0f, 1.0f, 0.0f,  1.0f, 1.0f,   // vertex 1: green,  bottom-right, UV(1,1)
         0.5f,  0.5f,  0.0f, 0.0f, 1.0f,  1.0f, 0.0f,   // vertex 2: blue,   top-right,    UV(1,0)
        -0.5f,  0.5f,  1.0f, 1.0f, 1.0f,  0.0f, 0.0f,   // vertex 3: white,  top-left,     UV(0,0)
};
```

**Stride**: 7 floats × 4 bytes = **28 байт** (было 20 байт в ch23)

UV координаты соответствуют стандартной текстурной развёртке:
- `(0, 0)` — верхний левый угол текстуры
- `(1, 1)` — нижний правый угол текстуры

**Y-flip**: Vulkan использует начало координат текстуры в верхнем левом углу, поэтому UV(0,1) — это bottom-left вершина квада.

### 9. BLAS — Автоматическая Адаптация

BLAS **не требует изменений** в коде создания! Он автоматически читает `vertexStride` из `VkAccelerationStructureGeometryTrianglesDataKHR`:

```java
var triangles = VkAccelerationStructureGeometryTrianglesDataKHR.allocate(arena)
        .vertexFormat(VkFormat.R32G32_SFLOAT)       // Только позиция (vec2)
        .vertexData(vd -> vd.deviceAddress(vertexAddress))
        .vertexStride(VERTEX_STRIDE_BYTES)          // 28 байт (обновлено!)
        ...
```

**Важно**: Ray tracing BLAS использует только `vertexFormat` для определения какие данные читать из vertex buffer. Формат `R32G32_SFLOAT` означает, что для acceleration structure нужны только позиции (vec2). UV координаты и цвета доступны только через shader device address в hit шейдере.

### 10. Closest Hit Шейдер — Сэмплирование Текстуры

Главное изменение — в `ray.rchit`:

```glsl
// UV координаты для каждой вершины
vec2 uv0 = vec2(0.0, 1.0);  // vertex 0 - bottom-left
vec2 uv1 = vec2(1.0, 1.0);  // vertex 1 - bottom-right  
vec2 uv2 = vec2(1.0, 0.0);  // vertex 2 - top-right
vec2 uv3 = vec2(0.0, 0.0);  // vertex 3 - top-left

// Интерполируем UV по барицентрическим координатам
vec2 texCoord;
if (gl_PrimitiveID == 0) {
    // Triangle 0,1,2
    texCoord = bary.x * uv0 + bary.y * uv1 + bary.z * uv2;
} else {
    // Triangle 2,3,0
    texCoord = bary.x * uv2 + bary.y * uv3 + bary.z * uv0;
}

// Сэмплируем текстуру
payload.color = texture(texSampler, texCoord).rgb;
```

**Ключевое отличие от ch23**: Вместо интерполяции цветов вершин (`bary.x*color0 + bary.y*color1 + bary.z*color2`), мы интерполируем UV координаты и сэмплируем текстуру.

**Почему интерполяция UV работает?** Барицентрические координаты — это веса для вершин треугольника. Они одинаково хорошо работают для интерполяции любых атрибутов: цветов, UV, нормалей и т.д.

---

## Отличия Ray Tracing от Растеризации

### Как UV попадают в шейдер

| Графический Пайплайн (part08/ch26) | Ray Tracing (ch26) |
|-------------------------------------|---------------------|
| UV передаются через `inTexCoord` (interpolated automatically) | UV интерполируются вручную через барицентрические координаты |
| Вершинный шейдер возвращает `fragTexCoord` | rchit шейдер вычисляет UV из `gl_PrimitiveID` + barycentrics |
| Фрагментный шейдер получает `fragTexCoord` | rchit сэмплирует текстуру напрямую |

### Почему нет автоматической интерполяции в RT?

В ray tracing **нет фиксированного-function интерполятора**. Hit шейдер получает только:
- `gl_PrimitiveID` — какой треугольник hit
- `barycentrics` — барицентрические координаты hit point

Всё остальное (цвета, UV, нормали) нужно **интерполировать вручную** по барицентрическим координатам.

---

## Структура Файлов

```
shader/raytracing/ch26/
├── ray.rgen      # Ray generation: dispatch rays, write to output image
├── ray.rchit     # Closest hit: interpolate UV, sample texture
└── ray.rmiss     # Miss: black background

resources/texture/
└── texture.png   # Текстура (шахматный паттерн или изображение)
```

---

## Запуск

```bash
cd modules
mvn exec:java -pl tutorial -Dexec.mainClass="tutorial.vulkan.raytracing.ch26.Application" -Dexec.cleanupDaemonThreads=false
```

**Ожидается**: Вращающийся квадрат с текстурой, идентичный `part08/ch26/Main.java`, но с ray tracing рендерингом.

---

## Ключевые Takeaways

1. **Текстуры в RT** требуют ручного вычисления UV через барицентрические координаты
2. **COMBINED_IMAGE_SAMPLER** — стандартный способ привязки текстур в Vulkan
3. **SRGB формат** важен для корректной цветопередачи текстур
4. **Image layout transitions** критичны — UNDEFINED → TRANSFER_DST → SHADER_READ_ONLY
5. **Анизотропия** улучшает качество текстуры под углом (хотя для квада это не заметно)
6. **BLAS** не знает о UV — он строится только по позициям вершин
7. **Stride** вершин влияет на то, как BLAS читает позиции из vertex buffer

---

## Следующие Шаги

- **Нормали**: Добавить нормали к вершинам и интерполировать их для освещения
- **Mipmaps**: Создать mip levels для текстуры
- **Несколько объектов**: Добавить больше квадов/моделей с разными текстурами
- **PBR**: Physically-Based Rendering с текстурами roughness/metallic
