package tutorial.vulkan.raytracing;

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
import static club.doki7.vulkan.VkConstants.WHOLE_SIZE;

public class Application {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final BytePtr WINDOW_TITLE = BytePtr.allocateString(Arena.global(), "Ray Tracing Demo");
    private static final boolean ENABLE_VALIDATION_LAYERS = System.getProperty("validation") != null;
    private static final String VALIDATION_LAYER_NAME = "VK_LAYER_KHRONOS_validation";

    // Ray tracing properties
    private int handleSize;
    private int handleAlignment;
    private int shaderGroupBaseAlignment;
    private int sbtRecordSize;
    private long raygenAddress;
    private long missAddress;
    private long hitAddress;

    // Core
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

    // VMA
    private VmaAllocator vmaAllocator;

    // Swapchain
    private VkSwapchainKHR swapChain;
    private VkImage.Ptr swapChainImages;
    private @EnumType(VkFormat.class) int swapChainImageFormat;
    private VkExtent2D swapChainExtent;
    private VkImageView.Ptr swapChainImageViews;

    // Ray tracing resources
    private VkBuffer vertexBuffer;
    private VkDeviceMemory vertexBufferMemory;
    private VkBuffer indexBuffer;
    private VkDeviceMemory indexBufferMemory;
    private VkBuffer blasBuffer;
    private VkDeviceMemory blasBufferMemory;
    private VkBuffer tlasBuffer;
    private VkDeviceMemory tlasBufferMemory;
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
    private VkDeviceMemory sbtBufferMemory;
    private long sbtDeviceAddress;

    // Sync
    private VkSemaphore[] imageAvailableSemaphores;
    private VkSemaphore[] renderFinishedSemaphores;
    private VkFence[] inFlightFences;
    private VkCommandPool commandPool;
    private VkCommandBuffer[] commandBuffers;
    private VkCommandPool transientCommandPool = null;
    private static final int MAX_FRAMES_IN_FLIGHT = 2;
    private int currentFrame = 0;
    private boolean framebufferResized = false;
    private boolean needsSwapchainRecreation = false;

    // Camera parameters
    private final Vector3f cameraPosition = new Vector3f(0.0f, 0.0f, 5.0f);
    private final float cameraFOV = 60.0f;

    // Libraries
    private static final ISharedLibrary libGLFW = GLFWLoader.loadGLFWLibrary();
    private static final GLFW glfw = GLFWLoader.loadGLFW(libGLFW);
    private static final ISharedLibrary libShaderc = ILibraryLoader.platformLoader().loadLibrary("shaderc_shared");
    private static final Shaderc shaderc = new Shaderc(libShaderc);
    private static final ISharedLibrary libVulkan = VulkanLoader.loadVulkanLibrary();
    private static final ISharedLibrary libVMA = ILibraryLoader.platformLoader().loadLibrary("vma");
    private static final VMA vma = new VMA(libVMA);
    private ShadercCompiler shadercCompiler;
    private ShadercCompileOptions shadercCompileOptions;

    public void run() {
        initWindow();
        initVulkan();
        mainLoop();
        cleanup();
    }

    private void initWindow() {
        if (glfw.init() != GLFW.TRUE) {
            throw new RuntimeException("Failed to initialize GLFW");
        }
        if (glfw.vulkanSupported() != GLFW.TRUE) {
            throw new RuntimeException("Vulkan is not supported");
        }
        glfw.windowHint(GLFW.CLIENT_API, GLFW.NO_API);
        window = Objects.requireNonNull(glfw.createWindow(WIDTH, HEIGHT, WINDOW_TITLE, null, null));
        glfw.setFramebufferSizeCallback(window, (w, width, height) -> {
            if (width > 0 && height > 0) {
                framebufferResized = true;
            }
        });
    }

