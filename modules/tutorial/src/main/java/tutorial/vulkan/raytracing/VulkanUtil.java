package tutorial.vulkan.raytracing;

import club.doki7.ffm.annotation.NativeType;
import club.doki7.ffm.ptr.IntPtr;
import club.doki7.ffm.ptr.PointerPtr;
import club.doki7.vma.VMA;
import club.doki7.vma.bitmask.VmaAllocationCreateFlags;
import club.doki7.vma.datatype.VmaAllocationCreateInfo;
import club.doki7.vma.enumtype.VmaMemoryUsage;
import club.doki7.vma.handle.VmaAllocation;
import club.doki7.vma.handle.VmaAllocator;
import club.doki7.vulkan.VkConstants;
import club.doki7.vulkan.bitmask.VkDebugUtilsMessageSeverityFlagsEXT;
import club.doki7.vulkan.bitmask.VkDebugUtilsMessageTypeFlagsEXT;
import club.doki7.vulkan.bitmask.VkImageAspectFlags;
import club.doki7.vulkan.bitmask.VkMemoryAllocateFlags;
import club.doki7.vulkan.bitmask.VkMemoryPropertyFlags;
import club.doki7.vulkan.command.VkDeviceCommands;
import club.doki7.vulkan.datatype.VkBufferCreateInfo;
import club.doki7.vulkan.datatype.VkDebugUtilsMessengerCallbackDataEXT;
import club.doki7.vulkan.datatype.VkDebugUtilsMessengerCreateInfoEXT;
import club.doki7.vulkan.datatype.VkImageMemoryBarrier;
import club.doki7.vulkan.datatype.VkMemoryAllocateFlagsInfo;
import club.doki7.vulkan.datatype.VkMemoryAllocateInfo;
import club.doki7.vulkan.datatype.VkMemoryRequirements;
import club.doki7.vulkan.enumtype.VkResult;
import club.doki7.vulkan.enumtype.VkSharingMode;
import club.doki7.vulkan.enumtype.VkStructureType;
import club.doki7.vulkan.handle.VkBuffer;
import club.doki7.vulkan.handle.VkDevice;
import club.doki7.vulkan.handle.VkDeviceMemory;
import club.doki7.vulkan.handle.VkImage;
import de.javagl.obj.ObjData;
import de.javagl.obj.ObjReader;
import de.javagl.obj.ObjUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * Vulkan utility helpers shared across ch20 ray tracing demo.
 * Includes: debug callback formatting, buffer creation helpers,
 * image barrier helpers.
 */
public final class VulkanUtil {

    private VulkanUtil() {}

    // ======================== Debug Callback ========================

    /**
     * Vulkan debug messenger callback.
     * Format matches etalon_triangle: [SEVERITY] message
     */
    public static @NativeType("VkBool32") int debugCallback(
            int messageSeverity, int messageType, MemorySegment pCallbackData, MemorySegment ignored
    ) {
        var data = new VkDebugUtilsMessengerCallbackDataEXT(pCallbackData.reinterpret(VkDebugUtilsMessengerCallbackDataEXT.BYTES));
        String message = Objects.requireNonNull(data.pMessage()).readString();
        String color = severityColor(messageSeverity);
        String severityStr = severityLabel(messageSeverity);
        System.err.println(color + "[" + severityStr + "] " + message + ANSI_RESET);
        return VkConstants.FALSE;
    }

    /**
     * Populates VkDebugUtilsMessengerCreateInfoEXT with max verbosity
     * (VERBOSE, WARNING, ERROR + GENERAL, VALIDATION, PERFORMANCE).
     */
    public static void populateDebugMessengerCreateInfo(VkDebugUtilsMessengerCreateInfoEXT info) {
        info.messageSeverity(
                VkDebugUtilsMessageSeverityFlagsEXT.VERBOSE
                | VkDebugUtilsMessageSeverityFlagsEXT.WARNING
                | VkDebugUtilsMessageSeverityFlagsEXT.ERROR
        ).messageType(
                VkDebugUtilsMessageTypeFlagsEXT.GENERAL
                | VkDebugUtilsMessageTypeFlagsEXT.VALIDATION
                | VkDebugUtilsMessageTypeFlagsEXT.PERFORMANCE
        ).pfnUserCallback(VulkanUtil::debugCallback);
    }

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_WHITE = "\u001B[37m";

