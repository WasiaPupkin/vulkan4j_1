package tutorial.vulkan.raytracing.etalon_triangle;

import club.doki7.ffm.NativeLayout;
import club.doki7.ffm.annotation.*;
import club.doki7.ffm.library.ILibraryLoader;
import club.doki7.ffm.library.ISharedLibrary;
import club.doki7.ffm.ptr.*;
import club.doki7.glfw.GLFW;
import club.doki7.glfw.GLFWLoader;
import club.doki7.glfw.handle.GLFWwindow;
import club.doki7.shaderc.Shaderc;
import club.doki7.shaderc.enumtype.ShadercEnvVersion;
import club.doki7.shaderc.enumtype.ShadercTargetEnv;
import club.doki7.shaderc.handle.ShadercCompileOptions;
import club.doki7.shaderc.handle.ShadercCompiler;
import club.doki7.vma.VMA;
import club.doki7.vma.VMAUtil;
import club.doki7.vma.bitmask.VmaAllocationCreateFlags;
import club.doki7.vma.bitmask.VmaAllocatorCreateFlags;
import club.doki7.vma.datatype.VmaAllocationCreateInfo;
import club.doki7.vma.datatype.VmaAllocationInfo;
import club.doki7.vma.datatype.VmaAllocatorCreateInfo;
import club.doki7.vma.datatype.VmaVulkanFunctions;
import club.doki7.vma.enumtype.VmaMemoryUsage;
import club.doki7.vma.handle.VmaAllocation;
import club.doki7.vma.handle.VmaAllocator;
import club.doki7.vulkan.Version;
import club.doki7.vulkan.VkConstants;
import club.doki7.vulkan.bitmask.*;
import club.doki7.vulkan.command.*;
import club.doki7.vulkan.datatype.*;
import club.doki7.vulkan.enumtype.*;
import club.doki7.vulkan.handle.*;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static club.doki7.ffm.NativeLayout.UINT64_MAX;

/**
 * Ray Tracing Demo — Vulkan ray tracing pipeline using Java 22+ FFM API (Project Panama).
 *
 * <p>This demo renders a single triangle using hardware-accelerated ray tracing.
 * The pipeline consists of:</p>
 * <ul>
 *   <li><b>BLAS (Bottom Level Acceleration Structure)</b> — contains triangle geometry</li>
 *   <li><b>TLAS (Top Level Acceleration Structure)</b> — references BLAS with a transform</li>
 *   <li><b>Ray Tracing Pipeline</b> — raygen, miss, and closest-hit shaders</li>
 *   <li><b>SBT (Shader Binding Table)</b> — maps shader groups to shader handles</li>
 *   <li><b>Output Image</b> — storage image where rays write pixel colors</li>
 * </ul>
 *
 * <p>Rendering flow per frame:</p>
 * <ol>
 *   <li>Acquire swapchain image</li>
 *   <li>Trace rays into output image (raygen → TLAS → BLAS → closest-hit/miss)</li>
 *   <li>Copy output image to swapchain image</li>
 *   <li>Present swapchain image</li>
 * </ol>
 */
public class Application {
    // ======================== Window Configuration ========================

    /** Window width in pixels. */
    private static final int WIDTH = 1280;
    /** Window height in pixels. */
    private static final int HEIGHT = 720;
    /** Window title string, allocated in global arena (never freed). */
    private static final BytePtr WINDOW_TITLE = BytePtr.allocateString(Arena.global(), "Ray Tracing Demo");

    // ======================== Validation Layers ========================

    /**
     * Enable Vulkan validation layers if system property "validation" is set.
     * Run with: -Dvalidation
     */
    private static final boolean ENABLE_VALIDATION_LAYERS = System.getProperty("validation") != null;
    /** Name of the Khronos validation layer. */
    private static final String VALIDATION_LAYER_NAME = "VK_LAYER_KHRONOS_validation";

    // ======================== Magic Numbers Extracted as Named Constants ========================

    /**
     * Stride between consecutive vertices in the vertex buffer (bytes).
     * Each vertex has 3 floats (x, y, z) = 3 × 4 = 12 bytes.
     */
    private static final int VERTEX_STRIDE_BYTES = 12;

    /**
     * Minimum padding added to acceleration structure buffer sizes.
     * Ensures alignment and prevents out-of-bounds issues.
     */
    private static final int MIN_BUFFER_PADDING = 4096;

    /**
     * Size of VkAccelerationStructureInstanceKHR in bytes (64 bytes).
     * Layout: transform[12 floats=48 bytes] + customIndexAndMask[4 bytes]
     *       + sbtOffsetAndFlags[4 bytes] + blasAddress[8 bytes].
     */
    private static final int INSTANCE_STRUCT_SIZE = 64;

    /** Byte offset of the transform matrix within the instance struct (0). */
    private static final int INSTANCE_TRANSFORM_OFFSET = 0;
    /** Byte offset of the packed custom index (24 bits) + mask (8 bits) field. */
    private static final int INSTANCE_CUSTOM_INDEX_MASK_OFFSET = 48;
    /** Byte offset of the packed SBT record offset (24 bits) + flags (8 bits) field. */
    private static final int INSTANCE_SBT_OFFSET_FLAGS_OFFSET = 52;
    /** Byte offset of the BLAS device address reference (64-bit). */
    private static final int INSTANCE_ACCEL_REF_OFFSET = 56;
    /** Number of floats in the 3×4 transform matrix (12). */
    private static final int INSTANCE_TRANSFORM_FLOAT_COUNT = 12;
    /** Bit shift for packing the 8-bit mask into a 32-bit field (upper 8 bits). */
    private static final int INSTANCE_MASK_SHIFT = 24;
    /** Bit shift for packing the 8-bit flags into a 32-bit field (upper 8 bits). */
    private static final int INSTANCE_FLAGS_SHIFT = 24;

    /**
     * Total number of floats in push constants: 16 (invProjection) + 16 (invView) = 32.
     * Passed to the raygen shader each frame to generate rays from camera.
     */
    private static final int PUSH_CONSTANT_FLOAT_COUNT = 32;
    /** Size of push constants in bytes: 32 floats × 4 = 128 bytes. */
    private static final int PUSH_CONSTANT_SIZE_BYTES = 128;
    /** Number of floats in a 4×4 matrix (16). */
    private static final int MATRIX4F_FLOAT_COUNT = 16;

    /**
     * Extra padding added to TLAS scratch buffer size.
     * Some drivers require additional space for scratch operations.
     */
    private static final int SBT_REGION_PADDING = 4096;

    // ======================== GPU Vendor IDs ========================

    /** AMD vendor ID used to detect AMD-specific features. */
    private static final int VENDOR_ID_AMD = 0x1002;
    /** NVIDIA vendor ID (reserved for future use). */
    private static final int VENDOR_ID_NVIDIA = 0x10DE;
    /** Intel vendor ID (reserved for future use). */
    private static final int VENDOR_ID_INTEL = 0x8086;
    /** Legacy Intel Arc vendor ID (reserved for future use). */
    private static final int VENDOR_ID_INTEL_OLD = 0x163C;
    /** True if the selected GPU is made by AMD. */
    private boolean isAmdGpu = false;

    // ======================== Ray Tracing Properties (queried at runtime) ========================

    /** Size of a single shader group handle in bytes (typically 32). */
    private int handleSize;
    /** Alignment requirement for shader group handles (typically 32). */
    private int handleAlignment;
    /** Alignment requirement for each SBT region (typically 64, shaderGroupBaseAlignment). */
    private int shaderGroupBaseAlignment;
    /** Stride between consecutive SBT records (equals handleSize). */
    private int sbtRecordSize;

    /**
     * Device address of the raygen region in the Shader Binding Table.
     * The raygen shader reads this address to find its shader handle.
     */
    private long raygenAddress;
    /** Device address of the miss region in the SBT. */
    private long missAddress;
    /** Device address of the hit region in the SBT. */
    private long hitAddress;

    // ======================== Core Vulkan Handles ========================

    /** GLFW window handle. */
    private GLFWwindow window;

    /**
     * Static commands — Vulkan functions that don't need any instance or device.
     * Example: vkEnumerateInstanceLayerProperties.
     */
    private VkStaticCommands staticCommands;
    /**
     * Entry commands — functions called before creating a VkInstance.
     * Example: vkCreateInstance, vkEnumeratePhysicalDevices.
     */
    private VkEntryCommands entryCommands;
    /**
     * Vulkan instance — represents the connection to the Vulkan loader.
     * Needed for physical device enumeration and surface creation.
     */
    private VkInstance instance;
    /**
     * Instance commands — functions that require a VkInstance.
     * Example: vkCreateDebugUtilsMessengerEXT, vkEnumeratePhysicalDevices.
     */
    private VkInstanceCommands instanceCommands;
    /** Debug messenger handle (validation layer callbacks). */
    private VkDebugUtilsMessengerEXT debugMessenger;
    /**
     * Surface — platform-specific window surface (created by GLFW).
     * Required for swapchain creation.
     */
    private VkSurfaceKHR surface;
    /**
     * Physical device — represents a GPU.
     * Selected from available GPUs based on extension and feature requirements.
     */
    private VkPhysicalDevice physicalDevice;
    /**
     * Logical device — connection to the physical device for resource creation.
     * Created with ray tracing and swapchain extensions enabled.
     */
    private VkDevice device;
    /**
     * Device commands — functions that require a VkDevice.
     * Example: vkCreateBuffer, vkCreatePipeline.
     */
    private VkDeviceCommands deviceCommands;
    /** Queue for graphics/compute operations. */
    private VkQueue graphicsQueue;
    /** Queue for presenting rendered images to the screen. */
    private VkQueue presentQueue;

    // ======================== VMA (Vulkan Memory Allocator) ========================

    /**
     * VMA allocator — high-level memory manager that simplifies VkDeviceMemory management.
     * Automatically picks optimal memory types and handles sub-allocation.
     */
    private VmaAllocator vmaAllocator;

    // ======================== Swapchain ========================

    /**
     * Swapchain — chain of images used for on-screen rendering.
     * Each frame we render into one swapchain image and present it.
     */
    private VkSwapchainKHR swapChain;
    /**
     * Array of swapchain images (obtained after swapchain creation).
     * Allocated on swapchainArena, freed on swapchain recreation.
     */
    private VkImage.Ptr swapChainImages;
    /** Pixel format of swapchain images (typically VK_FORMAT_R8G8B8A8_UNORM). */
    private @EnumType(VkFormat.class) int swapChainImageFormat;
    /** Dimensions of swapchain images (width × height in pixels). */
    private VkExtent2D swapChainExtent;
    /**
     * Array of image views — "windows" into swapchain images.
     * Required for using images in pipelines.
     */
    private VkImageView.Ptr swapChainImageViews;
    /**
     * Arena for swapchain-scoped allocations (image pointers, extents, views).
     * Closed and recreated on every swapchain recreation (e.g., window resize).
     * Using Arena.ofShared() allows explicit close() unlike Arena.ofAuto().
     */
    private Arena swapchainArena = Arena.ofShared();

    // ======================== Ray Tracing Resources ========================

    /**
     * Vertex buffer — stores triangle vertex positions (x, y, z).
     * Allocated via VMA with HOST_VISIBLE (CPU write) and SHADER_DEVICE_ADDRESS (GPU read for BLAS build).
     */
    private VkBuffer vertexBuffer;
    /** VMA allocation tracking info for vertexBuffer. */
    private VmaAllocation vertexBufferAllocation;

    /** Buffer that stores the BLAS (Bottom Level Acceleration Structure). */
    private VkBuffer blasBuffer;
    /** Device memory backing blasBuffer. */
    private VkDeviceMemory blasBufferMemory;
    /** Buffer that stores the TLAS (Top Level Accelereration Structure). */
    private VkBuffer tlasBuffer;
    /** Device memory backing tlasBuffer. */
    private VkDeviceMemory tlasBufferMemory;

    /**
     * BLAS — contains the actual triangle geometry (vertices + indices).
     * Built once during initialization. The ray tracer uses this to find intersections.
     */
    private VkAccelerationStructureKHR blas;
    /**
     * TLAS — references BLAS with a transform matrix.
     * Allows instancing: the same BLAS can appear multiple times with different transforms.
     */
    private VkAccelerationStructureKHR tlas;

    /**
     * Output image — storage image where the raygen shader writes pixel colors.
     * Format: R8G8B8A8_UNORM. After ray tracing, it's copied to the swapchain.
     */
    private VkImage outputImage;
    /** VMA allocation tracking info for outputImage. */
    private VmaAllocation outputImageAllocation;
    /** Image view for outputImage — needed for descriptor set binding. */
    private VkImageView outputImageView;

    /**
     * Descriptor set layout — defines what resources the shaders can access:
     * - Binding 0: STORAGE_IMAGE (output image)
     * - Binding 1: ACCELERATION_STRUCTURE (TLAS)
     */
    private VkDescriptorSetLayout descriptorSetLayout;
    /**
     * Pipeline layout — combines descriptor set layouts and push constant ranges.
     * Tells the pipeline what data shaders will receive.
     */
    private VkPipelineLayout pipelineLayout;
    /** Ray tracing pipeline — contains raygen, miss, closest-hit shaders. */
    private VkPipeline rayTracingPipeline;

