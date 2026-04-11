package tutorial.vulkan.raytracing.ch26;

import club.doki7.ffm.NativeLayout;
import club.doki7.ffm.annotation.EnumType;
import club.doki7.ffm.library.ILibraryLoader;
import club.doki7.ffm.library.ISharedLibrary;
import club.doki7.ffm.ptr.BytePtr;
import club.doki7.ffm.ptr.FloatPtr;
import club.doki7.ffm.ptr.IntPtr;
import club.doki7.ffm.ptr.PointerPtr;
import club.doki7.glfw.GLFW;
import club.doki7.glfw.GLFWLoader;
import club.doki7.glfw.handle.GLFWwindow;
import club.doki7.vma.VMA;
import club.doki7.vma.VMAUtil;
import club.doki7.vma.bitmask.VmaAllocationCreateFlags;
import club.doki7.vma.bitmask.VmaAllocatorCreateFlags;
import club.doki7.vma.datatype.VmaAllocationCreateInfo;
import club.doki7.vma.datatype.VmaAllocatorCreateInfo;
import club.doki7.vma.datatype.VmaVulkanFunctions;
import club.doki7.vma.enumtype.VmaMemoryUsage;
import club.doki7.vma.handle.VmaAllocation;
import club.doki7.vma.handle.VmaAllocator;
import club.doki7.vulkan.Version;
import club.doki7.vulkan.VkConstants;
import club.doki7.vulkan.bitmask.VkAccessFlags;
import club.doki7.vulkan.bitmask.VkBufferUsageFlags;
import club.doki7.vulkan.bitmask.VkBuildAccelerationStructureFlagsKHR;
import club.doki7.vulkan.bitmask.VkCommandBufferUsageFlags;
import club.doki7.vulkan.bitmask.VkCommandPoolCreateFlags;
import club.doki7.vulkan.bitmask.VkCompositeAlphaFlagsKHR;
import club.doki7.vulkan.bitmask.VkFenceCreateFlags;
import club.doki7.vulkan.bitmask.VkGeometryFlagsKHR;
import club.doki7.vulkan.bitmask.VkGeometryInstanceFlagsKHR;
import club.doki7.vulkan.bitmask.VkImageAspectFlags;
import club.doki7.vulkan.bitmask.VkImageUsageFlags;
import club.doki7.vulkan.bitmask.VkMemoryAllocateFlags;
import club.doki7.vulkan.bitmask.VkMemoryPropertyFlags;
import club.doki7.vulkan.bitmask.VkPipelineCreateFlags;
import club.doki7.vulkan.bitmask.VkPipelineStageFlags;
import club.doki7.vulkan.bitmask.VkQueueFlags;
import club.doki7.vulkan.bitmask.VkSampleCountFlags;
import club.doki7.vulkan.bitmask.VkShaderStageFlags;
import club.doki7.vulkan.command.VkDeviceCommands;
import club.doki7.vulkan.command.VkEntryCommands;
import club.doki7.vulkan.command.VkInstanceCommands;
import club.doki7.vulkan.command.VkStaticCommands;
import club.doki7.vulkan.command.VulkanLoader;
import club.doki7.vulkan.datatype.VkAccelerationStructureBuildGeometryInfoKHR;
import club.doki7.vulkan.datatype.VkAccelerationStructureBuildRangeInfoKHR;
import club.doki7.vulkan.datatype.VkAccelerationStructureBuildSizesInfoKHR;
import club.doki7.vulkan.datatype.VkAccelerationStructureCreateInfoKHR;
import club.doki7.vulkan.datatype.VkAccelerationStructureDeviceAddressInfoKHR;
import club.doki7.vulkan.datatype.VkAccelerationStructureGeometryInstancesDataKHR;
import club.doki7.vulkan.datatype.VkAccelerationStructureGeometryKHR;
import club.doki7.vulkan.datatype.VkAccelerationStructureGeometryTrianglesDataKHR;
import club.doki7.vulkan.datatype.VkApplicationInfo;
import club.doki7.vulkan.datatype.VkBufferCreateInfo;
import club.doki7.vulkan.datatype.VkBufferDeviceAddressInfo;
import club.doki7.vulkan.datatype.VkCommandBufferAllocateInfo;
import club.doki7.vulkan.datatype.VkCommandBufferBeginInfo;
import club.doki7.vulkan.datatype.VkCommandPoolCreateInfo;
import club.doki7.vulkan.datatype.VkDebugUtilsMessengerCreateInfoEXT;
import club.doki7.vulkan.datatype.VkDescriptorImageInfo;
import club.doki7.vulkan.datatype.VkDescriptorPoolCreateInfo;
import club.doki7.vulkan.datatype.VkDescriptorPoolSize;
import club.doki7.vulkan.datatype.VkDescriptorSetAllocateInfo;
import club.doki7.vulkan.datatype.VkDescriptorSetLayoutBinding;
import club.doki7.vulkan.datatype.VkDescriptorSetLayoutCreateInfo;
import club.doki7.vulkan.datatype.VkDeviceCreateInfo;
import club.doki7.vulkan.datatype.VkDeviceQueueCreateInfo;
import club.doki7.vulkan.datatype.VkExtensionProperties;
import club.doki7.vulkan.datatype.VkExtent2D;
import club.doki7.vulkan.datatype.VkExtent3D;
import club.doki7.vulkan.datatype.VkFenceCreateInfo;
import club.doki7.vulkan.datatype.VkImageCopy;
import club.doki7.vulkan.datatype.VkImageCreateInfo;
import club.doki7.vulkan.datatype.VkImageViewCreateInfo;
import club.doki7.vulkan.datatype.VkInstanceCreateInfo;
import club.doki7.vulkan.datatype.VkLayerProperties;
import club.doki7.vulkan.datatype.VkMemoryAllocateFlagsInfo;
import club.doki7.vulkan.datatype.VkMemoryAllocateInfo;
import club.doki7.vulkan.datatype.VkMemoryBarrier;
import club.doki7.vulkan.datatype.VkMemoryRequirements;
import club.doki7.vulkan.datatype.VkPhysicalDeviceAccelerationStructureFeaturesKHR;
import club.doki7.vulkan.datatype.VkPhysicalDeviceAccelerationStructurePropertiesKHR;
import club.doki7.vulkan.datatype.VkPhysicalDeviceCoherentMemoryFeaturesAMD;
import club.doki7.vulkan.datatype.VkPhysicalDeviceFeatures;
import club.doki7.vulkan.datatype.VkPhysicalDeviceMemoryProperties;
import club.doki7.vulkan.datatype.VkPhysicalDeviceProperties;
import club.doki7.vulkan.datatype.VkPhysicalDeviceProperties2;
import club.doki7.vulkan.datatype.VkPhysicalDeviceRayTracingPipelineFeaturesKHR;
import club.doki7.vulkan.datatype.VkPhysicalDeviceRayTracingPipelinePropertiesKHR;
import club.doki7.vulkan.datatype.VkPhysicalDeviceVulkan12Features;
import club.doki7.vulkan.datatype.VkPipelineLayoutCreateInfo;
import club.doki7.vulkan.datatype.VkPipelineShaderStageCreateInfo;
import club.doki7.vulkan.datatype.VkPresentInfoKHR;
import club.doki7.vulkan.datatype.VkPushConstantRange;
import club.doki7.vulkan.datatype.VkQueueFamilyProperties;
import club.doki7.vulkan.datatype.VkRayTracingPipelineCreateInfoKHR;
import club.doki7.vulkan.datatype.VkRayTracingShaderGroupCreateInfoKHR;
import club.doki7.vulkan.datatype.VkSemaphoreCreateInfo;
import club.doki7.vulkan.datatype.VkShaderModuleCreateInfo;
import club.doki7.vulkan.datatype.VkStridedDeviceAddressRegionKHR;
import club.doki7.vulkan.datatype.VkSubmitInfo;
import club.doki7.vulkan.datatype.VkSurfaceCapabilitiesKHR;
import club.doki7.vulkan.datatype.VkSurfaceFormatKHR;
import club.doki7.vulkan.datatype.VkSwapchainCreateInfoKHR;
import club.doki7.vulkan.datatype.VkWriteDescriptorSet;
import club.doki7.vulkan.datatype.VkWriteDescriptorSetAccelerationStructureKHR;
import club.doki7.vulkan.datatype.VkBufferImageCopy;
import club.doki7.vulkan.datatype.VkSamplerCreateInfo;
import club.doki7.vulkan.enumtype.VkAccelerationStructureBuildTypeKHR;
import club.doki7.vulkan.enumtype.VkAccelerationStructureTypeKHR;
import club.doki7.vulkan.enumtype.VkBuildAccelerationStructureModeKHR;
import club.doki7.vulkan.enumtype.VkColorSpaceKHR;
import club.doki7.vulkan.enumtype.VkCommandBufferLevel;
import club.doki7.vulkan.enumtype.VkDescriptorType;
import club.doki7.vulkan.enumtype.VkFilter;
import club.doki7.vulkan.enumtype.VkBorderColor;
import club.doki7.vulkan.enumtype.VkCompareOp;
import club.doki7.vulkan.enumtype.VkSamplerMipmapMode;
import club.doki7.vulkan.enumtype.VkSamplerAddressMode;
import club.doki7.vulkan.enumtype.VkFormat;
import club.doki7.vulkan.enumtype.VkGeometryTypeKHR;
import club.doki7.vulkan.enumtype.VkImageLayout;
import club.doki7.vulkan.enumtype.VkImageTiling;
import club.doki7.vulkan.enumtype.VkImageType;
import club.doki7.vulkan.enumtype.VkImageViewType;
import club.doki7.vulkan.enumtype.VkIndexType;
import club.doki7.vulkan.enumtype.VkPipelineBindPoint;
import club.doki7.vulkan.enumtype.VkPresentModeKHR;
import club.doki7.vulkan.enumtype.VkRayTracingShaderGroupTypeKHR;
import club.doki7.vulkan.enumtype.VkResult;
import club.doki7.vulkan.enumtype.VkSharingMode;
import club.doki7.vulkan.enumtype.VkStructureType;
import club.doki7.vulkan.handle.VkAccelerationStructureKHR;
import club.doki7.vulkan.handle.VkBuffer;
import club.doki7.vulkan.handle.VkCommandBuffer;
import club.doki7.vulkan.handle.VkCommandPool;
import club.doki7.vulkan.handle.VkDebugUtilsMessengerEXT;
import club.doki7.vulkan.handle.VkDescriptorPool;
import club.doki7.vulkan.handle.VkDescriptorSet;
import club.doki7.vulkan.handle.VkDescriptorSetLayout;
import club.doki7.vulkan.handle.VkDevice;
import club.doki7.vulkan.handle.VkDeviceMemory;
import club.doki7.vulkan.handle.VkFence;
import club.doki7.vulkan.handle.VkImage;
import club.doki7.vulkan.handle.VkImageView;
import club.doki7.vulkan.handle.VkInstance;
import club.doki7.vulkan.handle.VkPhysicalDevice;
import club.doki7.vulkan.handle.VkPipeline;
import club.doki7.vulkan.handle.VkPipelineLayout;
import club.doki7.vulkan.handle.VkQueue;
import club.doki7.vulkan.handle.VkSemaphore;
import club.doki7.vulkan.handle.VkShaderModule;
import club.doki7.vulkan.handle.VkSurfaceKHR;
import club.doki7.vulkan.handle.VkSwapchainKHR;
import club.doki7.vulkan.handle.VkSampler;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import static club.doki7.ffm.NativeLayout.UINT64_MAX;
import static tutorial.vulkan.raytracing.VulkanUtil.alignUp;
import static tutorial.vulkan.raytracing.VulkanUtil.checkResult;
import static tutorial.vulkan.raytracing.VulkanUtil.createImageBarrier;
import static tutorial.vulkan.raytracing.VulkanUtil.createVmaBuffer;
import static tutorial.vulkan.raytracing.VulkanUtil.populateDebugMessengerCreateInfo;