    private static String severityColor(int severity) {
        if ((severity & VkDebugUtilsMessageSeverityFlagsEXT.ERROR) != 0) return ANSI_RED;
        return ANSI_WHITE;
    }

    private static String severityLabel(int severity) {
        if ((severity & VkDebugUtilsMessageSeverityFlagsEXT.ERROR) != 0) return "ERROR";
        if ((severity & VkDebugUtilsMessageSeverityFlagsEXT.WARNING) != 0) return "WARNING";
        if ((severity & VkDebugUtilsMessageSeverityFlagsEXT.INFO) != 0) return "INFO";
        if ((severity & VkDebugUtilsMessageSeverityFlagsEXT.VERBOSE) != 0) return "VERBOSE";
        return "UNKNOWN";
    }

    // ======================== Math Helpers ========================

    /** Aligns value up to nearest multiple of alignment. */
    public static long alignUp(long value, long alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }

    // ======================== VMA Buffer Helpers ========================

    /** Result of createVmaBuffer. */
    public record VmaBufferResult(VkBuffer buffer, VmaAllocation allocation) {}

    /**
     * Creates a VkBuffer with VMA allocation.
     * Automatically handles HOST_VISIBLE flag mapping to HOST_ACCESS_SEQUENTIAL_WRITE.
     */
    public static VmaBufferResult createVmaBuffer(
            VMA vma, VmaAllocator allocator, Arena arena,
            long size, int usage, int vmaFlags, int memoryProps
    ) {
        var bufInfo = VkBufferCreateInfo.allocate(arena)
                .size(size).usage(usage).sharingMode(VkSharingMode.EXCLUSIVE);
        var allocInfo = VmaAllocationCreateInfo.allocate(arena)
                .usage(VmaMemoryUsage.AUTO)
                .flags(vmaFlags);
        if ((memoryProps & VkMemoryPropertyFlags.HOST_VISIBLE) != 0) {
            allocInfo.flags(allocInfo.flags() | VmaAllocationCreateFlags.HOST_ACCESS_SEQUENTIAL_WRITE);
        }
        var pBuf = VkBuffer.Ptr.allocate(arena);
        var pAlloc = VmaAllocation.Ptr.allocate(arena);
        checkResult(vma.createBuffer(allocator, bufInfo, allocInfo, pBuf, pAlloc, null), "Failed to create VMA buffer");
        return new VmaBufferResult(Objects.requireNonNull(pBuf.read()), Objects.requireNonNull(pAlloc.read()));
    }

    // ======================== Buffer Helpers ========================

    /** Result of createBufferWithMemory. */
    public record BufferWithMemory(VkBuffer buffer, VkDeviceMemory memory, long size) {}

    /**
     * Creates a VkBuffer, allocates VkDeviceMemory with DEVICE_ADDRESS flag,
     * and binds them together.
     */
    public static BufferWithMemory createBufferWithMemory(
            VkDeviceCommands deviceCmds, VkDevice device, Arena arena,
            long size, int usage, int memoryProps, FindMemoryType findMemoryType
    ) {
        var bufInfo = VkBufferCreateInfo.allocate(arena)
                .size(size).usage(usage).sharingMode(VkSharingMode.EXCLUSIVE);
        var pBuf = VkBuffer.Ptr.allocate(arena);
        checkResult(deviceCmds.createBuffer(device, bufInfo, null, pBuf), "Failed to create buffer");
        VkBuffer buffer = Objects.requireNonNull(pBuf.read());

        var memReqs = VkMemoryRequirements.allocate(arena);
        deviceCmds.getBufferMemoryRequirements(device, buffer, memReqs);

        var pMem = VkDeviceMemory.Ptr.allocate(arena);
        var memAlloc = VkMemoryAllocateInfo.allocate(arena)
                .allocationSize(memReqs.size())
                .memoryTypeIndex(findMemoryType.find(memReqs.memoryTypeBits(), memoryProps));
        var memFlags = VkMemoryAllocateFlagsInfo.allocate(arena)
                .sType(VkStructureType.MEMORY_ALLOCATE_FLAGS_INFO)
                .flags(VkMemoryAllocateFlags.DEVICE_ADDRESS);
        memAlloc.pNext(memFlags.segment());
        checkResult(deviceCmds.allocateMemory(device, memAlloc, null, pMem), "Failed to allocate memory");
        VkDeviceMemory memory = Objects.requireNonNull(pMem.read());
        deviceCmds.bindBufferMemory(device, buffer, memory, 0);

        return new BufferWithMemory(buffer, memory, memReqs.size());
    }