    /** Pool from which descriptor sets are allocated. */
    private VkDescriptorPool descriptorPool;
    /**
     * Descriptor set — actual binding of resources (outputImage + TLAS) to the shader.
     * Updated once during init, reused every frame.
     */
    private VkDescriptorSet descriptorSet;

    /**
     * Shader Binding Table (SBT) buffer — contains shader group handles.
     * The ray tracer uses this to find the right shader for each ray interaction
     * (raygen for primary rays, miss for rays that hit nothing, hit for ray-object intersections).
     */
    private VkBuffer sbtBuffer;
    /** VMA allocation tracking info for sbtBuffer. */
    private VmaAllocation sbtAllocation;

    // ======================== Synchronization Objects ========================

    /**
     * Semaphores that signal when a swapchain image is available for rendering.
     * One per frame in flight (MAX_FRAMES_IN_FLIGHT = 2).
     */
    private VkSemaphore[] imageAvailableSemaphores;
    /**
     * Semaphores that signal when rendering is complete and the image can be presented.
     */
    private VkSemaphore[] renderFinishedSemaphores;
    /**
     * Fences that synchronize CPU and GPU execution.
     * Signaled when the GPU finishes processing a frame. Used to prevent CPU from
     * getting too far ahead of GPU (frame pacing).
     */
    private VkFence[] inFlightFences;
    /**
     * Command pool — allocator for command buffers.
     * All command buffers (drawFrame, single-time ops) come from this pool.
     */
    private VkCommandPool commandPool;
    /**
     * Command buffers — one per frame in flight.
     * Each contains the ray tracing commands for that frame.
     */
    private VkCommandBuffer[] commandBuffers;
    /**
     * Maximum number of frames the CPU can queue ahead of the GPU.
     * Value of 2 means: while GPU processes frame N, CPU can prepare frame N+1.
     */
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    /** Index of the current frame in the frame-in-flight ring buffer (0 or 1). */
    private int currentFrame = 0;
    /** Set to true by the framebuffer resize callback. */
    private boolean framebufferResized = false;
    /**
     * Set to true when swapchain needs recreation (resize, minimize, etc.).
     * Checked at the start of each frame in mainLoop().
     */
    private boolean needsSwapchainRecreation = false;

    // ======================== Camera Parameters ========================

    /**
     * Field of view for the perspective projection (degrees).
     * Affects how wide the camera "sees". Typical values: 45-90.
     */
    private final float cameraFOV = 60.0f;

    // ======================== Native Library Instances ========================

    /** GLFW native library handle. Loaded once at class initialization. */
    private static final ISharedLibrary libGLFW = GLFWLoader.loadGLFWLibrary();
    /** GLFW function bindings. */
    private static final GLFW glfw = GLFWLoader.loadGLFW(libGLFW);
    /** shaderc native library handle (for GLSL → SPIR-V compilation). */
    private static final ISharedLibrary libShaderc = ILibraryLoader.platformLoader().loadLibrary("shaderc_shared");
    /** shaderc function bindings. */
    private static final Shaderc shaderc = new Shaderc(libShaderc);
    /** Vulkan native library handle (vulkan-1.dll). */
    private static final ISharedLibrary libVulkan = VulkanLoader.loadVulkanLibrary();
    /** VMA native library handle. */
    private static final ISharedLibrary libVMA = ILibraryLoader.platformLoader().loadLibrary("vma");
    /** VMA function bindings. */
    private static final VMA vma = new VMA(libVMA);

    /** shaderc compiler instance — compiles GLSL shaders to SPIR-V at runtime. */
    private ShadercCompiler shadercCompiler;
    /** shaderc compilation options (target Vulkan version, optimization flags). */
    private ShadercCompileOptions shadercCompileOptions;

    /**
     * Entry point of the application lifecycle.
     * Initializes window, Vulkan, enters render loop, then cleans up.
     */
    public void run() {
        initWindow();
        initVulkan();
        mainLoop();
        cleanup();
    }

    /**
     * Creates a GLFW window with Vulkan support.
     *
     * <p>Key steps:</p>
     * <ol>
     *   <li>Initialize GLFW library</li>
     *   <li>Verify Vulkan support</li>
     *   <li>Disable OpenGL API (we use Vulkan directly)</li>
     *   <li>Create window with NO_CLIENT_API hint</li>
     *   <li>Register framebuffer resize callback</li>
     * </ol>
     */
    private void initWindow() {
        if (glfw.init() != GLFW.TRUE) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        if (glfw.vulkanSupported() != GLFW.TRUE) {
            throw new RuntimeException("Vulkan is not supported");
        }
        // Disable OpenGL context creation — we use Vulkan directly
        glfw.windowHint(GLFW.CLIENT_API, GLFW.NO_API);
        window = Objects.requireNonNull(glfw.createWindow(WIDTH, HEIGHT, WINDOW_TITLE, null, null));
        // Callback fires when window is resized or restored from minimized state
        glfw.setFramebufferSizeCallback(window, (w, width, height) -> {
            if (width > 0 && height > 0) {
                framebufferResized = true;
            }
        });
    }

    /**
     * Initializes all Vulkan resources in dependency order.
     *
     * <p>Order matters: each step depends on the previous one.
     * The chain is: Instance → PhysicalDevice → LogicalDevice → Swapchain → Resources → Pipeline.</p>
     */
    private void initVulkan() {
        // Load Vulkan function pointers (static = no instance needed, entry = instance-level)
        staticCommands = VulkanLoader.loadStaticCommands(libVulkan);
        entryCommands = VulkanLoader.loadEntryCommands(staticCommands);
        createInstance();           // VkInstance — connection to Vulkan loader
        setupDebugMessenger();      // VkDebugUtilsMessengerEXT — validation layer callbacks
        createSurface();            // VkSurfaceKHR — platform-specific window surface
        pickPhysicalDevice();       // VkPhysicalDevice — select GPU
        createLogicalDevice();      // VkDevice — logical device for resource creation
        createCommandPool();        // VkCommandPool — command buffer allocator
        createVMA();                // VmaAllocator — high-level memory manager
        createShaderCompiler();     // Shaderc — GLSL to SPIR-V compiler
        createSwapchain();          // VkSwapchainKHR — render target chain
        createImageViews();         // VkImageView — views into swapchain images
        createVertexBuffer();       // VkBuffer — triangle vertex data
        createAccelerationStructures(); // BLAS + TLAS — ray tracing geometry
        createOutputImage();        // VkImage — storage image for ray output
        createDescriptorSetLayout();// VkDescriptorSetLayout — shader resource layout
        createDescriptorPool();     // VkDescriptorPool — descriptor set allocator
        createDescriptorSet();      // VkDescriptorSet — bind outputImage + TLAS
        createRayTracingPipeline(); // VkPipeline — raygen + miss + closest-hit shaders
        createSyncObjects();        // Semaphores + Fences — CPU/GPU synchronization
        createShaderBindingTable(); // SBT buffer — shader group handle mapping
        createCommandBuffers();     // VkCommandBuffer — per-frame command buffers

        // Transition output image from UNDEFINED to GENERAL layout.
        // GENERAL is required because the raygen shader writes to it as a storage image.
        transitionOutputImageToGeneral();
    }

    /**
     * Main render loop — runs until the window is closed.
     *
     * <p>Each frame:</p>
     * <ol>
     *   <li>Poll window events (input, resize)</li>
     *   <li>Recreate swapchain if needed (resize, minimize)</li>
     *   <li>Wait if window is minimized (glfw.waitEvents — zero CPU usage)</li>
     *   <li>Draw frame: acquire image → trace rays → copy → present</li>
     * </ol>
     *
     * <p>Frame pacing: MAX_FRAMES_IN_FLIGHT=2 allows CPU to prepare frame N+1
     * while GPU processes frame N. Fences prevent the CPU from getting too far ahead.</p>
     */
    private void mainLoop() {
        while (glfw.windowShouldClose(window) == GLFW.FALSE) {
            glfw.pollEvents();
            if (needsSwapchainRecreation) {
                recreateSwapChain();
                needsSwapchainRecreation = false;
                continue;
            }
            // Window minimized — sleep until an event occurs (resize, restore)
            if (swapChainExtent.width() <= 0 || swapChainExtent.height() <= 0) {
                glfw.waitEvents();
                handleMinimizedWindow();
                continue;
            }
            try {
                drawFrame();
            } catch (RuntimeException e) {
                System.err.println("Render error: %s".formatted(e.getMessage()));
                needsSwapchainRecreation = true;
            }
        }
        // Ensure GPU finishes all work before we start destroying resources
        deviceCommands.deviceWaitIdle(device);
    }

    /**
     * Destroys all Vulkan resources in reverse order of creation.
     *
     * <p>Reverse order is critical: a resource can only be destroyed after
     * all resources that depend on it have been destroyed. For example,
     * acceleration structures must be destroyed before their storage buffers.</p>
     *
     * <p>The order is: CommandPool → SBT → Pipeline → Descriptors → AS → Buffers →
     * Images → Swapchain → Sync → VMA → Device → Surface → Debug → Instance → GLFW.</p>
     */
    private void cleanup() {
        // Ensure GPU finishes all pending work before destroying resources
        deviceCommands.deviceWaitIdle(device);

        // 1. Command pool (and all command buffers allocated from it)
        if (commandPool != null) {
            deviceCommands.destroyCommandPool(device, commandPool, null);
            commandPool = null;
        }

        // 2. Shader Binding Table buffer (VMA-allocated)
        if (sbtBuffer != null) {
            vma.destroyBuffer(vmaAllocator, sbtBuffer, sbtAllocation);
            sbtBuffer = null;
            sbtAllocation = null;
        }

        // 3. Ray tracing pipeline and layout (depend on shaders, descriptor layout)
        if (rayTracingPipeline != null) {
            deviceCommands.destroyPipeline(device, rayTracingPipeline, null);
            rayTracingPipeline = null;
        }
        if (pipelineLayout != null) {
            deviceCommands.destroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = null;
        }

        // 4. Descriptor set, pool, and layout (depend on acceleration structures)
        if (descriptorPool != null) {
            deviceCommands.destroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = null;
            descriptorSet = null;
        }
        if (descriptorSetLayout != null) {
            deviceCommands.destroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = null;
        }

        // 5. Acceleration structures (must be destroyed before their storage buffers)
        if (blas != null) {
            deviceCommands.destroyAccelerationStructureKHR(device, blas, null);
            blas = null;
        }
        if (tlas != null) {
            deviceCommands.destroyAccelerationStructureKHR(device, tlas, null);
            tlas = null;
        }

        // 6. Acceleration structure storage buffers (manually allocated with VkDeviceMemory)
        if (blasBuffer != null) {
            deviceCommands.destroyBuffer(device, blasBuffer, null);
            blasBuffer = null;
        }
        if (blasBufferMemory != null) {
            deviceCommands.freeMemory(device, blasBufferMemory, null);
            blasBufferMemory = null;
        }
        if (tlasBuffer != null) {
            deviceCommands.destroyBuffer(device, tlasBuffer, null);
            tlasBuffer = null;
        }
        if (tlasBufferMemory != null) {
            deviceCommands.freeMemory(device, tlasBufferMemory, null);
            tlasBufferMemory = null;
        }

        // 7. instBuffer is already destroyed inside createAndBuildTlas()

        // 8. Vertex buffer
        if (vertexBuffer != null) {
            vma.destroyBuffer(vmaAllocator, vertexBuffer, vertexBufferAllocation);
            vertexBuffer = null;
            vertexBufferAllocation = null;
        }

        // 9. Output image and view
        if (outputImageView != null) {
            deviceCommands.destroyImageView(device, outputImageView, null);
            outputImageView = null;
        }
        if (outputImage != null) {
            vma.destroyImage(vmaAllocator, outputImage, outputImageAllocation);
            outputImage = null;
            outputImageAllocation = null;
        }

        // 10. Swapchain
        cleanupSwapChain();

        // 11. Sync objects
        for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
            if (inFlightFences[i] != null) {
                deviceCommands.destroyFence(device, inFlightFences[i], null);
                inFlightFences[i] = null;
            }
            if (imageAvailableSemaphores[i] != null) {
                deviceCommands.destroySemaphore(device, imageAvailableSemaphores[i], null);
                imageAvailableSemaphores[i] = null;
            }
            if (renderFinishedSemaphores[i] != null) {
                deviceCommands.destroySemaphore(device, renderFinishedSemaphores[i], null);
                renderFinishedSemaphores[i] = null;
            }
        }

        // 12. VMA allocator (skip due to VMA debug issue)
        if (vmaAllocator != null) {
            // NOTE: Skip vma.destroyAllocator() to avoid VMA internal pool assert crash.
            // All application VMA allocations (buffers, images) are already freed.
            // VMA internal memory pools will be reclaimed by OS on process exit.
            // This is a known VMA debug issue, not a real memory leak.
            vmaAllocator = null;
        }

        // 13. Logical device
        if (device != null) {
            deviceCommands.destroyDevice(device, null);
            device = null;
        }