/**
 * Chapter 26 — Ray Tracing: Textured Rotating Quad.
 *
 * <p>Renders the same textured rotating quad as the graphics pipeline in
 * part08/ch26/Main.java, but using ray tracing instead of rasterization.
 * The quad has 4 vertices with interpolated vertex colors, UV coordinates,
 * and a loaded texture (texture.png) sampled in the closest hit shader.
 * The quad rotates 90 degrees per second around the Z axis, viewed from (2,2,2).</p>
 */
public class Application {

    // ======================== Window Configuration ========================
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final BytePtr WINDOW_TITLE = BytePtr.allocateString(Arena.global(), "Chapter 26 - Ray Traced Textured Rotating Quad");

    // ======================== Validation Layers ========================
    private static final boolean ENABLE_VALIDATION_LAYERS = System.getProperty("validation") != null;
    private static final String VALIDATION_LAYER_NAME = "VK_LAYER_KHRONOS_validation";

    // ======================== Frame & Sync ========================
    private static final int MAX_FRAMES_IN_FLIGHT = 2;

    // ======================== Camera Parameters ========================
    private static final float CAMERA_FOV = 45.0f;
    private static final float CAMERA_NEAR = 0.1f;
    private static final float CAMERA_FAR = 10.0f;
    private static final Vector3f CAMERA_POSITION = new Vector3f(0.0f, 0.0f, 3.0f);
    private static final Vector3f CAMERA_LOOK_AT = new Vector3f(0.0f, 0.0f, 0.0f);
    private static final Vector3f CAMERA_UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final float ROTATION_SPEED_DEG_PER_SEC = 90.0f;
    private static final Vector3f ROTATION_AXIS = new Vector3f(1.0f, 0.0f, 0.0f);

    // ======================== Vertex & Geometry ========================
    private static final int VERTEX_STRIDE_BYTES = 28; // 7 floats * 4 bytes (pos: 2 + color: 3 + uv: 2)

    // ======================== Push Constants ========================
    private static final int MATRIX4F_FLOAT_COUNT = 16;
    private static final int PUSH_CONSTANT_FLOAT_COUNT = MATRIX4F_FLOAT_COUNT * 3; // model + invProj + invView
    private static final int PUSH_CONSTANT_SIZE_BYTES = PUSH_CONSTANT_FLOAT_COUNT * Float.BYTES;

    // ======================== Alignment & Padding ========================
    private static final int MIN_BUFFER_PADDING = 4096;
    private static final int SBT_REGION_PADDING = 4096;
    private static final int INSTANCE_STRUCT_SIZE = 64; // VkAccelerationStructureInstanceKHR size

    // ======================== TLAS Instance Layout ========================
    private static final int INSTANCE_TRANSFORM_ELEMENTS = 12; // 3x4 row-major matrix
    private static final int INSTANCE_CUSTOM_INDEX_MASK_OFFSET = 48;
    private static final int INSTANCE_SBT_OFFSET_FLAGS_OFFSET = 52;
    private static final int INSTANCE_ACCEL_REF_OFFSET = 56;
    private static final int INSTANCE_MASK_SHIFT = 24;
    private static final int INSTANCE_FLAGS_SHIFT = 24;

    // ======================== Shader Binding Table ========================
    private static final int SHADER_GROUP_COUNT = 3; // raygen + miss + closestHit

    // ======================== GPU Vendor IDs ========================
    private static final int VENDOR_ID_AMD = 0x1002;

    // ======================== Ray Tracing Properties (cached once) ========================
    private VkPhysicalDeviceRayTracingPipelinePropertiesKHR rtProps;
    private int handleSize;
    private int handleAlignment;
    private int shaderGroupBaseAlignment;
    private int sbtRecordSize;
    private long raygenAddress;
    private long missAddress;
    private long hitAddress;

    // ======================== Core Vulkan Handles ========================
    private GLFWwindow window;
    private VkStaticCommands staticCommands;
    private VkEntryCommands entryCommands;
    private VkInstance instance;
    private VkInstanceCommands instanceCommands;
    private VkDebugUtilsMessengerEXT debugMessenger;
    private VkSurfaceKHR surface;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkDeviceCommands deviceCommands;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;

    // ======================== Swapchain ========================
    private VkSwapchainKHR swapChain;
    private VkImage.Ptr swapChainImages;
    private @EnumType(VkFormat.class) int swapChainImageFormat;
    private VkExtent2D swapChainExtent;
    private VkImageView.Ptr swapChainImageViews;
    private Arena swapchainArena = Arena.ofShared();

    // ======================== Ray Tracing Resources ========================
    private VkBuffer vertexBuffer;
    private VmaAllocation vertexBufferAllocation;
    private VkBuffer indexBuffer;
    private VmaAllocation indexBufferAllocation;
    private VkBuffer blasBuffer;
    private VmaAllocation blasAllocation;
    private VkBuffer tlasBuffer;
    private VmaAllocation tlasAllocation;
    private VkAccelerationStructureKHR blas;
    private VkAccelerationStructureKHR tlas;
    private VkImage outputImage;
    private VmaAllocation outputImageAllocation;
    private VkImageView outputImageView;
    private VkDescriptorSetLayout descriptorSetLayout;
    private VkPipelineLayout pipelineLayout;
    private VkPipeline rayTracingPipeline;
    private VkDescriptorPool descriptorPool;
    private VkDescriptorSet descriptorSet;
    private VkBuffer sbtBuffer;
    private VmaAllocation sbtAllocation;

    // ======================== Texture Resources ========================
    private VkImage textureImage;
    private VmaAllocation textureImageAllocation;
    private VkImageView textureImageView;
    private VkSampler textureSampler;

    // ======================== VMA ========================
    private VmaAllocator vmaAllocator;

    // ======================== Synchronization ========================
    private VkSemaphore[] imageAvailableSemaphores;
    private VkSemaphore[] renderFinishedSemaphores;
    private VkFence[] inFlightFences;
    private VkCommandPool commandPool;
    private VkCommandBuffer[] commandBuffers;
    private int currentFrame = 0;
    private boolean framebufferResized = false;
    private boolean needsSwapchainRecreation = false;
    private long startTime;

    // ======================== Native Libraries ========================
    private static final ISharedLibrary libGLFW = GLFWLoader.loadGLFWLibrary();
    private static final GLFW glfw = GLFWLoader.loadGLFW(libGLFW);
    private static final ISharedLibrary libVulkan = VulkanLoader.loadVulkanLibrary();
    private static final ISharedLibrary libVMA = ILibraryLoader.platformLoader().loadLibrary("vma");
    private static final VMA vma = new VMA(libVMA);

    // ======================== Entry Point ========================
    public void run() {
        startTime = System.currentTimeMillis();
        initWindow();
        initVulkan();
        mainLoop();
        cleanup();
    }

    private void initWindow() {
        if (glfw.init() != GLFW.TRUE) throw new RuntimeException("Failed to initialize GLFW");
        if (glfw.vulkanSupported() != GLFW.TRUE) throw new RuntimeException("Vulkan is not supported");
        glfw.windowHint(GLFW.CLIENT_API, GLFW.NO_API);
        window = Objects.requireNonNull(glfw.createWindow(WIDTH, HEIGHT, WINDOW_TITLE, null, null));
        glfw.setFramebufferSizeCallback(window, (_, width, height) -> {
            if (width > 0 && height > 0) framebufferResized = true;
        });
    }

    private void initVulkan() {
        staticCommands = VulkanLoader.loadStaticCommands(libVulkan);
        entryCommands = VulkanLoader.loadEntryCommands(staticCommands);
        createInstance();
        setupDebugMessenger();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
        createVMA();
        createCommandPool();
        createSwapchain();
        createImageViews();
        createOutputImage();
        createTextureImage();
        createTextureImageView();
        createTextureSampler();
        createVertexBuffer();
        createIndexBuffer();
        createAccelerationStructures();
        createDescriptorSetLayout();
        createDescriptorPool();
        createDescriptorSet();
        createRayTracingPipeline();
        createSyncObjects();
        createShaderBindingTable();
        createCommandBuffers();
        transitionOutputImageToGeneral();
    }

