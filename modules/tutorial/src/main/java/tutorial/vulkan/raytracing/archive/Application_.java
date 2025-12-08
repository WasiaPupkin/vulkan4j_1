//package tutorial.vulkan.raytracing;
//
//import club.doki7.ffm.NativeLayout;
//import club.doki7.ffm.annotation.*;
//import club.doki7.ffm.library.ILibraryLoader;
//import club.doki7.ffm.library.ISharedLibrary;
//import club.doki7.ffm.ptr.*;
//import club.doki7.glfw.GLFW;
//import club.doki7.glfw.GLFWLoader;
//import club.doki7.glfw.handle.GLFWwindow;
//import club.doki7.shaderc.Shaderc;
//import club.doki7.shaderc.ShadercUtil;
//import club.doki7.shaderc.enumtype.ShadercEnvVersion;
//import club.doki7.shaderc.enumtype.ShadercShaderKind;
//import club.doki7.shaderc.enumtype.ShadercSpirvVersion;
//import club.doki7.shaderc.enumtype.ShadercTargetEnv;
//import club.doki7.shaderc.handle.ShadercCompileOptions;
//import club.doki7.shaderc.handle.ShadercCompiler;
//import club.doki7.vma.VMA;
//import club.doki7.vma.VMAJavaTraceUtil;
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
//import club.doki7.vulkan.bitmask.*;
//import club.doki7.vulkan.command.*;
//import club.doki7.vulkan.datatype.*;
//import club.doki7.vulkan.enumtype.*;
//import club.doki7.vulkan.handle.*;
//import org.jetbrains.annotations.Nullable;
//import org.joml.Matrix4f;
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
//
//    // Ray tracing properties
//    private int handleSize;
//    private int handleAlignment;
//    private int alignedHandleSize;
//    private long alignedSbtAddress;
//    private int sbtRecordStride;
//
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
//
//    // VMA
//    private VmaAllocator vmaAllocator;
//
//    // Swapchain
//    private VkSwapchainKHR swapChain;
//    private VkImage.Ptr swapChainImages;
//    private @EnumType(VkFormat.class) int swapChainImageFormat;
//    private VkExtent2D swapChainExtent;
//    private VkImageView.Ptr swapChainImageViews;
//
//    // Ray tracing resources
//    private VkBuffer vertexBuffer;
//    private VmaAllocation vertexBufferAllocation;
//    private VkAccelerationStructureKHR blas;  // ✅ store object
//    private VkAccelerationStructureKHR tlas;  // ✅ store object
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
//
//    // Shader compiler
//    private ShadercCompiler shadercCompiler;
//    private ShadercCompileOptions shadercCompileOptions;
//
//    private VkCommandPool transientCommandPool = null;
//    private VkFence imageAvailableFence;
//    private VkFence frameFence;
//    private VkSemaphore imageAvailableSemaphore;
//    private VkSemaphore renderFinishedSemaphore;
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
//        glfw.setFramebufferSizeCallback(window, (_, _, _) -> this.framebufferResized = true);
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
//
//        // ✅ Destroy AS using full objects
//        deviceCommands.destroyAccelerationStructureKHR(device, blas, null);
//        deviceCommands.destroyAccelerationStructureKHR(device, tlas, null);
//
//        deviceCommands.destroyImageView(device, outputImageView, null);
//        vma.destroyImage(vmaAllocator, outputImage, outputImageAllocation);
//        cleanupSwapChain();
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
//    // --- Instance, Device, Swapchain, etc. ---
//
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
//
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
//
//            // ✅ Vulkan 1.2 features — INCLUDE bufferDeviceAddress HERE
//            var vulkan12Features = VkPhysicalDeviceVulkan12Features.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_VULKAN_1_2_FEATURES)
//                    .descriptorIndexing(VkConstants.TRUE)
//                    .shaderSampledImageArrayNonUniformIndexing(VkConstants.TRUE)
//                    .bufferDeviceAddress(VkConstants.TRUE); // ← critical
//
//            var asFeatures = VkPhysicalDeviceAccelerationStructureFeaturesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR)
//                    .accelerationStructure(VkConstants.TRUE);
//            var rtFeatures = VkPhysicalDeviceRayTracingPipelineFeaturesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR)
//                    .rayTracingPipeline(VkConstants.TRUE);
//
//            // Chain: vulkan12 → as → rt
//            vulkan12Features.pNext(asFeatures.segment());
//            asFeatures.pNext(rtFeatures.segment());
//
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
//                    .pNext(vulkan12Features.segment()); // ← top of chain
//
//            if (ENABLE_VALIDATION_LAYERS) {
//                createInfo.enabledLayerCount(1)
//                        .ppEnabledLayerNames(PointerPtr.allocateStrings(arena, VALIDATION_LAYER_NAME));
//            }
//
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
//    private void createVMA() {
//        VMAJavaTraceUtil.enableJavaTraceForVMA(libVMA);
//        try (var arena = Arena.ofConfined()) {
//            var funcs = VmaVulkanFunctions.allocate(arena);
//            VMAUtil.fillVulkanFunctions(funcs, staticCommands, entryCommands, instanceCommands, deviceCommands);
//
//            // ✅ Enable buffer device address support
//            var flags = VmaAllocatorCreateFlags.BUFFER_DEVICE_ADDRESS;
//
//            var info = VmaAllocatorCreateInfo.allocate(arena)
//                    .instance(instance)
//                    .physicalDevice(physicalDevice)
//                    .device(device)
//                    .pVulkanFunctions(funcs)
//                    .vulkanApiVersion(Version.VK_API_VERSION_1_2.encode())
//                    .flags(flags); // ← add this line
//
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
//
//        // ✅ Critical: enable Vulkan 1.2 (SPIR-V 1.4)
////        shaderc.compileOptionsSetTargetSPIRV(shadercCompileOptions, ShadercSpirvVersion.VERSION_1_4);
//
//        // ✅ Set target environment to Vulkan 1.2 (SPIR-V 1.4)
//        // shaderc_target_env_vulkan = 0
//        // shaderc_vulkan_version_1_2 = 2
//        shaderc.compileOptionsSetTargetEnv(shadercCompileOptions, ShadercTargetEnv.VULKAN, ShadercEnvVersion.VULKAN_1_2);
//    }
//
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
//    // --- Ray Tracing Specific Code ---
//
//    private void createVertexBuffer() {
//        float[] vertices = {
//                -0.5f, -0.5f, 0.0f,
//                0.5f, -0.5f, 0.0f,
//                0.0f,  0.5f, 0.0f
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
//
//        // Total size: 64 (transform) + 4 (customIndex+mask) + 4 (sbtOffset+flags) + 8 (AS ref) = 80 bytes
//        int INSTANCE_SIZE = 80;
//        try (var arena = Arena.ofConfined()) {
//            // BLAS
//            var triangles = VkAccelerationStructureGeometryTrianglesDataKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
//                    .vertexFormat(VkFormat.R32G32B32_SFLOAT)
//                    .vertexStride(3 * Float.BYTES)
//                    .maxVertex(3);
//            triangles.vertexData().segment().copyFrom(MemorySegment.ofAddress(vertexAddress));
//
//            var geometry = VkAccelerationStructureGeometryKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
//                    .geometryType(VkGeometryTypeKHR.TRIANGLES);
//            geometry.geometry().triangles(triangles);
//
//            var buildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
//                    .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL)
//                    .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE)
//                    .pGeometries(geometry);
//
//            var maxPrimCount = IntPtr.allocate(arena);
//            maxPrimCount.write(3);
//
//            var sizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
//            deviceCommands.getAccelerationStructureBuildSizesKHR(
//                    device, VkAccelerationStructureBuildTypeKHR.DEVICE, buildInfo, maxPrimCount, sizeInfo);
//
//            var blasBuffer = createAccelerationStructureBuffer((int)sizeInfo.accelerationStructureSize());
//            var blasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
//                    .buffer(blasBuffer.first)
//                    .size(sizeInfo.accelerationStructureSize())
//                    .type(VkAccelerationStructureTypeKHR.BOTTOM_LEVEL);
//
//            var pBlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
//            var result = deviceCommands.createAccelerationStructureKHR(device, blasCreateInfo, null, pBlas);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create BLAS: " + VkResult.explain(result));
//            }
//            blas = pBlas.read();
//
//            long blasAddress = getAccelerationStructureDeviceAddress(blas);
//
//            // TLAS
//            var instanceData = arena.allocate(INSTANCE_SIZE); // allocates 80 bytes
//
//            // 1. Write transform (12 floats = 48 bytes)
//            float[] identity = {
//                    1, 0, 0, 0,
//                    0, 1, 0, 0,
//                    0, 0, 1, 0
//            };
//            for (int i = 0; i < 12; i++) {
//                instanceData.setAtIndex(ValueLayout.JAVA_FLOAT, i, identity[i]);
//            }
//
//            // 2. Pack instanceCustomIndex (24 bits) and mask (8 bits) into one uint32 at offset 48
//            // instanceCustomIndex is 24-bit, mask is 8-bit → pack into one uint32
//            int instanceCustomIndex = 0;
//            int mask = 0xFF;
//            int word1 = (instanceCustomIndex << 0) | (mask << 24); // clearer, no warning
//            instanceData.setAtIndex(ValueLayout.JAVA_INT, 12, word1); // 12 * 4 = 48
//
//            // 3. Pack sbtOffset (24 bits) and flags (8 bits) into one uint32 at offset 52
//            int sbtOffset = 0;
//            int flags = VkGeometryInstanceFlagsKHR.TRIANGLE_FACING_CULL_DISABLE;
//            int word2 = (sbtOffset << 0) | (flags << 24);
//            instanceData.setAtIndex(ValueLayout.JAVA_INT, 13, word2); // 13 * 4 = 52
//
//            // 4. Write accelerationStructureReference (uint64) at offset 56
//            instanceData.setAtIndex(ValueLayout.JAVA_LONG, 7, blasAddress); // 7 * 8 = 56
//
//            // Now create buffer and copy data
//            var instBuffer = createBuffer(
//                    INSTANCE_SIZE,
//                    VkBufferUsageFlags.SHADER_DEVICE_ADDRESS | VkBufferUsageFlags.ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_KHR,
//                    VmaAllocationCreateFlags.HOST_ACCESS_RANDOM,
//                    VkMemoryPropertyFlags.HOST_COHERENT,
//                    null
//            );
//
//            var ppData = PointerPtr.allocate(arena);
//            vma.mapMemory(vmaAllocator, instBuffer.second, ppData);
//            ppData.read().reinterpret(INSTANCE_SIZE).copyFrom(instanceData);
//            vma.unmapMemory(vmaAllocator, instBuffer.second);
//
//            long instAddress = getBufferDeviceAddress(instBuffer.first);
//
//            var instancesData = VkAccelerationStructureGeometryInstancesDataKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR);
//            instancesData.data().segment().copyFrom(MemorySegment.ofAddress(instAddress));
//
//            var tlasGeometry = VkAccelerationStructureGeometryKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_GEOMETRY_KHR)
//                    .geometryType(VkGeometryTypeKHR.INSTANCES);
//            tlasGeometry.geometry().instances(instancesData);
//
//            // Inside createAccelerationStructures(), after allocating tlasGeometry
//            var tlasBuildInfo = VkAccelerationStructureBuildGeometryInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
//                    .type(VkAccelerationStructureTypeKHR.TOP_LEVEL)
//                    .flags(VkBuildAccelerationStructureFlagsKHR.PREFER_FAST_TRACE)
//                    .mode(VkBuildAccelerationStructureModeKHR.BUILD)
//                    .geometryCount(1) // ← ADD THIS LINE
//                    .pGeometries(tlasGeometry);
//
//            var tlasMaxPrim = IntPtr.allocate(arena);
//            tlasMaxPrim.write(1);
//
//            var tlasSizeInfo = VkAccelerationStructureBuildSizesInfoKHR.allocate(arena);
//            deviceCommands.getAccelerationStructureBuildSizesKHR(
//                    device, VkAccelerationStructureBuildTypeKHR.DEVICE, tlasBuildInfo, tlasMaxPrim, tlasSizeInfo);
//
//            var tlasBuffer = createAccelerationStructureBuffer((int)tlasSizeInfo.accelerationStructureSize());
//            var tlasCreateInfo = VkAccelerationStructureCreateInfoKHR.allocate(arena)
//                    .sType(VkStructureType.ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
//                    .buffer(tlasBuffer.first)
//                    .size(tlasSizeInfo.accelerationStructureSize())
//                    .type(VkAccelerationStructureTypeKHR.TOP_LEVEL);
//
//            var pTlas = VkAccelerationStructureKHR.Ptr.allocate(arena);
//            result = deviceCommands.createAccelerationStructureKHR(device, tlasCreateInfo, null, pTlas);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create TLAS: " + VkResult.explain(result));
//            }
//            tlas = pTlas.read(); // ✅ store full object
//        }
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
//            info.accelerationStructure(as); // ✅ pass object directly
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
//            var allocInfo = VmaAllocationCreateInfo.allocate(arena)
//                    .usage(VmaMemoryUsage.AUTO); // no flags = GPU-only
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
//    private void createDescriptorSetLayout() {
//        try (var arena = Arena.ofConfined()) {
//            var bindings = VkDescriptorSetLayoutBinding.allocate(arena, 2)
//                    .at(0, b -> b
//                            .binding(0) // ← resultImage (STORAGE_IMAGE)
//                            .descriptorType(VkDescriptorType.STORAGE_IMAGE)
//                            .descriptorCount(1)
//                            .stageFlags(VkShaderStageFlags.RAYGEN_KHR))
//                    .at(1, b -> b
//                            .binding(1) // ← tlas (ACCELERATION_STRUCTURE)
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
//                    .at(0, s -> s.type(VkDescriptorType.ACCELERATION_STRUCTURE_KHR).descriptorCount(1))
//                    .at(1, s -> s.type(VkDescriptorType.STORAGE_IMAGE).descriptorCount(1));
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
//
//// Binding 0: resultImage (STORAGE_IMAGE)
//            var imageInfo = VkDescriptorImageInfo.allocate(arena)
//                    .imageView(outputImageView)
//                    .imageLayout(VkImageLayout.GENERAL);
//
//// Binding 1: tlas (ACCELERATION_STRUCTURE)
//            var asInfo = VkWriteDescriptorSetAccelerationStructureKHR.allocate(arena)
//                    .sType(VkStructureType.WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR)
//                    .accelerationStructureCount(1)
//                    .pAccelerationStructures(VkAccelerationStructureKHR.Ptr.allocateV(arena, tlas));
//
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
//
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
//
//            var rgenModule = createShaderModule(rgenCode);
//            var rchitModule = createShaderModule(rchitCode);
//            var rmissModule = createShaderModule(rmissCode);
//
//            var stages = VkPipelineShaderStageCreateInfo.allocate(arena, 3)
//                    .at(0, s -> s.stage(VkShaderStageFlags.RAYGEN_KHR).module(rgenModule).pName(BytePtr.allocateString(arena, "main")))
//                    .at(1, s -> s.stage(VkShaderStageFlags.CLOSEST_HIT_KHR).module(rchitModule).pName(BytePtr.allocateString(arena, "main")))
//                    .at(2, s -> s.stage(VkShaderStageFlags.MISS_KHR).module(rmissModule).pName(BytePtr.allocateString(arena, "main")));
//
//            // --- FIXED: Set unused shaders to VK_SHADER_UNUSED_KHR ---
//            var groups = VkRayTracingShaderGroupCreateInfoKHR.allocate(arena, 3)
//                    .at(0, g -> g
//                            .type(VkRayTracingShaderGroupTypeKHR.GENERAL)
//                            .generalShader(0)
//                            .closestHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .anyHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .intersectionShader(VkConstants.SHADER_UNUSED_KHR))
//                    .at(1, g -> g
//                            .type(VkRayTracingShaderGroupTypeKHR.GENERAL)
//                            .generalShader(2)
//                            .closestHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .anyHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .intersectionShader(VkConstants.SHADER_UNUSED_KHR))
//                    .at(2, g -> g
//                            .type(VkRayTracingShaderGroupTypeKHR.TRIANGLES_HIT_GROUP)
//                            .generalShader(VkConstants.SHADER_UNUSED_KHR)
//                            .closestHitShader(1)
//                            .anyHitShader(VkConstants.SHADER_UNUSED_KHR)
//                            .intersectionShader(VkConstants.SHADER_UNUSED_KHR));
//
//            var rtProps = VkPhysicalDeviceRayTracingPipelinePropertiesKHR.allocate(arena)
//                    .sType(VkStructureType.PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR);
//            var props2 = VkPhysicalDeviceProperties2.allocate(arena).pNext(rtProps.segment());
//            instanceCommands.getPhysicalDeviceProperties2(physicalDevice, props2);
//
//            handleSize = rtProps.shaderGroupHandleSize();
//            handleAlignment = rtProps.shaderGroupHandleAlignment();
//            alignedHandleSize = (handleSize + handleAlignment - 1) / handleAlignment * handleAlignment;
//
//            var pushConstantRange = VkPushConstantRange.allocate(arena)
//                    .stageFlags(VkShaderStageFlags.RAYGEN_KHR)
//                    .offset(0)
//                    .size(128); // ← was 32, now 128
//
//            var layoutInfo = VkPipelineLayoutCreateInfo.allocate(arena)
//                    .setLayoutCount(1)
//                    .pSetLayouts(VkDescriptorSetLayout.Ptr.allocateV(arena, descriptorSetLayout))
//                    .pushConstantRangeCount(1)
//                    .pPushConstantRanges(pushConstantRange);
//
//            var pLayout = VkPipelineLayout.Ptr.allocate(arena);
//            var result = deviceCommands.createPipelineLayout(device, layoutInfo, null, pLayout);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create pipeline layout: " + VkResult.explain(result));
//            }
//            pipelineLayout = pLayout.read();
//
//            var pipelineInfo = VkRayTracingPipelineCreateInfoKHR.allocate(arena)
//                    .sType(VkStructureType.RAY_TRACING_PIPELINE_CREATE_INFO_KHR)
//                    .pStages(stages)
//                    .stageCount(3)
//                    .pGroups(groups)
//                    .groupCount(3)
//                    .maxPipelineRayRecursionDepth(1)
//                    .layout(pipelineLayout);
//
//            var pPipeline = VkPipeline.Ptr.allocate(arena);
//            result = deviceCommands.createRayTracingPipelinesKHR(device, null, null, 1, pipelineInfo, null, pPipeline);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create ray tracing pipeline: " + VkResult.explain(result));
//            }
//            rayTracingPipeline = pPipeline.read();
//
//            deviceCommands.destroyShaderModule(device, rgenModule, null);
//            deviceCommands.destroyShaderModule(device, rchitModule, null);
//            deviceCommands.destroyShaderModule(device, rmissModule, null);
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
//
//            int handleSize = rtProps.shaderGroupHandleSize();
//            int handleAlignment = rtProps.shaderGroupHandleAlignment();
//            int shaderGroupBaseAlignment = rtProps.shaderGroupBaseAlignment();
//
//            // Step 1: Align handle size to handleAlignment
//            int alignedHandleSize = (handleSize + handleAlignment - 1) / handleAlignment * handleAlignment;
//
//            // ✅ Step 2: Align stride to shaderGroupBaseAlignment
//            sbtRecordStride = (alignedHandleSize + shaderGroupBaseAlignment - 1) / shaderGroupBaseAlignment * shaderGroupBaseAlignment;
//
//            // Use sbtRecordStride for SBT regions and size calculation
//            int sbtSize = 3 * sbtRecordStride;
//
//            // ✅ Allocate padded buffer
//            long paddedSize = sbtSize + shaderGroupBaseAlignment - 1;
//            var buffer = createBuffer(
//                    (int) paddedSize, // ← critical: padded size
//                    VkBufferUsageFlags.SHADER_BINDING_TABLE_KHR | VkBufferUsageFlags.SHADER_DEVICE_ADDRESS,
//                    VmaAllocationCreateFlags.HOST_ACCESS_RANDOM,
//                    VkMemoryPropertyFlags.HOST_COHERENT,
//                    null
//            );
//            sbtBuffer = buffer.first;
//            sbtAllocation = buffer.second;
//
//            long rawAddress = getBufferDeviceAddress(sbtBuffer);
//            long alignedAddress = (rawAddress + shaderGroupBaseAlignment - 1) & ~(shaderGroupBaseAlignment - 1);
//            long offsetInBuffer = alignedAddress - rawAddress;
//
//            // Get shader handles
//            var handles = BytePtr.allocate(arena, sbtSize);
//            var result = deviceCommands.getRayTracingShaderGroupHandlesKHR(
//                    device, rayTracingPipeline, 0, 3, sbtSize, handles.segment()
//            );
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to get shader group handles: " + VkResult.explain(result));
//            }
//
//            // Map and copy to aligned offset
//            var ppData = PointerPtr.allocate(arena);
//            vma.mapMemory(vmaAllocator, sbtAllocation, ppData);
//            // ✅ Copy into the aligned region (which has at least sbtSize space)
//            ppData.read().reinterpret(paddedSize).asSlice(offsetInBuffer, sbtSize).copyFrom(handles.segment());
//            vma.unmapMemory(vmaAllocator, sbtAllocation);
//
//            this.alignedSbtAddress = alignedAddress;
//            this.alignedHandleSize = alignedHandleSize;
//
//            System.out.println("rawAddress = " + rawAddress);
//            System.out.println("alignedAddress = " + alignedAddress);
//            System.out.println("alignedAddress % 64 = " + (alignedAddress % 64));
//            System.out.println();
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
//    private void drawFrame() {
//        try (var arena = Arena.ofConfined()) {
//            var pImageIndex = IntPtr.allocate(arena);
//            var result = deviceCommands.acquireNextImageKHR(
//                    device, swapChain, UINT64_MAX,
//                    null,
//                    null, // ← no fence
//                    pImageIndex
//            );
//            if (result != VkResult.SUCCESS && result != VkResult.SUBOPTIMAL_KHR) {
//                throw new RuntimeException("Failed to acquire swap chain image");
//            }
//            int imageIndex = pImageIndex.read();
//
//            var cmd = beginSingleTimeCommands();
//            recordCommandBuffer(cmd, imageIndex);
//            endSingleTimeCommands(cmd);
//
//            var presentInfo = VkPresentInfoKHR.allocate(arena)
//                    .swapchainCount(1)
//                    .pSwapchains(VkSwapchainKHR.Ptr.allocateV(arena, swapChain))
//                    .pImageIndices(IntPtr.allocateV(arena, imageIndex));
//            deviceCommands.queuePresentKHR(presentQueue, presentInfo);
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
//                    VkPipelineStageFlags.ALL_COMMANDS,
//                    VkPipelineStageFlags.ALL_COMMANDS,
//                    0,
//                    0, null,
//                    0, null,
//                    1, barrier);
//
//            deviceCommands.cmdBindPipeline(cmd, VkPipelineBindPoint.RAY_TRACING_KHR, rayTracingPipeline);
//            var pDescriptorSet = VkDescriptorSet.Ptr.allocate(arena);
//            pDescriptorSet.write(descriptorSet);
//            var pDynamicOffsets = IntPtr.allocate(arena);
//            deviceCommands.cmdBindDescriptorSets(cmd, VkPipelineBindPoint.RAY_TRACING_KHR,
//                    pipelineLayout, 0, 1, pDescriptorSet, 0, pDynamicOffsets);
//
//            var raygen = VkStridedDeviceAddressRegionKHR.allocate(arena)
//                    .deviceAddress(alignedSbtAddress)
//                    .stride(sbtRecordStride)  // ← use aligned stride
//                    .size(sbtRecordStride);
//
//            var miss = VkStridedDeviceAddressRegionKHR.allocate(arena)
//                    .deviceAddress(alignedSbtAddress + sbtRecordStride)
//                    .stride(sbtRecordStride)
//                    .size(sbtRecordStride);
//
//            var hit = VkStridedDeviceAddressRegionKHR.allocate(arena)
//                    .deviceAddress(alignedSbtAddress + 2 * sbtRecordStride)
//                    .stride(sbtRecordStride)
//                    .size(sbtRecordStride);
//
//            // Create dummy camera matrices (orthographic, looking down -Z)
//            Matrix4f invView = new Matrix4f().identity(); // No rotation/translation
//            Matrix4f invProj = new Matrix4f().setOrtho(-1, 1, -1, 1, 0.01f, 1000.0f).invert();
//
//            // Flatten to float array (column-major for GLSL)
//            float[] pushConstants = new float[32]; // 2 * 16
//            invProj.get(pushConstants, 0);
//            invView.get(pushConstants, 16);
//
//            // Upload to GPU
//            // ✅ Allocate native memory and copy
//            var data = arena.allocate(ValueLayout.JAVA_FLOAT, 32); // or use existing arena
//            data.copyFrom(MemorySegment.ofArray(pushConstants));
//            deviceCommands.cmdPushConstants(
//                    cmd,
//                    pipelineLayout,
//                    VkShaderStageFlags.RAYGEN_KHR,
//                    0,
//                    128, // 2 * mat4 = 128 bytes
//                    data
//            );
//
//            deviceCommands.cmdTraceRaysKHR(cmd, raygen, miss, hit,
//                    VkStridedDeviceAddressRegionKHR.allocate(arena),
//                    swapChainExtent.width(), swapChainExtent.height(), 1);
//
//            barrier.oldLayout(VkImageLayout.GENERAL)
//                    .newLayout(VkImageLayout.TRANSFER_SRC_OPTIMAL)
//                    .srcAccessMask(VkAccessFlags.SHADER_WRITE)
//                    .dstAccessMask(VkAccessFlags.TRANSFER_READ);
//            deviceCommands.cmdPipelineBarrier(cmd,
//                    VkPipelineStageFlags.ALL_COMMANDS,
//                    VkPipelineStageFlags.TRANSFER,
//                    0, 0, null, 0, null, 1, barrier);
//
//            barrier.oldLayout(VkImageLayout.UNDEFINED)
//                    .newLayout(VkImageLayout.TRANSFER_DST_OPTIMAL)
//                    .image(swapChainImages.read(imageIndex));
//            deviceCommands.cmdPipelineBarrier(cmd,
//                    VkPipelineStageFlags.ALL_COMMANDS,
//                    VkPipelineStageFlags.TRANSFER,
//                    0, 0, null, 0, null, 1, barrier);
//
//            var copy = VkImageCopy.allocate(arena)
//                    .srcSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
//                    .dstSubresource(s -> s.aspectMask(VkImageAspectFlags.COLOR).layerCount(1))
//                    .extent(e -> e.width(swapChainExtent.width()).height(swapChainExtent.height()).depth(1));
//            deviceCommands.cmdCopyImage(cmd,
//                    outputImage, VkImageLayout.TRANSFER_SRC_OPTIMAL,
//                    swapChainImages.read(imageIndex), VkImageLayout.TRANSFER_DST_OPTIMAL,
//                    1, copy);
//
//            barrier.oldLayout(VkImageLayout.TRANSFER_DST_OPTIMAL)
//                    .newLayout(VkImageLayout.PRESENT_SRC_KHR)
//                    .srcAccessMask(VkAccessFlags.TRANSFER_WRITE)
//                    .dstAccessMask(0);
//            deviceCommands.cmdPipelineBarrier(cmd,
//                    VkPipelineStageFlags.TRANSFER,
//                    VkPipelineStageFlags.BOTTOM_OF_PIPE,
//                    0, 0, null, 0, null, 1, barrier);
//        }
//    }
//
//    private VkCommandBuffer beginSingleTimeCommands() {
//        try (var arena = Arena.ofConfined()) {
//            var allocInfo = VkCommandBufferAllocateInfo.allocate(arena)
//                    .commandPool(findTransientCommandPool()) // ← consistent pool
//                    .level(VkCommandBufferLevel.PRIMARY)
//                    .commandBufferCount(1);
//            var pBuf = VkCommandBuffer.Ptr.allocate(arena);
//            var result = deviceCommands.allocateCommandBuffers(device, allocInfo, pBuf);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to allocate command buffer: " + VkResult.explain(result));
//            }
//            var buf = pBuf.read();
//            var beginInfo = VkCommandBufferBeginInfo.allocate(arena)
//                    .flags(VkCommandBufferUsageFlags.ONE_TIME_SUBMIT);
//            result = deviceCommands.beginCommandBuffer(buf, beginInfo);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to begin command buffer: " + VkResult.explain(result));
//            }
//            return buf;
//        }
//    }
//
//    private void endSingleTimeCommands(VkCommandBuffer cmd) {
//        deviceCommands.endCommandBuffer(cmd);
//        try (var arena = Arena.ofConfined()) {
//            var submit = VkSubmitInfo.allocate(arena)
//                    .commandBufferCount(1)
//                    .pCommandBuffers(VkCommandBuffer.Ptr.allocateV(arena, cmd));
//            deviceCommands.queueSubmit(graphicsQueue, 1, submit, null);
//            deviceCommands.queueWaitIdle(graphicsQueue);
//            deviceCommands.freeCommandBuffers(device, findTransientCommandPool(), 1, VkCommandBuffer.Ptr.allocateV(arena, cmd));
//        }
//    }
//
//    private VkCommandPool findCommandPool() {
//        try (var arena = Arena.ofConfined()) {
//            var indices = findQueueFamilies(physicalDevice);
//            var info = VkCommandPoolCreateInfo.allocate(arena)
//                    .queueFamilyIndex(indices.graphicsFamily())
//                    .flags(VkCommandPoolCreateFlags.RESET_COMMAND_BUFFER);
//            var pPool = VkCommandPool.Ptr.allocate(arena);
//            var result = deviceCommands.createCommandPool(device, info, null, pPool);
//            if (result != VkResult.SUCCESS) {
//                throw new RuntimeException("Failed to create command pool: " + VkResult.explain(result));
//            }
//            return pPool.read();
//        }
//    }
//
//    private void createSyncObjects() {
//        try (var arena = Arena.ofConfined()) {
//            var fenceInfo = VkFenceCreateInfo.allocate(arena)
//                    .flags(VkFenceCreateFlags.SIGNALED); // start signaled so first wait passes
//            var pFence = VkFence.Ptr.allocate(arena);
//            deviceCommands.createFence(device, fenceInfo, null, pFence);
//            frameFence = pFence.read();
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
//    private VkCommandPool findTransientCommandPool() {
//        if (transientCommandPool == null) {
//            try (var arena = Arena.ofConfined()) {
//                var indices = findQueueFamilies(physicalDevice);
//                var info = VkCommandPoolCreateInfo.allocate(arena)
//                        .queueFamilyIndex(indices.graphicsFamily())
//                        .flags(VkCommandPoolCreateFlags.TRANSIENT);
//                var pPool = VkCommandPool.Ptr.allocate(arena);
//                deviceCommands.createCommandPool(device, info, null, pPool);
//                transientCommandPool = pPool.read();
//            }
//        }
//        return transientCommandPool;
//    }
//
//    // --- Utility methods ---
//
//    private PointerPtr getRequiredExtensions(Arena arena) {
//        try (var localArena = Arena.ofConfined()) {
//            var pGLFWExtensionCount = IntPtr.allocate(localArena);
//            var glfwExtensions = glfw.getRequiredInstanceExtensions(pGLFWExtensionCount);
//            if (glfwExtensions == null) {
//                throw new RuntimeException("Failed to get GLFW required instance extensions");
//            }
//
//            var glfwExtensionCount = pGLFWExtensionCount.read();
//            glfwExtensions = glfwExtensions.reinterpret(glfwExtensionCount);
//
//            PointerPtr extensions;
//            if (!ENABLE_VALIDATION_LAYERS) {
//                extensions = PointerPtr.allocate(arena, glfwExtensionCount + 1);
//            }
//            else {
//                extensions = PointerPtr.allocate(arena, glfwExtensionCount + 2);
//            }
//
//            for (int i = 0; i < glfwExtensionCount; i++) {
//                extensions.write(i, glfwExtensions.read(i));
//            }
//
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
//        System.err.println("Validation: " + Objects.requireNonNull(cb.pMessage()).readString());
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
//    private boolean framebufferResized = false;
//
//    public static void main(String[] args) {
//        new Application().run();
//    }
//}