    private void initVulkan() {
        System.setProperty("AMD_FORCE_VULKAN_RAY_TRACING", "1");
        System.setProperty("AMD_RAY_TRACING_DEBUG", "1");
        staticCommands = VulkanLoader.loadStaticCommands(libVulkan);
        entryCommands = VulkanLoader.loadEntryCommands(staticCommands);
        createInstance();
        setupDebugMessenger();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
        createCommandPool();
        createVMA();
        createShaderCompiler();
        createSwapchain();
        createImageViews();
        createVertexBuffer();
        createAccelerationStructures();
        createOutputImage();
        createDescriptorSetLayout();
        createDescriptorPool();
        createDescriptorSet();
        createRayTracingPipeline();
        createSyncObjects();
        createShaderBindingTable();
        createCommandBuffers();
        
        // Transition output image to GENERAL layout после создания всех command buffers
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
                handleMinimizedWindow();
                try { Thread.sleep(16); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                continue;
            }
            drawFrame();
        }
        deviceCommands.deviceWaitIdle(device);
    }

    private void cleanup() {
        deviceCommands.deviceWaitIdle(device);
        if (commandPool != null) {
            deviceCommands.destroyCommandPool(device, commandPool, null);
            commandPool = null;
        }
        if (blas != null) {
            deviceCommands.destroyAccelerationStructureKHR(device, blas, null);
            blas = null;
        }
        if (tlas != null) {
            deviceCommands.destroyAccelerationStructureKHR(device, tlas, null);
            tlas = null;
        }
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
        if (sbtBuffer != null) {
            deviceCommands.destroyBuffer(device, sbtBuffer, null);
            sbtBuffer = null;
        }
        if (sbtBufferMemory != null) {
            deviceCommands.freeMemory(device, sbtBufferMemory, null);
            sbtBufferMemory = null;
        }
        if (vertexBuffer != null) {
            deviceCommands.destroyBuffer(device, vertexBuffer, null);
            vertexBuffer = null;
        }
        if (vertexBufferMemory != null) {
            deviceCommands.freeMemory(device, vertexBufferMemory, null);
            vertexBufferMemory = null;
        }
        if (indexBuffer != null) {
            deviceCommands.destroyBuffer(device, indexBuffer, null);
            indexBuffer = null;
        }
        if (indexBufferMemory != null) {
            deviceCommands.freeMemory(device, indexBufferMemory, null);
            indexBufferMemory = null;
        }
        if (rayTracingPipeline != null) {
            deviceCommands.destroyPipeline(device, rayTracingPipeline, null);
            rayTracingPipeline = null;
        }
        if (pipelineLayout != null) {
            deviceCommands.destroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = null;
        }
        if (descriptorSetLayout != null) {
            deviceCommands.destroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = null;
        }
        if (descriptorPool != null) {
            deviceCommands.destroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = null;
            descriptorSet = null;
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
        cleanupSwapChain();
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
        glfw.destroyWindow(window);
        glfw.terminate();
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

    private void createLogicalDevice() {
        var indices = findQueueFamilies(physicalDevice);
        assert indices != null;
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
                    .bufferDeviceAddress(VkConstants.TRUE);
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
            swapChainImages = VkImage.Ptr.allocate(Arena.ofAuto(), count);
            deviceCommands.getSwapchainImagesKHR(device, swapChain, pCount, swapChainImages);
            swapChainImageFormat = format.format();
            swapChainExtent = VkExtent2D.clone(Arena.ofAuto(), extent);
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
        swapChainImageViews = VkImageView.Ptr.allocate(Arena.ofAuto(), swapChainImages.size());
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

    // ✅ ИСПРАВЛЕНО: Копирование через ByteBuffer с HOST_COHERENT
    private void createVertexBuffer() {
        // Маленький треугольник ПЕРЕД камерой (Z=3), центрированный на оси
        float[] vertices = {
                -1.0f, -0.33f, 3.0f,   // Левый нижний
                 1.0f, -0.33f, 3.0f,   // Правый нижний
                 0.0f,  1.67f, 3.0f    // Верхний (смещён вверх чтобы центр был в Y=0)
        };
        try (var arena = Arena.ofConfined()) {
            var size = vertices.length * Float.BYTES;
            
            // Создаем буфер напрямую через Vulkan
            var bufferCreateInfo = VkBufferCreateInfo.allocate(arena)
                    .size(size)
                    .usage(VkBufferUsageFlags.SHADER_DEVICE_ADDRESS |
                            VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR);
            
            var pBuffer = VkBuffer.Ptr.allocate(arena);
            var result = deviceCommands.createBuffer(device, bufferCreateInfo, null, pBuffer);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create vertex buffer: " + VkResult.explain(result));
            }
            vertexBuffer = pBuffer.read();
            
            // Получаем memory requirements
            var memReqs = VkMemoryRequirements.allocate(arena);
            deviceCommands.getBufferMemoryRequirements(device, vertexBuffer, memReqs);
            
            // Выделяем память HOST_VISIBLE | HOST_COHERENT | DEVICE_ADDRESS
            var memAlloc = VkMemoryAllocateInfo.allocate(arena)
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(findMemoryType(memReqs.memoryTypeBits(), VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT));
            
            // Добавляем VkMemoryAllocateFlagsInfo для DEVICE_ADDRESS
            var memAllocFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                    .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                    .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
            memAlloc.pNext(memAllocFlags.segment());
            
            var pDeviceMemory = VkDeviceMemory.Ptr.allocate(arena);
            result = deviceCommands.allocateMemory(device, memAlloc, null, pDeviceMemory);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to allocate vertex buffer memory: " + VkResult.explain(result));
            }
            vertexBufferMemory = pDeviceMemory.read();
            
            // Bind memory
            result = deviceCommands.bindBufferMemory(device, vertexBuffer, vertexBufferMemory, 0);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to bind vertex buffer memory: " + VkResult.explain(result));
            }
            
            // Копируем данные
            var ppData = PointerPtr.allocate(arena);
            result = deviceCommands.mapMemory(device, pDeviceMemory.read(), 0, size, 0, ppData);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to map vertex buffer: " + VkResult.explain(result));
            }
            ppData.read().reinterpret(size).copyFrom(MemorySegment.ofArray(vertices));
            
            // FLUSH memory чтобы GPU видел данные
            var flushRange = VkMappedMemoryRange.allocate(arena)
                    .memory(pDeviceMemory.read())
                    .offset(0)
                    .size(size);
            deviceCommands.flushMappedMemoryRanges(device, 1, flushRange);
            
            deviceCommands.unmapMemory(device, pDeviceMemory.read());
            
            // Получаем device address
            long vertexAddress = getBufferDeviceAddress(vertexBuffer);
            System.out.println("=== VERTEX BUFFER CREATED (direct Vulkan) ===");
            System.out.println("Size: " + size + " bytes (" + vertices.length + " floats)");
            System.out.println("GPU Address: 0x" + Long.toHexString(vertexAddress));
            System.out.println("Vertex data: " + java.util.Arrays.toString(vertices));
        }
    }

    private long getBufferDeviceAddress(VkBuffer buffer) {
        try (var arena = Arena.ofConfined()) {
            // Пробуем через getBufferOpaqueCaptureAddress
            var info = VkBufferDeviceAddressInfo.allocate(arena)
                    .sType(VkStructureType.BUFFER_DEVICE_ADDRESS_INFO)
                    .buffer(buffer);
            
            // Для AMD может потребоваться opaque capture address
            long addr = deviceCommands.getBufferDeviceAddress(device, info);
            
            // Проверяем, что адрес выглядит правильно
            if (addr < 0x1000000000L) {
                System.out.println("getBufferDeviceAddress returned small address: 0x" + Long.toHexString(addr));
                // На AMD это может быть физический адрес, а нам нужен виртуальный
                // Попробуем использовать opaque capture address
                var opaqueInfo = VkBufferOpaqueCaptureAddressCreateInfo.allocate(arena)
                        .sType(VkStructureType.BUFFER_OPAQUE_CAPTURE_ADDRESS_CREATE_INFO);
            }
            
            return addr;
        }
    }

    private record Pair<T1, T2>(T1 first, T2 second) {}

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
        try (var arena = Arena.ofConfined()) {
            var info = VkBufferCreateInfo.allocate(arena)
                    .size(size)
                    .usage(VkBufferUsageFlags.STORAGE_BUFFER | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS);
            var pBuffer = VkBuffer.Ptr.allocate(arena);
            var result = deviceCommands.createBuffer(device, info, null, pBuffer);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create scratch buffer: " + VkResult.explain(result));
            }
            VkBuffer buffer = pBuffer.read();
            
            // Получаем memory requirements
            var memReqs = VkMemoryRequirements.allocate(arena);
            deviceCommands.getBufferMemoryRequirements(device, buffer, memReqs);
            
            // Выделяем память с DEVICE_ADDRESS флагом
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
                throw new RuntimeException("Failed to allocate scratch buffer memory: " + VkResult.explain(result));
            }
            
            result = deviceCommands.bindBufferMemory(device, buffer, pDeviceMemory.read(), 0);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to bind scratch buffer memory: " + VkResult.explain(result));
            }
            
            return new Pair<>(buffer, null);
        }
    }

    private Pair<VkBuffer, VmaAllocation> createAccelerationStructureBuffer(int size) {
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
            
            // Получаем memory requirements
            var memReqs = VkMemoryRequirements.allocate(arena);
            deviceCommands.getBufferMemoryRequirements(device, buffer, memReqs);
            
            // Выделяем память с DEVICE_ADDRESS флагом
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
            
            return new Pair<>(buffer, null);
        }
    }

    private void createAccelerationStructures() {
        long vertexAddress = getBufferDeviceAddress(vertexBuffer);
        try (var arena = Arena.ofConfined()) {
            // Получаем свойства устройства для выравнивания
            var asProps = VkPhysicalDeviceAccelerationStructurePropertiesKHR.allocate(arena)
                    .sType(VkStructureType.PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR);
            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(asProps.segment());
            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);

            // ========== BOTTOM LEVEL AS (BLAS) ==========
            // Получаем адрес индексного буфера (создадим его прямо здесь)
            int[] indices = { 0, 1, 2 };
            var indexSize = indices.length * Integer.BYTES;
            
            var indexBufferCreateInfo = VkBufferCreateInfo.allocate(arena)
                    .size(indexSize)
                    .usage(VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR);
            var pIndexBuffer = VkBuffer.Ptr.allocate(arena);
            var indexResult = deviceCommands.createBuffer(device, indexBufferCreateInfo, null, pIndexBuffer);
            if (indexResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create index buffer: " + VkResult.explain(indexResult));
            }
            VkBuffer localIndexBuffer = pIndexBuffer.read();
            
            var indexMemReqs = VkMemoryRequirements.allocate(arena);
            deviceCommands.getBufferMemoryRequirements(device, localIndexBuffer, indexMemReqs);
            var indexMemAlloc = VkMemoryAllocateInfo.allocate(arena)
                    .allocationSize(indexMemReqs.size())
                    .memoryTypeIndex(findMemoryType(indexMemReqs.memoryTypeBits(), VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT));
            var indexMemAllocFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                    .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                    .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
            indexMemAlloc.pNext(indexMemAllocFlags.segment());
            var pIndexMemory = VkDeviceMemory.Ptr.allocate(arena);
            indexResult = deviceCommands.allocateMemory(device, indexMemAlloc, null, pIndexMemory);
            if (indexResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to allocate index buffer memory: " + VkResult.explain(indexResult));
            }
            VkDeviceMemory indexBufferMemory = pIndexMemory.read();
            indexResult = deviceCommands.bindBufferMemory(device, localIndexBuffer, indexBufferMemory, 0);
            if (indexResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to bind index buffer memory: " + VkResult.explain(indexResult));
            }
            
            var ppIndexData = PointerPtr.allocate(arena);
            indexResult = deviceCommands.mapMemory(device, indexBufferMemory, 0, indexSize, 0, ppIndexData);
            if (indexResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to map index buffer: " + VkResult.explain(indexResult));
            }
            ppIndexData.read().reinterpret(indexSize).copyFrom(MemorySegment.ofArray(indices));
            
            // FLUSH index memory
            var flushIndex = VkMappedMemoryRange.allocate(arena)
                    .memory(indexBufferMemory)
                    .offset(0)
                    .size(indexSize);
            deviceCommands.flushMappedMemoryRanges(device, 1, flushIndex);
            
            deviceCommands.unmapMemory(device, indexBufferMemory);
            
            long indexAddress = getBufferDeviceAddress(localIndexBuffer);
            System.out.println("Index buffer address: 0x" + Long.toHexString(indexAddress));
            
            // Создаем transform buffer (identity matrix) как у Sascha Willems
            float[] transform = {
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f
            };
            var transformSize = transform.length * Float.BYTES;
            
            var transformBufferCreateInfo = VkBufferCreateInfo.allocate(arena)
                    .size(transformSize)
                    .usage(VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR);
            var pTransformBuffer = VkBuffer.Ptr.allocate(arena);
            var transformResult = deviceCommands.createBuffer(device, transformBufferCreateInfo, null, pTransformBuffer);
            if (transformResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create transform buffer: " + VkResult.explain(transformResult));
            }
            VkBuffer localTransformBuffer = pTransformBuffer.read();
            
            var transformMemReqs = VkMemoryRequirements.allocate(arena);
            deviceCommands.getBufferMemoryRequirements(device, localTransformBuffer, transformMemReqs);
            var transformMemAlloc = VkMemoryAllocateInfo.allocate(arena)
                    .allocationSize(transformMemReqs.size())
                    .memoryTypeIndex(findMemoryType(transformMemReqs.memoryTypeBits(), VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT));
            var transformMemAllocFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                    .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                    .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
            transformMemAlloc.pNext(transformMemAllocFlags.segment());
            var pTransformMemory = VkDeviceMemory.Ptr.allocate(arena);
            transformResult = deviceCommands.allocateMemory(device, transformMemAlloc, null, pTransformMemory);
            if (transformResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to allocate transform buffer memory: " + VkResult.explain(transformResult));
            }
            VkDeviceMemory transformBufferMemory = pTransformMemory.read();
            transformResult = deviceCommands.bindBufferMemory(device, localTransformBuffer, transformBufferMemory, 0);
            if (transformResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to bind transform buffer memory: " + VkResult.explain(transformResult));
            }
            
            var ppTransformData = PointerPtr.allocate(arena);
            transformResult = deviceCommands.mapMemory(device, transformBufferMemory, 0, transformSize, 0, ppTransformData);
            if (transformResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to map transform buffer: " + VkResult.explain(transformResult));
            }
            ppTransformData.read().reinterpret(transformSize).copyFrom(MemorySegment.ofArray(transform));
            
            // FLUSH transform memory
            var flushTransform = VkMappedMemoryRange.allocate(arena)
                    .memory(transformBufferMemory)
                    .offset(0)
                    .size(transformSize);
            deviceCommands.flushMappedMemoryRanges(device, 1, flushTransform);
            
            deviceCommands.unmapMemory(device, transformBufferMemory);
            
            long transformAddress = getBufferDeviceAddress(localTransformBuffer);
            System.out.println("Transform buffer address: 0x" + Long.toHexString(transformAddress));
            
            // ВАЖНО: deviceWaitIdle чтобы убедиться что host записи завершены
            deviceCommands.deviceWaitIdle(device);

            var triangles = VkAccelerationStructureGeometryTrianglesDataKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
                    .vertexFormat(VkFormat.R32G32B32_SFLOAT)
                    .vertexData(vd -> vd.deviceAddress(vertexAddress))
                    .vertexStride(12)  // 12 bytes (3 floats)
                    .maxVertex(2)  // Индекс последней вершины (0-based)
                    .indexType(VkIndexType.UINT32)  // Используем индексы!
                    .indexData(vd -> vd.deviceAddress(indexAddress))  // Адрес индексного буфера
                    .transformData(vd -> vd.deviceAddress(0));  // NULL transform (как у Sascha Willems!)

            var geometry = VkAccelerationStructureGeometryKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
                    .geometryType(VkGeometryTypeKHR.TRIANGLES)
                    .geometry(vkGeometryDataKHR -> vkGeometryDataKHR.triangles(triangles))
                    .flags(VkGeometryFlagsKHR.OPAQUE);  // OPAQUE

            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
                    .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL)
                    .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE | VkBuildAccelerationStructureFlagsKHR.ALLOW_UPDATE)
                    .mode(VkBuildAccelerationStructureModeKHR.BUILD)
                    .geometryCount(1)
                    .pGeometries(geometry);

            // 1 треугольник (3 вершины без индексов)
            var buildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.allocate(arena)
                    .primitiveCount(1)
                    .primitiveOffset(0)
                    .firstVertex(0)
                    .transformOffset(0);
            var ppBuildRangeInfo = PointerPtr.allocate(arena);
            ppBuildRangeInfo.write(buildRangeInfo);

            // Получаем размеры
            var maxPrimCount = IntPtr.allocate(arena, 1);
            maxPrimCount.write(0, 1);
            var sizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
            deviceCommands.getAccelerationStructureBuildSizesKHR(device, VkAccelerationStructureBuildTypeKHR.DEVICE, buildInfo, maxPrimCount, sizeInfo);

            System.out.println("=== BLAS DEBUG ===");
            System.out.println("Vertex address: 0x" + Long.toHexString(vertexAddress));
            System.out.println("Index address: 0x" + Long.toHexString(indexAddress));
            System.out.println("Transform address: 0x" + Long.toHexString(transformAddress));
            System.out.println("Vertex stride: 12");
            System.out.println("Vertex format: R32G32B32_SFLOAT");
            System.out.println("Index type: UINT32");
            System.out.println("Max vertex: 2");
            System.out.println("Primitive count: 1");
            System.out.println("AS size: " + sizeInfo.accelerationStructureSize());
            System.out.println("Build scratch size: " + sizeInfo.buildScratchSize());
            System.out.println("Build type: DEVICE");
            System.out.println("Build flags: PREFER_FAST_TRACE | ALLOW_UPDATE");

            // Выделяем буфер для самой структуры ускорения
            int alignment = asProps.minAccelerationStructureScratchOffsetAlignment();
            final int MIN_PADDING = 4096;
            long blasSize = sizeInfo.accelerationStructureSize();
            long scratchSize = sizeInfo.buildScratchSize();
            blasSize = (blasSize + MIN_PADDING - 1) & ~(MIN_PADDING - 1);
            scratchSize = (scratchSize + alignment - 1) & ~(alignment - 1);

            var blasResult = createAccelerationStructureBuffer((int) blasSize);
            blasBuffer = blasResult.first;
            blasBufferMemory = null; // createAccelerationStructureBuffer теперь не возвращает memory

            // Создаем саму структуру ускорения
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

            // ========== BUILD BLAS ==========
            var scratchBuffer1 = createScratchBuffer((int) scratchSize);
            long scratchAddress = getBufferDeviceAddress(scratchBuffer1.first);
            long alignedScratchAddress = (scratchAddress + alignment - 1) & ~(alignment - 1);

            buildInfo.scratchData().deviceAddress(alignedScratchAddress);
            buildInfo.dstAccelerationStructure(blas);

            var cmd1 = beginSingleTimeCommands();
            
            // Memory barrier перед BLAS build
            var memoryBarrier = VkMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.MEMORY_BARRIER)
                    .srcAccessMask(VkAccessFlags.HOST_WRITE)
                    .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
            deviceCommands.cmdPipelineBarrier(cmd1, VkPipelineStageFlags.HOST, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, 0, 1, memoryBarrier, 0, null, 0, null);
            
            deviceCommands.cmdBuildAccelerationStructuresKHR(cmd1, 1, buildInfo, ppBuildRangeInfo);

            // Memory barrier после BLAS build
            var blasBarrier = VkMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.MEMORY_BARRIER)
                    .srcAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_WRITE_KHR)
                    .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
            deviceCommands.cmdPipelineBarrier(cmd1, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR, 0, 1, blasBarrier, 0, null, 0, null);

            endSingleTimeCommands(cmd1);

            deviceCommands.destroyBuffer(device, scratchBuffer1.first, null);
            deviceCommands.destroyBuffer(device, localIndexBuffer, null);
            deviceCommands.freeMemory(device, indexBufferMemory, null);
            deviceCommands.destroyBuffer(device, localTransformBuffer, null);
            deviceCommands.freeMemory(device, transformBufferMemory, null);
            
            // Получаем ACCELERATION STRUCTURE device address (не buffer address!)
            var asAddressInfo = VkAccelerationStructureDeviceAddressInfoKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR)
                    .accelerationStructure(blas);
            long blasAddress = deviceCommands.getAccelerationStructureDeviceAddressKHR(device, asAddressInfo);
            System.out.println("BLAS AS handle: 0x" + Long.toHexString(blas.segment().address()));
            System.out.println("BLAS AS address (for TLAS): 0x" + Long.toHexString(blasAddress));

            // ========== TOP LEVEL AS (TLAS) ==========
            // VkAccelerationStructureInstanceKHR layout (64 байта, bitfield packing!):
            // Offset 0-47:  transform[12] (12 floats = 48 bytes)
            // Offset 48-51: packed uint32: instanceCustomIndex(24 bits) | mask(8 bits)
            // Offset 52-55: packed uint32: instanceShaderBindingTableRecordOffset(24 bits) | flags(8 bits)
            // Offset 56-63: accelerationStructureReference (8 bytes) - BLAS device address
            final int INSTANCE_SIZE = 64;
            var instanceData = arena.allocate(INSTANCE_SIZE);

            // Transform matrix: 12 floats (offset 0-47)
            float[] instanceTransform = {
                    1.0f, 0.0f, 0.0f, 0.0f,  // Row 0: x-ось
                    0.0f, 1.0f, 0.0f, 0.0f,  // Row 1: y-ось
                    0.0f, 0.0f, 1.0f, 0.0f   // Row 2: z-ось (no translation)
            };
            for (int i = 0; i < 12; i++) {
                instanceData.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, instanceTransform[i]);
            }

            // ПРАВИЛЬНЫЙ bitfield packing для Vulkan:
            // Offset 48: instanceCustomIndex(24) | mask(8)
            int customIndexAndMask = 0 | (0xFF << 24);  // customIndex=0, mask=0xFF
            instanceData.set(ValueLayout.JAVA_INT, 48, customIndexAndMask);

            // Offset 52: instanceShaderBindingTableRecordOffset(24) | flags(8)
            int sbtOffsetAndFlags = 0 | (VkGeometryInstanceFlagsKHR.TRIANGLE_FACING_CULL_DISABLE << 24);
            instanceData.set(ValueLayout.JAVA_INT, 52, sbtOffsetAndFlags);

            // Offset 56: accelerationStructureReference (BLAS device address)
            instanceData.set(ValueLayout.JAVA_LONG, 56, blasAddress);
            
            // Проверяем запись - читаем обратно
            long verifyAddress = instanceData.get(ValueLayout.JAVA_LONG, 56);
            System.out.println("BLAS address written: 0x" + Long.toHexString(blasAddress));
            System.out.println("BLAS address read back: 0x" + Long.toHexString(verifyAddress));
            System.out.println("Address match: " + (blasAddress == verifyAddress));
            
            // Debug: показываем все байты instance
            System.out.println("=== TLAS INSTANCE BYTES (64 bytes) ===");
            for (int i = 0; i < 64; i += 8) {
                long val = instanceData.get(ValueLayout.JAVA_LONG, i);
                System.out.printf("Offset %3d: 0x%016X%n", i, val);
            }

            // Проверяем BLAS address в буфере
            long addr56 = instanceData.get(ValueLayout.JAVA_LONG, 56);
            System.out.println("BLAS address in instBuffer: 0x" + Long.toHexString(addr56));

            // Буфер для экземпляра
            var instBufferCreateInfo = VkBufferCreateInfo.allocate(arena)
                    .size(INSTANCE_SIZE)
                    .usage(VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR);
            var pInstBuffer = VkBuffer.Ptr.allocate(arena);
            var instResult = deviceCommands.createBuffer(device, instBufferCreateInfo, null, pInstBuffer);
            if (instResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to create inst buffer: " + VkResult.explain(instResult));
            }
            VkBuffer instBuffer = pInstBuffer.read();
            
            // Получаем memory requirements
            var instMemReqs = VkMemoryRequirements.allocate(arena);
            deviceCommands.getBufferMemoryRequirements(device, instBuffer, instMemReqs);
            
            // Выделяем память HOST_VISIBLE | HOST_COHERENT
            var instMemAlloc = VkMemoryAllocateInfo.allocate(arena)
                    .allocationSize(instMemReqs.size())
                    .memoryTypeIndex(findMemoryType(instMemReqs.memoryTypeBits(), VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT));
            var instMemAllocFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                    .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                    .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
            instMemAlloc.pNext(instMemAllocFlags.segment());
            
            var pInstDeviceMemory = VkDeviceMemory.Ptr.allocate(arena);
            result = deviceCommands.allocateMemory(device, instMemAlloc, null, pInstDeviceMemory);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to allocate inst buffer memory: " + VkResult.explain(result));
            }
            
            result = deviceCommands.bindBufferMemory(device, instBuffer, pInstDeviceMemory.read(), 0);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to bind inst buffer memory: " + VkResult.explain(result));
            }
            
            var ppData = PointerPtr.allocate(arena);
            result = deviceCommands.mapMemory(device, pInstDeviceMemory.read(), 0, INSTANCE_SIZE, 0, ppData);
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to map inst buffer: " + VkResult.explain(result));
            }
            var mappedSegment = ppData.read().reinterpret(INSTANCE_SIZE);
            mappedSegment.copyFrom(instanceData);

            // Проверяем копирование
            long verifyInBuffer = mappedSegment.get(ValueLayout.JAVA_LONG, 56);
            System.out.println("BLAS address in instBuffer: 0x" + Long.toHexString(verifyInBuffer));
            
            deviceCommands.unmapMemory(device, pInstDeviceMemory.read());
            
            long instAddress = getBufferDeviceAddress(instBuffer);

            var instancesData = VkAccelerationStructureGeometryInstancesDataKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR);
            instancesData.data().deviceAddress(instAddress);

            var tlasGeometry = VkAccelerationStructureGeometryKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
                    .geometryType(VkGeometryTypeKHR.INSTANCES)
                    .flags(VkGeometryFlagsKHR.OPAQUE);  // OPAQUE как у Sascha!
            tlasGeometry.geometry().instances(instancesData);

            var tlasBuildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
                    .type(VkAccelerationStructureTypeKHR.TOP_LEVEL)
                    .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE)
                    .mode(VkBuildAccelerationStructureModeKHR.BUILD)
                    .geometryCount(1)
                    .pGeometries(tlasGeometry);

            var tlasBuildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.allocate(arena)
                    .primitiveCount(1) // 1 экземпляр в TLAS
                    .primitiveOffset(0)
                    .firstVertex(0)
                    .transformOffset(0);

            var ppTlasBuildRangeInfo = PointerPtr.allocate(arena);
            ppTlasBuildRangeInfo.write(tlasBuildRangeInfo);

            var tlasMaxPrim = IntPtr.allocate(arena, 1);
            tlasMaxPrim.write(0, 1);
            var tlasSizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
            deviceCommands.getAccelerationStructureBuildSizesKHR(device, VkAccelerationStructureBuildTypeKHR.DEVICE, tlasBuildInfo, tlasMaxPrim, tlasSizeInfo);

            long tlasSize = tlasSizeInfo.accelerationStructureSize();
            long tlasScratchSize = tlasSizeInfo.buildScratchSize();
            tlasSize = (tlasSize + MIN_PADDING - 1) & ~(MIN_PADDING - 1);
            tlasScratchSize = (tlasScratchSize + alignment - 1) & ~(alignment - 1);
            tlasScratchSize += 4096;

            var tlasResult = createAccelerationStructureBuffer((int) tlasSize);
            tlasBuffer = tlasResult.first;
            tlasBufferMemory = null;

            var tlasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
                    .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
                    .buffer(tlasBuffer)
                    .size(tlasSize)
                    .type(VkAccelerationStructureTypeKHR.TOP_LEVEL);
            var pTlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
            result = deviceCommands.createAccelerationStructureKHR(device, tlasCreateInfo, null, pTlas);
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
            
            var cmd2 = beginSingleTimeCommands();
            deviceCommands.cmdBuildAccelerationStructuresKHR(cmd2, 1, tlasBuildInfo, ppTlasBuildRangeInfo);
            endSingleTimeCommands(cmd2);
            
            deviceCommands.destroyBuffer(device, scratchBuffer2.first, null);
            deviceCommands.destroyBuffer(device, instBuffer, null);
        }
    }

    private void fenceSync() {
        try (var arena = Arena.ofConfined()) {
            var fenceInfo = VkFenceCreateInfo.allocate(arena);
            var pFence = VkFence.Ptr.allocate(arena);
            var result = deviceCommands.createFence(device, fenceInfo, null, pFence);
            if (result == VkResult.SUCCESS) {
                var submitInfo = VkSubmitInfo.allocate(arena);
                result = deviceCommands.queueSubmit(graphicsQueue, 1, submitInfo, pFence.read());
                if (result == VkResult.SUCCESS) {
                    deviceCommands.waitForFences(device, 1, pFence, VkConstants.TRUE, UINT64_MAX);
                }
                deviceCommands.destroyFence(device, pFence.read(), null);
            }
        }
    }

    private long getAccelerationStructureDeviceAddress(VkAccelerationStructureKHR as) {
        try (var arena = Arena.ofConfined()) {
            var info = VkAccelerationStructureDeviceAddressInfoKHR.allocate(arena);
            info.accelerationStructure(as);
            return deviceCommands.getAccelerationStructureDeviceAddressKHR(device, info);
        }
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
            
            System.out.println("Output image and view created successfully");
        }
    }

    private void transitionOutputImageToGeneral() {
        try (var arena = Arena.ofConfined()) {
            // Создаем временный command pool и buffer
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
            System.out.println("Shader group handle size: " + handleSize);
            System.out.println("Shader group handle alignment: " + handleAlignment);
            System.out.println("Shader group base alignment: " + shaderGroupBaseAlignment);
            // Используем stride=handleSize (32) как у Sascha Willems, НЕ 128!
            sbtRecordSize = handleSize;
            var pushConstantRange = VkPushConstantRange.allocate(arena)
                    .stageFlags(VkShaderStageFlags.RAYGEN_KHR)
                    .offset(0)
                    .size(128);
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
            System.out.println("Shader group handle size: " + handleSize);
            System.out.println("Shader group handle alignment: " + handleAlignment);
            System.out.println("Shader group base alignment: " + shaderGroupBaseAlignment);
            
            // Sascha Willems использует отдельные буферы для каждого SBT региона!
            // Каждый буфер содержит ровно один handle (32 байта).
            sbtRecordSize = handleSize;
            int handleSizeAligned = ((handleSize + handleAlignment - 1) / handleAlignment) * handleAlignment;

            // Получаем shader group handles
            var handles = BytePtr.allocate(arena, groupCount * handleSizeAligned);
            var getResult = deviceCommands.getRayTracingShaderGroupHandlesKHR(device, rayTracingPipeline, 0, groupCount, groupCount * handleSizeAligned, handles.segment());
            if (getResult != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to get shader group handles: " + VkResult.explain(getResult));
            }

            System.out.println("=== SHADER GROUP HANDLES ===");
            for (int i = 0; i < groupCount; i++) {
                int handle0 = handles.segment().get(ValueLayout.JAVA_INT, i * handleSizeAligned);
                System.out.println("Group " + i + " handle (first 4 bytes): 0x" + Integer.toHexString(handle0));
            }

            // Создаём ТРИ ОТДЕЛЬНЫХ буфера (как у Sascha Willems)
            // Буфер 0: Raygen
            raygenAddress = createSingleSbtBuffer(arena, handles.segment().asSlice(0, handleSize));
            // Буфер 1: Miss
            missAddress = createSingleSbtBuffer(arena, handles.segment().asSlice(handleSizeAligned, handleSize));
            // Буфер 2: Hit
            hitAddress = createSingleSbtBuffer(arena, handles.segment().asSlice(2 * handleSizeAligned, handleSize));
            
            sbtDeviceAddress = raygenAddress;

            System.out.println("=== SBT DEBUG (SEPARATE BUFFERS) ===");
            System.out.println("Raygen address: 0x" + Long.toHexString(raygenAddress));
            System.out.println("Miss address: 0x" + Long.toHexString(missAddress));
            System.out.println("Hit address: 0x" + Long.toHexString(hitAddress));
            System.out.println("Handle size: " + handleSize);
            System.out.println("SBT record size: " + sbtRecordSize);
        }
    }

    private long createSingleSbtBuffer(Arena arena, MemorySegment handleData) {
        int bufferSize = handleSize;
        
        // Создаём буфер
        var bufferCreateInfo = VkBufferCreateInfo.allocate(arena)
                .size(bufferSize)
                .usage(VkBufferUsageFlags.SHADER_BINDING_TABLE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS);

        var pBuffer = VkBuffer.Ptr.allocate(arena);
        var result = deviceCommands.createBuffer(device, bufferCreateInfo, null, pBuffer);
        if (result != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to create SBT buffer: " + VkResult.explain(result));
        }
        VkBuffer buffer = pBuffer.read();

        // Получаем memory requirements
        var memReqs = VkMemoryRequirements.allocate(arena);
        deviceCommands.getBufferMemoryRequirements(device, buffer, memReqs);

        // Выделяем память HOST_VISIBLE | HOST_COHERENT | DEVICE_ADDRESS
        var memAlloc = VkMemoryAllocateInfo.allocate(arena)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(findMemoryType(memReqs.memoryTypeBits(), VkMemoryPropertyFlags.HOST_VISIBLE | VkMemoryPropertyFlags.HOST_COHERENT));
        var memAllocFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
        memAlloc.pNext(memAllocFlags.segment());

        var pDeviceMemory = VkDeviceMemory.Ptr.allocate(arena);
        result = deviceCommands.allocateMemory(device, memAlloc, null, pDeviceMemory);
        if (result != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to allocate SBT memory: " + VkResult.explain(result));
        }
        VkDeviceMemory memory = pDeviceMemory.read();

        // Bind memory
        result = deviceCommands.bindBufferMemory(device, buffer, memory, 0);
        if (result != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to bind SBT buffer memory: " + VkResult.explain(result));
        }

        // Получаем device address
        var info = VkBufferDeviceAddressInfo.allocate(arena)
                .sType(VkStructureType.BUFFER_DEVICE_ADDRESS_INFO)
                .buffer(buffer);
        long address = deviceCommands.getBufferDeviceAddress(device, info);

        // Копируем handle в буфер
        var ppData = PointerPtr.allocate(arena);
        result = deviceCommands.mapMemory(device, memory, 0, bufferSize, 0, ppData);
        if (result != VkResult.SUCCESS) {
            throw new RuntimeException("Failed to map SBT memory: " + VkResult.explain(result));
        }
        ppData.read().reinterpret(bufferSize).copyFrom(handleData);
        deviceCommands.unmapMemory(device, memory);

        return address;
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
            if (result != VkResult.SUCCESS) {
                throw new RuntimeException("Failed to submit draw command buffer: " + VkResult.explain(result));
            }
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
            if (width > 0 && height > 0) {
                needsSwapchainRecreation = true;
            }
        }
    }

    private void recordCommandBuffer(VkCommandBuffer cmd, int imageIndex) {
        try (var arena = Arena.ofConfined()) {
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
            var callableRegion = VkStridedDeviceAddressRegionKHR.allocate(arena)
                    .deviceAddress(0)
                    .stride(0)
                    .size(0);
            
            // outputImage уже в GENERAL layout после инициализации
            // Просто делаем barrier для synchronization
            var rayTraceBarrier = VkImageMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                    .oldLayout(VkImageLayout.GENERAL)
                    .newLayout(VkImageLayout.GENERAL)
                    .srcAccessMask(VkAccessFlags.SHADER_WRITE)  // Ждём пока закончит писать из предыдущего фрейма
                    .dstAccessMask(VkAccessFlags.SHADER_WRITE)  // Разрешаем писать снова
                    .image(outputImage)
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
            deviceCommands.cmdPipelineBarrier(cmd, 
                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,  // srcStage
                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,  // dstStage
                    0, 0, null, 0, null, 1, rayTraceBarrier);
            
            deviceCommands.cmdBindPipeline(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, rayTracingPipeline);
            var pDescriptorSet = VkDescriptorSet.Ptr.allocate(arena);
            pDescriptorSet.write(descriptorSet);
            deviceCommands.cmdBindDescriptorSets(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, pipelineLayout, 0, 1, pDescriptorSet, 0, null);
            float aspectRatio = (float)swapChainExtent.width() / (float)swapChainExtent.height();
            float fovRadians = (float)Math.toRadians(cameraFOV);
            Matrix4f projection = new Matrix4f().setPerspective(fovRadians, aspectRatio, 0.1f, 100.0f, true);
            Matrix4f view = new Matrix4f();
            view.lookAt(new Vector3f(0.0f, 0.0f, 5.0f), new Vector3f(0.0f, 0.0f, 0.0f), new Vector3f(0.0f, 1.0f, 0.0f));
            Matrix4f invProjection = new Matrix4f(projection).invert();
            Matrix4f invView = new Matrix4f(view).invert();
            float[] pushConstants = new float[32];
            invProjection.get(pushConstants, 0);
            invView.get(pushConstants, 16);
            var nativeMemory = arena.allocate(ValueLayout.JAVA_FLOAT, 32);
            for (int i = 0; i < 32; i++) {
                nativeMemory.set(ValueLayout.JAVA_FLOAT, i * Float.BYTES, pushConstants[i]);
            }
            deviceCommands.cmdPushConstants(cmd, pipelineLayout, VkShaderStageFlags.RAYGEN_KHR, 0, 128, nativeMemory);
            
            System.out.println("=== TRACE RAYS DEBUG ===");
            System.out.println("Raygen region: addr=0x" + Long.toHexString(raygenAddress) + ", stride=" + sbtRecordSize + ", size=" + sbtRecordSize);
            System.out.println("Miss region: addr=0x" + Long.toHexString(missAddress) + ", stride=" + sbtRecordSize + ", size=" + sbtRecordSize);
            System.out.println("Hit region: addr=0x" + Long.toHexString(hitAddress) + ", stride=" + sbtRecordSize + ", size=" + sbtRecordSize);
            System.out.println("Launch size: " + swapChainExtent.width() + "x" + swapChainExtent.height());
            
            deviceCommands.cmdTraceRaysKHR(cmd, raygenRegion, missRegion, hitRegion, callableRegion, swapChainExtent.width(), swapChainExtent.height(), 1);
            
            // Barrier 2: outputImage GENERAL → TRANSFER_SRC для копирования
            var transferBarrier = VkImageMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                    .oldLayout(VkImageLayout.GENERAL)
                    .newLayout(VkImageLayout.TRANSFER_SRC_OPTIMAL)
                    .srcAccessMask(VkAccessFlags.SHADER_WRITE)
                    .dstAccessMask(VkAccessFlags.TRANSFER_READ)
                    .image(outputImage)
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.RAY_TRACING_SHADER_KHR, VkPipelineStageFlags.TRANSFER, 0, 0, null, 0, null, 1, transferBarrier);
            
            // Barrier 3: swapchain image UNDEFINED → TRANSFER_DST
            var swapchainBarrier = VkImageMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                    .oldLayout(VkImageLayout.UNDEFINED)
                    .newLayout(VkImageLayout.TRANSFER_DST_OPTIMAL)
                    .srcAccessMask(0)
                    .dstAccessMask(VkAccessFlags.TRANSFER_WRITE)
                    .image(swapChainImages.read(imageIndex))
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TOP_OF_PIPE, VkPipelineStageFlags.TRANSFER, 0, 0, null, 0, null, 1, swapchainBarrier);
            
            // Copy output image to swapchain
            var copy = VkImageCopy.allocate(arena)
                    .srcSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
                    .dstSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
                    .extent(e -> e.width(swapChainExtent.width()).height(swapChainExtent.height()).depth(1));
            deviceCommands.cmdCopyImage(cmd, outputImage, VkImageLayout.TRANSFER_SRC_OPTIMAL, swapChainImages.read(imageIndex), VkImageLayout.TRANSFER_DST_OPTIMAL, 1, copy);

            // Barrier 4: swapchain image TRANSFER_DST → PRESENT_SRC
            var presentBarrier = VkImageMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                    .oldLayout(VkImageLayout.TRANSFER_DST_OPTIMAL)
                    .newLayout(VkImageLayout.PRESENT_SRC_KHR)
                    .srcAccessMask(VkAccessFlags.TRANSFER_WRITE)
                    .dstAccessMask(0)
                    .image(swapChainImages.read(imageIndex))
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TRANSFER, VkPipelineStageFlags.BOTTOM_OF_PIPE, 0, 0, null, 0, null, 1, presentBarrier);
            
            // Barrier 5: outputImage TRANSFER_SRC → GENERAL для следующего фрейма
            var outputBarrier = VkImageMemoryBarrier.allocate(arena)
                    .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                    .oldLayout(VkImageLayout.TRANSFER_SRC_OPTIMAL)
                    .newLayout(VkImageLayout.GENERAL)
                    .srcAccessMask(VkAccessFlags.TRANSFER_READ)
                    .dstAccessMask(VkAccessFlags.SHADER_WRITE)
                    .image(outputImage)
                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
            deviceCommands.cmdPipelineBarrier(cmd, VkPipelineStageFlags.TRANSFER, VkPipelineStageFlags.RAY_TRACING_SHADER_KHR, 0, 0, null, 0, null, 1, outputBarrier);
        }
    }

    private VkCommandBuffer beginSingleTimeCommands() {
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
            this.transientCommandPool = cmdPool;
            return cmd;
        }
    }

    private void endSingleTimeCommands(VkCommandBuffer cmd) {
        try (var arena = Arena.ofConfined()) {
            deviceCommands.endCommandBuffer(cmd);
            var submitInfo = VkSubmitInfo.allocate(arena).commandBufferCount(1).pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, cmd));
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
            if (transientCommandPool != null) {
                deviceCommands.freeCommandBuffers(device, transientCommandPool, 1, VkCommandBuffer.Ptr.allocateV(arena, cmd));
                deviceCommands.destroyCommandPool(device, transientCommandPool, null);
                transientCommandPool = null;
            }
        }
    }

    private void recreateSwapChain() {
        try (var arena = Arena.ofConfined()) {
            IntPtr w = IntPtr.allocate(arena), h = IntPtr.allocate(arena);
            glfw.getFramebufferSize(window, w, h);
            int width = w.read();
            int height = h.read();
            if (width <= 0 || height <= 0) {
                System.out.println("Window has invalid dimensions. Deferring swapchain recreation.");
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
            recreateDescriptorPool();
            createDescriptorSet();
            System.out.println("Swapchain successfully recreated with dimensions: " + swapChainExtent.width() + "x" + swapChainExtent.height());
        } catch (RuntimeException e) {
            System.err.println("Failed to recreate swapchain: " + e.getMessage());
            needsSwapchainRecreation = true;
        }
    }

    private void cleanupSwapChain() {
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