    /**
     * Uploads float array to mapped VkDeviceMemory.
     */
    public static void uploadBufferData(
            VkDeviceCommands deviceCmds, VkDevice device, Arena arena,
            VkDeviceMemory memory, long offset, long size, float[] data
    ) {
        var pData = PointerPtr.allocate(arena);
        deviceCmds.mapMemory(device, memory, offset, size, 0, pData);
        var seg = pData.read().reinterpret(size);
        for (int i = 0; i < data.length; i++) {
            seg.set(java.lang.foreign.ValueLayout.JAVA_FLOAT, i * Float.BYTES, data[i]);
        }
        deviceCmds.unmapMemory(device, memory);
    }

    /**
     * Uploads int array to mapped VkDeviceMemory.
     */
    public static void uploadBufferData(
            VkDeviceCommands deviceCmds, VkDevice device, Arena arena,
            VkDeviceMemory memory, long offset, long size, int[] data
    ) {
        var pData = PointerPtr.allocate(arena);
        deviceCmds.mapMemory(device, memory, offset, size, 0, pData);
        var seg = pData.read().reinterpret(size);
        for (int i = 0; i < data.length; i++) {
            seg.set(java.lang.foreign.ValueLayout.JAVA_INT, i * Integer.BYTES, data[i]);
        }
        deviceCmds.unmapMemory(device, memory);
    }

