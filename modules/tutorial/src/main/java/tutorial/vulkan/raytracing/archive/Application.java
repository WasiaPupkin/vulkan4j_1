//package tutorial.vulkan.raytracing.archive;
//
//import club.doki7.ffm.NativeLayout;
//import club.doki7.ffm.annotation.Bitmask;
//import club.doki7.ffm.annotation.EnumType;
//import club.doki7.ffm.library.ILibraryLoader;
//import club.doki7.ffm.library.ISharedLibrary;
//import club.doki7.ffm.ptr.BytePtr;
//import club.doki7.ffm.ptr.FloatPtr;
//import club.doki7.ffm.ptr.IntPtr;
//import club.doki7.ffm.ptr.PointerPtr;
//import club.doki7.glfw.GLFW;
//import club.doki7.glfw.GLFWLoader;
//import club.doki7.glfw.handle.GLFWwindow;
//import club.doki7.shaderc.Shaderc;
//import club.doki7.shaderc.enumtype.ShadercEnvVersion;
//import club.doki7.shaderc.enumtype.ShadercTargetEnv;
//import club.doki7.shaderc.handle.ShadercCompileOptions;
//import club.doki7.shaderc.handle.ShadercCompiler;
//import club.doki7.vma.VMA;
//import club.doki7.vma.VMAUtil;
//import club.doki7.vma.bitmask.VmaAllocationCreateFlags;
//import club.doki7.vma.bitmask.VmaAllocatorCreateFlags;
//import club.doki7.vma.datatype.VmaAllocationCreateInfo;
//import club.doki7.vma.datatype.VmaAllocationInfo;
//import club.doki7.vma.datatype.VmaAllocatorCreateInfo;
//import club.doki7.vma.datatype.VmaVulkanFunctions;
//import club.doki7.vma.enumtype.VmaMemoryUsage;
//import club.doki7.vma.handle.VmaAllocation;
//import club.doki7.vma.handle.VmaAllocator;
//import club.doki7.vulkan.Version;
//import club.doki7.vulkan.VkConstants;
//import club.doki7.vulkan.bitmask.VkAccessFlags;
//import club.doki7.vulkan.bitmask.VkBufferUsageFlags;
//import club.doki7.vulkan.bitmask.VkBuildAccelerationStructureFlagsKHR;
//import club.doki7.vulkan.bitmask.VkCommandBufferUsageFlags;
//import club.doki7.vulkan.bitmask.VkCommandPoolCreateFlags;
//import club.doki7.vulkan.bitmask.VkCompositeAlphaFlagsKHR;
//import club.doki7.vulkan.bitmask.VkDebugUtilsMessageSeverityFlagsEXT;
//import club.doki7.vulkan.bitmask.VkDebugUtilsMessageTypeFlagsEXT;
//import club.doki7.vulkan.bitmask.VkFenceCreateFlags;
//import club.doki7.vulkan.bitmask.VkGeometryInstanceFlagsKHR;
//import club.doki7.vulkan.bitmask.VkImageAspectFlags;
//import club.doki7.vulkan.bitmask.VkImageUsageFlags;
//import club.doki7.vulkan.bitmask.VkMemoryPropertyFlags;
//import club.doki7.vulkan.bitmask.VkPipelineStageFlags;
//import club.doki7.vulkan.bitmask.VkQueueFlags;
//import club.doki7.vulkan.bitmask.VkSampleCountFlags;
//import club.doki7.vulkan.bitmask.VkShaderStageFlags;
//import club.doki7.vulkan.command.VkDeviceCommands;
//import club.doki7.vulkan.command.VkEntryCommands;
//import club.doki7.vulkan.command.VkInstanceCommands;
//import club.doki7.vulkan.command.VkStaticCommands;
//import club.doki7.vulkan.command.VulkanLoader;
//import club.doki7.vulkan.datatype.VkAccelerationStructureBuildGeometryInfoKHR;
//import club.doki7.vulkan.datatype.VkAccelerationStructureBuildRangeInfoKHR;
//import club.doki7.vulkan.datatype.VkAccelerationStructureBuildSizesInfoKHR;
//import club.doki7.vulkan.datatype.VkAccelerationStructureCreateInfoKHR;
//import club.doki7.vulkan.datatype.VkAccelerationStructureDeviceAddressInfoKHR;
//import club.doki7.vulkan.datatype.VkAccelerationStructureGeometryInstancesDataKHR;
//import club.doki7.vulkan.datatype.VkAccelerationStructureGeometryKHR;
//import club.doki7.vulkan.datatype.VkAccelerationStructureGeometryTrianglesDataKHR;
//import club.doki7.vulkan.datatype.VkApplicationInfo;
//import club.doki7.vulkan.datatype.VkBufferCreateInfo;
//import club.doki7.vulkan.datatype.VkBufferDeviceAddressInfo;
//import club.doki7.vulkan.datatype.VkCommandBufferAllocateInfo;
//import club.doki7.vulkan.datatype.VkCommandBufferBeginInfo;
//import club.doki7.vulkan.datatype.VkCommandPoolCreateInfo;
//import club.doki7.vulkan.datatype.VkDebugUtilsMessengerCallbackDataEXT;
//import club.doki7.vulkan.datatype.VkDebugUtilsMessengerCreateInfoEXT;
//import club.doki7.vulkan.datatype.VkDescriptorImageInfo;
//import club.doki7.vulkan.datatype.VkDescriptorPoolCreateInfo;
//import club.doki7.vulkan.datatype.VkDescriptorPoolSize;
//import club.doki7.vulkan.datatype.VkDescriptorSetAllocateInfo;
//import club.doki7.vulkan.datatype.VkDescriptorSetLayoutBinding;
//import club.doki7.vulkan.datatype.VkDescriptorSetLayoutCreateInfo;
//import club.doki7.vulkan.datatype.VkDeviceCreateInfo;
//import club.doki7.vulkan.datatype.VkDeviceQueueCreateInfo;
//import club.doki7.vulkan.datatype.VkExtensionProperties;
//import club.doki7.vulkan.datatype.VkExtent2D;
//import club.doki7.vulkan.datatype.VkFenceCreateInfo;
//import club.doki7.vulkan.datatype.VkImageCopy;
//import club.doki7.vulkan.datatype.VkImageCreateInfo;
//import club.doki7.vulkan.datatype.VkImageMemoryBarrier;
//import club.doki7.vulkan.datatype.VkImageViewCreateInfo;
//import club.doki7.vulkan.datatype.VkInstanceCreateInfo;
//import club.doki7.vulkan.datatype.VkLayerProperties;
//import club.doki7.vulkan.datatype.VkMemoryBarrier;
//import club.doki7.vulkan.datatype.VkPhysicalDeviceAccelerationStructureFeaturesKHR;
//import club.doki7.vulkan.datatype.VkPhysicalDeviceAccelerationStructurePropertiesKHR;
//import club.doki7.vulkan.datatype.VkPhysicalDeviceFeatures;
//import club.doki7.vulkan.datatype.VkPhysicalDeviceProperties2;
//import club.doki7.vulkan.datatype.VkPhysicalDeviceRayTracingPipelineFeaturesKHR;
//import club.doki7.vulkan.datatype.VkPhysicalDeviceRayTracingPipelinePropertiesKHR;
//import club.doki7.vulkan.datatype.VkPhysicalDeviceVulkan12Features;
//import club.doki7.vulkan.datatype.VkPipelineLayoutCreateInfo;
//import club.doki7.vulkan.datatype.VkPipelineShaderStageCreateInfo;
//import club.doki7.vulkan.datatype.VkPresentInfoKHR;
//import club.doki7.vulkan.datatype.VkPushConstantRange;
//import club.doki7.vulkan.datatype.VkQueueFamilyProperties;
//import club.doki7.vulkan.datatype.VkRayTracingPipelineCreateInfoKHR;
//import club.doki7.vulkan.datatype.VkRayTracingShaderGroupCreateInfoKHR;
//import club.doki7.vulkan.datatype.VkSemaphoreCreateInfo;
//import club.doki7.vulkan.datatype.VkShaderModuleCreateInfo;
//import club.doki7.vulkan.datatype.VkStridedDeviceAddressRegionKHR;
//import club.doki7.vulkan.datatype.VkSubmitInfo;
//import club.doki7.vulkan.datatype.VkSurfaceCapabilitiesKHR;
//import club.doki7.vulkan.datatype.VkSurfaceFormatKHR;
//import club.doki7.vulkan.datatype.VkSwapchainCreateInfoKHR;
//import club.doki7.vulkan.datatype.VkWriteDescriptorSet;
//import club.doki7.vulkan.datatype.VkWriteDescriptorSetAccelerationStructureKHR;
//import club.doki7.vulkan.enumtype.VkAccelerationStructureBuildTypeKHR;
//import club.doki7.vulkan.enumtype.VkAccelerationStructureTypeKHR;
//import club.doki7.vulkan.enumtype.VkBuildAccelerationStructureModeKHR;
//import club.doki7.vulkan.enumtype.VkColorSpaceKHR;
//import club.doki7.vulkan.enumtype.VkCommandBufferLevel;
//import club.doki7.vulkan.enumtype.VkDescriptorType;
//import club.doki7.vulkan.enumtype.VkFormat;
//import club.doki7.vulkan.enumtype.VkGeometryTypeKHR;
//import club.doki7.vulkan.enumtype.VkImageLayout;
//import club.doki7.vulkan.enumtype.VkImageTiling;
//import club.doki7.vulkan.enumtype.VkImageType;
//import club.doki7.vulkan.enumtype.VkImageViewType;
//import club.doki7.vulkan.enumtype.VkIndexType;
//import club.doki7.vulkan.enumtype.VkPipelineBindPoint;
//import club.doki7.vulkan.enumtype.VkPresentModeKHR;
//import club.doki7.vulkan.enumtype.VkRayTracingShaderGroupTypeKHR;
//import club.doki7.vulkan.enumtype.VkResult;
//import club.doki7.vulkan.enumtype.VkSharingMode;
//import club.doki7.vulkan.enumtype.VkStructureType;
//import club.doki7.vulkan.handle.VkAccelerationStructureKHR;
//import club.doki7.vulkan.handle.VkBuffer;
//import club.doki7.vulkan.handle.VkCommandBuffer;
//import club.doki7.vulkan.handle.VkCommandPool;
//import club.doki7.vulkan.handle.VkDebugUtilsMessengerEXT;
//import club.doki7.vulkan.handle.VkDescriptorPool;
//import club.doki7.vulkan.handle.VkDescriptorSet;
//import club.doki7.vulkan.handle.VkDescriptorSetLayout;
//import club.doki7.vulkan.handle.VkDevice;
//import club.doki7.vulkan.handle.VkFence;
//import club.doki7.vulkan.handle.VkImage;
//import club.doki7.vulkan.handle.VkImageView;
//import club.doki7.vulkan.handle.VkInstance;
//import club.doki7.vulkan.handle.VkPhysicalDevice;
//import club.doki7.vulkan.handle.VkPipeline;
//import club.doki7.vulkan.handle.VkPipelineLayout;
//import club.doki7.vulkan.handle.VkQueue;
//import club.doki7.vulkan.handle.VkSemaphore;
//import club.doki7.vulkan.handle.VkShaderModule;
//import club.doki7.vulkan.handle.VkSurfaceKHR;
//import club.doki7.vulkan.handle.VkSwapchainKHR;
//import org.jetbrains.annotations.Nullable;
//import org.joml.Matrix4f;
//import org.joml.Vector3f;
//
//import java.io.IOException;
//import java.lang.foreign.Arena;
//import java.lang.foreign.MemorySegment;
//import java.lang.foreign.ValueLayout;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.util.Objects;
//
//import static club.doki7.ffm.NativeLayout.UINT64_MAX;
//
//public class Application {
//    private static final int WIDTH = 1280;
//    private static final int HEIGHT = 720;
//    private static final BytePtr WINDOW_TITLE = BytePtr.allocateString(Arena.global(), "Ray Tracing Demo");
//    private static final boolean ENABLE_VALIDATION_LAYERS = System.getProperty("validation") != null;
//    private static final String VALIDATION_LAYER_NAME = "VK_LAYER_KHRONOS_validation";
//    // Ray tracing properties
//    private int handleSize;
//    private int handleAlignment;
//    private int alignedHandleSize;
//    private int sbtRecordStride;
//    private long alignedSbtAddress;
//    // Core
//    private GLFWwindow window;
//    private VkEntryCommands entryCommands;
//    private VkInstance instance;
//    private VkInstanceCommands instanceCommands;
//    private VkDebugUtilsMessengerEXT debugMessenger;
//    private VkSurfaceKHR surface;
//    private VkPhysicalDevice physicalDevice;
//    private VkDevice device;
//    private VkDeviceCommands deviceCommands;
//    private VkQueue graphicsQueue;
//    private VkQueue presentQueue;
//    // VMA
//    private VmaAllocator vmaAllocator;
//    // Swapchain
//    private VkSwapchainKHR swapChain;
//    private VkImage.Ptr swapChainImages;
//    private @EnumType(VkFormat.class) int swapChainImageFormat;
//    private VkExtent2D swapChainExtent;
//    private VkImageView.Ptr swapChainImageViews;
//    // Ray tracing resources
//    private VkBuffer vertexBuffer;
//    private VmaAllocation vertexBufferAllocation;
//    private VkAccelerationStructureKHR blas;
//    private VkAccelerationStructureKHR tlas;
//    private VkImage outputImage;
//    private VmaAllocation outputImageAllocation;
//    private VkImageView outputImageView;
//    private VkDescriptorSetLayout descriptorSetLayout;
//    private VkPipelineLayout pipelineLayout;
//    private VkPipeline rayTracingPipeline;
//    private VkDescriptorPool descriptorPool;
//    private VkDescriptorSet descriptorSet;
//    private VkBuffer sbtBuffer;
//    private VmaAllocation sbtAllocation;
//    // Sync
//    private VkSemaphore imageAvailableSemaphore;
//    private VkSemaphore renderFinishedSemaphore;
//    private VkCommandPool commandPool;
//    private VkCommandBuffer commandBuffer;
//    // Shader compiler
//    private ShadercCompiler shadercCompiler;
//    private ShadercCompileOptions shadercCompileOptions;
//    private VkFence inFlightFence;
//    private static final int MAX_FRAMES_IN_FLIGHT = 2;
//    private VkSemaphore[] imageAvailableSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
//    private VkSemaphore[] renderFinishedSemaphores = new VkSemaphore[MAX_FRAMES_IN_FLIGHT];
//    private VkFence[] inFlightFences = new VkFence[MAX_FRAMES_IN_FLIGHT];
//    private VkCommandBuffer[] commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
//    private VkCommandPool transientCommandPool = null;
//    private int currentFrame = 0;
//
//    // Camera parameters
//    private final Vector3f cameraPosition = new Vector3f(0.0f, 0.0f, 2.0f);
//    private final float cameraFOV = 60.0f;
//
//    public void run() {
//        initWindow();
//        initVulkan();
//        mainLoop();
//        cleanup();
//    }
//
//    private void initWindow() {
//        if (glfw.init() != GLFW.TRUE) {
//            throw new RuntimeException("Failed to initialize GLFW");
//        }
//        if (glfw.vulkanSupported() != GLFW.TRUE) {
//            throw new RuntimeException("Vulkan is not supported");
//        }
//        glfw.windowHint(GLFW.CLIENT_API, GLFW.NO_API);
//        window = Objects.requireNonNull(glfw.createWindow(WIDTH, HEIGHT, WINDOW_TITLE, null, null));
//    }
//
//    private void initVulkan() {
//        entryCommands = VulkanLoader.loadEntryCommands(staticCommands);
//        createInstance();
//        setupDebugMessenger();
//        createSurface();
//        pickPhysicalDevice();
//        createLogicalDevice();
//        createSyncObjects();
//        createCommandPool();
//        createVMA();
//        createShaderCompiler();
//        createSwapchain();
//        createImageViews();
//        createVertexBuffer();
//        createAccelerationStructures();
//        createOutputImage();
//        createDescriptorSetLayout();
//        createDescriptorPool();
//        createDescriptorSet();
//        createRayTracingPipeline();
//        createShaderBindingTable();
//        createCommandBuffers();
//    }
//
//    private void mainLoop() {
//        while (glfw.windowShouldClose(window) == GLFW.FALSE) {
//            glfw.pollEvents();
//            drawFrame();
//        }
//        deviceCommands.deviceWaitIdle(device);
//    }
//
//    private void cleanup() {
//        deviceCommands.destroyPipeline(device, rayTracingPipeline, null);
//        deviceCommands.destroyPipelineLayout(device, pipelineLayout, null);
//        deviceCommands.destroyDescriptorSetLayout(device, descriptorSetLayout, null);
//        deviceCommands.destroyDescriptorPool(device, descriptorPool, null);
//        vma.destroyBuffer(vmaAllocator, sbtBuffer, sbtAllocation);
//        vma.destroyBuffer(vmaAllocator, vertexBuffer, vertexBufferAllocation);
//        deviceCommands.destroyAccelerationStructureKHR(device, blas, null);
//        deviceCommands.destroyAccelerationStructureKHR(device, tlas, null);
//        deviceCommands.destroyImageView(device, outputImageView, null);
//        vma.destroyImage(vmaAllocator, outputImage, outputImageAllocation);
//        cleanupSwapChain();
//        deviceCommands.destroyCommandPool(device, commandPool, null);
//        deviceCommands.destroySemaphore(device, imageAvailableSemaphore, null);
//        deviceCommands.destroySemaphore(device, renderFinishedSemaphore, null);
//        vma.destroyAllocator(vmaAllocator);
//        deviceCommands.destroyDevice(device, null);
//        instanceCommands.destroySurfaceKHR(instance, surface, null);
//        if (ENABLE_VALIDATION_LAYERS) {
//            instanceCommands.destroyDebugUtilsMessengerEXT(instance, debugMessenger, null);
//        }
//        instanceCommands.destroyInstance(instance, null);
//        glfw.destroyWindow(window);
//        glfw.terminate();
//        shaderc.compileOptionsRelease(shadercCompileOptions);
//        shaderc.compilerRelease(shadercCompiler);
//    }
//
//    // --- Core Vulkan setup ---
//    private void createInstance() {
//        try (var arena = Arena.ofConfined()) {
//            if (ENABLE_VALIDATION_LAYERS && !checkValidationLayerSupport()) {
//                throw new RuntimeException("Validation layers requested, but not available");
//            }
//            var appInfo = VkApplicationInfo.allocate(arena)
//                    .pApplicationName(BytePtr.allocateString(arena, "Ray Tracing Demo"))
//                    .applicationVersion(new Version(0, 1, 0, 0).encode())
//                    .pEngineName(BytePtr.allocateString(arena, "vk4j-rt"))
//                    .engineVersion(new Version(0, 1, 0, 0).encode())
//                    .apiVersion(Version.VK_API_VERSION_1_2.encode());
//            var instanceCreateInfo = VkInstanceCreateInfo.allocate(arena)
//                    .pApplicationInfo(appInfo);
//            var extensions = getRequiredExtensions(arena);
//            instanceCreateInfo.enabledExtensionCount((int) extensions.size())
//                    .ppEnabledExtensionNames(extensions);
//            if (ENABLE_VALIDATION_LAYERS) {
//                instanceCreateInfo.enabledLayerCount(1)
//                        .ppEnabledLayerNames(PointerPtr.allocateStrings(arena, VALIDATION_LAYER_NAME));
//                var debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate(arena);
//                populateDebugMessengerCreateInfo(debugCreateInfo);
//                instanceCreateInfo.pNext(debugCreateInfo);
//            }
//            var pInstance = VkInstance.Ptr.allocate(arena);
//            var result = entryCommands.createInstance(instanceCreateInfo, null, pInstance);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create instance: " + VkResult.explain(result));
//            }
//            instance = Objects.requireNonNull(pInstance.read());
//            instanceCommands = VulkanLoader.loadInstanceCommands(instance, staticCommands);
//        }
//    }
//
//    private void setupDebugMessenger() {
//        if (!ENABLE_VALIDATION_LAYERS) return;
//        try (var arena = Arena.ofConfined()) {
//            var debugUtilsMessengerCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate(arena);
//            populateDebugMessengerCreateInfo(debugUtilsMessengerCreateInfo);
//            var pDebugMessenger = VkDebugUtilsMessengerEXT.Ptr.allocate(arena);
//            var result = instanceCommands.createDebugUtilsMessengerEXT(
//                    instance, debugUtilsMessengerCreateInfo, null, pDebugMessenger);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to set up debug messenger: " + VkResult.explain(result));
//            }
//            debugMessenger = Objects.requireNonNull(pDebugMessenger.read());
//        }
//    }
//
//    private void createSurface() {
//        try (var arena = Arena.ofConfined()) {
//            var pSurface = VkSurfaceKHR.Ptr.allocate(arena);
//            var result = glfw.createWindowSurface(instance, window, null, pSurface);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create window surface: " + VkResult.explain(result));
//            }
//            surface = Objects.requireNonNull(pSurface.read());
//        }
//    }
//
//    private void pickPhysicalDevice() {
//        try (var arena = Arena.ofConfined()) {
//            var pDeviceCount = IntPtr.allocate(arena);
//            var result = instanceCommands.enumeratePhysicalDevices(instance, pDeviceCount, null);
//            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to enumerate physical devices");
//            var deviceCount = pDeviceCount.read();
//            if (deviceCount == 0) throw new RuntimeException("No GPUs with Vulkan support");
//            var pDevices = VkPhysicalDevice.Ptr.allocate(arena, deviceCount);
//            result = instanceCommands.enumeratePhysicalDevices(instance, pDeviceCount, pDevices);
//            if (result != VkResult.SUCCESS) throw new RuntimeException("Failed to enumerate physical devices");
//            for (var device : pDevices) {
//                if (isDeviceSuitable(device)) {
//                    physicalDevice = device;
//                    return;
//                }
//            }
//            throw new RuntimeException("No suitable GPU found");
//        }
//    }
//
//    private boolean isDeviceSuitable(VkPhysicalDevice device) {
//        var indices = findQueueFamilies(device);
//        if (indices == null) return false;
//        return checkDeviceExtensionSupport(device, new String[]{
//                VkConstants.KHR_SWAPCHAIN_EXTENSION_NAME,
//                VkConstants.KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
//                VkConstants.KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
//                VkConstants.KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
//        });
//    }
//
//    private record QueueFamilyIndices(int graphicsFamily, int presentFamily) {}
//    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device) {
//        try (var arena = Arena.ofConfined()) {
//            var pQueueFamilyCount = IntPtr.allocate(arena);
//            instanceCommands.getPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, null);
//            var count = pQueueFamilyCount.read();
//            var queues = VkQueueFamilyProperties.allocate(arena, count);
//            instanceCommands.getPhysicalDeviceQueueFamilyProperties(device, pQueueFamilyCount, queues);
//            int g = -1, p = -1;
//            var support = IntPtr.allocate(arena);
//            for (int i = 0; i < count; i++) {
//                if ((queues.at(i).queueFlags() & VkQueueFlags.GRAPHICS) != 0) g = i;
//                if (instanceCommands.getPhysicalDeviceSurfaceSupportKHR(device, i, surface, support) == VkResult.SUCCESS
//                        && support.read() == VkConstants.TRUE) p = i;
//                if (g >= 0 && p >= 0) break;
//            }
//            return (g >= 0 && p >= 0) ? new QueueFamilyIndices(g, p) : null;
//        }
//    }
//
//    private boolean checkDeviceExtensionSupport(VkPhysicalDevice device, String[] required) {
//        try (var arena = Arena.ofConfined()) {
//            var pCount = IntPtr.allocate(arena);
//            instanceCommands.enumerateDeviceExtensionProperties(device, null, pCount, null);
//            var count = pCount.read();
//            var exts = VkExtensionProperties.allocate(arena, count);
//            instanceCommands.enumerateDeviceExtensionProperties(device, null, pCount, exts);
//            for (String req : required) {
//                boolean found = false;
//                for (var ext : exts) {
//                    if (req.equals(ext.extensionName().readString())) {
//                        found = true;
//                        break;
//                    }
//                }
//                if (!found) return false;
//            }
//            return true;
//        }
//    }
//
//    private void createLogicalDevice() {
//        var indices = findQueueFamilies(physicalDevice);
//        assert indices != null;
//        try (var arena = Arena.ofConfined()) {
//            var priorities = FloatPtr.allocateV(arena, 1.0f);
//            var queueInfo = VkDeviceQueueCreateInfo.allocate(arena)
//                    .queueFamilyIndex(indices.graphicsFamily())
//                    .queueCount(1)
//                    .pQueuePriorities(priorities);
//            var vulkan12Features = VkPhysicalDeviceVulkan12Features.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
//                    .descriptorIndexing(VkConstants.TRUE)
//                    .shaderSampledImageArrayNonUniformIndexing(VkConstants.TRUE)
//                    .bufferDeviceAddress(VkConstants.TRUE);
//            var asFeatures = VkPhysicalDeviceAccelerationStructureFeaturesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR)
//                    .accelerationStructure(VkConstants.TRUE);
//            var rtFeatures = VkPhysicalDeviceRayTracingPipelineFeaturesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR)
//                    .rayTracingPipeline(VkConstants.TRUE);
//            vulkan12Features.pNext(asFeatures.segment());
//            asFeatures.pNext(rtFeatures.segment());
//            var deviceFeatures = VkPhysicalDeviceFeatures.allocate(arena);
//            var createInfo = VkDeviceCreateInfo.allocate(arena)
//                    .pQueueCreateInfos(queueInfo)
//                    .queueCreateInfoCount(1)
//                    .pEnabledFeatures(deviceFeatures)
//                    .enabledExtensionCount(4)
//                    .ppEnabledExtensionNames(PointerPtr.allocateStrings(arena,
//                            VkConstants.KHR_SWAPCHAIN_EXTENSION_NAME,
//                            VkConstants.KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
//                            VkConstants.KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
//                            VkConstants.KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME))
//                    .pNext(vulkan12Features.segment());
//            if (ENABLE_VALIDATION_LAYERS) {
//                createInfo.enabledLayerCount(1)
//                        .ppEnabledLayerNames(PointerPtr.allocateStrings(arena, VALIDATION_LAYER_NAME));
//            }
//            var pDevice = VkDevice.Ptr.allocate(arena);
//            var result = instanceCommands.createDevice(physicalDevice, createInfo, null, pDevice);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create logical device: " + VkResult.explain(result));
//            }
//            device = Objects.requireNonNull(pDevice.read());
//            deviceCommands = VulkanLoader.loadDeviceCommands(device, staticCommands);
//            var pQueue = VkQueue.Ptr.allocate(arena);
//            deviceCommands.getDeviceQueue(device, indices.graphicsFamily(), 0, pQueue);
//            graphicsQueue = Objects.requireNonNull(pQueue.read());
//            deviceCommands.getDeviceQueue(device, indices.presentFamily(), 0, pQueue);
//            presentQueue = Objects.requireNonNull(pQueue.read());
//        }
//    }
//
//    private void createSyncObjects() {
//        try (var arena = Arena.ofConfined()) {
//            System.out.println("Creating semaphores and fence...");
//            // Semaphores
//            var semInfo = VkSemaphoreCreateInfo.allocate(arena);
//            var pSem = VkSemaphore.Ptr.allocate(arena);
//            var result = deviceCommands.createSemaphore(device, semInfo, null, pSem);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create semaphore: " + VkResult.explain(result));
//            }
//            System.out.println("imageAvailableSemaphore result: " + VkResult.explain(result));
//            imageAvailableSemaphore = pSem.read();
//            var p2 = VkSemaphore.Ptr.allocate(arena);
//            result = deviceCommands.createSemaphore(device, semInfo, null, p2);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create semaphore: " + VkResult.explain(result));
//            }
//            System.out.println("renderFinishedSemaphore result: " + VkResult.explain(result));
//            renderFinishedSemaphore = p2.read();
//            // Fence (start unsignaled)
//            var fenceInfo = VkFenceCreateInfo.allocate(arena);
//            var pFence = VkFence.Ptr.allocate(arena);
//            result = deviceCommands.createFence(device, fenceInfo, null, pFence);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create fence: " + VkResult.explain(result));
//            }
//            System.out.println("Fence result: " + VkResult.explain(result));
//            inFlightFence = pFence.read();
//        }
//    }
//
//    private void createVMA() {
//        try (var arena = Arena.ofConfined()) {
//            var funcs = VmaVulkanFunctions.allocate(arena);
//            VMAUtil.fillVulkanFunctions(funcs, staticCommands, entryCommands, instanceCommands, deviceCommands);
//            var flags = VmaAllocatorCreateFlags.BUFFER_DEVICE_ADDRESS;
//            var info = VmaAllocatorCreateInfo.allocate(arena)
//                    .instance(instance)
//                    .physicalDevice(physicalDevice)
//                    .device(device)
//                    .pVulkanFunctions(funcs)
//                    .vulkanApiVersion(Version.VK_API_VERSION_1_2.encode())
//                    .flags(flags);
//            var pAlloc = VmaAllocator.Ptr.allocate(arena);
//            var result = vma.createAllocator(info, pAlloc);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create VMA allocator: " + VkResult.explain(result));
//            }
//            vmaAllocator = Objects.requireNonNull(pAlloc.read());
//        }
//    }
//
//    private void createShaderCompiler() {
//        shadercCompiler = shaderc.compilerInitialize();
//        shadercCompileOptions = shaderc.compileOptionsInitialize();
//        shaderc.compileOptionsSetTargetEnv(
//                shadercCompileOptions,
//                ShadercTargetEnv.VULKAN,
//                ShadercEnvVersion.VULKAN_1_2
//        );
//    }
//
//    // --- Swapchain ---
//    private void createSwapchain() {
//        try (var arena = Arena.ofConfined()) {
//            var support = querySwapChainSupport(physicalDevice, arena);
//            var format = chooseSwapSurfaceFormat(support.formats());
//            var presentMode = chooseSwapPresentMode(support.presentModes());
//            var extent = chooseSwapExtent(support.capabilities(), arena);
//            var imageCount = Math.min(support.capabilities.maxImageCount(),
//                    Math.max(support.capabilities.minImageCount() + 1, 2));
//            var createInfo = VkSwapchainCreateInfoKHR.allocate(arena)
//                    .surface(surface)
//                    .minImageCount(imageCount)
//                    .imageFormat(format.format())
//                    .imageColorSpace(format.colorSpace())
//                    .imageExtent(extent)
//                    .imageArrayLayers(1)
//                    .imageUsage(VkImageUsageFlags.COLOR_ATTACHMENT | VkImageUsageFlags.TRANSFER_DST)
//                    .imageSharingMode(VkSharingMode.EXCLUSIVE)
//                    .preTransform(support.capabilities.currentTransform())
//                    .compositeAlpha(VkCompositeAlphaFlagsKHR.OPAQUE)
//                    .presentMode(presentMode)
//                    .clipped(VkConstants.TRUE);
//            var pSwapchain = VkSwapchainKHR.Ptr.allocate(arena);
//            var result = deviceCommands.createSwapchainKHR(device, createInfo, null, pSwapchain);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create swapchain: " + VkResult.explain(result));
//            }
//            swapChain = Objects.requireNonNull(pSwapchain.read());
//            var pCount = IntPtr.allocate(arena);
//            deviceCommands.getSwapchainImagesKHR(device, swapChain, pCount, null);
//            var count = pCount.read();
//            swapChainImages = VkImage.Ptr.allocate(Arena.ofAuto(), count);
//            deviceCommands.getSwapchainImagesKHR(device, swapChain, pCount, swapChainImages);
//            swapChainImageFormat = format.format();
//            swapChainExtent = VkExtent2D.clone(Arena.ofAuto(), extent);
//        }
//    }
//
//    private record SwapchainSupportDetails(
//            VkSurfaceCapabilitiesKHR capabilities,
//            VkSurfaceFormatKHR.Ptr formats,
//            IntPtr presentModes
//    ) {}
//
//    private SwapchainSupportDetails querySwapChainSupport(VkPhysicalDevice dev, Arena arena) {
//        var caps = VkSurfaceCapabilitiesKHR.allocate(arena);
//        instanceCommands.getPhysicalDeviceSurfaceCapabilitiesKHR(dev, surface, caps);
//        try (var local = Arena.ofConfined()) {
//            var fcount = IntPtr.allocate(local);
//            instanceCommands.getPhysicalDeviceSurfaceFormatsKHR(dev, surface, fcount, null);
//            var formats = VkSurfaceFormatKHR.allocate(arena, fcount.read());
//            instanceCommands.getPhysicalDeviceSurfaceFormatsKHR(dev, surface, fcount, formats);
//            var pcount = IntPtr.allocate(local);
//            instanceCommands.getPhysicalDeviceSurfacePresentModesKHR(dev, surface, pcount, null);
//            var modes = IntPtr.allocate(arena, pcount.read());
//            instanceCommands.getPhysicalDeviceSurfacePresentModesKHR(dev, surface, pcount, modes);
//            return new SwapchainSupportDetails(caps, formats, modes);
//        }
//    }
//
//    private VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Ptr formats) {
//        for (var f : formats) {
//            if (f.format() == VkFormat.R8G8B8A8_UNORM && f.colorSpace() == VkColorSpaceKHR.SRGB_NONLINEAR)
//                return f;
//        }
//        return formats.at(0);
//    }
//
//    private int chooseSwapPresentMode(IntPtr modes) {
//        for (int m : modes) {
//            if (m == VkPresentModeKHR.MAILBOX) return m;
//        }
//        return VkPresentModeKHR.FIFO;
//    }
//
//    private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR caps, Arena arena) {
//        if (caps.currentExtent().width() != NativeLayout.UINT32_MAX) return caps.currentExtent();
//        try (var local = Arena.ofConfined()) {
//            IntPtr w = IntPtr.allocate(local), h = IntPtr.allocate(local);
//            glfw.getFramebufferSize(window, w, h);
//            return VkExtent2D.allocate(arena)
//                    .width(Math.clamp(w.read(), caps.minImageExtent().width(), caps.maxImageExtent().width()))
//                    .height(Math.clamp(h.read(), caps.minImageExtent().height(), caps.maxImageExtent().height()));
//        }
//    }
//
//    private void createImageViews() {
//        swapChainImageViews = VkImageView.Ptr.allocate(Arena.ofAuto(), swapChainImages.size());
//        for (long i = 0; i < swapChainImages.size(); i++) {
//            swapChainImageViews.write(i, createImageView(
//                    swapChainImages.read(i), swapChainImageFormat, VkImageAspectFlags.COLOR, 1));
//        }
//    }
//
//    private VkImageView createImageView(VkImage image, int format, int aspect, int mipLevels) {
//        try (var arena = Arena.ofConfined()) {
//            var info = VkImageViewCreateInfo.allocate(arena)
//                    .image(image)
//                    .viewType(VkImageViewType._2D)
//                    .format(format)
//                    .subresourceRange(r -> r.aspectMask(aspect).levelCount(mipLevels).layerCount(1));
//            var pView = VkImageView.Ptr.allocate(arena);
//            var result = deviceCommands.createImageView(device, info, null, pView);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create image view: " + VkResult.explain(result));
//            }
//            return pView.read();
//        }
//    }
//
//    // --- Ray Tracing ---
//    private void createVertexBuffer() {
//        float[] vertices = {
//                -0.5f, -0.5f, 0.0f,  // Bottom left
//                0.5f, -0.5f, 0.0f,   // Bottom right
//                0.0f,  0.5f, 0.0f    // Top middle (this order makes counter-clockwise from camera view)
//        };
//        try (var arena = Arena.ofConfined()) {
//            var size = vertices.length * Float.BYTES;
//            var info = VkBufferCreateInfo.allocate(arena)
//                    .size(size)
//                    .usage(VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR);
//            var allocInfo = VmaAllocationCreateInfo.allocate(arena)
//                    .usage(VmaMemoryUsage.AUTO)
//                    .flags(VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE);
//            var pBuffer = VkBuffer.Ptr.allocate(arena);
//            var pAlloc = VmaAllocation.Ptr.allocate(arena);
//            var result = vma.createBuffer(vmaAllocator, info, allocInfo, pBuffer, pAlloc, null);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create vertex buffer: " + VkResult.explain(result));
//            }
//            vertexBuffer = pBuffer.read();
//            vertexBufferAllocation = pAlloc.read();
//            var ppData = PointerPtr.allocate(arena);
//            vma.mapMemory(vmaAllocator, vertexBufferAllocation, ppData);
//            ppData.read().reinterpret(size).copyFrom(MemorySegment.ofArray(vertices));
//            vma.unmapMemory(vmaAllocator, vertexBufferAllocation);
//        }
//    }
//
//    private long getBufferDeviceAddress(VkBuffer buffer) {
//        try (var arena = Arena.ofConfined()) {
//            var info = VkBufferDeviceAddressInfo.allocate(arena).buffer(buffer);
//            return deviceCommands.getBufferDeviceAddress(device, info);
//        }
//    }
//
//    private void createAccelerationStructures() {
//        long vertexAddress = getBufferDeviceAddress(vertexBuffer);
//        try (var arena = Arena.ofConfined()) {
//            // Query acceleration structure properties
//            var asProps = VkPhysicalDeviceAccelerationStructurePropertiesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_PROPERTIES_KHR);
//            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(asProps.segment());
//            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);
//
//            long maxPrimitives = asProps.maxPrimitiveCount();
//            System.out.println("Device max primitive count: " + maxPrimitives);
//
//            // Validate primitive count
//            final int TRIANGLE_COUNT = 1; // We have 1 triangle (3 vertices)
//            if (TRIANGLE_COUNT > maxPrimitives) {
//                throw new RuntimeException("Triangle count exceeds device limit: " + TRIANGLE_COUNT + " > " + maxPrimitives);
//            }
//
//            // === BLAS (Bottom-Level Acceleration Structure) ===
//            var triangles = VkAccelerationStructureGeometryTrianglesDataKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
//                    .vertexFormat(VkFormat.R32G32B32_SFLOAT)
//                    .indexType(VkIndexType.NONE_KHR)
//                    .vertexStride(3 * Float.BYTES)
//                    .maxVertex(2);  // 3 vertices - 1 = 2
//            triangles.vertexData().deviceAddress(vertexAddress);
//            var geometry = VkAccelerationStructureGeometryKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
//                    .geometryType(VkGeometryTypeKHR.TRIANGLES)
//                    .flags(VkGeometryInstanceFlagsKHR.TRIANGLE_FACING_CULL_DISABLE);
//            geometry.geometry().triangles(triangles);
//            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
//                    .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL)
//                    .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE)
//                    .mode(VkBuildAccelerationStructureModeKHR.BUILD)
//                    .geometryCount(1)
//                    .pGeometries(geometry);
//
//            // Create build range info - THIS IS CRITICAL
//            var buildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.allocate(arena)
//                    .primitiveCount(1)  // Only 1 triangle in our geometry
//                    .primitiveOffset(0)
//                    .firstVertex(0)
//                    .transformOffset(0);
//
//            var ppBuildRangeInfo = PointerPtr.allocate(arena);
//            ppBuildRangeInfo.write(buildRangeInfo);
//
//            // Set correct primitive count (1 triangle)
//            var maxPrimCount = IntPtr.allocate(arena, 1);
//            maxPrimCount.write(0, 1);
//
//            // Get required sizes with proper primitive count
//            var sizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
//            deviceCommands.getAccelerationStructureBuildSizesKHR(
//                    device, VkAccelerationStructureBuildTypeKHR.DEVICE, buildInfo, maxPrimCount, sizeInfo);
//
//            // Calculate proper buffer sizes with alignment padding
//            long blasSize = sizeInfo.accelerationStructureSize();
//            long scratchSize = sizeInfo.buildScratchSize();
//            int alignment = asProps.minAccelerationStructureScratchOffsetAlignment();
//
//            // Add proper padding for alignment (must be at least 256 bytes)
//            final int MIN_PADDING = 256;
//            blasSize = (blasSize + MIN_PADDING - 1) & ~(MIN_PADDING - 1);
//            scratchSize = (scratchSize + alignment - 1) & ~(alignment - 1);
//
//            // Add extra safety padding (1KB) to ensure validation passes
//            scratchSize += 1024;
//
//            System.out.println("Required BLAS size: " + blasSize + " bytes (with padding)");
//            System.out.println("Required scratch size: " + scratchSize + " bytes (with padding)");
//
//            // Verify buffer sizes are sufficient
//            if (blasSize < sizeInfo.accelerationStructureSize()) {
//                throw new RuntimeException("BLAS size too small: " + blasSize + " < " + sizeInfo.accelerationStructureSize());
//            }
//            if (scratchSize < sizeInfo.buildScratchSize()) {
//                throw new RuntimeException("Scratch size too small: " + scratchSize + " < " + sizeInfo.buildScratchSize());
//            }
//
//            // Create properly sized buffers
//            var blasBuffer = createAccelerationStructureBuffer((int) blasSize);
//            var scratchBuffer1 = createScratchBuffer((int) scratchSize);
//
//            // Verify scratch buffer has enough space after alignment
//            long scratchAddress = getBufferDeviceAddress(scratchBuffer1.first);
//            long alignedScratchAddress = (scratchAddress + alignment - 1) & ~(alignment - 1);
//            long offsetInBuffer = alignedScratchAddress - scratchAddress;
//
//            // Double-check alignment padding is sufficient
//            if (offsetInBuffer + sizeInfo.buildScratchSize() > scratchSize) {
//                throw new RuntimeException("Scratch buffer too small after alignment! Required: " +
//                        (offsetInBuffer + sizeInfo.buildScratchSize()) + ", available: " + scratchSize);
//            }
//
//            // Create BLAS
//            var blasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
//                    .buffer(blasBuffer.first)
//                    .size(blasSize)
//                    .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL);
//            var pBlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
//            var result = deviceCommands.createAccelerationStructureKHR(device, blasCreateInfo, null, pBlas);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create BLAS: " + VkResult.explain(result));
//            }
//            blas = pBlas.read();
//
//            // Submit BLAS build
//            var cmd1 = beginSingleTimeCommands();
//            buildInfo.scratchData().deviceAddress(alignedScratchAddress);
//            buildInfo.dstAccelerationStructure(blas);
//
//            // The critical fix: use the build range info we set up
//            deviceCommands.cmdBuildAccelerationStructuresKHR(cmd1, 1, buildInfo, ppBuildRangeInfo);
//
//            // After cmdBuildAccelerationStructuresKHR for BLAS:
//            var blasBarrier = VkMemoryBarrier.allocate(arena)
//                    .sType(VkStructureType.MEMORY_BARRIER)
//                    .srcAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_WRITE_KHR)
//                    .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
//
//            deviceCommands.cmdPipelineBarrier(cmd1,
//                    VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR,
//                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
//                    0, 1, blasBarrier, 0, null, 0, null);
//
//            // End the command buffer and wait for it to complete
//            endSingleTimeCommands(cmd1);
//
//            // ONLY NOW destroy the scratch buffer
//            vma.destroyBuffer(vmaAllocator, scratchBuffer1.first, scratchBuffer1.second);
//
//            long blasAddress = getAccelerationStructureDeviceAddress(blas);
//
//            // === TLAS (Top-Level Acceleration Structure) ===
//            final int INSTANCE_COUNT = 1;
//            final int INSTANCE_SIZE = 64;
//
//            // Validate instance count
//            if (INSTANCE_COUNT > maxPrimitives) {
//                throw new RuntimeException("Instance count exceeds device limit: " + INSTANCE_COUNT + " > " + maxPrimitives);
//            }
//
//            var instanceData = arena.allocate(INSTANCE_SIZE);
//            // Transform (transform matrix)
//            float[] transform = {
//                    1.0f, 0.0f, 0.0f,  // Column 0
//                    0.0f, 1.0f, 0.0f,  // Column 1
//                    0.0f, 0.0f, 1.0f,  // Column 2
//                    0.0f, 0.0f, 0.0f   // Column 3 (translation)
//            };
//            for (int i = 0; i < 12; i++) {
//                instanceData.setAtIndex(ValueLayout.JAVA_FLOAT, i, transform[i]);
//            }
//// Pack instance data correctly (offset 48 bytes)
//            int instanceCustomIndex = 0;
//            int mask = 0xFF;
//            instanceData.setAtIndex(ValueLayout.JAVA_INT, 12, (mask << 24) | instanceCustomIndex); // offset 48
//
//// Pack SBT offset and flags (offset 52 bytes)
//            int sbtRecordOffset = 0;
//            int flags = VkGeometryInstanceFlagsKHR.TRIANGLE_FACING_CULL_DISABLE;
//            instanceData.setAtIndex(ValueLayout.JAVA_INT, 13, (flags << 24) | sbtRecordOffset); // offset 52
//
//// BLAS device address (offset 56 bytes)
//            instanceData.setAtIndex(ValueLayout.JAVA_LONG, 7, blasAddress); // offset 56
//
//            // Create instance buffer
//            var instBuffer = createBuffer(
//                    INSTANCE_SIZE,
//                    VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
//                    VmaAllocationCreateFlags.HOST_ACCESS_RANDOM,
//                    VkMemoryPropertyFlags.HOST_COHERENT,
//                    null
//            );
//            var ppData = PointerPtr.allocate(arena);
//            vma.mapMemory(vmaAllocator, instBuffer.second, ppData);
//            ppData.read().reinterpret(INSTANCE_SIZE).copyFrom(instanceData);
//            vma.unmapMemory(vmaAllocator, instBuffer.second);
//            long instAddress = getBufferDeviceAddress(instBuffer.first);
//
//            var instancesData = VkAccelerationStructureGeometryInstancesDataKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR);
//            instancesData.data().deviceAddress(instAddress);
//
//            var tlasGeometry = VkAccelerationStructureGeometryKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
//                    .geometryType(VkGeometryTypeKHR.INSTANCES);
//            tlasGeometry.geometry().instances(instancesData);
//
//            var tlasBuildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
//                    .type(VkAccelerationStructureTypeKHR.TOP_LEVEL)
//                    .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE)
//                    .mode(VkBuildAccelerationStructureModeKHR.BUILD)
//                    .geometryCount(1)
//                    .pGeometries(tlasGeometry);
//
//            // Set correct instance count (1 instance)
//            var tlasMaxPrim = IntPtr.allocate(arena, 1);
//            tlasMaxPrim.write(0, INSTANCE_COUNT);
//
//            // Create build range info for TLAS
//            var tlasBuildRangeInfo = VkAccelerationStructureBuildRangeInfoKHR.allocate(arena)
//                    .primitiveCount(INSTANCE_COUNT)
//                    .primitiveOffset(0)
//                    .firstVertex(0)
//                    .transformOffset(0);
//
//            var ppTlasBuildRangeInfo = PointerPtr.allocate(arena);
//            ppTlasBuildRangeInfo.write(tlasBuildRangeInfo);
//
//            var tlasSizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
//            deviceCommands.getAccelerationStructureBuildSizesKHR(
//                    device, VkAccelerationStructureBuildTypeKHR.DEVICE, tlasBuildInfo, tlasMaxPrim, tlasSizeInfo);
//
//            // Calculate proper TLAS buffer size with alignment padding
//            long tlasSize = tlasSizeInfo.accelerationStructureSize();
//            long tlasScratchSize = tlasSizeInfo.buildScratchSize();
//
//            // Use consistent padding strategy for TLAS as well
//            tlasSize = (tlasSize + MIN_PADDING - 1) & ~(MIN_PADDING - 1);
//            tlasScratchSize = (tlasScratchSize + alignment - 1) & ~(alignment - 1);
//            tlasScratchSize += 1024; // Extra padding for safety
//
//            System.out.println("Required TLAS size: " + tlasSize + " bytes (with padding)");
//            System.out.println("Required TLAS scratch size: " + tlasScratchSize + " bytes (with padding)");
//
//            var tlasBuffer = createAccelerationStructureBuffer((int) tlasSize);
//            var tlasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
//                    .buffer(tlasBuffer.first)
//                    .size(tlasSize)
//                    .type(VkAccelerationStructureTypeKHR.TOP_LEVEL);
//
//            var pTlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
//            result = deviceCommands.createAccelerationStructureKHR(device, tlasCreateInfo, null, pTlas);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create TLAS: " + VkResult.explain(result));
//            }
//            tlas = pTlas.read();
//
//            // After creating TLAS in createAccelerationStructures()
//            System.out.println("BLAS device address: " + blasAddress);
//            System.out.println("TLAS device address: " + getAccelerationStructureDeviceAddress(tlas));
//            System.out.println("TLAS created successfully");
//
//            // Add validation query
//            var asInfo = VkAccelerationStructureDeviceAddressInfoKHR.allocate(arena);
//            asInfo.accelerationStructure(tlas);
//            long tlasAddress = deviceCommands.getAccelerationStructureDeviceAddressKHR(device, asInfo);
//            System.out.println("Validated TLAS device address: " + tlasAddress);
//
//            // Submit TLAS build
//            var cmd2 = beginSingleTimeCommands();
//            var scratchBuffer2 = createScratchBuffer((int) tlasScratchSize);
//            long scratchAddressTlas = getBufferDeviceAddress(scratchBuffer2.first);
//
//            // Align scratch address for TLAS
//            long alignedScratchAddressTlas = (scratchAddressTlas + alignment - 1) & ~(alignment - 1);
//            long offsetInBufferTlas = alignedScratchAddressTlas - scratchAddressTlas;
//
//            // Validate scratch buffer size after alignment
//            if (offsetInBufferTlas + tlasSizeInfo.buildScratchSize() > tlasScratchSize) {
//                throw new RuntimeException("TLAS scratch buffer too small after alignment! Required: " +
//                        (offsetInBufferTlas + tlasSizeInfo.buildScratchSize()) + ", available: " + tlasScratchSize);
//            }
//
//            tlasBuildInfo.scratchData().deviceAddress(alignedScratchAddressTlas);
//            tlasBuildInfo.dstAccelerationStructure(tlas);
//
//            // Use the build range info for TLAS
//            deviceCommands.cmdBuildAccelerationStructuresKHR(cmd2, 1, tlasBuildInfo, ppTlasBuildRangeInfo);
//
//            // After cmdBuildAccelerationStructuresKHR for TLAS:
//            var tlasBarrier = VkMemoryBarrier.allocate(arena)
//                    .sType(VkStructureType.MEMORY_BARRIER)
//                    .srcAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_WRITE_KHR)
//                    .dstAccessMask(VkAccessFlags.ACCELERATION_STRUCTURE_READ_KHR);
//
//            deviceCommands.cmdPipelineBarrier(cmd2,
//                    VkPipelineStageFlags.ACCELERATION_STRUCTURE_BUILD_KHR,
//                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
//                    0, 1, tlasBarrier, 0, null, 0, null);
//
//            // End the command buffer and wait for it to complete
//            endSingleTimeCommands(cmd2);
//
//            // ONLY NOW destroy the TLAS scratch buffer
//            vma.destroyBuffer(vmaAllocator, scratchBuffer2.first, scratchBuffer2.second);
//
//            // Cleanup instance buffer (no longer needed after TLAS build)
//            vma.destroyBuffer(vmaAllocator, instBuffer.first, instBuffer.second);
//        }
//    }
//
//    private Pair<VkBuffer, VmaAllocation> createScratchBuffer(int size) {
//        return createBuffer(
//                size,
//                VkBufferUsageFlags.STORAGE_BUFFER | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
//                0,
//                0,
//                null
//        );
//    }
//
//    private Pair<VkBuffer, VmaAllocation> createBuffer(
//            int size,
//            @Bitmask(VkBufferUsageFlags.class) int usage,
//            @Bitmask(VmaAllocationCreateFlags.class) int vmaFlags,
//            @Bitmask(VkMemoryPropertyFlags.class) int memFlags,
//            @Nullable VmaAllocationInfo allocInfo
//    ) {
//        try (var arena = Arena.ofConfined()) {
//            var info = VkBufferCreateInfo.allocate(arena).size(size).usage(usage).sharingMode(VkSharingMode.EXCLUSIVE);
//            var allocCreateInfo = VmaAllocationCreateInfo.allocate(arena)
//                    .usage(VmaMemoryUsage.AUTO)
//                    .flags(vmaFlags)
//                    .requiredFlags(memFlags);
//            var pBuffer = VkBuffer.Ptr.allocate(arena);
//            var pAlloc = VmaAllocation.Ptr.allocate(arena);
//            var result = vma.createBuffer(vmaAllocator, info, allocCreateInfo, pBuffer, pAlloc, allocInfo);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create buffer: " + VkResult.explain(result));
//            }
//            return new Pair<>(pBuffer.read(), pAlloc.read());
//        }
//    }
//
//    private Pair<VkBuffer, VmaAllocation> createAccelerationStructureBuffer(int size) {
//        return createBuffer(
//                size,
//                VkBufferUsageFlags.ACCELERATION_STRUCTURE_STORAGE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
//                0,
//                0,
//                null
//        );
//    }
//
//    private long getAccelerationStructureDeviceAddress(VkAccelerationStructureKHR as) {
//        try (var arena = Arena.ofConfined()) {
//            var info = VkAccelerationStructureDeviceAddressInfoKHR.allocate(arena);
//            info.accelerationStructure(as);
//            return deviceCommands.getAccelerationStructureDeviceAddressKHR(device, info);
//        }
//    }
//
//    private void createOutputImage() {
//        try (var arena = Arena.ofConfined()) {
//            var info = VkImageCreateInfo.allocate(arena)
//                    .imageType(VkImageType._2D)
//                    .format(VkFormat.R8G8B8A8_UNORM)
//                    .extent(e -> e.width(swapChainExtent.width()).height(swapChainExtent.height()).depth(1))
//                    .mipLevels(1)
//                    .arrayLayers(1)
//                    .samples(VkSampleCountFlags._1)
//                    .tiling(VkImageTiling.OPTIMAL)
//                    .usage(VkImageUsageFlags.STORAGE | VkImageUsageFlags.TRANSFER_SRC);
//            var allocInfo = VmaAllocationCreateInfo.allocate(arena).usage(VmaMemoryUsage.GPU_ONLY);
//            var pImage = VkImage.Ptr.allocate(arena);
//            var pAlloc = VmaAllocation.Ptr.allocate(arena);
//            var result = vma.createImage(vmaAllocator, info, allocInfo, pImage, pAlloc, null);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create output image: " + VkResult.explain(result));
//            }
//            outputImage = pImage.read();
//            outputImageAllocation = pAlloc.read();
//            outputImageView = createImageView(outputImage, VkFormat.R8G8B8A8_UNORM, VkImageAspectFlags.COLOR, 1);
//        }
//    }
//
//    private void createDescriptorSetLayout() {
//        try (var arena = Arena.ofConfined()) {
//            var bindings = VkDescriptorSetLayoutBinding.allocate(arena, 2)
//                    .at(0, b -> b
//                            .binding(0)
//                            .descriptorType(VkDescriptorType.STORAGE_IMAGE)
//                            .descriptorCount(1)
//                            .stageFlags(VkShaderStageFlags.RAYGEN_KHR))
//                    .at(1, b -> b
//                            .binding(1)
//                            .descriptorType(VkDescriptorType.ACCELERATION_STRUCTURE_KHR)
//                            .descriptorCount(1)
//                            .stageFlags(VkShaderStageFlags.RAYGEN_KHR | VkShaderStageFlags.CLOSEST_HIT_KHR));
//            var layoutInfo = VkDescriptorSetLayoutCreateInfo.allocate(arena)
//                    .bindingCount(2)
//                    .pBindings(bindings);
//            var pLayout = VkDescriptorSetLayout.Ptr.allocate(arena);
//            var result = deviceCommands.createDescriptorSetLayout(device, layoutInfo, null, pLayout);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create descriptor set layout: " + VkResult.explain(result));
//            }
//            descriptorSetLayout = pLayout.read();
//        }
//    }
//
//    private void createDescriptorPool() {
//        try (var arena = Arena.ofConfined()) {
//            var sizes = VkDescriptorPoolSize.allocate(arena, 2)
//                    .at(0, s -> s.type(VkDescriptorType.STORAGE_IMAGE).descriptorCount(1))
//                    .at(1, s -> s.type(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(1));
//            var poolInfo = VkDescriptorPoolCreateInfo.allocate(arena)
//                    .poolSizeCount(2)
//                    .pPoolSizes(sizes)
//                    .maxSets(1);
//            var pPool = VkDescriptorPool.Ptr.allocate(arena);
//            var result = deviceCommands.createDescriptorPool(device, poolInfo, null, pPool);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create descriptor pool: " + VkResult.explain(result));
//            }
//            descriptorPool = pPool.read();
//        }
//    }
//
//    private void createDescriptorSet() {
//        try (var arena = Arena.ofConfined()) {
//            var allocInfo = VkDescriptorSetAllocateInfo.allocate(arena)
//                    .descriptorPool(descriptorPool)
//                    .pSetLayouts(VkDescriptorSetLayout.Ptr.allocateV(arena, descriptorSetLayout))
//                    .descriptorSetCount(1);
//            var pSet = VkDescriptorSet.Ptr.allocate(arena);
//            var result = deviceCommands.allocateDescriptorSets(device, allocInfo, pSet);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to allocate descriptor set: " + VkResult.explain(result));
//            }
//            descriptorSet = pSet.read();
//            var imageInfo = VkDescriptorImageInfo.allocate(arena)
//                    .imageView(outputImageView)
//                    .imageLayout(VkImageLayout.GENERAL);
//            var asInfo = VkWriteDescriptorSetAccelerationStructureKHR.allocate(arena)
//                    .sType(VkStructureType.WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR)
//                    .accelerationStructureCount(1)
//                    .pAccelerationStructures(VkAccelerationStructureKHR.Ptr.allocateV(arena, tlas));
//            var writes = VkWriteDescriptorSet.allocate(arena, 2)
//                    .at(0, w -> w
//                            .dstSet(descriptorSet)
//                            .dstBinding(0)
//                            .descriptorType(VkDescriptorType.STORAGE_IMAGE)
//                            .descriptorCount(1)
//                            .pImageInfo(imageInfo))
//                    .at(1, w -> w
//                            .dstSet(descriptorSet)
//                            .dstBinding(1)
//                            .descriptorType(VkDescriptorType.ACCELERATION_STRUCTURE_KHR)
//                            .descriptorCount(1)
//                            .pNext(asInfo.segment()));
//            deviceCommands.updateDescriptorSets(device, 2, writes, 0, null);
//        }
//    }
//
//    private void createRayTracingPipeline() {
//        try (var arena = Arena.ofConfined()) {
//            // Load precompiled SPIR-V
//            var rgenCode = loadSpirvShader(arena, "/shader/raytracing/ray.rgen.spv");
//            var rchitCode = loadSpirvShader(arena, "/shader/raytracing/ray.rchit.spv");
//            var rmissCode = loadSpirvShader(arena, "/shader/raytracing/ray.rmiss.spv");
//            var rgenModule = createShaderModule(rgenCode);
//            var rchitModule = createShaderModule(rchitCode);
//            var rmissModule = createShaderModule(rmissCode);
//            var stages = VkPipelineShaderStageCreateInfo.allocate(arena, 3)
//                    .at(0, s -> s.stage(VkShaderStageFlags.RAYGEN_KHR).module(rgenModule).pName(BytePtr.allocateString(arena, "main")))
//                    .at(1, s -> s.stage(VkShaderStageFlags.CLOSEST_HIT_KHR).module(rchitModule).pName(BytePtr.allocateString(arena, "main")))
//                    .at(2, s -> s.stage(VkShaderStageFlags.MISS_KHR).module(rmissModule).pName(BytePtr.allocateString(arena, "main")));
//            var groups = VkRayTracingShaderGroupCreateInfoKHR.allocate(arena, 3)
//                    .at(0, g -> g.type(VkRayTracingShaderGroupTypeKHR.GENERAL).generalShader(0)
//                            .closestHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .anyHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .intersectionShader(VkConstants.SHADER_UNUSED_KHR))
//                    .at(1, g -> g.type(VkRayTracingShaderGroupTypeKHR.GENERAL).generalShader(2)
//                            .closestHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .anyHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .intersectionShader(VkConstants.SHADER_UNUSED_KHR))
//                    .at(2, g -> g.type(VkRayTracingShaderGroupTypeKHR.TRIANGLES_HIT_GROUP).closestHitShader(1)
//                            .generalShader(VkConstants.SHADER_UNUSED_KHR)
//                            .anyHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .intersectionShader(VkConstants.SHADER_UNUSED_KHR));
//            var rtProps = VkPhysicalDeviceRayTracingPipelinePropertiesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR);
//            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(rtProps.segment());
//            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);
//            handleSize = rtProps.shaderGroupHandleSize();
//            handleAlignment = rtProps.shaderGroupHandleAlignment();
//            // Corrected stride calculation for AMD compatibility
//            alignedHandleSize = (handleSize + handleAlignment - 1) & ~(handleAlignment - 1);
//            int shaderGroupBaseAlignment = rtProps.shaderGroupBaseAlignment();
//            sbtRecordStride = (alignedHandleSize + shaderGroupBaseAlignment - 1) & ~(shaderGroupBaseAlignment - 1);
//            var pushConstantRange = VkPushConstantRange.allocate(arena)
//                    .stageFlags(VkShaderStageFlags.RAYGEN_KHR)
//                    .offset(0)
//                    .size(128);
//            var layoutInfo = VkPipelineLayoutCreateInfo.allocate(arena)
//                    .setLayoutCount(1)
//                    .pSetLayouts(VkDescriptorSetLayout.Ptr.allocateV(arena, descriptorSetLayout))
//                    .pushConstantRangeCount(1)
//                    .pPushConstantRanges(pushConstantRange);
//            var pLayout = VkPipelineLayout.Ptr.allocate(arena);
//            var result = deviceCommands.createPipelineLayout(device, layoutInfo, null, pLayout);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create pipeline layout: " + VkResult.explain(result));
//            }
//            pipelineLayout = pLayout.read();
//            var pipelineInfo = VkRayTracingPipelineCreateInfoKHR.allocate(arena)
//                    .sType(VkStructureType.RAY_TRACING_PIPELINE_CREATE_INFO_KHR)
//                    .pStages(stages)
//                    .stageCount(3)
//                    .pGroups(groups)
//                    .groupCount(3)
//                    .maxPipelineRayRecursionDepth(1)
//                    .layout(pipelineLayout);
//            var pPipeline = VkPipeline.Ptr.allocate(arena);
//            result = deviceCommands.createRayTracingPipelinesKHR(device, null, null, 1, pipelineInfo, null, pPipeline);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create ray tracing pipeline: " + VkResult.explain(result));
//            }
//            rayTracingPipeline = pPipeline.read();
//            deviceCommands.destroyShaderModule(device, rgenModule, null);
//            deviceCommands.destroyShaderModule(device, rchitModule, null);
//            deviceCommands.destroyShaderModule(device, rmissModule, null);
//        }
//    }
//
//    private IntPtr loadSpirvShader(Arena arena, String filename) {
//        try (var stream = Application.class.getResourceAsStream(filename)) {
//            if (stream == null) throw new RuntimeException("Shader not found: " + filename);
//            byte[] bytes = stream.readAllBytes();
//            if (bytes.length % 4 != 0) {
//                throw new RuntimeException("Invalid SPIR-V: size not multiple of 4");
//            }
//            int[] words = new int[bytes.length / 4];
//            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(words);
//            var code = IntPtr.allocate(arena, words.length);
//            code.segment().copyFrom(MemorySegment.ofArray(words));
//            return code;
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private VkShaderModule createShaderModule(IntPtr code) {
//        try (var arena = Arena.ofConfined()) {
//            var info = VkShaderModuleCreateInfo.allocate(arena)
//                    .codeSize(code.size() * Integer.BYTES)
//                    .pCode(code);
//            var pModule = VkShaderModule.Ptr.allocate(arena);
//            var result = deviceCommands.createShaderModule(device, info, null, pModule);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create shader module: " + VkResult.explain(result));
//            }
//            return pModule.read();
//        }
//    }
//
//    private void createShaderBindingTable() {
//        try (var arena = Arena.ofConfined()) {
//            var rtProps = VkPhysicalDeviceRayTracingPipelinePropertiesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR);
//            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(rtProps.segment());
//            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);
//            int handleSize = rtProps.shaderGroupHandleSize();
//            int handleAlignment = rtProps.shaderGroupHandleAlignment();
//            // Corrected stride calculation for AMD compatibility
//            int alignedHandleSize = (handleSize + handleAlignment - 1) & ~(handleAlignment - 1);
//            int shaderGroupBaseAlignment = rtProps.shaderGroupBaseAlignment();
//            int sbtRecordStride = (alignedHandleSize + shaderGroupBaseAlignment - 1) & ~(shaderGroupBaseAlignment - 1);
//            int totalSbtSize = 3 * sbtRecordStride;
//
//            // Allocate buffer with enough space for alignment padding
//            long paddedSize = totalSbtSize + shaderGroupBaseAlignment - 1;
//            var buffer = createBuffer(
//                    (int) paddedSize,
//                    VkBufferUsageFlags.SHADER_BINDING_TABLE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
//                    VmaAllocationCreateFlags.HOST_ACCESS_RANDOM,
//                    VkMemoryPropertyFlags.HOST_COHERENT,
//                    null
//            );
//            sbtBuffer = buffer.first;
//            sbtAllocation = buffer.second;
//            long rawAddress = getBufferDeviceAddress(buffer.first);
//            long alignedAddress = (rawAddress + shaderGroupBaseAlignment - 1) & ~(shaderGroupBaseAlignment - 1);
//            long offsetInBuffer = alignedAddress - rawAddress;
//
//            // In createShaderBindingTable(), after calculating strides:
//            if (sbtRecordStride < handleSize) {
//                System.err.println("WARNING: SBT record stride (" + sbtRecordStride +
//                        ") smaller than handle size (" + handleSize + ")");
//                sbtRecordStride = (handleSize + shaderGroupBaseAlignment - 1) & ~(shaderGroupBaseAlignment - 1);
//            }
//
//            // Ensure we have enough space after alignment
//            if (offsetInBuffer + totalSbtSize > paddedSize) {
//                throw new RuntimeException("SBT buffer too small after alignment!");
//            }
//
//            var handles = BytePtr.allocate(arena, totalSbtSize);
//            var result = deviceCommands.getRayTracingShaderGroupHandlesKHR(
//                    device, rayTracingPipeline, 0, 3, totalSbtSize, handles.segment()
//            );
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to get shader group handles: " + VkResult.explain(result));
//            }
//
//            var ppData = PointerPtr.allocate(arena);
//            vma.mapMemory(vmaAllocator, sbtAllocation, ppData);
//            ppData.read().reinterpret(paddedSize).asSlice(offsetInBuffer, totalSbtSize).copyFrom(handles.segment());
//            vma.unmapMemory(vmaAllocator, sbtAllocation);
//
//            this.alignedSbtAddress = alignedAddress;
//            this.alignedHandleSize = alignedHandleSize;
//            this.sbtRecordStride = sbtRecordStride;
//
//            System.out.println("Ray tracing properties:");
//            System.out.println("  Handle size: " + handleSize);
//            System.out.println("  Handle alignment: " + handleAlignment);
//            System.out.println("  Aligned handle size: " + alignedHandleSize);
//            System.out.println("  SBT record stride: " + sbtRecordStride);
//            System.out.println("  Total SBT size: " + totalSbtSize);
//            System.out.println("  Aligned SBT address: " + alignedSbtAddress);
//        }
//    }
//
//    private void createCommandPool() {
//        try (var arena = Arena.ofConfined()) {
//            var indices = findQueueFamilies(physicalDevice);
//            var poolInfo = VkCommandPoolCreateInfo.allocate(arena)
//                    .queueFamilyIndex(indices.graphicsFamily())
//                    .flags(VkCommandPoolCreateFlags.RESET_COMMAND_BUFFER);
//            var pPool = VkCommandPool.Ptr.allocate(arena);
//            var result = deviceCommands.createCommandPool(device, poolInfo, null, pPool);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create command pool: " + VkResult.explain(result));
//            }
//            commandPool = pPool.read();
//
//            // Create frame resources
//            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
//                // Semaphores
//                var semInfo = VkSemaphoreCreateInfo.allocate(arena);
//                var p1 = VkSemaphore.Ptr.allocate(arena);
//                deviceCommands.createSemaphore(device, semInfo, null, p1);
//                imageAvailableSemaphores[i] = p1.read();
//                var p2 = VkSemaphore.Ptr.allocate(arena);
//                deviceCommands.createSemaphore(device, semInfo, null, p2);
//                renderFinishedSemaphores[i] = p2.read();
//
//                // Fence (created signaled so first frame doesn't block)
//                var fenceInfo = VkFenceCreateInfo.allocate(arena)
//                        .flags(VkFenceCreateFlags.SIGNALED);
//                var pFence = VkFence.Ptr.allocate(arena);
//                deviceCommands.createFence(device, fenceInfo, null, pFence);
//                inFlightFences[i] = pFence.read();
//            }
//
//            // Allocate command buffers
//            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
//                    .commandPool(commandPool)
//                    .level(VkCommandBufferLevel.PRIMARY)
//                    .commandBufferCount(MAX_FRAMES_IN_FLIGHT);
//            var pBufs = VkCommandBuffer.Ptr.allocate(arena, MAX_FRAMES_IN_FLIGHT);
//            deviceCommands.allocateCommandBuffers(device, allocInfo, pBufs);
//            for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
//                commandBuffers[i] = pBufs.read(i);
//            }
//        }
//    }
//
//    private void createCommandBuffers() {
//        try (var arena = Arena.ofConfined()) {
//            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
//                    .commandPool(commandPool)
//                    .level(VkCommandBufferLevel.PRIMARY)
//                    .commandBufferCount(1);
//            var pBuf = VkCommandBuffer.Ptr.allocate(arena);
//            deviceCommands.allocateCommandBuffers(device, allocInfo, pBuf);
//            commandBuffer = pBuf.read();
//        }
//    }
//
//    private void drawFrame() {
//        try (var arena = Arena.ofConfined()) {
//            // Wait for this frame's fence
//            deviceCommands.waitForFences(
//                    device, 1,
//                    VkFence.Ptr.allocateV(arena, inFlightFences[currentFrame]),
//                    1, UINT64_MAX
//            );
//            // Acquire image
//            var pImageIndex = IntPtr.allocate(arena);
//            var result = deviceCommands.acquireNextImageKHR(
//                    device, swapChain, UINT64_MAX,
//                    imageAvailableSemaphores[currentFrame],
//                    null,
//                    pImageIndex
//            );
//            if (result != VkResult.SUCCESS && result != VkResult.SUBOPTIMAL_KHR) {
//                throw new RuntimeException("Failed to acquire swap chain image");
//            }
//            int imageIndex = pImageIndex.read();
//            // Reset fence
//            deviceCommands.resetFences(device, 1, VkFence.Ptr.allocateV(arena, inFlightFences[currentFrame]));
//            // Record command buffer
//            var beginInfo = VkCommandBufferBeginInfo.allocate(arena);
//            deviceCommands.beginCommandBuffer(commandBuffers[currentFrame], beginInfo);
//            recordCommandBuffer(commandBuffers[currentFrame], imageIndex);
//            deviceCommands.endCommandBuffer(commandBuffers[currentFrame]);
//            // Submit
//            var submitInfo = VkSubmitInfo.allocate(arena)
//                    .waitSemaphoreCount(1)
//                    .pWaitSemaphores(VkSemaphore.Ptr.allocateV(arena, imageAvailableSemaphores[currentFrame]))
//                    .pWaitDstStageMask(IntPtr.allocateV(arena, VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT))
//                    .commandBufferCount(1)
//                    .pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, commandBuffers[currentFrame]))
//                    .signalSemaphoreCount(1)
//                    .pSignalSemaphores(VkSemaphore.Ptr.allocateV(arena, renderFinishedSemaphores[currentFrame]));
//            deviceCommands.queueSubmit(
//                    graphicsQueue, 1, submitInfo,
//                    inFlightFences[currentFrame] // signal fence on completion
//            );
//            // Present
//            var presentInfo = VkPresentInfoKHR.allocate(arena)
//                    .waitSemaphoreCount(1)
//                    .pWaitSemaphores(VkSemaphore.Ptr.allocateV(arena, renderFinishedSemaphores[currentFrame]))
//                    .swapchainCount(1)
//                    .pSwapchains(VkSwapchainKHR.Ptr.allocateV(arena, swapChain))
//                    .pImageIndices(IntPtr.allocateV(arena, imageIndex));
//            deviceCommands.queuePresentKHR(presentQueue, presentInfo);
//            // Advance frame
//            currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
//        }
//    }
//
//    private void recordCommandBuffer(VkCommandBuffer cmd, int imageIndex) {
//        try (var arena = Arena.ofConfined()) {
//            var barrier = VkImageMemoryBarrier.allocate(arena)
//                    .oldLayout(VkImageLayout.UNDEFINED)
//                    .newLayout(VkImageLayout.GENERAL)
//                    .srcAccessMask(0)
//                    .dstAccessMask(VkAccessFlags.SHADER_WRITE)
//                    .image(outputImage)
//                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
//            deviceCommands.cmdPipelineBarrier(cmd,
//                    VkPipelineStageFlags.TOP_OF_PIPE,
//                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
//                    0, 0, null, 0, null, 1, barrier);
//            deviceCommands.cmdBindPipeline(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, rayTracingPipeline);
//            var pDescriptorSet = VkDescriptorSet.Ptr.allocate(arena);
//            pDescriptorSet.write(descriptorSet);
//            var pDynamicOffsets = IntPtr.allocate(arena);
//            deviceCommands.cmdBindDescriptorSets(cmd, VkPipelineBindPoint.RAY_TRACING_KHR,
//                    pipelineLayout, 0, 1, pDescriptorSet, 0, pDynamicOffsets);
//            // Push constants - CORRECTED to use perspective projection
//            float[] pushConstants = new float[32];
//            // Calculate perspective projection matrix (correct for ray tracing)
//            Matrix4f projection = new Matrix4f().setPerspective(
//                    (float) Math.toRadians(cameraFOV),
//                    (float) swapChainExtent.width() / (float) swapChainExtent.height(),
//                    0.1f,
//                    1000.0f
//            );
//            // Invert the projection matrix for ray tracing
//            Matrix4f invProj = new Matrix4f(projection).invert();
//
//            // Camera view matrix (identity for now)
//            // CORRECT - this moves the world relative to the camera
//            Matrix4f view = new Matrix4f().lookAt(
//                    cameraPosition,           // Camera position (0,0,2)
//                    new Vector3f(0, 0, 0),    // Look at origin
//                    new Vector3f(0, 1, 0)     // Up vector
//            );
//            Matrix4f invView = new Matrix4f(view).invert();
//
//            invProj.get(pushConstants, 0);
//            invView.get(pushConstants, 16);
//            var data = arena.allocate(ValueLayout.JAVA_FLOAT, 32);
//            data.copyFrom(MemorySegment.ofArray(pushConstants));
//            deviceCommands.cmdPushConstants(cmd, pipelineLayout, VkShaderStageFlags.RAYGEN_KHR, 0, 128, data);
//
//            System.out.println("Must be EXACTLY one record size(sbtRecordStride): " + sbtRecordStride);
//            var raygen = VkStridedDeviceAddressRegionKHR.allocate(arena)
//                    .deviceAddress(alignedSbtAddress)
//                    .stride(sbtRecordStride)
//                    .size(sbtRecordStride);
//
//            System.out.println("Must be EXACTLY one record size(sbtRecordStride): " + sbtRecordStride);
//            var miss = VkStridedDeviceAddressRegionKHR.allocate(arena)
//                    .deviceAddress(alignedSbtAddress + sbtRecordStride)
//                    .stride(sbtRecordStride)
//                    .size(sbtRecordStride);
//
//            System.out.println("Must be EXACTLY one record size(sbtRecordStride): " + sbtRecordStride);
//            var hit = VkStridedDeviceAddressRegionKHR.allocate(arena)
//                    .deviceAddress(alignedSbtAddress + 2 * sbtRecordStride)
//                    .stride(sbtRecordStride)
//                    .size(sbtRecordStride);
//
//            var callable = VkStridedDeviceAddressRegionKHR.allocate(arena)
//                    .deviceAddress(0)
//                    .stride(0)
//                    .size(0);
//            deviceCommands.cmdTraceRaysKHR(cmd, raygen, miss, hit, callable,
//                    swapChainExtent.width(), swapChainExtent.height(), 1);
//            barrier.oldLayout(VkImageLayout.GENERAL)
//                    .newLayout(VkImageLayout.TRANSFER_SRC_OPTIMAL)
//                    .srcAccessMask(VkAccessFlags.SHADER_WRITE)
//                    .dstAccessMask(VkAccessFlags.TRANSFER_READ);
//            deviceCommands.cmdPipelineBarrier(cmd,
//                    VkPipelineStageFlags.ALL_COMMANDS,
//                    VkPipelineStageFlags.TRANSFER,
//                    0, 0, null, 0, null, 1, barrier);
//            barrier.oldLayout(VkImageLayout.UNDEFINED)
//                    .newLayout(VkImageLayout.TRANSFER_DST_OPTIMAL)
//                    .srcAccessMask(0)
//                    .dstAccessMask(VkAccessFlags.TRANSFER_WRITE)
//                    .image(swapChainImages.read(imageIndex))
//                    .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
//            deviceCommands.cmdPipelineBarrier(cmd,
//                    VkPipelineStageFlags.TOP_OF_PIPE,
//                    VkPipelineStageFlags.TRANSFER,
//                    0, 0, null, 0, null, 1, barrier);
//            var copy = VkImageCopy.allocate(arena)
//                    .srcSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
//                    .dstSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
//                    .extent(e -> e.width(swapChainExtent.width()).height(swapChainExtent.height()).depth(1));
//            deviceCommands.cmdCopyImage(cmd,
//                    outputImage, VkImageLayout.TRANSFER_SRC_OPTIMAL,
//                    swapChainImages.read(imageIndex), VkImageLayout.TRANSFER_DST_OPTIMAL,
//                    1, copy);
//            barrier.oldLayout(VkImageLayout.TRANSFER_DST_OPTIMAL)
//                    .newLayout(VkImageLayout.PRESENT_SRC_KHR)
//                    .srcAccessMask(VkAccessFlags.TRANSFER_WRITE)
//                    .dstAccessMask(0);
//            deviceCommands.cmdPipelineBarrier(cmd,
//                    VkPipelineStageFlags.TRANSFER,
//                    VkPipelineStageFlags.BOTTOM_OF_PIPE,
//                    0, 0, null, 0, null, 1, barrier);
//
//            // Add AMD-specific memory barrier to ensure proper synchronization
//            var memoryBarrier = VkMemoryBarrier.allocate(arena)
//                    .sType(VkStructureType.MEMORY_BARRIER)
//                    .srcAccessMask(VkAccessFlags.SHADER_WRITE)
//                    .dstAccessMask(VkAccessFlags.SHADER_READ);
//            deviceCommands.cmdPipelineBarrier(
//                    cmd,
//                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
//                    VkPipelineStageFlags.RAY_TRACING_SHADER_KHR,
//                    0, 1, memoryBarrier, 0, null, 0, null);
//        }
//    }
//
//    private VkCommandBuffer beginSingleTimeCommands() {
//        try (var arena = Arena.ofConfined()) {
//            // Create transient command pool
//            var indices = findQueueFamilies(physicalDevice);
//            var poolInfo = VkCommandPoolCreateInfo.allocate(arena)
//                    .queueFamilyIndex(indices.graphicsFamily())
//                    .flags(VkCommandPoolCreateFlags.TRANSIENT);
//            var pPool = VkCommandPool.Ptr.allocate(arena);
//            deviceCommands.createCommandPool(device, poolInfo, null, pPool);
//            VkCommandPool commandPool = pPool.read();
//            // Allocate command buffer
//            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
//                    .commandPool(commandPool)
//                    .level(VkCommandBufferLevel.PRIMARY)
//                    .commandBufferCount(1);
//            var pBuf = VkCommandBuffer.Ptr.allocate(arena);
//            deviceCommands.allocateCommandBuffers(device, allocInfo, pBuf);
//            VkCommandBuffer cmd = pBuf.read();
//            // Begin recording
//            var beginInfo = VkCommandBufferBeginInfo.allocate(arena)
//                    .flags(VkCommandBufferUsageFlags.ONE_TIME_SUBMIT);
//            deviceCommands.beginCommandBuffer(cmd, beginInfo);
//            // Store pool for cleanup
//            this.transientCommandPool = commandPool;
//            return cmd;
//        }
//    }
//
//    private void endSingleTimeCommands(VkCommandBuffer cmd) {
//        deviceCommands.endCommandBuffer(cmd);
//        try (var arena = Arena.ofConfined()) {
//            // Submit
//            var submitInfo = VkSubmitInfo.allocate(arena)
//                    .commandBufferCount(1)
//                    .pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, cmd));
//            deviceCommands.queueSubmit(graphicsQueue, 1, submitInfo, null);
//            // Wait for completion
//            deviceCommands.queueWaitIdle(graphicsQueue);
//            // Clean up
//            deviceCommands.freeCommandBuffers(device, transientCommandPool, 1, VkCommandBuffer.Ptr.allocateV(arena, cmd));
//            deviceCommands.destroyCommandPool(device, transientCommandPool, null);
//            this.transientCommandPool = null;
//        }
//    }
//
//    private void cleanupSwapChain() {
//        for (var view : swapChainImageViews) {
//            deviceCommands.destroyImageView(device, view, null);
//        }
//        deviceCommands.destroySwapchainKHR(device, swapChain, null);
//    }
//
//    // --- Utility methods ---
//    private PointerPtr getRequiredExtensions(Arena arena) {
//        try (var localArena = Arena.ofConfined()) {
//            var pGLFWExtensionCount = IntPtr.allocate(localArena);
//            var glfwExtensions = glfw.getRequiredInstanceExtensions(pGLFWExtensionCount);
//            if (glfwExtensions == null) {
//                throw new RuntimeException("Failed to get GLFW required instance extensions");
//            }
//            var glfwExtensionCount = pGLFWExtensionCount.read();
//            glfwExtensions = glfwExtensions.reinterpret(glfwExtensionCount);
//            PointerPtr extensions;
//            if (!ENABLE_VALIDATION_LAYERS) {
//                extensions = PointerPtr.allocate(arena, glfwExtensionCount + 1);
//            }
//            else {
//                extensions = PointerPtr.allocate(arena, glfwExtensionCount + 2);
//            }
//            for (int i = 0; i < glfwExtensionCount; i++) {
//                extensions.write(i, glfwExtensions.read(i));
//            }
//            extensions.write(glfwExtensionCount, BytePtr.allocateString(arena, VkConstants.KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME));
//            if (ENABLE_VALIDATION_LAYERS) {
//                extensions.write(glfwExtensionCount + 1, BytePtr.allocateString(arena, VkConstants.EXT_DEBUG_UTILS_EXTENSION_NAME));
//            }
//            return extensions;
//        }
//    }
//
//    private boolean checkValidationLayerSupport() {
//        try (var arena = Arena.ofConfined()) {
//            var count = IntPtr.allocate(arena);
//            entryCommands.enumerateInstanceLayerProperties(count, null);
//            var layers = VkLayerProperties.allocate(arena, count.read());
//            entryCommands.enumerateInstanceLayerProperties(count, layers);
//            for (var layer : layers) {
//                if (VALIDATION_LAYER_NAME.equals(layer.layerName().readString())) return true;
//            }
//            return false;
//        }
//    }
//
//    static int debugCallback(int severity, int type, MemorySegment data, MemorySegment userData) {
//        var cb = new VkDebugUtilsMessengerCallbackDataEXT(data.reinterpret(VkDebugUtilsMessengerCallbackDataEXT.BYTES));
//        System.err.println("Validation: " + cb.pMessage().readString());
//        return VkConstants.FALSE;
//    }
//
//    private static void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT info) {
//        info.messageSeverity(VkDebugUtilsMessageSeverityFlagsEXT.WARNING | VkDebugUtilsMessageSeverityFlagsEXT.ERROR)
//                .messageType(VkDebugUtilsMessageTypeFlagsEXT.VALIDATION | VkDebugUtilsMessageTypeFlagsEXT.GENERAL)
//                .pfnUserCallback(Application::debugCallback);
//    }
//
//    private record Pair<T1, T2>(T1 first, T2 second) {}
//
//    // --- Static fields ---
//    private static final ISharedLibrary libGLFW = GLFWLoader.loadGLFWLibrary();
//    private static final GLFW glfw = GLFWLoader.loadGLFW(libGLFW);
//    private static final ISharedLibrary libShaderc = ILibraryLoader.platformLoader().loadLibrary("shaderc_shared");
//    private static final Shaderc shaderc = new Shaderc(libShaderc);
//    private static final ISharedLibrary libVulkan = VulkanLoader.loadVulkanLibrary();
//    private static final VkStaticCommands staticCommands = VulkanLoader.loadStaticCommands(libVulkan);
//    private static final ISharedLibrary libVMA = ILibraryLoader.platformLoader().loadLibrary("vma");
//    private static final VMA vma = new VMA(libVMA);
//
//    public static void main(String[] args) {
//        new Application().run();
//    }
//}