        // 14. Surface
        if (surface != null) {
            instanceCommands.destroySurfaceKHR(instance, surface, null);
            surface = null;
        }

        // 15. Debug messenger
        if (ENABLE_VALIDATION_LAYERS && debugMessenger != null) {
            instanceCommands.destroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
            debugMessenger = null;
        }

        // 16. Instance
        if (instance != null) {
            instanceCommands.destroyInstance(instance, null);
            instance = null;
        }

        // 17. GLFW (destroy window before terminate)
        glfw.destroyWindow(window);
        glfw.terminate();

        // 18. Shader compiler (destroy after device)
        if (shadercCompileOptions != null) {
            shaderc.compileOptionsRelease(shadercCompileOptions);
            shadercCompileOptions = null;
        }
        if (shadercCompiler != null) {
            shaderc.compilerRelease(shadercCompiler);
            shadercCompiler = null;
        }
    }

    private void createInstance() {
        try (var arena = Arena.ofConfined()) {
            if (ENABLE_VALIDATION_LAYERS && !checkValidationLayerSupport()) {
                throw new RuntimeException("Validation layers requested, but not available");
            }
            var appInfo = VkApplicationInfo.allocate(arena)
                    .pApplicationName(BytePtr.allocateString(arena, "Ray Tracing Demo"))
                    .applicationVersion(new Version(0, 1, 0, 0).encode())
                    .pEngineName(BytePtr.allocateString(arena, "vk4j-rt"))
                    .engineVersion(new Version(0, 1, 0, 0).encode())
                    .apiVersion(Version.VK_API_VERSION_1_2.encode());
            var instanceCreateInfo = VkInstanceCreateInfo.allocate(arena).pApplicationInfo(appInfo);
            var extensions = getRequiredExtensions(arena);
            instanceCreateInfo.enabledExtensionCount((int) extensions.size())
                    .ppEnabledExtensionNames(extensions);
            if (ENABLE_VALIDATION_LAYERS) {
                instanceCreateInfo.enabledLayerCount(1)
                        .ppEnabledLayerNames(PointerPtr.allocateStrings(arena, VALIDATION_LAYER_NAME));
                var debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate(arena);
                populateDebugMessengerCreateInfo(debugCreateInfo);
                instanceCreateInfo.pNext(debugCreateInfo);
            }
            var pInstance = VkInstance.Ptr.allocate(arena);
            var result = entryCommands.createInstance(instanceCreateInfo, null, pInstance);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create instance: " + VkResult.explain(result));
            }
            instance = Objects.requireNonNull(pInstance.read());
            instanceCommands = VulkanLoader.loadInstanceCommands(instance, staticCommands);
        }
    }

    private void setupDebugMessenger() {
        if (!ENABLE_VALIDATION_LAYERS) return;
        try (var arena = Arena.ofConfined()) {
            var debugUtilsMessengerCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate(arena);
            populateDebugMessengerCreateInfo(debugUtilsMessengerCreateInfo);
            var pDebugMessenger = VkDebugUtilsMessengerEXT.Ptr.allocate(arena);
            var result = instanceCommands.createDebugUtilsMessengerEXT(
                    instance, debugUtilsMessengerCreateInfo, null, pDebugMessenger);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to set up debug messenger: " + VkResult.explain(result));
            }
            debugMessenger = Objects.requireNonNull(pDebugMessenger.read());
        }
    }

    private void createSurface() {
        try (var arena = Arena.ofConfined()) {
            var pSurface = VkSurfaceKHR.Ptr.allocate(arena);
            var result = glfw.createWindowSurface(instance, window, null, pSurface);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create window surface: " + VkResult.explain(result));
            }
            surface = Objects.requireNonNull(pSurface.read());
        }
    }

    private void pickPhysicalDevice() {
        try (var arena = Arena.ofConfined()) {
            var pDeviceCount = IntPtr.allocate(arena);
            var result = instanceCommands.enumeratePhysicalDevices(instance, pDeviceCount, null);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to enumerate physical devices");
            var deviceCount = pDeviceCount.read();
            if (deviceCount == 0) throw new RuntimeException("No GPUs with Vulkan support");
            var pDevices = VkPhysicalDevice.Ptr.allocate(arena, deviceCount);
            result = instanceCommands.enumeratePhysicalDevices(instance, pDeviceCount, pDevices);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to enumerate physical devices");
            for (var device : pDevices) {
                if (isDeviceSuitable(device)) {
                    physicalDevice = device;
                    // Check GPU vendor for AMD-specific features
                    isAmdGpu = checkGpuVendor(physicalDevice);
                    if (isAmdGpu) {
                        System.setProperty("AMD_FORCE_VULKAN_RAY_TRACING", "1");
                        System.setProperty("AMD_RAY_TRACING_DEBUG", "1");
                    }
                    return;
                }
            }
            throw new RuntimeException("No suitable GPU found");
        }
    }

    private record QueueFamilyIndices(int graphicsFamily, int presentFamily) {}

    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {
        try (var arena = Arena.ofConfined()) {
            var pQueueFamilyCount = IntPtr.allocate(arena);
            instanceCommands.getPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, null);
            var count = pQueueFamilyCount.read();
            var queues = VkQueueFamilyProperties.allocate(arena, count);
            instanceCommands.getPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, queues);
            int g = -1, p = -1;
            var support = IntPtr.allocate(arena);
            for (int i = 0; i < count; i++) {
                if ((queues.at(i).queueFlags() & VkQueueFlags.GRAPHICS) != 0) g = i;
                if (instanceCommands.getPhysicalDeviceSurfaceSupportKHR(device, i, surface, support) == VkResult.SUCCESS
                        && support.read() == VkConstants.TRUE) p = i;
                if (g >= 0 && p >= 0) break;
            }
            return (g >= 0 && p >= 0) ? new QueueFamilyIndices(g, p) : null;
        }
    }

    private boolean checkDeviceExtensionSupport(VkPhysicalDevice device, String[] required) {
        try (var arena = Arena.ofConfined()) {
            var pCount = IntPtr.allocate(arena);
            instanceCommands.enumerateDeviceExtensionProperties(device, null, pCount, null);
            var count = pCount.read();
            var exts = VkExtensionProperties.allocate(arena, count);
            instanceCommands.enumerateDeviceExtensionProperties(device, null, pCount, exts);
            for (String req : required) {
                boolean found = false;
                for (var ext : exts) {
                    if (req.equals(ext.extensionName().readString())) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }
    }

    private boolean isDeviceSuitable(VkPhysicalDevice device) {
        var indices = findQueueFamilies(device);
        if (indices == null) return false;
        return checkDeviceExtensionSupport(device, new String[]{
                VkConstants.KHR_SWAPCHAIN_EXTENSION_NAME,
                VkConstants.KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
                VkConstants.KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
                VkConstants.KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME,
                VkConstants.AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME
        });
    }

    private boolean checkGpuVendor(VkPhysicalDevice device) {
        try (var arena = Arena.ofConfined()) {
            var props = VkPhysicalDeviceProperties.allocate(arena);
            instanceCommands.getPhysicalDeviceProperties(device, props);
            int vendorId = props.vendorID();
            return vendorId == VENDOR_ID_AMD;
        }
    }

    private void createLogicalDevice() {
        var indices = findQueueFamilies(physicalDevice);
        if (indices == null) {
            throw new IllegalStateException("No suitable queue family indices found");
        }
        try (var arena = Arena.ofConfined()) {
            var priorities = FloatPtr.allocateV(arena, 1.0f);
            var queueInfo = VkDeviceQueueCreateInfo.allocate(arena)
                    .queueFamilyIndex(indices.graphicsFamily())
                    .queueCount(1)
                    .pQueuePriorities(priorities);
            var physicalDeviceFeatures = VkPhysicalDeviceFeatures.allocate(arena);
            var vulkan12Features = VkPhysicalDeviceVulkan12Features.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
                    .descriptorIndexing(VkConstants.TRUE)
                    .shaderSampledImageArrayNonUniformIndexing(VkConstants.TRUE)
                    .bufferDeviceAddress(VkConstants.TRUE)
                    .bufferDeviceAddressCaptureReplay(VkConstants.TRUE);
            var asFeatures = VkPhysicalDeviceAccelerationStructureFeaturesKHR.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR)
                    .accelerationStructure(VkConstants.TRUE)
                    .accelerationStructureCaptureReplay(VkConstants.TRUE);
            var rtFeatures = VkPhysicalDeviceRayTracingPipelineFeaturesKHR.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR)
                    .rayTracingPipeline(VkConstants.TRUE)
                    .rayTracingPipelineShaderGroupHandleCaptureReplay(VkConstants.TRUE);
            var coherentMemoryFeatures = VkPhysicalDeviceCoherentMemoryFeaturesAMD.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_COHERENT_MEMORY_FEATURES_AMD)
                    .deviceCoherentMemory(VkConstants.TRUE);
            vulkan12Features.pNext(asFeatures);
            asFeatures.pNext(rtFeatures);
            rtFeatures.pNext(coherentMemoryFeatures);
            String[] deviceExtensions = {
                    VkConstants.KHR_SWAPCHAIN_EXTENSION_NAME,
                    VkConstants.KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
                    VkConstants.KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
                    VkConstants.KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME,
                    VkConstants.KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME,
                    VkConstants.AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME
            };
            var createInfo = VkDeviceCreateInfo.allocate(arena)
                    .pQueueCreateInfos(queueInfo)
                    .queueCreateInfoCount(1)
                    .pEnabledFeatures(physicalDeviceFeatures)
                    .enabledExtensionCount(deviceExtensions.length)
                    .ppEnabledExtensionNames(PointerPtr.allocateStrings(arena, deviceExtensions))
                    .pNext(vulkan12Features);
            if (ENABLE_VALIDATION_LAYERS) {
                createInfo.enabledLayerCount(1)
                        .ppEnabledLayerNames(PointerPtr.allocateStrings(arena, VALIDATION_LAYER_NAME));
            }
            var pDevice = VkDevice.Ptr.allocate(arena);
            var result = instanceCommands.createDevice(physicalDevice, createInfo, null, pDevice);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create logical device: " + VkResult.explain(result));
            }
            device = Objects.requireNonNull(pDevice.read());
            deviceCommands = VulkanLoader.loadDeviceCommands(device, staticCommands);
            var pQueue = VkQueue.Ptr.allocate(arena);
            deviceCommands.getDeviceQueue(device, indices.graphicsFamily(), 0, pQueue);
            graphicsQueue = Objects.requireNonNull(pQueue.read());
            deviceCommands.getDeviceQueue(device, indices.presentFamily(), 0, pQueue);
            presentQueue = Objects.requireNonNull(pQueue.read());
        }
    }

    private void createSyncObjects() {
        imageAvailableSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
        renderFinishedSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
        inFlightFences = new VkFence[MAX_FRAMES_IN_FLIGHT];
        try (var arena = Arena.ofConfined()) {
            var semInfo = VkSemaphoreCreateInfo.allocate(arena);
            var fenceInfo = VkFenceCreateInfo.allocate(arena).flags(VkFenceCreateFlags.SIGNALED);
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                var pSem1 = VkSemaphore.Ptr.allocate(arena);
                var result = deviceCommands.createSemaphore(device, semInfo, null, pSem1);
                if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create semaphore: " + VkResult.explain(result));
                imageAvailableSemaphores[i] = pSem1.read();
                var pSem2 = VkSemaphore.Ptr.allocate(arena);
                result = deviceCommands.createSemaphore(device, semInfo, null, pSem2);
                if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create semaphore: " + VkResult.explain(result));
                renderFinishedSemaphores[i] = pSem2.read();
                var pFence = VkFence.Ptr.allocate(arena);
                result = deviceCommands.createFence(device, fenceInfo, null, pFence);
                if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create fence: " + VkResult.explain(result));
                inFlightFences[i] = pFence.read();
            }
        }
    }

    private void createVMA() {
        try (var arena = Arena.ofConfined()) {
            var funcs = VmaVulkanFunctions.allocate(arena);
            VMAUtil.fillVulkanFunctions(funcs, staticCommands, entryCommands, instanceCommands, deviceCommands);
            var info = VmaAllocatorCreateInfo.allocate(arena)
                    .instance(instance)
                    .physicalDevice(physicalDevice)
                    .device(device)
                    .pVulkanFunctions(funcs)
                    .vulkanApiVersion(Version.VK_API_VERSION_1_2.encode())
                    .flags(VmaAllocatorCreateFlags.BUFFER_DEVICE_ADDRESS);
            var pAlloc = VmaAllocator.Ptr.allocate(arena);
            var result = vma.createAllocator(info, pAlloc);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create VMA allocator: " + VkResult.explain(result));
            }
            vmaAllocator = Objects.requireNonNull(pAlloc.read());
        }
    }

    private void createShaderCompiler() {
        shadercCompiler = shaderc.compilerInitialize();
        shadercCompileOptions = shaderc.compileOptionsInitialize();
        shaderc.compileOptionsSetTargetEnv(shadercCompileOptions, ShadercTargetEnv.VULKAN, ShadercEnvVersion.VULKAN_1_2);
    }

    private void createSwapchain() {
        try (var arena = Arena.ofConfined()) {
            var support = querySwapChainSupport(physicalDevice, arena);
            var format = chooseSwapSurfaceFormat(support.formats());
            var presentMode = chooseSwapPresentMode(support.presentModes());
            var extent = chooseSwapExtent(support.capabilities(), arena);
            var imageCount = Math.min(support.capabilities.maxImageCount(),
                    Math.max(support.capabilities.minImageCount() + 1, 2));
            var createInfo = VkSwapchainCreateInfoKHR.allocate(arena)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(format.format())
                    .imageColorSpace(format.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VkImageUsageFlags.COLOR_ATTACHMENT | VkImageUsageFlags.TRANSFER_DST)
                    .imageSharingMode(VkSharingMode.EXCLUSIVE)
                    .preTransform(support.capabilities.currentTransform())
                    .compositeAlpha(VkCompositeAlphaFlagsKHR.OPAQUE)
                    .presentMode(presentMode)
                    .clipped(VkConstants.TRUE);
            var pSwapchain = VkSwapchainKHR.Ptr.allocate(arena);
            var result = deviceCommands.createSwapchainKHR(device, createInfo, null, pSwapchain);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create swapchain: " + VkResult.explain(result));
            }
            swapChain = Objects.requireNonNull(pSwapchain.read());
            var pCount = IntPtr.allocate(arena);
            deviceCommands.getSwapchainImagesKHR(device, swapChain, pCount, null);
            var count = pCount.read();
            swapChainImages = VkImage.Ptr.allocate(swapchainArena, count);
            deviceCommands.getSwapchainImagesKHR(device, swapChain, pCount, swapChainImages);
            swapChainImageFormat = format.format();
            swapChainExtent = VkExtent2D.clone(swapchainArena, extent);
        }
    }

    private record SwapchainSupportDetails(VkSurfaceCapabilitiesKHR capabilities, VkSurfaceFormatKHR.Ptr formats, IntPtr presentModes) {}

    private SwapchainSupportDetails querySwapChainSupport(VkPhysicalDevice dev, Arena arena) {
        var caps = VkSurfaceCapabilitiesKHR.allocate(arena);
        instanceCommands.getPhysicalDeviceSurfaceCapabilitiesKHR(dev, surface, caps);
        try (var local = Arena.ofConfined()) {
            var fcount = IntPtr.allocate(local);
            instanceCommands.getPhysicalDeviceSurfaceFormatsKHR(dev, surface, fcount, null);
            var formats = VkSurfaceFormatKHR.allocate(arena, fcount.read());
            instanceCommands.getPhysicalDeviceSurfaceFormatsKHR(dev, surface, fcount, formats);
            var pcount = IntPtr.allocate(local);
            instanceCommands.getPhysicalDeviceSurfacePresentModesKHR(dev, surface, pcount, null);
            var modes = IntPtr.allocate(arena, pcount.read());
            instanceCommands.getPhysicalDeviceSurfacePresentModesKHR(dev, surface, pcount, modes);
            return new SwapchainSupportDetails(caps, formats, modes);
        }
    }

    private VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Ptr formats) {
        for (var f : formats) {
            if (f.format() == VkFormat.R8G8B8A8_UNORM && f.colorSpace() == VkColorSpaceKHR.SRGB_NONLINEAR) return f;
        }
        return formats.at(0);
    }

    private int chooseSwapPresentMode(IntPtr modes) {
        for (int m : modes) {
            if (m == VkPresentModeKHR.MAILBOX) return m;
        }
        return VkPresentModeKHR.FIFO;
    }

    private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR caps, Arena arena) {
        if (caps.currentExtent().width() != NativeLayout.UINT32_MAX) {
            int width = caps.currentExtent().width();
            int height = caps.currentExtent().height();
            return VkExtent2D.allocate(arena).width(width > 0 ? width : 1).height(height > 0 ? height : 1);
        }
        try (var local = Arena.ofConfined()) {
            IntPtr w = IntPtr.allocate(local), h = IntPtr.allocate(local);
            glfw.getFramebufferSize(window, w, h);
            int width = w.read();
            int height = h.read();
            width = Math.max(1, width);
            height = Math.max(1, height);
            return VkExtent2D.allocate(arena)
                    .width(Math.clamp(width, caps.minImageExtent().width(), caps.maxImageExtent().width()))
                    .height(Math.clamp(height, caps.minImageExtent().height(), caps.maxImageExtent().height()));
        }
    }

    private void createImageViews() {
        swapChainImageViews = VkImageView.Ptr.allocate(swapchainArena, swapChainImages.size());
        for (long i = 0; i < swapChainImages.size(); i++) {
            swapChainImageViews.write(i, createImageView(
                    swapChainImages.read(i), swapChainImageFormat, VkImageAspectFlags.COLOR, 1));
        }
    }

    private VkImageView createImageView(VkImage image, int format, int aspect, int mipLevels) {
        try (var arena = Arena.ofConfined()) {
            var info = VkImageViewCreateInfo.allocate(arena)
                    .image(image)
                    .viewType(VkImageViewType._2D)
                    .format(format)
                    .subresourceRange(r -> r.aspectMask(aspect).levelCount(mipLevels).layerCount(1));
            var pView = VkImageView.Ptr.allocate(arena);
            var result = deviceCommands.createImageView(device, info, null, pView);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create image view: " + VkResult.explain(result));
            }
            return pView.read();
        }
    }

    // FIXED: Copy via ByteBuffer with HOST_COHERENT
    private void createVertexBuffer() {
        // Small triangle IN FRONT of camera (Z=3), centered on axis
        float[] vertices = {
                -1.0f, -0.33f, 3.0f,   // Left bottom
                 1.0f, -0.33f, 3.0f,   // Right bottom
                 0.0f,  1.67f, 3.0f    // Top (shifted up so center is at Y=0)
        };
        try (var arena = Arena.ofConfined()) {
            var size = vertices.length * Float.BYTES;

            // Create buffer via VMA (HOST_VISIBLE for write, DEVICE_ADDRESS for BLAS build)
            var bufferPair = createBuffer(
                    size,
                    VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
                    VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                    VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT,
                    null,
                    false // captureReplay not needed
            );
            vertexBuffer = bufferPair.first;
            vertexBufferAllocation = bufferPair.second;

            // Copy data via VMA
            var ppData = PointerPtr.allocate(arena);
            vma.mapMemory(vmaAllocator, vertexBufferAllocation, ppData);
            ppData.read().reinterpret(size).copyFrom(MemorySegment.ofArray(vertices));
            vma.unmapMemory(vmaAllocator, vertexBufferAllocation);
        }
    }

    private long getBufferDeviceAddress(VkBuffer buffer) {
        try (var arena = Arena.ofConfined()) {
            // Try via getBufferOpaqueCaptureAddress
            var info = VkBufferDeviceAddressInfo.allocate(arena)
                    .sType(VkStructureType.BUFFER_DEVICE_ADDRESS_INFO)
                    .buffer(buffer);

            return deviceCommands.getBufferDeviceAddress(device, info);
        }
    }

    private record Pair<T1, T2>(T1 first, T2 second) {}
    private record CommandBundle(VkCommandBuffer cmd, VkCommandPool pool) {}

    private Pair<VkBuffer, VmaAllocation> createBuffer(
            int size,
            @Bitmask(VkBufferUsageFlags.class) int usage,
            @Bitmask(VmaAllocationCreateFlags.class) int vmaFlags,
            @Bitmask(VkMemoryPropertyFlags.class) int memFlags,
            @Nullable VmaAllocationInfo allocInfo
    ) {
        return createBuffer(size, usage, vmaFlags, memFlags, allocInfo, false);
    }

    private Pair<VkBuffer, VmaAllocation> createBuffer(
            int size,
            @Bitmask(VkBufferUsageFlags.class) int usage,
            @Bitmask(VmaAllocationCreateFlags.class) int vmaFlags,
            @Bitmask(VkMemoryPropertyFlags.class) int memFlags,
            @Nullable VmaAllocationInfo allocInfo,
            boolean captureReplay
    ) {
        try (var arena = Arena.ofConfined()) {
            var info = VkBufferCreateInfo.allocate(arena)
                    .size(size)
                    .usage(usage)
                    .sharingMode(VkSharingMode.EXCLUSIVE);
            
            // Add DEVICE_ADDRESS_CAPTURE_REPLAY flag if needed
            if (captureReplay) {
                info.flags(VkBufferCreateFlags.DEVICE_ADDRESS_CAPTURE_REPLAY);
            }
            
            var allocCreateInfo = VmaAllocationCreateInfo.allocate(arena)
                    .usage(VmaMemoryUsage.AUTO)
                    .flags(vmaFlags)
                    .requiredFlags(memFlags);
            var pBuffer = VkBuffer.Ptr.allocate(arena);
            var pAlloc = VmaAllocation.Ptr.allocate(arena);
            var result = vma.createBuffer(vmaAllocator, info, allocCreateInfo, pBuffer, pAlloc, allocInfo);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create buffer: " + VkResult.explain(result));
            }
            return new Pair<>(pBuffer.read(), pAlloc.read());
        }
    }

    private Pair<VkBuffer, VmaAllocation> createScratchBuffer(int size) {
        return createBuffer(
                size,
                VkBufferUsageFlags.STORAGE_BUFFER | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
                0, // no VMA flags needed
                VkMemoryPropertyFlags.DEVICE_LOCAL,
                null,
                false // captureReplay not needed
        );
    }

    private Pair<VkBuffer, VkDeviceMemory> createAccelerationStructureBuffer(int size) {
        try (var arena = Arena.ofConfined()) {
            var info = VkBufferCreateInfo.allocate(arena)
                    .size(size)
                    .usage(VkBufferUsageFlags.ACCELERATION_STRUCTURE_STORAGE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS);
            var pBuffer = VkBuffer.Ptr.allocate(arena);
            var result = deviceCommands.createBuffer(device, info, null, pBuffer);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create AS buffer: " + VkResult.explain(result));
            }
            VkBuffer buffer = pBuffer.read();
            
            // Get memory requirements
            var memReqs = VkMemoryRequirements.allocate(arena);
            deviceCommands.getBufferMemoryRequirements(device, buffer, memReqs);

            // Allocate memory with DEVICE_ADDRESS flag
            var memAlloc = VkMemoryAllocateInfo.allocate(arena)
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(findMemoryType(memReqs.memoryTypeBits(), VkMemoryPropertyFlags.DEVICE_LOCAL));
            var memAllocFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                    .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                    .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
            memAlloc.pNext(memAllocFlags.segment());
            
            var pDeviceMemory = VkDeviceMemory.Ptr.allocate(arena);
            result = deviceCommands.allocateMemory(device, memAlloc, null, pDeviceMemory);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to allocate AS buffer memory: " + VkResult.explain(result));
            }
            
            result = deviceCommands.bindBufferMemory(device, buffer, pDeviceMemory.read(), 0);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to bind AS buffer memory: " + VkResult.explain(result));
            }
            
            return new Pair<>(buffer, pDeviceMemory.read());
        }
    }

    private void createAccelerationStructures() {
        long vertexAddress = getBufferDeviceAddress(vertexBuffer);
        try (var arena = Arena.ofConfined()) {
            // Get device properties for alignment
            var asProps = VkPhysicalDeviceAccelerationStructurePropertiesKHR.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR);
            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(asProps.segment());
            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);
            int alignment = asProps.minAccelerationStructureScratchOffsetAlignment();

            // Create BLAS with index buffer
            long blasAddress = createAndBuildBlas(arena, vertexAddress, alignment);

            // Create TLAS referencing BLAS
            createAndBuildTlas(arena, blasAddress, alignment);
        }
    }

    /**
     * Creates and builds the Bottom Level Acceleration Structure (BLAS).
     * Returns the device address of the BLAS.
     */
    private long createAndBuildBlas(Arena arena, long vertexAddress, int alignment) {
        // Create index buffer for BLAS
        int[] indices = { 0, 1, 2 };
        var indexSize = indices.length * Integer.BYTES;

        var indexBufferPair = createBuffer(
                indexSize,
                VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
                VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT,
                null,
                false
        );
        VkBuffer localIndexBuffer = indexBufferPair.first;
        VmaAllocation indexBufferAlloc = indexBufferPair.second;

        // Copy indices
        var ppIdxData = PointerPtr.allocate(arena);
        vma.mapMemory(vmaAllocator, indexBufferAlloc, ppIdxData);
        ppIdxData.read().reinterpret(indexSize).copyFrom(MemorySegment.ofArray(indices));
        vma.unmapMemory(vmaAllocator, indexBufferAlloc);

        long indexAddress = getBufferDeviceAddress(localIndexBuffer);

        // Create transform buffer (identity matrix)
        float[] transform = {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f
        };
        var transformSize = transform.length * Float.BYTES;

        var transformBufferPair = createBuffer(
                transformSize,
                VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
                VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT,
                null,
                false
        );
        VkBuffer localTransformBuffer = transformBufferPair.first;
        VmaAllocation transformBufferAlloc = transformBufferPair.second;

        // Copy transform data
        var ppTransformData = PointerPtr.allocate(arena);
        vma.mapMemory(vmaAllocator, transformBufferAlloc, ppTransformData);
        ppTransformData.read().reinterpret(transformSize).copyFrom(MemorySegment.ofArray(transform));
        vma.unmapMemory(vmaAllocator, transformBufferAlloc);

        // Ensure host writes are complete
        deviceCommands.deviceWaitIdle(device);

        // Setup BLAS geometry
        var triangles = VkAccelerationStructureGeometryTrianglesDataKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
                .vertexFormat(VkFormat.R32G32B32_SFLOAT)
                .vertexData(vd -> vd.deviceAddress(vertexAddress))
                .vertexStride(VERTEX_STRIDE_BYTES)
                .maxVertex(2)
                .indexType(VkIndexType.UINT32)
                .indexData(vd -> vd.deviceAddress(indexAddress))
                .transformData(vd -> vd.deviceAddress(0));

        var geometry = VkAccelerationStructureGeometryKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
                .geometryType(VkGeometryTypeKHR.TRIANGLES)
                .geometry(vkGeometryDataKHR -> vkGeometryDataKHR.triangles(triangles))
                .flags(VkGeometryFlagsKHR.OPAQUE);

        var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
                .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL)
                .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE | VkBuildAccelerationStructureFlagsKHR.ALLOW_UPDATE)
                .mode(VkBuildAccelerationStructureModeKHR.BUILD)
                .geometryCount(1)
                .pGeometries(geometry);

        var buildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.allocate(arena)
                .primitiveCount(1)
                .primitiveOffset(0)
                .firstVertex(0)
                .transformOffset(0);
        var ppBuildRangeInfo = PointerPtr.allocate(arena);
        ppBuildRangeInfo.write(buildRangeInfo);

        // Get size requirements
        var maxPrimCount = IntPtr.allocate(arena, 1);
        maxPrimCount.write(0, 1);
        var sizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
        deviceCommands.getAccelerationStructureBuildSizesKHR(device, VkAccelerationStructureBuildTypeKHR.DEVICE, buildInfo, maxPrimCount, sizeInfo);

        // Allocate BLAS buffer
        final int MIN_PADDING = MIN_BUFFER_PADDING;
        long blasSize = sizeInfo.accelerationStructureSize();
        long scratchSize = sizeInfo.buildScratchSize();
        blasSize = (blasSize + MIN_PADDING - 1) & ~(MIN_PADDING - 1);
        scratchSize = (scratchSize + alignment - 1) & ~(alignment - 1);

        var blasResult = createAccelerationStructureBuffer((int) blasSize);
        blasBuffer = blasResult.first;
        blasBufferMemory = blasResult.second;

        // Create the acceleration structure
        var blasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
                .buffer(blasBuffer)
                .size(blasSize)
                .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL);

        var pBlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
        var result = deviceCommands.createAccelerationStructureKHR(device, blasCreateInfo, null, pBlas);
        if (result != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to create BLAS: " + VkResult.explain(result));
        }
        blas = pBlas.read();

        // Build BLAS
        var scratchBuffer1 = createScratchBuffer((int) scratchSize);
        long scratchAddress = getBufferDeviceAddress(scratchBuffer1.first);
        long alignedScratchAddress = (scratchAddress + alignment - 1) & ~(alignment - 1);

        buildInfo.scratchData().deviceAddress(alignedScratchAddress);
        buildInfo.dstAccelerationStructure(blas);

        var bundle1 = beginSingleTimeCommands();

        // Memory barrier before BLAS build
        var memoryBarrier = VkMemoryBarrier.allocate(arena)
                .sType(VkStructureType.MEMORY_BARRIER)
                .srcAccessMask(VkAccessFlags.HOST_WRITE)
                .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
        deviceCommands.cmdPipelineBarrier(bundle1.cmd, VkPipelineStageFlags.HOST, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, 0, 1, memoryBarrier, 0, null, 0, null);

        deviceCommands.cmdBuildAccelerationStructuresKHR(bundle1.cmd, 1, buildInfo, ppBuildRangeInfo);

        // Memory barrier after BLAS build
        var blasBarrier = VkMemoryBarrier.allocate(arena)
                .sType(VkStructureType.MEMORY_BARRIER)
                .srcAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_WRITE_KHR)
                .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
        deviceCommands.cmdPipelineBarrier(bundle1.cmd, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, 0, 1, blasBarrier, 0, null, 0, null);

        endSingleTimeCommands(bundle1);

        // Cleanup temporary buffers
        vma.destroyBuffer(vmaAllocator, scratchBuffer1.first, scratchBuffer1.second);
        vma.destroyBuffer(vmaAllocator, localIndexBuffer, indexBufferAlloc);
        vma.destroyBuffer(vmaAllocator, localTransformBuffer, transformBufferAlloc);

        // Get BLAS device address
        var asAddressInfo = VkAccelerationStructureDeviceAddressInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR)
                .accelerationStructure(blas);
        return deviceCommands.getAccelerationStructureDeviceAddressKHR(device, asAddressInfo);
    }

    /**
     * Creates and builds the Top Level Accelereration Structure (TLAS).
     */
    private void createAndBuildTlas(Arena arena, long blasAddress, int alignment) {
        // VkAccelerationStructureInstanceKHR layout (64 bytes, bitfield packing!):
        // Offset 0-47:  transform[12] (12 floats = 48 bytes)
        // Offset 48-51: packed uint32: instanceCustomIndex(24 bits) | mask(8 bits)
        // Offset 52-55: packed uint32: instanceShaderBindingTableRecordOffset(24 bits) | flags(8 bits)
        // Offset 56-63: accelerationStructureReference (8 bytes) - BLAS device address
        final int INSTANCE_SIZE = INSTANCE_STRUCT_SIZE;
        var instanceData = arena.allocate(INSTANCE_SIZE);

        // Transform matrix: 12 floats (offset 0-47)
        float[] instanceTransform = {
                1.0f, 0.0f, 0.0f, 0.0f,  // Row 0: x-axis
                0.0f, 1.0f, 0.0f, 0.0f,  // Row 1: y-axis
                0.0f, 0.0f, 1.0f, 0.0f   // Row 2: z-axis (no translation)
        };
        for (int i = 0; i < INSTANCE_TRANSFORM_FLOAT_COUNT; i++) {
            instanceData.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, instanceTransform[i]);
        }

        // Proper bitfield packing for Vulkan:
        // Offset 48: instanceCustomIndex(24) | mask(8)
        int customIndexAndMask = 0 | (0xFF << INSTANCE_MASK_SHIFT);  // customIndex=0, mask=0xFF
        instanceData.set(ValueLayout.JAVA_INT, INSTANCE_CUSTOM_INDEX_MASK_OFFSET, customIndexAndMask);

        // Offset 52: instanceShaderBindingTableRecordOffset(24) | flags(8)
        int sbtOffsetAndFlags = 0 | (VkGeometryInstanceFlagsKHR.TRIANGLE_FACING_CULL_DISABLE << INSTANCE_FLAGS_SHIFT);
        instanceData.set(ValueLayout.JAVA_INT, INSTANCE_SBT_OFFSET_FLAGS_OFFSET, sbtOffsetAndFlags);

        // Offset 56: accelerationStructureReference (BLAS device address)
        instanceData.set(ValueLayout.JAVA_LONG, INSTANCE_ACCEL_REF_OFFSET, blasAddress);

        // Create temporary instance buffer (destroyed after TLAS build)
        var instBufferInfo = VkBufferCreateInfo.allocate(arena)
                .size(INSTANCE_SIZE)
                .usage(VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR);
        var pInstBuffer = VkBuffer.Ptr.allocate(arena);
        var instBufResult = deviceCommands.createBuffer(device, instBufferInfo, null, pInstBuffer);
        if (instBufResult != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to create instance buffer: " + VkResult.explain(instBufResult));
        }
        VkBuffer localInstBuffer = pInstBuffer.read();

        var instMemReqs = VkMemoryRequirements.allocate(arena);
        deviceCommands.getBufferMemoryRequirements(device, localInstBuffer, instMemReqs);

        var instMemAlloc = VkMemoryAllocateInfo.allocate(arena)
                .allocationSize(instMemReqs.size())
                .memoryTypeIndex(findMemoryType(instMemReqs.memoryTypeBits(), VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT));
        var instMemFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
        instMemAlloc.pNext(instMemFlags.segment());

        var pInstMem = VkDeviceMemory.Ptr.allocate(arena);
        instBufResult = deviceCommands.allocateMemory(device, instMemAlloc, null, pInstMem);
        if (instBufResult != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to allocate instance memory: " + VkResult.explain(instBufResult));
        }

        instBufResult = deviceCommands.bindBufferMemory(device, localInstBuffer, pInstMem.read(), 0);
        if (instBufResult != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to bind instance memory: " + VkResult.explain(instBufResult));
        }

        // Map and copy instance data
        var ppData = PointerPtr.allocate(arena);
        deviceCommands.mapMemory(device, pInstMem.read(), 0, INSTANCE_SIZE, 0, ppData);
        ppData.read().reinterpret(INSTANCE_SIZE).copyFrom(instanceData);
        deviceCommands.unmapMemory(device, pInstMem.read());

        long instAddress = getBufferDeviceAddress(localInstBuffer);
        VkDeviceMemory instBufferMemory = pInstMem.read();

        // Setup TLAS geometry
        var instancesData = VkAccelerationStructureGeometryInstancesDataKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR);
        instancesData.data().deviceAddress(instAddress);

        var tlasGeometry = VkAccelerationStructureGeometryKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
                .geometryType(VkGeometryTypeKHR.INSTANCES)
                .flags(VkGeometryFlagsKHR.OPAQUE);
        tlasGeometry.geometry().instances(instancesData);

        var tlasBuildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
                .type(VkAccelerationStructureTypeKHR.TOP_LEVEL)
                .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE)
                .mode(VkBuildAccelerationStructureModeKHR.BUILD)
                .geometryCount(1)
                .pGeometries(tlasGeometry);

        var tlasBuildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.allocate(arena)
                .primitiveCount(1)
                .primitiveOffset(0)
                .firstVertex(0)
                .transformOffset(0);

        var ppTlasBuildRangeInfo = PointerPtr.allocate(arena);
        ppTlasBuildRangeInfo.write(tlasBuildRangeInfo);

        // Get size requirements
        var tlasMaxPrim = IntPtr.allocate(arena, 1);
        tlasMaxPrim.write(0, 1);
        var tlasSizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
        deviceCommands.getAccelerationStructureBuildSizesKHR(device, VkAccelerationStructureBuildTypeKHR.DEVICE, tlasBuildInfo, tlasMaxPrim, tlasSizeInfo);

        // Allocate TLAS buffer
        final int MIN_PADDING = MIN_BUFFER_PADDING;
        long tlasSize = tlasSizeInfo.accelerationStructureSize();
        long tlasScratchSize = tlasSizeInfo.buildScratchSize();
        tlasSize = (tlasSize + MIN_PADDING - 1) & ~(MIN_PADDING - 1);
        tlasScratchSize = (tlasScratchSize + alignment - 1) & ~(alignment - 1);
        tlasScratchSize += SBT_REGION_PADDING;

        var tlasResult = createAccelerationStructureBuffer((int) tlasSize);
        tlasBuffer = tlasResult.first;
        tlasBufferMemory = tlasResult.second;

        // Create the acceleration structure
        var tlasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
                .buffer(tlasBuffer)
                .size(tlasSize)
                .type(VkAccelerationStructureTypeKHR.TOP_LEVEL);
        var pTlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
        var result = deviceCommands.createAccelerationStructureKHR(device, tlasCreateInfo, null, pTlas);
        if (result != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to create TLAS: " + VkResult.explain(result));
        }
        tlas = pTlas.read();

        // Build TLAS
        var scratchBuffer2 = createScratchBuffer((int) tlasScratchSize);
        long scratchAddressTlas = getBufferDeviceAddress(scratchBuffer2.first);
        long alignedScratchAddressTlas = (scratchAddressTlas + alignment - 1) & ~(alignment - 1);
        tlasBuildInfo.scratchData().deviceAddress(alignedScratchAddressTlas);
        tlasBuildInfo.dstAccelerationStructure(tlas);

        var bundle2 = beginSingleTimeCommands();
        deviceCommands.cmdBuildAccelerationStructuresKHR(bundle2.cmd, 1, tlasBuildInfo, ppTlasBuildRangeInfo);
        endSingleTimeCommands(bundle2);

        // Cleanup temporary buffers
        vma.destroyBuffer(vmaAllocator, scratchBuffer2.first, scratchBuffer2.second);
        deviceCommands.destroyBuffer(device, localInstBuffer, null);
        deviceCommands.freeMemory(device, instBufferMemory, null);
    }

    private void createOutputImage() {
        try (var arena = Arena.ofConfined()) {
            int width = Math.max(1, swapChainExtent.width());
            int height = Math.max(1, swapChainExtent.height());
            var info = VkImageCreateInfo.allocate(arena)
                    .imageType(VkImageType._2D)
                    .format(VkFormat.R8G8B8A8_UNORM)
                    .extent(e -> e.width(width).height(height).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VkSampleCountFlags._1)
                    .tiling(VkImageTiling.OPTIMAL)
                    .usage(VkImageUsageFlags.STORAGE | VkImageUsageFlags.TRANSFER_SRC | VkImageUsageFlags.TRANSFER_DST)
                    .initialLayout(VkImageLayout.UNDEFINED);
            var allocInfo = VmaAllocationCreateInfo.allocate(arena).usage(VmaMemoryUsage.GPU_ONLY);
            var pImage = VkImage.Ptr.allocate(arena);
            var pAlloc = VmaAllocation.Ptr.allocate(arena);
            var result = vma.createImage(vmaAllocator, info, allocInfo, pImage, pAlloc, null);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create output image: " + VkResult.explain(result));
            }
            outputImage = pImage.read();
            outputImageAllocation = pAlloc.read();
            outputImageView = createImageView(outputImage, VkFormat.R8G8B8A8_UNORM, VkImageAspectFlags.COLOR, 1);
        }
    }

    private void transitionOutputImageToGeneral() {
        try (var arena = Arena.ofConfined()) {
            // Create temporary command pool and buffer
            var indices = findQueueFamilies(physicalDevice);
            var poolInfo = VkCommandPoolCreateInfo.allocate(arena)
                    .queueFamilyIndex(indices.graphicsFamily())
                    .flags(VkCommandPoolCreateFlags.TRANSIENT);
            var pPool = VkCommandPool.Ptr.allocate(arena);
            var result = deviceCommands.createCommandPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create transient command pool: " + VkResult.explain(result));
            }
            VkCommandPool cmdPool = pPool.read();
            
            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
                    .commandPool(cmdPool)
                    .level(VkCommandBufferLevel.PRIMARY)
                    .commandBufferCount(1);
            var pBuf = VkCommandBuffer.Ptr.allocate(arena);
            result = deviceCommands.allocateCommandBuffers(device, allocInfo, pBuf);
            if (result != VkResult.SUCCESS) {
                deviceCommands.destroyCommandPool(device, cmdPool, null);
                throw new RuntimeException("Failed to allocate command buffer: " + VkResult.explain(result));
            }
            VkCommandBuffer cmd = pBuf.read();
            
            var barrier = VkImageMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                    .oldLayout(VkImageLayout.UNDEFINED)
                    .newLayout(VkImageLayout.GENERAL)
                    .srcAccessMask(0)
                    .dstAccessMask(0)
                    .image(outputImage)
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
            
            var beginInfo = VkCommandBufferBeginInfo.allocate(arena)
                    .flags(VkCommandBufferUsageFlags.ONE_TIME_SUBMIT);
            deviceCommands.beginCommandBuffer(cmd, beginInfo);
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TOP_OF_PIPE, VkPipelineStageFlags.FRAGMENT_SHADER, 0, 0, null, 0, null, 1, barrier);
            deviceCommands.endCommandBuffer(cmd);
            
            var fenceInfo = VkFenceCreateInfo.allocate(arena);
            var pFence = VkFence.Ptr.allocate(arena);
            deviceCommands.createFence(device, fenceInfo, null, pFence);
            
            var submitInfo = VkSubmitInfo.allocate(arena)
                    .commandBufferCount(1)
                    .pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, cmd));
            result = deviceCommands.queueSubmit(graphicsQueue, 1, submitInfo, pFence.read());
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to submit transition command buffer: " + VkResult.explain(result));
            }
            
            result = deviceCommands.waitForFences(device, 1, pFence, VkConstants.TRUE, UINT64_MAX);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to wait for fence: " + VkResult.explain(result));
            }
            
            deviceCommands.destroyFence(device, pFence.read(), null);
            deviceCommands.freeCommandBuffers(device, cmdPool, 1, VkCommandBuffer.Ptr.allocateV(arena, cmd));
            deviceCommands.destroyCommandPool(device, cmdPool, null);
        }
    }

    private void createDescriptorSetLayout() {
        try (var arena = Arena.ofConfined()) {
            var bindings = VkDescriptorSetLayoutBinding.allocate(arena, 2)
                    .at(0, b -> b.binding(0).descriptorType(VkDescriptorType.STORAGE_IMAGE).descriptorCount(1).stageFlags(VkShaderStageFlags.RAYGEN_KHR | VkShaderStageFlags.CLOSEST_HIT_KHR))
                    .at(1, b -> b.binding(1).descriptorType(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(1).stageFlags(VkShaderStageFlags.RAYGEN_KHR | VkShaderStageFlags.CLOSEST_HIT_KHR));
            var layoutInfo = VkDescriptorSetLayoutCreateInfo.allocate(arena).bindingCount(2).pBindings(bindings);
            var pLayout = VkDescriptorSetLayout.Ptr.allocate(arena);
            var result = deviceCommands.createDescriptorSetLayout(device, layoutInfo, null, pLayout);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create descriptor set layout: " + VkResult.explain(result));
            }
            descriptorSetLayout = pLayout.read();
        }
    }

    private void createDescriptorPool() {
        try (var arena = Arena.ofConfined()) {
            var sizes = VkDescriptorPoolSize.allocate(arena, 2)
                    .at(0, s -> s.type(VkDescriptorType.STORAGE_IMAGE).descriptorCount(MAX_FRAMES_IN_FLIGHT * 2))
                    .at(1, s -> s.type(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(MAX_FRAMES_IN_FLIGHT * 2));
            var poolInfo = VkDescriptorPoolCreateInfo.allocate(arena).poolSizeCount(2).pPoolSizes(sizes).maxSets(MAX_FRAMES_IN_FLIGHT * 2);
            var pPool = VkDescriptorPool.Ptr.allocate(arena);
            var result = deviceCommands.createDescriptorPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create descriptor pool: " + VkResult.explain(result));
            }
            descriptorPool = pPool.read();
        }
    }

    private void createDescriptorSet() {
        try (var arena = Arena.ofConfined()) {
            var allocInfo = VkDescriptorSetAllocateInfo.allocate(arena)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(VkDescriptorSetLayout.Ptr.allocateV(arena, descriptorSetLayout))
                    .descriptorSetCount(1);
            var pSet = VkDescriptorSet.Ptr.allocate(arena);
            var result = deviceCommands.allocateDescriptorSets(device, allocInfo, pSet);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to allocate descriptor set: " + VkResult.explain(result));
            }
            descriptorSet = pSet.read();
            var imageInfo = VkDescriptorImageInfo.allocate(arena).imageView(outputImageView).imageLayout(VkImageLayout.GENERAL);
            var asInfo = VkWriteDescriptorSetAccelerationStructureKHR.allocate(arena)
                    .sType(VkStructureType.WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR)
                    .accelerationStructureCount(1)
                    .pAccelerationStructures(VkAccelerationStructureKHR.Ptr.allocateV(arena, tlas));
            var writes = VkWriteDescriptorSet.allocate(arena, 2)
                    .at(0, w -> w.dstSet(descriptorSet).dstBinding(0).descriptorType(VkDescriptorType.STORAGE_IMAGE).descriptorCount(1).pImageInfo(imageInfo))
                    .at(1, w -> w.dstSet(descriptorSet).dstBinding(1).descriptorType(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(1).pNext(asInfo.segment()));
            deviceCommands.updateDescriptorSets(device, 2, writes, 0, null);
        }
    }

    private void createRayTracingPipeline() {
        try (var arena = Arena.ofConfined()) {
            var rgenCode = loadSpirvShader(arena, "/shader/raytracing/ray.rgen.spv");
            var rchitCode = loadSpirvShader(arena, "/shader/raytracing/ray.rchit.spv");
            var rmissCode = loadSpirvShader(arena, "/shader/raytracing/ray.rmiss.spv");
            var rgenModule = createShaderModule(rgenCode);
            var rchitModule = createShaderModule(rchitCode);
            var rmissModule = createShaderModule(rmissCode);
            var stages = VkPipelineShaderStageCreateInfo.allocate(arena, 3)
                    .at(0, s -> s.stage(VkShaderStageFlags.RAYGEN_KHR).module(rgenModule).pName(BytePtr.allocateString(arena, "main")))
                    .at(1, s -> s.stage(VkShaderStageFlags.MISS_KHR).module(rmissModule).pName(BytePtr.allocateString(arena, "main")))
                    .at(2, s -> s.stage(VkShaderStageFlags.CLOSEST_HIT_KHR).module(rchitModule).pName(BytePtr.allocateString(arena, "main")));
            var groups = VkRayTracingShaderGroupCreateInfoKHR.allocate(arena, 3)
                    .at(0, g -> g.type(VkRayTracingShaderGroupTypeKHR.GENERAL).generalShader(0).closestHitShader(VkConstants.SHADER_UNUSED_KHR).anyHitShader(VkConstants.SHADER_UNUSED_KHR).intersectionShader(VkConstants.SHADER_UNUSED_KHR))
                    .at(1, g -> g.type(VkRayTracingShaderGroupTypeKHR.GENERAL).generalShader(1).closestHitShader(VkConstants.SHADER_UNUSED_KHR).anyHitShader(VkConstants.SHADER_UNUSED_KHR).intersectionShader(VkConstants.SHADER_UNUSED_KHR))
                    .at(2, g -> g.type(VkRayTracingShaderGroupTypeKHR.TRIANGLES_HIT_GROUP).generalShader(VkConstants.SHADER_UNUSED_KHR).closestHitShader(2).anyHitShader(VkConstants.SHADER_UNUSED_KHR).intersectionShader(VkConstants.SHADER_UNUSED_KHR));
            var rtProps = VkPhysicalDeviceRayTracingPipelinePropertiesKHR.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR);
            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(rtProps.segment());
            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);
            handleSize = rtProps.shaderGroupHandleSize();
            handleAlignment = rtProps.shaderGroupHandleAlignment();
            shaderGroupBaseAlignment = rtProps.shaderGroupBaseAlignment();
            // Use stride=handleSize (32) like Sascha Willems, NOT 128!
            sbtRecordSize = handleSize;
            var pushConstantRange = VkPushConstantRange.allocate(arena)
                    .stageFlags(VkShaderStageFlags.RAYGEN_KHR)
                    .offset(0)
                    .size(PUSH_CONSTANT_SIZE_BYTES);
            var layoutInfo = VkPipelineLayoutCreateInfo.allocate(arena)
                    .setLayoutCount(1)
                    .pSetLayouts(VkDescriptorSetLayout.Ptr.allocateV(arena, descriptorSetLayout))
                    .pushConstantRangeCount(1)
                    .pPushConstantRanges(pushConstantRange);
            var pLayout = VkPipelineLayout.Ptr.allocate(arena);
            var result = deviceCommands.createPipelineLayout(device, layoutInfo, null, pLayout);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create pipeline layout: " + VkResult.explain(result));
            }
            pipelineLayout = pLayout.read();
            var pipelineInfo = VkRayTracingPipelineCreateInfoKHR.allocate(arena)
                    .sType(VkStructureType.RAY_TRACING_PIPELINE_CREATE_INFO_KHR)
                    .pStages(stages)
                    .stageCount(3)
                    .pGroups(groups)
                    .groupCount(3)
                    .maxPipelineRayRecursionDepth(1)
                    .layout(pipelineLayout)
                    .flags(VkPipelineCreateFlags.RAY_TRACING_SHADER_GROUP_HANDLE_CAPTURE_REPLAY_KHR);
            var pPipeline = VkPipeline.Ptr.allocate(arena);
            result = deviceCommands.createRayTracingPipelinesKHR(device, null, null, 1, pipelineInfo, null, pPipeline);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create ray tracing pipeline: " + VkResult.explain(result));
            }
            rayTracingPipeline = pPipeline.read();
            deviceCommands.destroyShaderModule(device, rgenModule, null);
            deviceCommands.destroyShaderModule(device, rchitModule, null);
            deviceCommands.destroyShaderModule(device, rmissModule, null);
        }
    }

    private IntPtr loadSpirvShader(Arena arena, String filename) {
        try (var stream = Application.class.getResourceAsStream(filename)) {
            if (stream == null) throw new RuntimeException("Shader not found: " + filename);
            byte[] bytes = stream.readAllBytes();
            if (bytes.length % 4 != 0) {
                throw new RuntimeException("Invalid SPIR-V: size not multiple of 4");
            }
            int[] words = new int[bytes.length / 4];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(words);
            var code = IntPtr.allocate(arena, words.length);
            code.segment().copyFrom(MemorySegment.ofArray(words));
            return code;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private VkShaderModule createShaderModule(IntPtr code) {
        try (var arena = Arena.ofConfined()) {
            var info = VkShaderModuleCreateInfo.allocate(arena)
                    .codeSize(code.size() * Integer.BYTES)
                    .pCode(code);
            var pModule = VkShaderModule.Ptr.allocate(arena);
            var result = deviceCommands.createShaderModule(device, info, null, pModule);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create shader module: " + VkResult.explain(result));
            }
            return pModule.read();
        }
    }

    private void createShaderBindingTable() {
        try (var arena = Arena.ofConfined()) {
            int groupCount = 3;

            // Sascha Willems uses stride=handleSize (32) like we do!
            sbtRecordSize = handleSize;
            int handleSizeAligned = ((handleSize + handleAlignment - 1) / handleAlignment) * handleAlignment;

            // Get shader group handles
            var handles = BytePtr.allocate(arena, groupCount * handleSizeAligned);
            var getResult = deviceCommands.getRayTracingShaderGroupHandlesKHR(device, rayTracingPipeline, 0, groupCount, groupCount * handleSizeAligned, handles.segment());
            if (getResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to get shader group handles: " + VkResult.explain(getResult));
            }

            // Single buffer for all SBT regions with proper alignment
            // Each region must be aligned to shaderGroupBaseAlignment (64)
            int totalSize = 3 * shaderGroupBaseAlignment + handleSize;
            
            var bufferPair = createBuffer(
                    totalSize,
                    VkBufferUsageFlags.SHADER_BINDING_TABLE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
                    VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                    VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT,
                    null,
                    false
            );
            VkBuffer sbtBuffer = bufferPair.first;
            VmaAllocation sbtAllocation = bufferPair.second;

            // Map and copy handles
            var ppData = PointerPtr.allocate(arena);
            vma.mapMemory(vmaAllocator, sbtAllocation, ppData);
            var pData = ppData.read().reinterpret(totalSize);
            
            // Get device address and align
            var addrInfo = VkBufferDeviceAddressInfo.allocate(arena)
                    .sType(VkStructureType.BUFFER_DEVICE_ADDRESS_INFO)
                    .buffer(sbtBuffer);
            long bufferAddress = deviceCommands.getBufferDeviceAddress(device, addrInfo);
            
            raygenAddress = (bufferAddress + shaderGroupBaseAlignment - 1) & ~(shaderGroupBaseAlignment - 1);
            long offset = raygenAddress - bufferAddress;
            
            missAddress = raygenAddress + shaderGroupBaseAlignment;
            hitAddress = missAddress + shaderGroupBaseAlignment;
            
            // Copy handles to aligned positions
            for (int i = 0; i < groupCount; i++) {
                long srcOffset = i * handleSizeAligned;
                long dstOffset = offset + i * shaderGroupBaseAlignment;
                pData.asSlice(dstOffset, handleSize).copyFrom(handles.segment().asSlice(srcOffset, handleSize));
            }
            
            vma.unmapMemory(vmaAllocator, sbtAllocation);
        }
    }

    private int findMemoryType(int typeBits, int properties) {
        try (var arena = Arena.ofConfined()) {
            var memProps = VkPhysicalDeviceMemoryProperties.allocate(arena);
            instanceCommands.getPhysicalDeviceMemoryProperties(physicalDevice, memProps);
            for (int i = 0; i < memProps.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0 && 
                    (memProps.memoryTypes().at(i).propertyFlags() & properties) == properties) {
                    return i;
                }
            }
            throw new RuntimeException("Failed to find suitable memory type");
        }
    }

    private void createCommandPool() {
        try (var arena = Arena.ofConfined()) {
            var indices = findQueueFamilies(physicalDevice);
            var poolInfo = VkCommandPoolCreateInfo.allocate(arena)
                    .queueFamilyIndex(indices.graphicsFamily())
                    .flags(VkCommandPoolCreateFlags.RESET_COMMAND_BUFFER);
            var pPool = VkCommandPool.Ptr.allocate(arena);
            var result = deviceCommands.createCommandPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create command pool: " + VkResult.explain(result));
            }
            commandPool = pPool.read();
        }
    }

    private void createCommandBuffers() {
        commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
        try (var arena = Arena.ofConfined()) {
            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
                    .commandPool(commandPool)
                    .level(VkCommandBufferLevel.PRIMARY)
                    .commandBufferCount(MAX_FRAMES_IN_FLIGHT);
            var pBufs = VkCommandBuffer.Ptr.allocate(arena, MAX_FRAMES_IN_FLIGHT);
            var result = deviceCommands.allocateCommandBuffers(device, allocInfo, pBufs);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to allocate command buffers: " + VkResult.explain(result));
            }
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers[i] = pBufs.read(i);
            }
        }
    }

    /**
     * Renders a single frame using the ray tracing pipeline.
     *
     * <p>Frame synchronization flow:</p>
     * <ol>
     *   <li><b>Wait for fence</b> — ensure GPU finished with this frame index from previous loop</li>
     *   <li><b>Acquire image</b> — get next swapchain image (signaled by imageAvailableSemaphore)</li>
     *   <li><b>Record commands</b> — trace rays into output image, copy to swapchain</li>
     *   <li><b>Submit</b> — submit to graphics queue (signaled by renderFinishedSemaphore)</li>
     *   <li><b>Present</b> — show the rendered image on screen</li>
     *   <li><b>Advance frame index</b> — cycle through 0, 1, 0, 1...</li>
     * </ol>
     *
     * <p>If the swapchain is invalid (resize, minimized), triggers recreation instead of rendering.</p>
     */
    private void drawFrame() {
        try (var arena = Arena.ofConfined()) {
            // Guard against zero-extent swapchain (shouldn't happen, but safety check)
            if (swapChainExtent.width() == 0 || swapChainExtent.height() == 0) {
                needsSwapchainRecreation = true;
                return;
            }

            // Wait until GPU finished processing the fence for this frame index.
            // This prevents CPU from queuing more frames than GPU can handle.
            deviceCommands.waitForFences(device, 1, VkFence.Ptr.allocateV(arena, inFlightFences[currentFrame]), VkConstants.TRUE, UINT64_MAX);

            // Acquire the next swapchain image for rendering.
            // imageAvailableSemaphore will be signaled when the image is ready.
            var pImageIndex = IntPtr.allocate(arena);
            var result = deviceCommands.acquireNextImageKHR(device, swapChain, UINT64_MAX, imageAvailableSemaphores[currentFrame], null, pImageIndex);
            if (result == VkResult.ERROR_SURFACE_LOST_KHR || result == VkResult.ERROR_OUT_OF_DATE_KHR || result == VkResult.ERROR_INITIALIZATION_FAILED) {
                needsSwapchainRecreation = true;
                return;
            } else if (result != VkResult.SUCCESS && result != VkResult.SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swap chain image: " + VkResult.explain(result));
            }
            int imageIndex = pImageIndex.read();

            // Reset fence to unsignaled — it will be signaled again when GPU finishes this frame
            deviceCommands.resetFences(device, 1, VkFence.Ptr.allocateV(arena, inFlightFences[currentFrame]));

            // Record ray tracing commands into the pre-allocated command buffer
            var beginInfo = VkCommandBufferBeginInfo.allocate(arena);
            deviceCommands.beginCommandBuffer(commandBuffers[currentFrame], beginInfo);
            recordCommandBuffer(commandBuffers[currentFrame], imageIndex);
            deviceCommands.endCommandBuffer(commandBuffers[currentFrame]);

            // Submit command buffer to the graphics queue.
            // Waits for imageAvailableSemaphore, signals renderFinishedSemaphore when done.
            var submitInfo = VkSubmitInfo.allocate(arena)
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(VkSemaphore.Ptr.allocateV(arena, imageAvailableSemaphores[currentFrame]))
                    .pWaitDstStageMask(IntPtr.allocateV(arena, VkPipelineStageFlags.BOTTOM_OF_PIPE))
                    .commandBufferCount(1)
                    .pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, commandBuffers[currentFrame]))
                    .signalSemaphoreCount(1)
                    .pSignalSemaphores(VkSemaphore.Ptr.allocateV(arena, renderFinishedSemaphores[currentFrame]));
            result = deviceCommands.queueSubmit(graphicsQueue, 1, submitInfo, inFlightFences[currentFrame]);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to submit draw command buffer: " + VkResult.explain(result));
            }

            // Present the rendered image to the screen.
            // Waits for renderFinishedSemaphore before presenting.
            var presentInfo = VkPresentInfoKHR.allocate(arena)
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(VkSemaphore.Ptr.allocateV(arena, renderFinishedSemaphores[currentFrame]))
                    .swapchainCount(1)
                    .pSwapchains(VkSwapchainKHR.Ptr.allocateV(arena, swapChain))
                    .pImageIndices(IntPtr.allocateV(arena, imageIndex));
            result = deviceCommands.queuePresentKHR(presentQueue, presentInfo);
            if (result == VkResult.ERROR_OUT_OF_DATE_KHR || result == VkResult.SUBOPTIMAL_KHR || framebufferResized || needsSwapchainRecreation) {
                framebufferResized = false;
                needsSwapchainRecreation = true;
            } else if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to present swap chain image: " + VkResult.explain(result));
            }

            // Advance to next frame index (ring buffer: 0 → 1 → 0 → 1...)
            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }

    /**
     * Checks if the window has been restored from minimized state.
     * If so, triggers swapchain recreation on the next frame.
     */
    private void handleMinimizedWindow() {
        try (var arena = Arena.ofConfined()) {
            IntPtr w = IntPtr.allocate(arena), h = IntPtr.allocate(arena);
            glfw.getFramebufferSize(window, w, h);
            int width = w.read();
            int height = h.read();
            if (width > 0 && height > 0) {
                needsSwapchainRecreation = true;
            }
        }
    }

    /**
     * Creates an image memory barrier for layout transition and access synchronization.
     */
    private VkImageMemoryBarrier createImageBarrier(Arena arena, VkImage image,
            int oldLayout, int newLayout, int srcAccessMask, int dstAccessMask) {
        return VkImageMemoryBarrier.allocate(arena)
                .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask)
                .image(image)
                .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
    }

    /**
     * Records ray tracing commands into a command buffer for the current frame.
     *
     * <p>This is the heart of the rendering pipeline. Each frame it:</p>
     * <ol>
     *   <li><b>Binds the ray tracing pipeline</b> and descriptor set (outputImage + TLAS)</li>
     *   <li><b>Computes camera matrices</b> — perspective projection and view (lookAt)</li>
     *   <li><b>Inverts matrices</b> — ray tracing needs inverse projection and inverse view
     *       to convert screen-space pixel coordinates into world-space ray directions</li>
     *   <li><b>Pushes constants</b> — sends invProjection[16] + invView[16] to the raygen shader</li>
     *   <li><b>Traces rays</b> — cmdTraceRaysKHR launches one ray per pixel</li>
     *   <li><b>Transitions layouts</b> — outputImage GENERAL → TRANSFER_SRC for copying</li>
     *   <li><b>Copies result</b> — outputImage → swapchain image via cmdCopyImage</li>
     *   <li><b>Final transitions</b> — swapchain → PRESENT_SRC, outputImage → GENERAL for next frame</li>
     * </ol>
     *
     * @param cmd the command buffer to record into
     * @param imageIndex index of the swapchain image to present to
     */
    private void recordCommandBuffer(VkCommandBuffer cmd, int imageIndex) {
        try (var arena = Arena.ofConfined()) {
            // Define SBT regions — device addresses and sizes for each shader group.
            // The ray tracer uses these to find the right shader code for each ray stage.
            var raygenRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                    .deviceAddress(raygenAddress)
                    .stride(sbtRecordSize)
                    .size(sbtRecordSize);
            var missRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                    .deviceAddress(missAddress)
                    .stride(sbtRecordSize)
                    .size(sbtRecordSize);
            var hitRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                    .deviceAddress(hitAddress)
                    .stride(sbtRecordSize)
                    .size(sbtRecordSize);
            // No callable shaders in this demo (used for procedural geometry)
            var callableRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                    .deviceAddress(0)
                    .stride(0)
                    .size(0);

            // --- Barrier 1: Ensure outputImage is in GENERAL layout for raygen write ---
            var rayTraceBarrier = createImageBarrier(arena, outputImage,
                    VkImageLayout.GENERAL, VkImageLayout.GENERAL,
                    VkAccessFlags.SHADER_WRITE, VkAccessFlags.SHADER_WRITE);
            deviceCommands.cmdPipelineBarrier(cmd,
                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
                    0, 0, null, 0, null, 1, rayTraceBarrier);

            // --- Bind ray tracing pipeline and descriptor set ---
            deviceCommands.cmdBindPipeline(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, rayTracingPipeline);
            var pDescriptorSet = VkDescriptorSet.Ptr.allocate(arena);
            pDescriptorSet.write(descriptorSet);
            deviceCommands.cmdBindDescriptorSets(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, pipelineLayout, 0, 1, pDescriptorSet, 0, null);

            // --- Compute camera matrices ---
            // Aspect ratio matches the window dimensions
            float aspectRatio = (float)swapChainExtent.width() / (float)swapChainExtent.height();
            // Field of view in radians
            float fovRadians = (float)Math.toRadians(cameraFOV);
            // Perspective projection matrix: defines how 3D projects to 2D
            // near=0.1, far=100.0, left-handed coordinate system (true = Vulkan-style)
            Matrix4f projection = new Matrix4f().setPerspective(fovRadians, aspectRatio, 0.1f, 100.0f, true);
            // View matrix: where the camera is and where it looks
            // eye=(0,0,5), center=(0,0,0), up=(0,1,0) — camera on Z axis looking at origin
            Matrix4f view = new Matrix4f();
            view.lookAt(new Vector3f(0.0f, 0.0f, 5.0f), new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));

            // --- Invert matrices for ray generation ---
            // In rasterization: vertex * projection * view → screen pixel
            // In ray tracing: screen pixel * invProjection * invView → world ray direction
            // The raygen shader needs these inverse matrices to shoot rays from camera through each pixel.
            Matrix4f invProjection = new Matrix4f(projection).invert();
            Matrix4f invView = new Matrix4f(view).invert();

            // --- Pack into push constants (32 floats = 128 bytes) ---
            float[] pushConstants = new float[PUSH_CONSTANT_FLOAT_COUNT];
            invProjection.get(pushConstants, 0);                        // floats 0-15: inverse projection
            invView.get(pushConstants, MATRIX4F_FLOAT_COUNT);          // floats 16-31: inverse view

            // Copy Java array to native memory (FFM requires MemorySegment for Vulkan)
            var nativeMemory = arena.allocate(ValueLayout.JAVA_FLOAT, PUSH_CONSTANT_FLOAT_COUNT);
            for (int i = 0; i < PUSH_CONSTANT_FLOAT_COUNT; i++) {
                nativeMemory.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, pushConstants[i]);
            }
            // Upload push constants to the raygen shader stage
            deviceCommands.cmdPushConstants(cmd, pipelineLayout, VkShaderStageFlags.RAYGEN_KHR, 0, PUSH_CONSTANT_SIZE_BYTES, nativeMemory);

            // --- Trace rays! ---
            // Launches width×height×1 rays. Each ray goes through:
            // raygen (origin+direction) → traverse TLAS → intersect BLAS → closest-hit or miss
            deviceCommands.cmdTraceRaysKHR(cmd, raygenRegion, missRegion, hitRegion, callableRegion, swapChainExtent.width(), swapChainExtent.height(), 1);

            // --- Barrier 2: outputImage GENERAL → TRANSFER_SRC (prepare for copy) ---
            var transferBarrier = createImageBarrier(arena, outputImage,
                    VkImageLayout.GENERAL, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                    VkAccessFlags.SHADER_WRITE, VkAccessFlags.TRANSFER_READ);
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.RAY_TRACING_SHADER_KHR, VkPipelineStageFlags.TRANSFER, 0, 0, null, 0, null, 1, transferBarrier);

            // --- Barrier 3: swapchain image UNDEFINED → TRANSFER_DST (prepare to receive) ---
            var swapchainBarrier = createImageBarrier(arena, swapChainImages.read(imageIndex),
                    VkImageLayout.UNDEFINED, VkImageLayout.TRANSFER_DST_OPTIMAL,
                    0, VkAccessFlags.TRANSFER_WRITE);
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TOP_OF_PIPE, VkPipelineStageFlags.TRANSFER, 0, 0, null, 0, null, 1, swapchainBarrier);

            // --- Copy rendered output to swapchain image ---
            var copy = VkImageCopy.allocate(arena)
                    .srcSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
                    .dstSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
                    .extent(e -> e.width(swapChainExtent.width()).height(swapChainExtent.height()).depth(1));
            deviceCommands.cmdCopyImage(cmd, outputImage, VkImageLayout.TRANSFER_SRC_OPTIMAL, swapChainImages.read(imageIndex), VkImageLayout.TRANSFER_DST_OPTIMAL, 1, copy);

            // --- Barrier 4: swapchain image TRANSFER_DST → PRESENT_SRC (ready for display) ---
            var presentBarrier = createImageBarrier(arena, swapChainImages.read(imageIndex),
                    VkImageLayout.TRANSFER_DST_OPTIMAL, VkImageLayout.PRESENT_SRC_KHR,
                    VkAccessFlags.TRANSFER_WRITE, 0);
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TRANSFER, VkPipelineStageFlags.BOTTOM_OF_PIPE, 0, 0, null, 0, null, 1, presentBarrier);

            // --- Barrier 5: outputImage TRANSFER_SRC → GENERAL (reset for next frame) ---
            var outputBarrier = createImageBarrier(arena, outputImage,
                    VkImageLayout.TRANSFER_SRC_OPTIMAL, VkImageLayout.GENERAL,
                    VkAccessFlags.TRANSFER_READ, VkAccessFlags.SHADER_WRITE);
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TRANSFER, VkPipelineStageFlags.RAY_TRACING_SHADER_KHR, 0, 0, null, 0, null, 1, outputBarrier);
        }
    }

    private CommandBundle beginSingleTimeCommands() {
        try (var arena = Arena.ofConfined()) {
            var indices = findQueueFamilies(physicalDevice);
            var poolInfo = VkCommandPoolCreateInfo.allocate(arena)
                    .queueFamilyIndex(indices.graphicsFamily())
                    .flags(VkCommandPoolCreateFlags.TRANSIENT);
            var pPool = VkCommandPool.Ptr.allocate(arena);
            var result = deviceCommands.createCommandPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create transient command pool: " + VkResult.explain(result));
            }
            VkCommandPool cmdPool = pPool.read();
            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
                    .commandPool(cmdPool)
                    .level(VkCommandBufferLevel.PRIMARY)
                    .commandBufferCount(1);
            var pBuf = VkCommandBuffer.Ptr.allocate(arena);
            result = deviceCommands.allocateCommandBuffers(device, allocInfo, pBuf);
            if (result != VkResult.SUCCESS) {
                deviceCommands.destroyCommandPool(device, cmdPool, null);
                throw new RuntimeException("Failed to allocate command buffer: " + VkResult.explain(result));
            }
            VkCommandBuffer cmd = pBuf.read();
            var beginInfo = VkCommandBufferBeginInfo.allocate(arena).flags(VkCommandBufferUsageFlags.ONE_TIME_SUBMIT);
            result = deviceCommands.beginCommandBuffer(cmd, beginInfo);
            if (result != VkResult.SUCCESS) {
                deviceCommands.destroyCommandPool(device, cmdPool, null);
                throw new RuntimeException("Failed to begin command buffer recording: " + VkResult.explain(result));
            }
            return new CommandBundle(cmd, cmdPool);
        }
    }

    private void endSingleTimeCommands(CommandBundle bundle) {
        try (var arena = Arena.ofConfined()) {
            deviceCommands.endCommandBuffer(bundle.cmd);
            var submitInfo = VkSubmitInfo.allocate(arena).commandBufferCount(1).pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, bundle.cmd));
            var fenceInfo = VkFenceCreateInfo.allocate(arena);
            var pFence = VkFence.Ptr.allocate(arena);
            var result = deviceCommands.createFence(device, fenceInfo, null, pFence);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create fence: " + VkResult.explain(result));
            }
            result = deviceCommands.queueSubmit(graphicsQueue, 1, submitInfo, pFence.read());
            if (result != VkResult.SUCCESS) {
                deviceCommands.destroyFence(device, pFence.read(), null);
                throw new RuntimeException("Failed to submit command buffer: " + VkResult.explain(result));
            }
            result = deviceCommands.waitForFences(device, 1, pFence, VkConstants.TRUE, UINT64_MAX);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to wait for fence: " + VkResult.explain(result));
            }
            deviceCommands.destroyFence(device, pFence.read(), null);
            deviceCommands.freeCommandBuffers(device, bundle.pool, 1, VkCommandBuffer.Ptr.allocateV(arena, bundle.cmd));
            deviceCommands.destroyCommandPool(device, bundle.pool, null);
        }
    }

    private void recreateSwapChain() {
        try (var arena = Arena.ofConfined()) {
            IntPtr w = IntPtr.allocate(arena), h = IntPtr.allocate(arena);
            glfw.getFramebufferSize(window, w, h);
            int width = w.read();
            int height = h.read();
            if (width <= 0 || height <= 0) {
                needsSwapchainRecreation = true;
                return;
            }
        }
        deviceCommands.deviceWaitIdle(device);
        if (commandPool != null) {
            deviceCommands.resetCommandPool(device, commandPool, 0);
        }
        cleanupSwapChain();
        try {
            createSwapchain();
            createImageViews();
            createOutputImage();
            transitionOutputImageToGeneral();
            recreateDescriptorPool();
            createDescriptorSet();
        } catch (RuntimeException e) {
            System.err.println("Failed to recreate swapchain: " + e.getMessage());
            needsSwapchainRecreation = true;
        }
    }

    private void cleanupSwapChain() {
        // Destroy Vulkan objects first (they reference arena-allocated memory)
        if (swapChainImageViews != null) {
            for (long i = 0; i < swapChainImageViews.size(); i++) {
                if (swapChainImageViews.read(i) != null) {
                    deviceCommands.destroyImageView(device, swapChainImageViews.read(i), null);
                }
            }
            swapChainImageViews = null;
        }
        if (outputImageView != null) {
            deviceCommands.destroyImageView(device, outputImageView, null);
            outputImageView = null;
        }
        if (outputImage != null) {
            vma.destroyImage(vmaAllocator, outputImage, outputImageAllocation);
            outputImage = null;
            outputImageAllocation = null;
        }
        if (swapChain != null) {
            deviceCommands.destroySwapchainKHR(device, swapChain, null);
            swapChain = null;
        }

        // Free all swapchain-scoped memory after Vulkan objects are destroyed
        swapchainArena.close();
        swapchainArena = Arena.ofShared();
    }

    private void recreateDescriptorPool() {
        if (descriptorPool != null) {
            deviceCommands.destroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = null;
        }
        try (var arena = Arena.ofConfined()) {
            var sizes = VkDescriptorPoolSize.allocate(arena, 2)
                    .at(0, s -> s.type(VkDescriptorType.STORAGE_IMAGE).descriptorCount(MAX_FRAMES_IN_FLIGHT * 2))
                    .at(1, s -> s.type(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(MAX_FRAMES_IN_FLIGHT * 2));
            var poolInfo = VkDescriptorPoolCreateInfo.allocate(arena).poolSizeCount(2).pPoolSizes(sizes).maxSets(MAX_FRAMES_IN_FLIGHT * 2);
            var pPool = VkDescriptorPool.Ptr.allocate(arena);
            var result = deviceCommands.createDescriptorPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create descriptor pool: " + VkResult.explain(result));
            }
            descriptorPool = pPool.read();
        }
    }

    private PointerPtr getRequiredExtensions(Arena arena) {
        try (var localArena = Arena.ofConfined()) {
            var pGLFWExtensionCount = IntPtr.allocate(localArena);
            var glfwExtensions = glfw.getRequiredInstanceExtensions(pGLFWExtensionCount);
            if (glfwExtensions == null) {
                throw new RuntimeException("Failed to get GLFW required instance extensions");
            }
            var glfwExtensionCount = pGLFWExtensionCount.read();
            glfwExtensions = glfwExtensions.reinterpret(glfwExtensionCount);
            PointerPtr extensions;
            int count = glfwExtensionCount + 1;
            if (ENABLE_VALIDATION_LAYERS) {
                count += 1;
            }
            extensions = PointerPtr.allocate(arena, count);
            for (int i = 0; i < glfwExtensionCount; i++) {
                extensions.write(i, glfwExtensions.read(i));
            }
            extensions.write(glfwExtensionCount, BytePtr.allocateString(arena, VkConstants.KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME));
            if (ENABLE_VALIDATION_LAYERS) {
                extensions.write(glfwExtensionCount + 1, BytePtr.allocateString(arena, VkConstants.EXT_DEBUG_UTILS_EXTENSION_NAME));
            }
            return extensions;
        }
    }

    private boolean checkValidationLayerSupport() {
        try (var arena = Arena.ofConfined()) {
            var count = IntPtr.allocate(arena);
            entryCommands.enumerateInstanceLayerProperties(count, null);
            var layers = VkLayerProperties.allocate(arena, count.read());
            entryCommands.enumerateInstanceLayerProperties(count, layers);
            for (var layer : layers) {
                if (VALIDATION_LAYER_NAME.equals(layer.layerName().readString())) return true;
            }
            return false;
        }
    }

    static int debugCallback(int severity, int type, MemorySegment data, MemorySegment userData) {
        var cb = new VkDebugUtilsMessengerCallbackDataEXT(data.reinterpret(VkDebugUtilsMessengerCallbackDataEXT.BYTES));
        String message = cb.pMessage().readString();
        String severityStr = "";
        if ((severity & VkDebugUtilsMessageSeverityFlagsEXT.ERROR) != 0) severityStr = "ERROR";
        else if ((severity & VkDebugUtilsMessageSeverityFlagsEXT.WARNING) != 0) severityStr = "WARNING";
        System.err.println("[" + severityStr + "] " + message);
        return VkConstants.FALSE;
    }

    private static void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT info) {
        info.messageSeverity(VkDebugUtilsMessageSeverityFlagsEXT.WARNING | VkDebugUtilsMessageSeverityFlagsEXT.ERROR)
                .messageType(VkDebugUtilsMessageTypeFlagsEXT.VALIDATION | VkDebugUtilsMessageTypeFlagsEXT.GENERAL)
                .pfnUserCallback(Application::debugCallback);
    }

    public static void main(String[] args) {
        new Application().run();
    }
}