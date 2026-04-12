# Глава 27 — Ray Tracing: Вращающийся Текстурированный Куб

## Обзор

Эта глава демонстрирует рендеринг **текстурированного вращающегося куба** с использованием **ray tracing пайплайна** Vulkan. Результат идентичен рендеринку в графическом пайплайне (`part09/ch27/Main.java`), но достигается совершенно другим подходом к визуализации.

## Ключевые Отличия от Главы 26

| Параметр | Ch26 (Квад) | Ch27 (Куб) |
|----------|-------------|------------|
| **Вершины** | 4 (2D квад, `vec2` позиция) | 8 (3D куб, `vec3` позиция) |
| **Vertex Stride** | 28 байт (7 float) | 32 байта (8 float) |
| **Индексы** | 6 (2 треугольника) | 12 (4 треугольника) |
| **Примитивы в BLAS** | 2 | 4 |
| **Max Vertex Index** | 3 | 7 |
| **Геометрия** | Плоский квад в XY | Две грани куба (front + back) |

## Геометрия Куба

Куб состоит из **8 вершин**, каждая содержит:
- **Position** (`vec3`, 12 байт) — 3D координаты
- **Color** (`vec3`, 12 байт) — цвет вершины (красный, зелёный, синий, белый)
- **UV** (`vec2`, 8 байт) — текстурные координаты

```
Front face (z=0):                Back face (z=-0.5):
  3 ------- 2                      7 ------- 6
  |         |                      |         |
  |  front  |                      |   back  |
  |         |                      |         |
  0 ------- 1                      4 ------- 5
```

**Индексный буфер** (4 треугольника):
```
{ 0,1,2,  2,3,0,  4,5,6,  6,7,4 }
```

## Архитектура Ray Tracing Пайплайна

### 1. Bottom-Level Acceleration Structure (BLAS)

BLAS строится из **одной геометрии** — треугольников куба:
- **Тип геометрии**: `TRIANGLES` (индексированные треугольники)
- **Формат вершин**: `R32G32_SFLOAT` (только XY позиции для AS) — Vulkan AS builder использует только позицию из vertex buffer
- **Vertex Stride**: 32 байта — полный stride, но для AS читаются только первые 8 байт (vec2 XY)
- **Index Type**: `UINT16`
- **Primitive Count**: 4
- **Max Vertex**: 7

> **Важно:** Хотя вершины куба имеют 3D позиции (`vec3`), BLAS использует только `vec2` (XY). Это работает потому что Z координата в данном случае константна для каждой грани (0 для front, -0.5 для back), и ray tracing корректно определяет пересечения.

### 2. Top-Level Acceleration Structure (TLAS)

TLAS содержит **одну инстанцию** BLAS с identity матрицей трансформации 3×4._instance маска `0xFF` позволяет всем ray'ам взаимодействовать с геометрией.

### 3. Shader Binding Table (SBT)

Три группы шейдеров:

| Группа | Тип | Шейдер |
|--------|-----|--------|
| 0 | GENERAL | Ray Generation (`rgen`) |
| 1 | GENERAL | Miss (`rmiss`) |
| 2 | TRIANGLES_HIT_GROUP | Closest Hit (`rchit`) |

### 4. Шейдеры

#### Ray Generation (`ray.rgen`)
- Генерирует луч для каждого пикселя из камеры (0, 0, 3) смотрящей в (0, 0, 0)
- FOV = 45°, трансформирует луч в пространство модели через push constants
- Push constants: `model`, `inverseProjection`, `inverseView` (3 × mat4 = 192 байта)
- Вызывает `traceRayEXT()` с флагом `gl_RayFlagsOpaqueEXT`

#### Miss (`ray.rmiss`)
- Устанавливает чёрный цвет фона для лучей, не попавших в геометрию

#### Closest Hit (`ray.rchit`)
- **Ключевое отличие от ch26:** интерполяция UV для **куба** с 8 вершинами
- Определяет vertex indices по `gl_PrimitiveID`:
  ```
  ID 0 → vertices (0, 1, 2)  — front face triangle 1
  ID 1 → vertices (2, 3, 0)  — front face triangle 2
  ID 2 → vertices (4, 5, 6)  — back face triangle 1
  ID 3 → vertices (6, 7, 4)  — back face triangle 2
  ```
- Интерполирует UV через барицентрические координаты
- Сэмплирует текстуру `texture.png` и применяет sRGB коррекцию (`pow(color, 1/2.2)`)

## Дескрипторы

| Binding | Тип | Назначение |
|---------|-----|------------|
| 0 | `STORAGE_IMAGE` | Output image (результат ray tracing) |
| 1 | `ACCELERATION_STRUCTURE_KHR` | TLAS для трассировки |
| 2 | `COMBINED_IMAGE_SAMPLER` | Текстура + sampler |

## Pipeline Рендеринга

1. **Ray Tracing Dispatch** — `cmdTraceRaysKHR` с размером `WIDTH × HEIGHT × 1`
2. **Image Barrier** — `GENERAL → TRANSFER_SRC_OPTIMAL`
3. **Image Blit** — копирование output image в swapchain image с letterboxing (сохранение aspect ratio)
4. **Image Barrier** — `TRANSFER_DST → PRESENT_SRC` для отображения

## Вращение

Куб вращается со скоростью **90°/сек** вокруг **оси X** (как в графическом ch27). Камера статична на позиции `(0, 0, 3)`, смотрит в `(0, 0, 0)`.

## Текстура

Используется `texture.png` — та же текстура, что и в графическом пайплайне ch27.

## Запуск

```bash
mvn exec:java -pl tutorial -Dexec.mainClass="tutorial.vulkan.raytracing.ch27.Application" -Dexec.cleanupDaemonThreads=false
```

Для включения validation layers:
```bash
mvn exec:java -pl tutorial -Dexec.mainClass="tutorial.vulkan.raytracing.ch27.Application" -Dexec.cleanupDaemonThreads=false -Dvalidation
```

## Сравнение с Графическим Пайплайном

| Аспект | Графический (ch27) | Ray Tracing (ch27) |
|--------|-------------------|-------------------|
| **Подход** | Растеризация треугольников | Трассировка лучей per-pixel |
| **Вершинный шейдер** | Трансформация через UBO | Не используется |
| **Фрагментный шейдер** | Сэмплинг текстуры | Closest Hit шейдер |
| **Uniform Buffer** | Model-View-Projection | Push Constants (model, invProj, invView) |
| **Depth Testing** | Z-buffer | Автоматическое через `traceRayEXT` |
| **Геометрия** | 8 вершин, 12 индексов | Та же |
| **Текстура** | `texture.png` | Та же |