    /**
     * Uploads byte array to mapped VkDeviceMemory.
     */
    public static void uploadBufferData(
            VkDeviceCommands deviceCmds, VkDevice device, Arena arena,
            VkDeviceMemory memory, long offset, long size, byte[] data
    ) {
        var pData = PointerPtr.allocate(arena);
        deviceCmds.mapMemory(device, memory, offset, size, 0, pData);
        var seg = pData.read().reinterpret(size);
        for (int i = 0; i < data.length; i++) {
            seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, data[i]);
        }
        deviceCmds.unmapMemory(device, memory);
    }

    // ======================== Image Barrier ========================

    /**
     * Creates a VkImageMemoryBarrier for color images.
     */
    public static VkImageMemoryBarrier createImageBarrier(
            Arena arena, VkImage image,
            int oldLayout, int newLayout, int srcAccess, int dstAccess
    ) {
        return VkImageMemoryBarrier.allocate(arena)
                .sType(VkStructureType.IMAGE_MEMORY_BARRIER)
                .oldLayout(oldLayout).newLayout(newLayout)
                .srcAccessMask(srcAccess).dstAccessMask(dstAccess)
                .image(image)
                .subresourceRange(r -> r.aspectMask(VkImageAspectFlags.COLOR).levelCount(1).layerCount(1));
    }

    // ======================== Result Check ========================

    /**
     * Throws RuntimeException if Vulkan result is not SUCCESS.
     */
    public static void checkResult(int result, String message) {
        if (result != VkResult.SUCCESS) {
            throw new RuntimeException(message + ": " + VkResult.explain(result));
        }
    }

    /**
     * Functional interface for findMemoryType — allows passing
     * instance method as lambda to static helpers.
     */
    @FunctionalInterface
    public interface FindMemoryType {
        int find(int typeBits, int properties);
    }

    // ======================== OBJ Model Loading ========================

    /**
     * Result of loading an OBJ model — vertices and indices arrays.
     * <p>Vertex structure: vec3 position (3 floats) + vec3 color (3 floats, always white) + vec2 texCoord (2 floats).
     * Total: 8 floats per vertex (32 bytes).</p>
     */
    public record ObjModelData(float[] vertices, int[] indices) {}

    /**
     * Loads an OBJ model from a resource stream and converts it to vertex/index arrays.
     * <p>Vertices are packed as: pos(3) + color(3) + texCoord(2) = 8 floats per vertex.
     * Color is always white (1.0, 1.0, 1.0). Texture coordinates are flipped vertically (1.0 - v).</p>
     *
     * @param stream input stream to the OBJ file resource
     * @return ObjModelData containing vertices and indices arrays
     * @throws RuntimeException if loading fails
     */
    public static ObjModelData loadObjModel(InputStream stream) {
        if (stream == null) {
            throw new RuntimeException("Failed to load model: stream is null");
        }

        try {
            var obj = ObjReader.read(stream);
            obj = ObjUtils.convertToRenderable(obj);

            int[] indices = ObjData.getFaceVertexIndicesArray(obj);
            var verticesArray = ObjData.getVerticesArray(obj);
            var texCoordsArray = ObjData.getTexCoordsArray(obj, 2);
            float[] vertices = new float[obj.getNumVertices() * 8];

            for (int i = 0; i < obj.getNumVertices(); i++) {
                // vec3 position
                vertices[i * 8] = verticesArray[i * 3];
                vertices[i * 8 + 1] = verticesArray[i * 3 + 1];
                vertices[i * 8 + 2] = verticesArray[i * 3 + 2];
                // vec3 color (white)
                vertices[i * 8 + 3] = 1.0f;
                vertices[i * 8 + 4] = 1.0f;
                vertices[i * 8 + 5] = 1.0f;
                // vec2 texCoord (V flipped)
                vertices[i * 8 + 6] = texCoordsArray[i * 2];
                vertices[i * 8 + 7] = 1.0f - texCoordsArray[i * 2 + 1];
            }

            return new ObjModelData(vertices, indices);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load OBJ model", e);
        }
    }

    // ======================== Shader Compilation ========================

    /**
     * Compiles GLSL source to SPIR-V at runtime using glslangValidator.
     * <p>Requires glslangValidator to be in PATH (Vulkan SDK).</p>
     *
     * @param arena    the arena for the returned IntPtr
     * @param source   the GLSL source code string
     * @param stage    shader stage: "rgen", "rchit", "rmiss", "vert", "frag", "comp"
     * @param filename optional filename for error messages
     * @return IntPtr containing SPIR-V words
     * @throws RuntimeException if compilation fails
     */
    public static IntPtr compileGlslShader(
            Arena arena, String source, String stage, String filename
    ) {
        java.nio.file.Path tempSpv;
        try {
            tempSpv = java.nio.file.Files.createTempFile("vulkan_shader_", ".spv");
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create temp file", e);
        }

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
            var ret = club.doki7.ffm.ptr.IntPtr.allocate(arena, wordCount);
            ret.segment().copyFrom(MemorySegment.ofArray(words));
            return ret;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Shader compilation interrupted", e);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Shader compilation error", e);
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(tempSpv);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    /**
     * Loads a shader resource and compiles it to SPIR-V.
     *
     * @param arena    the arena for the returned IntPtr
     * @param clazz    the class to load the resource from
     * @param filename resource path (e.g. "/shader/raytracing/ch20/ray.rgen")
     * @param stage    shader stage: "rgen", "rchit", "rmiss", "vert", "frag", "comp"
     * @return IntPtr containing SPIR-V words
     * @throws RuntimeException if loading or compilation fails
     */
    public static IntPtr compileShaderFromClass(
            Arena arena, Class<?> clazz, String filename, String stage
    ) {
        String path = filename.startsWith("/") ? filename : "/" + filename;
        try (var stream = clazz.getResourceAsStream(path)) {
            if (stream == null) throw new RuntimeException("Shader not found: " + filename);
            String source = new String(stream.readAllBytes());
            return compileGlslShader(arena, source, stage, filename);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load shader: " + filename, e);
        }
    }
}