    private void mainLoop() {
        while (glfw.windowShouldClose(window) == GLFW.FALSE) {
            glfw.pollEvents();
            if (needsSwapchainRecreation) {
                recreateSwapChain();
                needsSwapchainRecreation = false;
                continue;
            }
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
        deviceCommands.deviceWaitIdle(device);
    }

    private void cleanup() {
        deviceCommands.deviceWaitIdle(device);
        cleanupCommandPool();
        cleanupShaderBindingTable();
        cleanupRayTracingPipeline();
        cleanupDescriptorResources();
        cleanupAccelerationStructures();
        cleanupBuffers();
        cleanupOutputImage();
        cleanupTextureResources();
        cleanupSwapChain();
        cleanupSyncObjects();
        cleanupVulkanHandles();
        glfw.destroyWindow(window);
        glfw.terminate();
    }

    private void cleanupCommandPool() {
        if (commandPool != null) {
            deviceCommands.destroyCommandPool(device, commandPool, null);
            commandPool = null;
        }
    }

    private void cleanupShaderBindingTable() {
        if (sbtBuffer != null) {
            vma.destroyBuffer(vmaAllocator, sbtBuffer, sbtAllocation);
            sbtBuffer = null;
            sbtAllocation = null;
        }
    }

    private void cleanupRayTracingPipeline() {
        if (rayTracingPipeline != null) {
            deviceCommands.destroyPipeline(device, rayTracingPipeline, null);
            rayTracingPipeline = null;
        }
        if (pipelineLayout != null) {
            deviceCommands.destroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = null;
        }
    }

    private void cleanupDescriptorResources() {
        if (descriptorPool != null) {
            deviceCommands.destroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = null;
            descriptorSet = null;
        }
        if (descriptorSetLayout != null) {
            deviceCommands.destroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = null;
        }
    }

    private void cleanupAccelerationStructures() {
        if (blas != null) {
            deviceCommands.destroyAccelerationStructureKHR(device, blas, null);
            blas = null;
        }
        if (tlas != null) {
            deviceCommands.destroyAccelerationStructureKHR(device, tlas, null);
            tlas = null;
        }
        if (blasBuffer != null) {
            vma.destroyBuffer(vmaAllocator, blasBuffer, blasAllocation);
            blasBuffer = null;
            blasAllocation = null;
        }
        if (tlasBuffer != null) {
            vma.destroyBuffer(vmaAllocator, tlasBuffer, tlasAllocation);
            tlasBuffer = null;
            tlasAllocation = null;
        }
    }

    private void cleanupBuffers() {
        if (vertexBuffer != null) {
            vma.destroyBuffer(vmaAllocator, vertexBuffer, vertexBufferAllocation);
            vertexBuffer = null;
            vertexBufferAllocation = null;
        }
        if (indexBuffer != null) {
            vma.destroyBuffer(vmaAllocator, indexBuffer, indexBufferAllocation);
            indexBuffer = null;
            indexBufferAllocation = null;
        }
    }

    private void cleanupOutputImage() {
        if (outputImageView != null) {
            deviceCommands.destroyImageView(device, outputImageView, null);
            outputImageView = null;
        }
        if (outputImage != null) {
            vma.destroyImage(vmaAllocator, outputImage, outputImageAllocation);
            outputImage = null;
            outputImageAllocation = null;
        }
    }

    private void cleanupTextureResources() {
        if (textureSampler != null) {
            deviceCommands.destroySampler(device, textureSampler, null);
            textureSampler = null;
        }
        if (textureImageView != null) {
            deviceCommands.destroyImageView(device, textureImageView, null);
            textureImageView = null;
        }
        if (textureImage != null) {
            vma.destroyImage(vmaAllocator, textureImage, textureImageAllocation);
            textureImage = null;
            textureImageAllocation = null;
        }
    }

    private void cleanupSyncObjects() {
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
    }

    private void cleanupVulkanHandles() {
        if (vmaAllocator != null) {
            vma.destroyAllocator(vmaAllocator);
            vmaAllocator = null;
        }

        if (device != null) {
            deviceCommands.destroyDevice(device, null);
            device = null;
        }
        if (surface != null) {
            instanceCommands.destroySurfaceKHR(instance, surface, null);
            surface = null;
        }
        if (ENABLE_VALIDATION_LAYERS && debugMessenger != null) {
            instanceCommands.destroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
            debugMessenger = null;
        }
        if (instance != null) {
            instanceCommands.destroyInstance(instance, null);
            instance = null;
        }
    }

    // ======================== Instance Creation ========================
    private void createInstance() {
        try (var arena = Arena.ofConfined()) {
            if (ENABLE_VALIDATION_LAYERS && !checkValidationLayerSupport()) {
                throw new RuntimeException("Validation layers requested, but not available");
            }
            var appInfo = VkApplicationInfo.allocate(arena)
                    .pApplicationName(BytePtr.allocateString(arena, "Ch23 Ray Tracing"))
                    .applicationVersion(new Version(0, 1, 0, 0).encode())
                    .pEngineName(BytePtr.allocateString(arena, "No Engine"))
                    .engineVersion(new Version(0, 1, 0, 0).encode())
                    .apiVersion(Version.VK_API_VERSION_1_2.encode());

            var instanceCreateInfo = VkInstanceCreateInfo.allocate(arena).pApplicationInfo(appInfo);

            if (ENABLE_VALIDATION_LAYERS) {
                instanceCreateInfo.enabledLayerCount(1)
                        .ppEnabledLayerNames(PointerPtr.allocateStrings(arena, VALIDATION_LAYER_NAME));
                var debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate(arena);
                populateDebugMessengerCreateInfo(debugCreateInfo);
                instanceCreateInfo.pNext(debugCreateInfo);
            }

            var extensions = getRequiredExtensions(arena);
            instanceCreateInfo.enabledExtensionCount((int) extensions.size())
                    .ppEnabledExtensionNames(extensions);

            var pInstance = VkInstance.Ptr.allocate(arena);
            var result = entryCommands.createInstance(instanceCreateInfo, null, pInstance);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create instance: " + VkResult.explain(result));
            instance = Objects.requireNonNull(pInstance.read());
            instanceCommands = VulkanLoader.loadInstanceCommands(instance, staticCommands);
        }
    }

    private boolean checkValidationLayerSupport() {
        try (var arena = Arena.ofConfined()) {
            var pCount = IntPtr.allocate(arena);
            entryCommands.enumerateInstanceLayerProperties(pCount, null);
            var count = pCount.read();
            var layers = VkLayerProperties.allocate(arena, count);
            entryCommands.enumerateInstanceLayerProperties(pCount, layers);
            for (var layer : layers) {
                if (VALIDATION_LAYER_NAME.equals(layer.layerName().readString())) return true;
            }
            return false;
        }
    }

    private PointerPtr getRequiredExtensions(Arena arena) {
        try (var local = Arena.ofConfined()) {
            var pCount = IntPtr.allocate(local);
            var extensions = glfw.getRequiredInstanceExtensions(pCount);
            if (extensions == null) throw new RuntimeException("Failed to get GLFW required extensions");
            var count = pCount.read();
            extensions = extensions.reinterpret(count);
            var all = PointerPtr.allocate(arena, count + 1 + (ENABLE_VALIDATION_LAYERS ? 1 : 0));
            for (int i = 0; i < count; i++) all.write(i, extensions.read(i));
            int idx = count;
            all.write(idx++, BytePtr.allocateString(arena, VkConstants.KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME));
            if (ENABLE_VALIDATION_LAYERS) {
                all.write(idx++, BytePtr.allocateString(arena, VkConstants.EXT_DEBUG_UTILS_EXTENSION_NAME));
            }
            return all;
        }
    }

    private void setupDebugMessenger() {
        if (!ENABLE_VALIDATION_LAYERS) return;
        try (var arena = Arena.ofConfined()) {
            var createInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate(arena);
            populateDebugMessengerCreateInfo(createInfo);
            var pMessenger = VkDebugUtilsMessengerEXT.Ptr.allocate(arena);
            var result = instanceCommands.createDebugUtilsMessengerEXT(instance, createInfo, null, pMessenger);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to set up debug messenger: " + VkResult.explain(result));
            debugMessenger = Objects.requireNonNull(pMessenger.read());
        }
    }

    private void createSurface() {
        try (var arena = Arena.ofConfined()) {
            var pSurface = VkSurfaceKHR.Ptr.allocate(arena);
            var result = glfw.createWindowSurface(instance, window, null, pSurface);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create surface: " + VkResult.explain(result));
            surface = Objects.requireNonNull(pSurface.read());
        }
    }

    // ======================== Physical Device ========================
    private void pickPhysicalDevice() {
        try (var arena = Arena.ofConfined()) {
            var pCount = IntPtr.allocate(arena);
            var result = instanceCommands.enumeratePhysicalDevices(instance, pCount, null);
            checkResult(result, "Failed to enumerate physical devices");
            int count = pCount.read();
            if (count == 0) throw new RuntimeException("No GPUs with Vulkan support");
            var devices = VkPhysicalDevice.Ptr.allocate(arena, count);
            result = instanceCommands.enumeratePhysicalDevices(instance, pCount, devices);
            checkResult(result, "Failed to enumerate physical devices");
            for (var dev : devices) {
                if (isDeviceSuitable(dev)) {
                    physicalDevice = dev;
                    checkGpuVendor(physicalDevice);
                    return;
                }
            }
            if (physicalDevice == null) throw new RuntimeException("No suitable GPU found");
        }
    }

    private void checkGpuVendor(VkPhysicalDevice device) {
        try (var arena = Arena.ofConfined()) {
            var props = VkPhysicalDeviceProperties.allocate(arena);
            instanceCommands.getPhysicalDeviceProperties(device, props);
            if (props.vendorID() == VENDOR_ID_AMD) {
                System.setProperty("AMD_FORCE_VULKAN_RAY_TRACING", "1");
                System.setProperty("AMD_RAY_TRACING_DEBUG", "1");
            }
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

    // ======================== Logical Device ========================
    private void createLogicalDevice() {
        var indices = findQueueFamilies(physicalDevice);
        if (indices == null) throw new IllegalStateException("No suitable queue family indices found");
        try (var arena = Arena.ofConfined()) {
            var priorities = FloatPtr.allocateV(arena, 1.0f);
            var queueCreateInfo = VkDeviceQueueCreateInfo.allocate(arena)
                    .queueFamilyIndex(indices.graphicsFamily())
                    .queueCount(1)
                    .pQueuePriorities(priorities);

            var physicalDeviceFeatures = VkPhysicalDeviceFeatures.allocate(arena)
                    .samplerAnisotropy(VkConstants.TRUE);

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

            var rtPipelineFeatures = VkPhysicalDeviceRayTracingPipelineFeaturesKHR.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR)
                    .rayTracingPipeline(VkConstants.TRUE)
                    .rayTracingPipelineShaderGroupHandleCaptureReplay(VkConstants.TRUE);

            var coherentMemoryFeatures = VkPhysicalDeviceCoherentMemoryFeaturesAMD.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_COHERENT_MEMORY_FEATURES_AMD)
                    .deviceCoherentMemory(VkConstants.TRUE);

            vulkan12Features.pNext(asFeatures.segment());
            asFeatures.pNext(rtPipelineFeatures.segment());
            rtPipelineFeatures.pNext(coherentMemoryFeatures.segment());

            rtProps = VkPhysicalDeviceRayTracingPipelinePropertiesKHR.allocate(Arena.global())
                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR);
            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(rtProps.segment());
            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);

            handleSize = rtProps.shaderGroupHandleSize();
            handleAlignment = rtProps.shaderGroupHandleAlignment();
            shaderGroupBaseAlignment = rtProps.shaderGroupBaseAlignment();

            String[] deviceExtensions = {
                    VkConstants.KHR_SWAPCHAIN_EXTENSION_NAME,
                    VkConstants.KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
                    VkConstants.KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
                    VkConstants.KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME,
                    VkConstants.KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME,
                    VkConstants.AMD_DEVICE_COHERENT_MEMORY_EXTENSION_NAME
            };

            var deviceCreateInfo = VkDeviceCreateInfo.allocate(arena)
                    .pQueueCreateInfos(queueCreateInfo)
                    .queueCreateInfoCount(1)
                    .pEnabledFeatures(physicalDeviceFeatures)
                    .enabledExtensionCount(deviceExtensions.length)
                    .ppEnabledExtensionNames(PointerPtr.allocateStrings(arena, deviceExtensions))
                    .pNext(vulkan12Features.segment());

            if (ENABLE_VALIDATION_LAYERS) {
                deviceCreateInfo.enabledLayerCount(1)
                        .ppEnabledLayerNames(PointerPtr.allocateStrings(arena, VALIDATION_LAYER_NAME));
            }

            var pDevice = VkDevice.Ptr.allocate(arena);
            var result = instanceCommands.createDevice(physicalDevice, deviceCreateInfo, null, pDevice);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create logical device: " + VkResult.explain(result));
            device = Objects.requireNonNull(pDevice.read());
            deviceCommands = VulkanLoader.loadDeviceCommands(device, staticCommands);

            var pQueue = VkQueue.Ptr.allocate(arena);
            deviceCommands.getDeviceQueue(device, indices.graphicsFamily(), 0, pQueue);
            graphicsQueue = Objects.requireNonNull(pQueue.read());
            deviceCommands.getDeviceQueue(device, indices.presentFamily(), 0, pQueue);
            presentQueue = Objects.requireNonNull(pQueue.read());
        }
    }

    // ======================== VMA ========================
    private void createVMA() {
        try (var arena = Arena.ofConfined()) {
            var funcs = VmaVulkanFunctions.allocate(arena);
            VMAUtil.fillVulkanFunctions(funcs, staticCommands, entryCommands, instanceCommands, deviceCommands);
            var info = VmaAllocatorCreateInfo.allocate(arena)
                    .instance(instance)
                    .physicalDevice(physicalDevice)
                    .device(device)
                    .vulkanApiVersion(Version.VK_API_VERSION_1_2.encode())
                    .pVulkanFunctions(funcs)
                    .flags(VmaAllocatorCreateFlags.BUFFER_DEVICE_ADDRESS);
            var pAlloc = VmaAllocator.Ptr.allocate(arena);
            var result = vma.createAllocator(info, pAlloc);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create VMA allocator: " + VkResult.explain(result));
            }
            vmaAllocator = Objects.requireNonNull(pAlloc.read());
        }
    }

    // NOTE: shaderc does NOT support ray tracing extensions (libshaderc uses old SPIR-V target).
    // Using glslangValidator via ProcessBuilder instead.
    // ======================== Sync Objects ========================
    private void createSyncObjects() {
        imageAvailableSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
        renderFinishedSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
        inFlightFences = new VkFence[MAX_FRAMES_IN_FLIGHT];
        try (var arena = Arena.ofConfined()) {
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                imageAvailableSemaphores[i] = createSemaphore(arena);
                renderFinishedSemaphores[i] = createSemaphore(arena);
                inFlightFences[i] = createFence(arena, VkFenceCreateFlags.SIGNALED);
            }
        }
    }

    private VkSemaphore createSemaphore(Arena arena) {
        var semInfo = VkSemaphoreCreateInfo.allocate(arena);
        var pSem = VkSemaphore.Ptr.allocate(arena);
        var result = deviceCommands.createSemaphore(device, semInfo, null, pSem);
        if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create semaphore: " + VkResult.explain(result));
        return Objects.requireNonNull(pSem.read());
    }

    private VkFence createFence(Arena arena, int flags) {
        var fenceInfo = VkFenceCreateInfo.allocate(arena).flags(flags);
        var pFence = VkFence.Ptr.allocate(arena);
        var result = deviceCommands.createFence(device, fenceInfo, null, pFence);
        if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create fence: " + VkResult.explain(result));
        return Objects.requireNonNull(pFence.read());
    }

    // ======================== Command Pool ========================
    private void createCommandPool() {
        try (var arena = Arena.ofConfined()) {
            var indices = findQueueFamilies(physicalDevice);
            var poolInfo = VkCommandPoolCreateInfo.allocate(arena)
                    .flags(VkCommandPoolCreateFlags.RESET_COMMAND_BUFFER)
                    .queueFamilyIndex(indices.graphicsFamily());
            var pPool = VkCommandPool.Ptr.allocate(arena);
            checkResult(deviceCommands.createCommandPool(device, poolInfo, null, pPool), "Failed to create command pool");
            commandPool = Objects.requireNonNull(pPool.read());
        }
    }

    // ======================== Swapchain ========================
    private void createSwapchain() {
        try (var arena = Arena.ofConfined()) {
            var support = querySwapChainSupport(physicalDevice, arena);
            var format = chooseSwapSurfaceFormat(support.formats());
            var presentMode = chooseSwapPresentMode(support.presentModes());
            var extent = chooseSwapExtent(support.capabilities(), arena);
            var imageCount = Math.min(support.capabilities.maxImageCount(),
                    Math.max(support.capabilities.minImageCount() + 1, 2));
            var indices = findQueueFamilies(physicalDevice);
            var createInfo = VkSwapchainCreateInfoKHR.allocate(arena)
                    .surface(surface)
                    .minImageCount(imageCount)
                    .imageFormat(format.format())
                    .imageColorSpace(format.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VkImageUsageFlags.COLOR_ATTACHMENT | VkImageUsageFlags.TRANSFER_DST);
            if (indices.graphicsFamily() != indices.presentFamily()) {
                var qfi = IntPtr.allocateV(arena, indices.graphicsFamily(), indices.presentFamily());
                createInfo.imageSharingMode(VkSharingMode.CONCURRENT)
                        .queueFamilyIndexCount(2)
                        .pQueueFamilyIndices(qfi);
            } else {
                createInfo.imageSharingMode(VkSharingMode.EXCLUSIVE);
            }
            createInfo.preTransform(support.capabilities.currentTransform())
                    .compositeAlpha(VkCompositeAlphaFlagsKHR.OPAQUE)
                    .presentMode(presentMode)
                    .clipped(VkConstants.TRUE);

            var pSwapchain = VkSwapchainKHR.Ptr.allocate(arena);
            var result = deviceCommands.createSwapchainKHR(device, createInfo, null, pSwapchain);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create swapchain: " + VkResult.explain(result));
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
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create image view: " + VkResult.explain(result));
            return Objects.requireNonNull(pView.read());
        }
    }

    private void cleanupSwapChain() {
        if (swapChainImageViews != null) {
            for (long i = 0; i < swapChainImageViews.size(); i++) {
                var imageView = swapChainImageViews.read(i);
                if (imageView != null) {
                    deviceCommands.destroyImageView(device, imageView, null);
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
        try { swapchainArena.close(); } catch (Exception ignored) {}
        swapchainArena = Arena.ofShared();
    }

    private void recreateSwapChain() {
        try (var arena = Arena.ofConfined()) {
            IntPtr w = IntPtr.allocate(arena), h = IntPtr.allocate(arena);
            glfw.getFramebufferSize(window, w, h);
            int width = w.read();
            int height = h.read();
            if (width <= 0 || height <= 0) return;
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

    // ======================== Output Image ========================
    private void createOutputImage() {
        try (var arena = Arena.ofConfined()) {
            int width = Math.max(1, swapChainExtent.width());
            int height = Math.max(1, swapChainExtent.height());
            var imageInfo = VkImageCreateInfo.allocate(arena)
                    .imageType(VkImageType._2D)
                    .format(VkFormat.R8G8B8A8_UNORM)
                    .extent(VkExtent3D.allocate(arena).width(width).height(height).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VkSampleCountFlags._1)
                    .tiling(VkImageTiling.OPTIMAL)
                    .usage(VkImageUsageFlags.STORAGE | VkImageUsageFlags.TRANSFER_SRC | VkImageUsageFlags.TRANSFER_DST)
                    .initialLayout(VkImageLayout.UNDEFINED);
            var allocInfo = VmaAllocationCreateInfo.allocate(arena).usage(VmaMemoryUsage.GPU_ONLY);
            var pImage = VkImage.Ptr.allocate(arena);
            var pAlloc = VmaAllocation.Ptr.allocate(arena);
            checkResult(vma.createImage(vmaAllocator, imageInfo, allocInfo, pImage, pAlloc, null), "Failed to create output image");
            outputImage = Objects.requireNonNull(pImage.read());
            outputImageAllocation = Objects.requireNonNull(pAlloc.read());

            var viewInfo = VkImageViewCreateInfo.allocate(arena)
                    .image(outputImage)
                    .viewType(VkImageViewType._2D)
                    .format(VkFormat.R8G8B8A8_UNORM)
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1));
            var pView = VkImageView.Ptr.allocate(arena);
            checkResult(deviceCommands.createImageView(device, viewInfo, null, pView), "Failed to create output image view");
            outputImageView = Objects.requireNonNull(pView.read());
        }
    }

    private void transitionOutputImageToGeneral() {
        try (var arena = Arena.ofConfined()) {
            var bundle = beginSingleTimeCommands();
            var barrier = createImageBarrier(arena, outputImage,
                    VkImageLayout.UNDEFINED, VkImageLayout.GENERAL, 0, 0);
            deviceCommands.cmdPipelineBarrier(bundle.cmd,
                    VkPipelineStageFlags.TOP_OF_PIPE, VkPipelineStageFlags.FRAGMENT_SHADER,
                    0, 0, null, 0, null, 1, barrier);
            endSingleTimeCommands(bundle);
        }
    }

    // ======================== Texture Image ========================
    private void createTextureImage() {
        BufferedImage image;
        try (var stream = Application.class.getResourceAsStream("/texture/texture.png")) {
            if (stream == null) throw new RuntimeException("Failed to load texture image");
            image = ImageIO.read(stream);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load texture image", e);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int imageSize = width * height * 4;

        try (var arena = Arena.ofConfined()) {
            // Staging buffer
            var stagingResult = createVmaBuffer(vma, vmaAllocator, arena, imageSize,
                    VkBufferUsageFlags.TRANSFER_SRC,
                    VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                    VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT);
            var stagingBuffer = stagingResult.buffer();
            var stagingAllocation = stagingResult.allocation();

            var ppData = PointerPtr.allocate(arena);
            vma.mapMemory(vmaAllocator, stagingAllocation, ppData);
            var seg = Objects.requireNonNull(ppData.read()).reinterpret(imageSize);
            int idx = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    var color = new Color(image.getRGB(x, y), true);
                    seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getRed());
                    seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getGreen());
                    seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getBlue());
                    seg.set(ValueLayout.JAVA_BYTE, idx++, (byte) color.getAlpha());
                }
            }
            vma.unmapMemory(vmaAllocator, stagingAllocation);

            // Create texture image
            var imageInfo = VkImageCreateInfo.allocate(arena)
                    .imageType(VkImageType._2D)
                    .format(VkFormat.R8G8B8A8_SRGB)
                    .extent(VkExtent3D.allocate(arena).width(width).height(height).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VkSampleCountFlags._1)
                    .tiling(VkImageTiling.OPTIMAL)
                    .usage(VkImageUsageFlags.TRANSFER_DST | VkImageUsageFlags.SAMPLED)
                    .initialLayout(VkImageLayout.UNDEFINED);
            var allocInfo = VmaAllocationCreateInfo.allocate(arena).usage(VmaMemoryUsage.GPU_ONLY);
            var pImage = VkImage.Ptr.allocate(arena);
            var pAlloc = VmaAllocation.Ptr.allocate(arena);
            checkResult(vma.createImage(vmaAllocator, imageInfo, allocInfo, pImage, pAlloc, null), "Failed to create texture image");
            textureImage = Objects.requireNonNull(pImage.read());
            textureImageAllocation = Objects.requireNonNull(pAlloc.read());

            transitionImageLayout(VkFormat.R8G8B8A8_SRGB, VkImageLayout.UNDEFINED, VkImageLayout.TRANSFER_DST_OPTIMAL);
            copyBufferToImage(stagingBuffer, width, height);
            transitionImageLayout(VkFormat.R8G8B8A8_SRGB, VkImageLayout.TRANSFER_DST_OPTIMAL, VkImageLayout.SHADER_READ_ONLY_OPTIMAL);

            vma.destroyBuffer(vmaAllocator, stagingBuffer, stagingAllocation);
        }
    }

    private void createTextureImageView() {
        try (var arena = Arena.ofConfined()) {
            var info = VkImageViewCreateInfo.allocate(arena)
                    .image(textureImage)
                    .viewType(VkImageViewType._2D)
                    .format(VkFormat.R8G8B8A8_SRGB)
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
            var pView = VkImageView.Ptr.allocate(arena);
            checkResult(deviceCommands.createImageView(device, info, null, pView), "Failed to create texture image view");
            textureImageView = Objects.requireNonNull(pView.read());
        }
    }

    private void createTextureSampler() {
        try (var arena = Arena.ofConfined()) {
            var props = VkPhysicalDeviceProperties.allocate(arena);
            instanceCommands.getPhysicalDeviceProperties(physicalDevice, props);

            var samplerInfo = VkSamplerCreateInfo.allocate(arena)
                    .magFilter(VkFilter.LINEAR)
                    .minFilter(VkFilter.LINEAR)
                    .addressModeU(VkSamplerAddressMode.REPEAT)
                    .addressModeV(VkSamplerAddressMode.REPEAT)
                    .addressModeW(VkSamplerAddressMode.REPEAT)
                    .anisotropyEnable(VkConstants.TRUE)
                    .maxAnisotropy(props.limits().maxSamplerAnisotropy())
                    .borderColor(VkBorderColor.INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(VkConstants.FALSE)
                    .compareEnable(VkConstants.FALSE)
                    .compareOp(VkCompareOp.ALWAYS)
                    .mipmapMode(VkSamplerMipmapMode.LINEAR)
                    .mipLodBias(0.0f)
                    .minLod(0.0f)
                    .maxLod(0.0f);
            var pSampler = VkSampler.Ptr.allocate(arena);
            var result = deviceCommands.createSampler(device, samplerInfo, null, pSampler);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create texture sampler: " + VkResult.explain(result));
            textureSampler = Objects.requireNonNull(pSampler.read());
        }
    }

    private void transitionImageLayout(int format, int oldLayout, int newLayout) {
        try (var arena = Arena.ofConfined()) {
            int srcAccessMask = switch (oldLayout) {
                case VkImageLayout.UNDEFINED -> 0;
                case VkImageLayout.TRANSFER_DST_OPTIMAL -> VkAccessFlags.TRANSFER_WRITE;
                default -> 0;
            };
            int dstAccessMask = switch (newLayout) {
                case VkImageLayout.TRANSFER_DST_OPTIMAL -> VkAccessFlags.TRANSFER_WRITE;
                case VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> VkAccessFlags.SHADER_READ;
                default -> 0;
            };

            int srcStage = switch (oldLayout) {
                case VkImageLayout.UNDEFINED -> VkPipelineStageFlags.TOP_OF_PIPE;
                case VkImageLayout.TRANSFER_DST_OPTIMAL -> VkPipelineStageFlags.TRANSFER;
                default -> VkPipelineStageFlags.TOP_OF_PIPE;
            };
            int dstStage = switch (newLayout) {
                case VkImageLayout.TRANSFER_DST_OPTIMAL -> VkPipelineStageFlags.TRANSFER;
                case VkImageLayout.SHADER_READ_ONLY_OPTIMAL -> VkPipelineStageFlags.FRAGMENT_SHADER;
                default -> VkPipelineStageFlags.TOP_OF_PIPE;
            };

            var barrier = createImageBarrier(arena, textureImage, oldLayout, newLayout, srcAccessMask, dstAccessMask);
            var bundle = beginSingleTimeCommands();
            deviceCommands.cmdPipelineBarrier(bundle.cmd, srcStage, dstStage, 0, 0, null, 0, null, 1, barrier);
            endSingleTimeCommands(bundle);
        }
    }

    private void copyBufferToImage(VkBuffer buffer, int width, int height) {
        try (var arena = Arena.ofConfined()) {
            var region = VkBufferImageCopy.allocate(arena)
                    .bufferOffset(0)
                    .bufferRowLength(0)
                    .bufferImageHeight(0)
                    .imageSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
                    .imageExtent(e -> e.width(width).height(height).depth(1));
            var bundle = beginSingleTimeCommands();
            deviceCommands.cmdCopyBufferToImage(bundle.cmd, buffer, textureImage, VkImageLayout.TRANSFER_DST_OPTIMAL, 1, region);
            endSingleTimeCommands(bundle);
        }
    }

    // ======================== Vertex Buffer ========================
    private void createVertexBuffer() {
        // 4 vertices forming a quad (same as part08/ch26 Main.java)
        // vec2 pos + vec3 color + vec2 UV
        float[] vertices = {
                // Position    // Color       // UV
                -0.5f, -0.5f,  1.0f, 0.0f, 0.0f,  0.0f, 1.0f,   // vertex 0: red,    bottom-left,  UV(0,1)
                 0.5f, -0.5f,  0.0f, 1.0f, 0.0f,  1.0f, 1.0f,   // vertex 1: green,  bottom-right, UV(1,1)
                 0.5f,  0.5f,  0.0f, 0.0f, 1.0f,  1.0f, 0.0f,   // vertex 2: blue,   top-right,    UV(1,0)
                -0.5f,  0.5f,  1.0f, 1.0f, 1.0f,  0.0f, 0.0f,   // vertex 3: white,  top-left,     UV(0,0)
        };
        try (var arena = Arena.ofConfined()) {
            var size = vertices.length * Float.BYTES;

            var bufferPair = createVmaBuffer(vma, vmaAllocator, arena, size,
                    VkBufferUsageFlags.VERTEX_BUFFER | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
                    VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                    VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT);
            vertexBuffer = bufferPair.buffer();
            vertexBufferAllocation = bufferPair.allocation();

            var ppData = PointerPtr.allocate(arena);
            vma.mapMemory(vmaAllocator, vertexBufferAllocation, ppData);
            var seg = ppData.read().reinterpret(size);
            for (int i = 0; i < vertices.length; i++) {
                seg.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, vertices[i]);
            }
            vma.unmapMemory(vmaAllocator, vertexBufferAllocation);
        }
    }

    // ======================== Index Buffer ========================
    private void createIndexBuffer() {
        // 6 indices forming 2 triangles (quad) - same as part07/ch23 Main.java (UINT16)
        short[] indices = {
                0, 1, 2,    // Triangle 1: bottom-left -> bottom-right -> top-right
                2, 3, 0     // Triangle 2: top-right -> top-left -> bottom-left
        };
        try (var arena = Arena.ofConfined()) {
            var size = indices.length * Short.BYTES;

            var bufferPair = createVmaBuffer(vma, vmaAllocator, arena, size,
                    VkBufferUsageFlags.INDEX_BUFFER | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
                    VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                    VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT);
            indexBuffer = bufferPair.buffer();
            indexBufferAllocation = bufferPair.allocation();

            var ppData = PointerPtr.allocate(arena);
            vma.mapMemory(vmaAllocator, indexBufferAllocation, ppData);
            var seg = ppData.read().reinterpret(size);
            for (int i = 0; i < indices.length; i++) {
                seg.set(ValueLayout.JAVA_SHORT, i * Short.BYTES, indices[i]);
            }
            vma.unmapMemory(vmaAllocator, indexBufferAllocation);
        }
    }

    // ======================== Acceleration Structures ========================
    private record CommandBundle(VkCommandBuffer cmd, VkCommandPool pool) {}

    private void createAccelerationStructures() {
        long vertexAddress = getBufferDeviceAddress(vertexBuffer);
        long indexAddress = getBufferDeviceAddress(indexBuffer);
        try (var arena = Arena.ofConfined()) {
            var asProps = VkPhysicalDeviceAccelerationStructurePropertiesKHR.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR);
            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(asProps.segment());
            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);
            int alignment = asProps.minAccelerationStructureScratchOffsetAlignment();

            long blasAddress = createAndBuildBlas(arena, vertexAddress, indexAddress, alignment);
            createAndBuildTlas(arena, blasAddress, alignment);
        }
    }

    private long getBufferDeviceAddress(VkBuffer buffer) {
        try (var arena = Arena.ofConfined()) {
            var info = VkBufferDeviceAddressInfo.allocate(arena)
                    .sType(VkStructureType.BUFFER_DEVICE_ADDRESS_INFO)
                    .buffer(buffer);
            return deviceCommands.getBufferDeviceAddress(device, info);
        }
    }

    private long createAndBuildBlas(Arena arena, long vertexAddress, long indexAddress, int alignment) {
        // 2 triangles = 2 primitives (quad with 6 indices)
        int primitiveCount = 2;
        int maxVertex = 3;  // 4 vertices, so max index is 3

        var triangles = VkAccelerationStructureGeometryTrianglesDataKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
                .vertexFormat(VkFormat.R32G32_SFLOAT)
                .vertexData(vd -> vd.deviceAddress(vertexAddress))
                .vertexStride(VERTEX_STRIDE_BYTES)
                .maxVertex(maxVertex)
                .indexType(VkIndexType.UINT16)
                .indexData(vd -> vd.deviceAddress(indexAddress))
                .transformData(vd -> vd.deviceAddress(0));

        var geometry = VkAccelerationStructureGeometryKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
                .geometryType(VkGeometryTypeKHR.TRIANGLES)
                .geometry(d -> d.triangles(triangles))
                .flags(VkGeometryFlagsKHR.OPAQUE);

        var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
                .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL)
                .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE | VkBuildAccelerationStructureFlagsKHR.ALLOW_UPDATE)
                .mode(VkBuildAccelerationStructureModeKHR.BUILD)
                .geometryCount(1)
                .pGeometries(geometry);

        var buildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.allocate(arena)
                .primitiveCount(primitiveCount).primitiveOffset(0).firstVertex(0).transformOffset(0);
        var ppBuildRangeInfo = PointerPtr.allocate(arena);
        ppBuildRangeInfo.write(buildRangeInfo);

        var maxPrimCount = IntPtr.allocate(arena, 1);
        maxPrimCount.write(0, primitiveCount);
        var sizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
        deviceCommands.getAccelerationStructureBuildSizesKHR(device, VkAccelerationStructureBuildTypeKHR.DEVICE, buildInfo, maxPrimCount, sizeInfo);

        long blasSize = sizeInfo.accelerationStructureSize();
        long scratchSize = sizeInfo.buildScratchSize();
        blasSize = (blasSize + MIN_BUFFER_PADDING - 1) & ~(MIN_BUFFER_PADDING - 1);
        scratchSize = (scratchSize + alignment - 1) & ~(alignment - 1);

        var blasResult = createVmaBuffer(vma, vmaAllocator, arena, blasSize,
                VkBufferUsageFlags.ACCELERATION_STRUCTURE_STORAGE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
                0,
                VkMemoryPropertyFlags.DEVICE_LOCAL);
        blasBuffer = blasResult.buffer();
        blasAllocation = blasResult.allocation();

        var blasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
                .buffer(blasBuffer)
                .size(blasSize)
                .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL);
        var pBlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
        var result = deviceCommands.createAccelerationStructureKHR(device, blasCreateInfo, null, pBlas);
        if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create BLAS: " + VkResult.explain(result));
        blas = Objects.requireNonNull(pBlas.read());

        var scratchResult = createVmaBuffer(vma, vmaAllocator, arena, scratchSize,
                VkBufferUsageFlags.STORAGE_BUFFER | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
                0,
                VkMemoryPropertyFlags.DEVICE_LOCAL);
        var scratchBuffer = scratchResult.buffer();
        var scratchAllocation = scratchResult.allocation();
        long scratchAddress = getBufferDeviceAddress(scratchBuffer);
        long alignedScratchAddress = (scratchAddress + alignment - 1) & ~(alignment - 1);

        buildInfo.scratchData().deviceAddress(alignedScratchAddress);
        buildInfo.dstAccelerationStructure(blas);

        var bundle = beginSingleTimeCommands();

        var memoryBarrier = VkMemoryBarrier.allocate(arena)
                .sType(VkStructureType.MEMORY_BARRIER)
                .srcAccessMask(VkAccessFlags.HOST_WRITE)
                .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
        deviceCommands.cmdPipelineBarrier(bundle.cmd, VkPipelineStageFlags.HOST, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, 0, 1, memoryBarrier, 0, null, 0, null);

        deviceCommands.cmdBuildAccelerationStructuresKHR(bundle.cmd, 1, buildInfo, ppBuildRangeInfo);

        var blasBarrier = VkMemoryBarrier.allocate(arena)
                .sType(VkStructureType.MEMORY_BARRIER)
                .srcAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_WRITE_KHR)
                .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
        deviceCommands.cmdPipelineBarrier(bundle.cmd, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, 0, 1, blasBarrier, 0, null, 0, null);

        endSingleTimeCommands(bundle);

        vma.destroyBuffer(vmaAllocator, scratchBuffer, scratchAllocation);

        var asAddressInfo = VkAccelerationStructureDeviceAddressInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR)
                .accelerationStructure(blas);
        return deviceCommands.getAccelerationStructureDeviceAddressKHR(device, asAddressInfo);
    }

    private void createAndBuildTlas(Arena arena, long blasAddress, int alignment) {
        var instanceData = arena.allocate(INSTANCE_STRUCT_SIZE);

        // Identity transform matrix (3x4 row-major)
        float[] instanceTransform = {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f
        };
        for (int i = 0; i < INSTANCE_TRANSFORM_ELEMENTS; i++) {
            instanceData.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, instanceTransform[i]);
        }

        int customIndexAndMask = 0 | (0xFF << INSTANCE_MASK_SHIFT);
        instanceData.set(ValueLayout.JAVA_INT, INSTANCE_CUSTOM_INDEX_MASK_OFFSET, customIndexAndMask);

        int sbtOffsetAndFlags = 0 | (VkGeometryInstanceFlagsKHR.TRIANGLE_FACING_CULL_DISABLE << INSTANCE_FLAGS_SHIFT);
        instanceData.set(ValueLayout.JAVA_INT, INSTANCE_SBT_OFFSET_FLAGS_OFFSET, sbtOffsetAndFlags);

        instanceData.set(ValueLayout.JAVA_LONG, INSTANCE_ACCEL_REF_OFFSET, blasAddress);

        // NOTE: Cannot use VMA for TLAS instance buffer — device address must be 16-byte aligned.
        // VMA does not guarantee alignment for device addresses, so use manual allocation.
        long instBufSize = alignUp(INSTANCE_STRUCT_SIZE, 16);
        var instBufInfo = VkBufferCreateInfo.allocate(arena)
                .size(instBufSize)
                .usage(VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR)
                .sharingMode(VkSharingMode.EXCLUSIVE);
        var pInstBuf = VkBuffer.Ptr.allocate(arena);
        checkResult(deviceCommands.createBuffer(device, instBufInfo, null, pInstBuf), "Failed to create TLAS instance buffer");
        var localInstBuffer = Objects.requireNonNull(pInstBuf.read());

        var instMemReqs = VkMemoryRequirements.allocate(arena);
        deviceCommands.getBufferMemoryRequirements(device, localInstBuffer, instMemReqs);
        var pInstMem = VkDeviceMemory.Ptr.allocate(arena);
        var instMemAlloc = VkMemoryAllocateInfo.allocate(arena)
                .allocationSize(instMemReqs.size())
                .memoryTypeIndex(findMemoryType(instMemReqs.memoryTypeBits(), VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT));
        var instMemFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
        instMemAlloc.pNext(instMemFlags.segment());
        checkResult(deviceCommands.allocateMemory(device, instMemAlloc, null, pInstMem), "Failed to allocate instance buffer memory");
        var localInstMemory = Objects.requireNonNull(pInstMem.read());
        deviceCommands.bindBufferMemory(device, localInstBuffer, localInstMemory, 0);

        var ppData = PointerPtr.allocate(arena);
        deviceCommands.mapMemory(device, localInstMemory, 0, INSTANCE_STRUCT_SIZE, 0, ppData);
        Objects.requireNonNull(ppData.read()).reinterpret(INSTANCE_STRUCT_SIZE).copyFrom(instanceData);
        deviceCommands.unmapMemory(device, localInstMemory);

        long instAddress = getBufferDeviceAddress(localInstBuffer);

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
                .primitiveCount(1).primitiveOffset(0).firstVertex(0).transformOffset(0);
        var ppTlasBuildRangeInfo = PointerPtr.allocate(arena);
        ppTlasBuildRangeInfo.write(tlasBuildRangeInfo);

        var tlasMaxPrim = IntPtr.allocate(arena, 1);
        tlasMaxPrim.write(0, 1);
        var tlasSizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
        deviceCommands.getAccelerationStructureBuildSizesKHR(device, VkAccelerationStructureBuildTypeKHR.DEVICE, tlasBuildInfo, tlasMaxPrim, tlasSizeInfo);

        long tlasSize = tlasSizeInfo.accelerationStructureSize();
        long tlasScratchSize = tlasSizeInfo.buildScratchSize();
        tlasSize = (tlasSize + MIN_BUFFER_PADDING - 1) & ~(MIN_BUFFER_PADDING - 1);
        tlasScratchSize = (tlasScratchSize + alignment - 1) & ~(alignment - 1);
        tlasScratchSize += SBT_REGION_PADDING;

        var tlasResult = createVmaBuffer(vma, vmaAllocator, arena, tlasSize,
                VkBufferUsageFlags.ACCELERATION_STRUCTURE_STORAGE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
                0,
                VkMemoryPropertyFlags.DEVICE_LOCAL);
        tlasBuffer = tlasResult.buffer();
        tlasAllocation = tlasResult.allocation();

        var tlasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
                .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
                .buffer(tlasBuffer)
                .size(tlasSize)
                .type(VkAccelerationStructureTypeKHR.TOP_LEVEL);
        var pTlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
        var result = deviceCommands.createAccelerationStructureKHR(device, tlasCreateInfo, null, pTlas);
        if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create TLAS: " + VkResult.explain(result));
        tlas = Objects.requireNonNull(pTlas.read());

        var scratchResult = createVmaBuffer(vma, vmaAllocator, arena, tlasScratchSize,
                VkBufferUsageFlags.STORAGE_BUFFER | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
                0,
                VkMemoryPropertyFlags.DEVICE_LOCAL);
        var scratchBuffer = scratchResult.buffer();
        var scratchAllocation = scratchResult.allocation();
        long scratchAddressTlas = getBufferDeviceAddress(scratchBuffer);
        long alignedScratchAddressTlas = (scratchAddressTlas + alignment - 1) & ~(alignment - 1);

        tlasBuildInfo.scratchData().deviceAddress(alignedScratchAddressTlas);
        tlasBuildInfo.dstAccelerationStructure(tlas);

        var bundle = beginSingleTimeCommands();
        deviceCommands.cmdBuildAccelerationStructuresKHR(bundle.cmd, 1, tlasBuildInfo, ppTlasBuildRangeInfo);
        endSingleTimeCommands(bundle);

        vma.destroyBuffer(vmaAllocator, scratchBuffer, scratchAllocation);
        // Cleanup manual instance buffer allocation
        deviceCommands.destroyBuffer(device, localInstBuffer, null);
        deviceCommands.freeMemory(device, localInstMemory, null);
    }

    private CommandBundle beginSingleTimeCommands() {
        try (var arena = Arena.ofConfined()) {
            var indices = findQueueFamilies(physicalDevice);
            var poolInfo = VkCommandPoolCreateInfo.allocate(arena)
                    .queueFamilyIndex(indices.graphicsFamily())
                    .flags(VkCommandPoolCreateFlags.TRANSIENT);
            var pPool = VkCommandPool.Ptr.allocate(arena);
            var result = deviceCommands.createCommandPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create transient command pool: " + VkResult.explain(result));
            VkCommandPool cmdPool = Objects.requireNonNull(pPool.read());

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
            VkCommandBuffer cmd = Objects.requireNonNull(pBuf.read());
            var beginInfo = VkCommandBufferBeginInfo.allocate(arena).flags(VkCommandBufferUsageFlags.ONE_TIME_SUBMIT);
            deviceCommands.beginCommandBuffer(cmd, beginInfo);
            return new CommandBundle(cmd, cmdPool);
        }
    }

    private void endSingleTimeCommands(CommandBundle bundle) {
        try (var arena = Arena.ofConfined()) {
            deviceCommands.endCommandBuffer(bundle.cmd);
            var submitInfo = VkSubmitInfo.allocate(arena)
                    .commandBufferCount(1)
                    .pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, bundle.cmd));
            var fenceInfo = VkFenceCreateInfo.allocate(arena);
            var pFence = VkFence.Ptr.allocate(arena);
            var result = deviceCommands.createFence(device, fenceInfo, null, pFence);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create fence: " + VkResult.explain(result));
            result = deviceCommands.queueSubmit(graphicsQueue, 1, submitInfo, Objects.requireNonNull(pFence.read()));
            if (result != VkResult.SUCCESS) {
                deviceCommands.destroyFence(device, pFence.read(), null);
                throw new RuntimeException("Failed to submit command buffer: " + VkResult.explain(result));
            }
            result = deviceCommands.waitForFences(device, 1, pFence, VkConstants.TRUE, UINT64_MAX);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to wait for fence: " + VkResult.explain(result));
            deviceCommands.destroyFence(device, pFence.read(), null);
            deviceCommands.freeCommandBuffers(device, bundle.pool, 1, VkCommandBuffer.Ptr.allocateV(arena, bundle.cmd));
            deviceCommands.destroyCommandPool(device, bundle.pool, null);
        }
    }

    // ======================== Descriptor Set Layout ========================
    private void createDescriptorSetLayout() {
        try (var arena = Arena.ofConfined()) {
            var bindings = VkDescriptorSetLayoutBinding.allocate(arena, 3)
                    .at(0, b -> b.binding(0).descriptorType(VkDescriptorType.STORAGE_IMAGE).descriptorCount(1).stageFlags(VkShaderStageFlags.RAYGEN_KHR | VkShaderStageFlags.CLOSEST_HIT_KHR))
                    .at(1, b -> b.binding(1).descriptorType(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(1).stageFlags(VkShaderStageFlags.RAYGEN_KHR | VkShaderStageFlags.CLOSEST_HIT_KHR))
                    .at(2, b -> b.binding(2).descriptorType(VkDescriptorType.COMBINED_IMAGE_SAMPLER).descriptorCount(1).stageFlags(VkShaderStageFlags.CLOSEST_HIT_KHR));
            var layoutInfo = VkDescriptorSetLayoutCreateInfo.allocate(arena).bindingCount(3).pBindings(bindings);
            var pLayout = VkDescriptorSetLayout.Ptr.allocate(arena);
            var result = deviceCommands.createDescriptorSetLayout(device, layoutInfo, null, pLayout);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create descriptor set layout: " + VkResult.explain(result));
            descriptorSetLayout = Objects.requireNonNull(pLayout.read());
        }
    }

    private void createDescriptorPool() {
        try (var arena = Arena.ofConfined()) {
            var sizes = populateDescriptorPoolSizes(arena);
            var poolInfo = VkDescriptorPoolCreateInfo.allocate(arena)
                    .poolSizeCount(3)
                    .pPoolSizes(sizes)
                    .maxSets(MAX_FRAMES_IN_FLIGHT * 2);
            var pPool = VkDescriptorPool.Ptr.allocate(arena);
            var result = deviceCommands.createDescriptorPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create descriptor pool: " + VkResult.explain(result));
            descriptorPool = Objects.requireNonNull(pPool.read());
        }
    }

    private void recreateDescriptorPool() {
        if (descriptorPool != null) {
            deviceCommands.destroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = null;
        }
        try (var arena = Arena.ofConfined()) {
            var sizes = populateDescriptorPoolSizes(arena);
            var poolInfo = VkDescriptorPoolCreateInfo.allocate(arena)
                    .poolSizeCount(3)
                    .pPoolSizes(sizes)
                    .maxSets(MAX_FRAMES_IN_FLIGHT * 2);
            var pPool = VkDescriptorPool.Ptr.allocate(arena);
            var result = deviceCommands.createDescriptorPool(device, poolInfo, null, pPool);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create descriptor pool: " + VkResult.explain(result));
            descriptorPool = Objects.requireNonNull(pPool.read());
        }
    }

    private VkDescriptorPoolSize.Ptr populateDescriptorPoolSizes(Arena arena) {
        return VkDescriptorPoolSize.allocate(arena, 3)
                .at(0, s -> s.type(VkDescriptorType.STORAGE_IMAGE).descriptorCount(MAX_FRAMES_IN_FLIGHT * 2))
                .at(1, s -> s.type(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(MAX_FRAMES_IN_FLIGHT * 2))
                .at(2, s -> s.type(VkDescriptorType.COMBINED_IMAGE_SAMPLER).descriptorCount(MAX_FRAMES_IN_FLIGHT * 2));
    }

    private void createDescriptorSet() {
        try (var arena = Arena.ofConfined()) {
            var allocInfo = VkDescriptorSetAllocateInfo.allocate(arena)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(VkDescriptorSetLayout.Ptr.allocateV(arena, descriptorSetLayout))
                    .descriptorSetCount(1);
            var pSet = VkDescriptorSet.Ptr.allocate(arena);
            var result = deviceCommands.allocateDescriptorSets(device, allocInfo, pSet);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to allocate descriptor set: " + VkResult.explain(result));
            descriptorSet = Objects.requireNonNull(pSet.read());
        }
        updateDescriptorSet();
    }

    private void updateDescriptorSet() {
        try (var arena = Arena.ofConfined()) {
            var imageInfo = VkDescriptorImageInfo.allocate(arena).imageView(outputImageView).imageLayout(VkImageLayout.GENERAL);
            var asInfo = VkWriteDescriptorSetAccelerationStructureKHR.allocate(arena)
                    .sType(VkStructureType.WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR)
                    .accelerationStructureCount(1)
                    .pAccelerationStructures(VkAccelerationStructureKHR.Ptr.allocateV(arena, tlas));
            var textureInfo = VkDescriptorImageInfo.allocate(arena)
                    .imageLayout(VkImageLayout.SHADER_READ_ONLY_OPTIMAL)
                    .imageView(textureImageView)
                    .sampler(textureSampler);
            var writes = VkWriteDescriptorSet.allocate(arena, 3)
                    .at(0, w -> w.dstSet(descriptorSet).dstBinding(0).descriptorType(VkDescriptorType.STORAGE_IMAGE).descriptorCount(1).pImageInfo(imageInfo))
                    .at(1, w -> w.dstSet(descriptorSet).dstBinding(1).descriptorType(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(1).pNext(asInfo.segment()))
                    .at(2, w -> w.dstSet(descriptorSet).dstBinding(2).descriptorType(VkDescriptorType.COMBINED_IMAGE_SAMPLER).descriptorCount(1).pImageInfo(textureInfo));
            deviceCommands.updateDescriptorSets(device, 3, writes, 0, null);
        }
    }

    // ======================== Ray Tracing Pipeline ========================
    private void createRayTracingPipeline() {
        try (var arena = Arena.ofConfined()) {
            var rgenCode = compileShader(arena, "/shader/raytracing/ch26/ray.rgen", "rgen");
            var rchitCode = compileShader(arena, "/shader/raytracing/ch26/ray.rchit", "rchit");
            var rmissCode = compileShader(arena, "/shader/raytracing/ch26/ray.rmiss", "rmiss");
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
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create pipeline layout: " + VkResult.explain(result));
            pipelineLayout = Objects.requireNonNull(pLayout.read());

            var pipelineInfo = VkRayTracingPipelineCreateInfoKHR.allocate(arena)
                    .sType(VkStructureType.RAY_TRACING_PIPELINE_CREATE_INFO_KHR)
                    .pStages(stages).stageCount(SHADER_GROUP_COUNT).pGroups(groups).groupCount(SHADER_GROUP_COUNT)
                    .maxPipelineRayRecursionDepth(1).layout(pipelineLayout)
                    .flags(VkPipelineCreateFlags.RAY_TRACING_SHADER_GROUP_HANDLE_CAPTURE_REPLAY_KHR);
            var pPipeline = VkPipeline.Ptr.allocate(arena);
            result = deviceCommands.createRayTracingPipelinesKHR(device, null, null, 1, pipelineInfo, null, pPipeline);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create ray tracing pipeline: " + VkResult.explain(result));
            rayTracingPipeline = Objects.requireNonNull(pPipeline.read());

            deviceCommands.destroyShaderModule(device, rgenModule, null);
            deviceCommands.destroyShaderModule(device, rchitModule, null);
            deviceCommands.destroyShaderModule(device, rmissModule, null);
        }
    }

    // ======================== Shader Compilation ========================
    /**
     * Compiles GLSL to SPIR-V at runtime using glslangValidator.
     * Required -- glslangValidator must be in PATH (Vulkan SDK).
     *
     * @param arena  the arena for the returned IntPtr
     * @param filename resource path (e.g. "/shader/raytracing/ch20/ray.rgen")
     * @param stage  shader stage: "rgen", "rchit", "rmiss", "vert", "frag", "comp"
     * @return IntPtr containing SPIR-V words
     */
    private IntPtr compileShader(Arena arena, String filename, String stage) {
        String path = filename.startsWith("/") ? filename : "/" + filename;
        try (var stream = Application.class.getResourceAsStream(path)) {
            if (stream == null) throw new RuntimeException("Shader not found: " + filename);
            String source = new String(stream.readAllBytes());

            java.nio.file.Path tempSpv = java.nio.file.Files.createTempFile("vulkan_shader_", ".spv");

            try {
                Process process = new ProcessBuilder(
                        "glslangValidator",
                        "--target-env", "vulkan1.2",
                        "--stdin", "-S", stage, "-V", "-o", tempSpv.toString()
                ).start();

                try (var out = process.getOutputStream()) {
                    out.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    String errMsg = new String(process.getErrorStream().readAllBytes());
                    throw new RuntimeException("Glslang failed (" + filename + "):\n" + errMsg);
                }

                byte[] spirvBytes = java.nio.file.Files.readAllBytes(tempSpv);

                int wordCount = spirvBytes.length / Integer.BYTES;
                int[] words = new int[wordCount];
                java.nio.ByteBuffer.wrap(spirvBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(words);
                IntPtr ret = IntPtr.allocate(arena, wordCount);
                ret.segment().copyFrom(MemorySegment.ofArray(words));
                return ret;
            } finally {
                java.nio.file.Files.deleteIfExists(tempSpv);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Shader compilation interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Shader compilation error", e);
        }
    }

    private VkShaderModule createShaderModule(IntPtr code) {
        try (var arena = Arena.ofConfined()) {
            var info = VkShaderModuleCreateInfo.allocate(arena).codeSize(code.size() * Integer.BYTES).pCode(code);
            var pModule = VkShaderModule.Ptr.allocate(arena);
            var result = deviceCommands.createShaderModule(device, info, null, pModule);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to create shader module: " + VkResult.explain(result));
            return Objects.requireNonNull(pModule.read());
        }
    }

    // ======================== Shader Binding Table ========================
    private void createShaderBindingTable() {
        try (var arena = Arena.ofConfined()) {
            sbtRecordSize = handleSize;
            int handleSizeAligned = (int) alignUp(handleSize, handleAlignment);

            var handles = BytePtr.allocate(arena, SHADER_GROUP_COUNT * handleSizeAligned);
            var getResult = deviceCommands.getRayTracingShaderGroupHandlesKHR(device, rayTracingPipeline, 0, SHADER_GROUP_COUNT, SHADER_GROUP_COUNT * handleSizeAligned, handles.segment());
            if (getResult != VkResult.SUCCESS) throw new RuntimeException("Failed to get shader group handles: " + VkResult.explain(getResult));

            int totalSize = SHADER_GROUP_COUNT * shaderGroupBaseAlignment + handleSize;

            var sbtResult = createVmaBuffer(vma, vmaAllocator, arena, totalSize,
                    VkBufferUsageFlags.SHADER_BINDING_TABLE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
                    VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE,
                    VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT);
            sbtBuffer = sbtResult.buffer();
            sbtAllocation = sbtResult.allocation();

            var pMap = PointerPtr.allocate(arena);
            vma.mapMemory(vmaAllocator, sbtAllocation, pMap);
            var sbtSeg = Objects.requireNonNull(pMap.read()).reinterpret(totalSize);

            var bufAddr = deviceCommands.getBufferDeviceAddress(device, VkBufferDeviceAddressInfo.allocate(arena).buffer(sbtBuffer));
            raygenAddress = (bufAddr + shaderGroupBaseAlignment - 1) & ~(long)(shaderGroupBaseAlignment - 1);
            long offset = raygenAddress - bufAddr;

            missAddress = raygenAddress + shaderGroupBaseAlignment;
            hitAddress = missAddress + shaderGroupBaseAlignment;

            for (int i = 0; i < SHADER_GROUP_COUNT; i++) {
                long srcOffset = i * handleSizeAligned;
                long dstOffset = offset + i * shaderGroupBaseAlignment;
                sbtSeg.asSlice(dstOffset, handleSize).copyFrom(handles.segment().asSlice(srcOffset, handleSize));
            }
            vma.unmapMemory(vmaAllocator, sbtAllocation);
        }
    }

    // ======================== Command Buffers ========================
    private void createCommandBuffers() {
        commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
        try (var arena = Arena.ofConfined()) {
            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
                    .commandPool(commandPool).level(VkCommandBufferLevel.PRIMARY).commandBufferCount(MAX_FRAMES_IN_FLIGHT);
            var pCmds = VkCommandBuffer.Ptr.allocate(arena, MAX_FRAMES_IN_FLIGHT);
            checkResult(deviceCommands.allocateCommandBuffers(device, allocInfo, pCmds), "Failed to allocate command buffers");
            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
                commandBuffers[i] = Objects.requireNonNull(pCmds.read(i));
            }
        }
    }

    // ======================== Draw Frame ========================
    private void drawFrame() {
        try (var arena = Arena.ofConfined()) {
            if (swapChainExtent.width() == 0 || swapChainExtent.height() == 0) {
                needsSwapchainRecreation = true;
                return;
            }

            deviceCommands.waitForFences(device, 1, VkFence.Ptr.allocateV(arena, inFlightFences[currentFrame]), VkConstants.TRUE, UINT64_MAX);

            var pImageIndex = IntPtr.allocate(arena);
            var result = deviceCommands.acquireNextImageKHR(device, swapChain, UINT64_MAX, imageAvailableSemaphores[currentFrame], null, pImageIndex);
            if (result == VkResult.ERROR_SURFACE_LOST_KHR || result == VkResult.ERROR_OUT_OF_DATE_KHR || result == VkResult.ERROR_INITIALIZATION_FAILED) {
                needsSwapchainRecreation = true;
                return;
            } else if (result != VkResult.SUCCESS && result != VkResult.SUBOPTIMAL_KHR) {
                throw new RuntimeException("Failed to acquire swap chain image: " + VkResult.explain(result));
            }
            int imageIndex = pImageIndex.read();

            deviceCommands.resetFences(device, 1, VkFence.Ptr.allocateV(arena, inFlightFences[currentFrame]));

            var beginInfo = VkCommandBufferBeginInfo.allocate(arena);
            deviceCommands.beginCommandBuffer(commandBuffers[currentFrame], beginInfo);
            recordCommandBuffer(commandBuffers[currentFrame], imageIndex);
            deviceCommands.endCommandBuffer(commandBuffers[currentFrame]);

            var submitInfo = VkSubmitInfo.allocate(arena)
                    .waitSemaphoreCount(1)
                    .pWaitSemaphores(VkSemaphore.Ptr.allocateV(arena, imageAvailableSemaphores[currentFrame]))
                    .pWaitDstStageMask(IntPtr.allocateV(arena, VkPipelineStageFlags.BOTTOM_OF_PIPE))
                    .commandBufferCount(1)
                    .pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, commandBuffers[currentFrame]))
                    .signalSemaphoreCount(1)
                    .pSignalSemaphores(VkSemaphore.Ptr.allocateV(arena, renderFinishedSemaphores[currentFrame]));
            result = deviceCommands.queueSubmit(graphicsQueue, 1, submitInfo, inFlightFences[currentFrame]);
            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to submit draw command buffer: " + VkResult.explain(result));

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

            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
        }
    }

    private void handleMinimizedWindow() {
        try (var arena = Arena.ofConfined()) {
            IntPtr w = IntPtr.allocate(arena), h = IntPtr.allocate(arena);
            glfw.getFramebufferSize(window, w, h);
            int width = w.read();
            int height = h.read();
            if (width > 0 && height > 0) needsSwapchainRecreation = true;
        }
    }

    private void recordCommandBuffer(VkCommandBuffer cmd, int imageIndex) {
        try (var arena = Arena.ofConfined()) {
            recordRayTracingDispatch(cmd, arena);
            recordImageTransfers(cmd, imageIndex, arena);
        }
    }

    private void recordRayTracingDispatch(VkCommandBuffer cmd, Arena arena) {
        var raygenRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                .deviceAddress(raygenAddress).stride(sbtRecordSize).size(sbtRecordSize);
        var missRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                .deviceAddress(missAddress).stride(sbtRecordSize).size(sbtRecordSize);
        var hitRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                .deviceAddress(hitAddress).stride(sbtRecordSize).size(sbtRecordSize);
        var callableRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                .deviceAddress(0).stride(0).size(0);

        // Barrier: outputImage GENERAL -> GENERAL
        var rayTraceBarrier = createImageBarrier(arena, outputImage,
                VkImageLayout.GENERAL, VkImageLayout.GENERAL,
                VkAccessFlags.SHADER_WRITE, VkAccessFlags.SHADER_WRITE);
        deviceCommands.cmdPipelineBarrier(cmd,
                VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
                VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
                0, 0, null, 0, null, 1, rayTraceBarrier);

        deviceCommands.cmdBindPipeline(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, rayTracingPipeline);
        var pDescriptorSet = VkDescriptorSet.Ptr.allocate(arena);
        pDescriptorSet.write(descriptorSet);
        deviceCommands.cmdBindDescriptorSets(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, pipelineLayout, 0, 1, pDescriptorSet, 0, null);

        recordPushConstants(cmd, arena);

        deviceCommands.cmdTraceRaysKHR(cmd, raygenRegion, missRegion, hitRegion, callableRegion,
                swapChainExtent.width(), swapChainExtent.height(), 1);
    }

    private void recordPushConstants(VkCommandBuffer cmd, Arena arena) {
        float time = (System.currentTimeMillis() - startTime) / 1000.0f;
        float angleRad = (float) Math.toRadians(ROTATION_SPEED_DEG_PER_SEC * time);
        Matrix4f model = new Matrix4f().rotate(angleRad, ROTATION_AXIS);
        Matrix4f view = new Matrix4f().lookAt(CAMERA_POSITION, CAMERA_LOOK_AT, CAMERA_UP);
        float aspectRatio = (float) swapChainExtent.width() / (float) swapChainExtent.height();
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(CAMERA_FOV), aspectRatio, CAMERA_NEAR, CAMERA_FAR, true);
        projection.m11(-projection.m11());

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f invView = new Matrix4f(view).invert();

        float[] pushConstants = new float[PUSH_CONSTANT_FLOAT_COUNT];
        model.get(pushConstants, 0);
        invProjection.get(pushConstants, MATRIX4F_FLOAT_COUNT);
        invView.get(pushConstants, MATRIX4F_FLOAT_COUNT * 2);

        var nativeMemory = arena.allocate(ValueLayout.JAVA_FLOAT, PUSH_CONSTANT_FLOAT_COUNT);
        for (int i = 0; i < PUSH_CONSTANT_FLOAT_COUNT; i++) {
            nativeMemory.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, pushConstants[i]);
        }
        deviceCommands.cmdPushConstants(cmd, pipelineLayout, VkShaderStageFlags.RAYGEN_KHR, 0, PUSH_CONSTANT_SIZE_BYTES, nativeMemory);
    }

    private void recordImageTransfers(VkCommandBuffer cmd, int imageIndex, Arena arena) {
        // Barrier: outputImage GENERAL -> TRANSFER_SRC
        var transferBarrier = createImageBarrier(arena, outputImage,
                VkImageLayout.GENERAL, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                VkAccessFlags.SHADER_WRITE, VkAccessFlags.TRANSFER_READ);
        deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.RAY_TRACING_SHADER_KHR, VkPipelineStageFlags.TRANSFER, 0, 0, null, 0, null, 1, transferBarrier);

        // Barrier: swapchain UNDEFINED -> TRANSFER_DST
        var swapchainBarrier = createImageBarrier(arena, swapChainImages.read(imageIndex),
                VkImageLayout.UNDEFINED, VkImageLayout.TRANSFER_DST_OPTIMAL,
                0, VkAccessFlags.TRANSFER_WRITE);
        deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TOP_OF_PIPE, VkPipelineStageFlags.TRANSFER, 0, 0, null, 0, null, 1, swapchainBarrier);

        var copy = VkImageCopy.allocate(arena)
                .srcSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
                .dstSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
                .extent(e -> e.width(swapChainExtent.width()).height(swapChainExtent.height()).depth(1));
        deviceCommands.cmdCopyImage(cmd, outputImage, VkImageLayout.TRANSFER_SRC_OPTIMAL,
                swapChainImages.read(imageIndex), VkImageLayout.TRANSFER_DST_OPTIMAL, 1, copy);

        // Barrier: swapchain TRANSFER_DST -> PRESENT_SRC
        var presentBarrier = createImageBarrier(arena, swapChainImages.read(imageIndex),
                VkImageLayout.TRANSFER_DST_OPTIMAL, VkImageLayout.PRESENT_SRC_KHR,
                VkAccessFlags.TRANSFER_WRITE, 0);
        deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TRANSFER, VkPipelineStageFlags.BOTTOM_OF_PIPE, 0, 0, null, 0, null, 1, presentBarrier);

        // Barrier: outputImage TRANSFER_SRC -> GENERAL
        var outputBarrier = createImageBarrier(arena, outputImage,
                VkImageLayout.TRANSFER_SRC_OPTIMAL, VkImageLayout.GENERAL,
                VkAccessFlags.TRANSFER_READ, VkAccessFlags.SHADER_WRITE);
        deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TRANSFER, VkPipelineStageFlags.RAY_TRACING_SHADER_KHR, 0, 0, null, 0, null, 1, outputBarrier);
    }

    // ======================== Helpers ========================
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

    public static void main(String[] args) {
        try {
            var app = new Application();
            app.run();
        } catch (Throwable e) {
            e.printStackTrace(System.err);
        }
    }
}
