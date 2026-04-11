package tutorial.vulkan.raytracing;

import club.doki7.ffm.library.ILibraryLoader;
import club.doki7.ffm.library.ISharedLibrary;
import club.doki7.ffm.library.WindowsLibrary;
import club.doki7.ffm.ptr.BytePtr;
import club.doki7.shaderc.Shaderc;
import club.doki7.shaderc.enumtype.ShadercEnvVersion;
import club.doki7.shaderc.enumtype.ShadercOptimizationLevel;
import club.doki7.shaderc.enumtype.ShadercShaderKind;
import club.doki7.shaderc.enumtype.ShadercSourceLanguage;
import club.doki7.shaderc.enumtype.ShadercSpirvVersion;
import club.doki7.shaderc.enumtype.ShadercTargetEnv;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

/**
 * Diagnostic utility to find out which shaderc_shared.dll is loaded
 * and whether it supports ray tracing extensions.
 */
public class CheckShadercDll {
    public static void main(String[] args) throws Throwable {
        ISharedLibrary lib = ILibraryLoader.platformLoader().loadLibrary("shaderc_shared");
        
        MemorySegment hModule = null;
        if (lib instanceof WindowsLibrary wl) {
            var field = WindowsLibrary.class.getDeclaredField("hModule");
            field.setAccessible(true);
            hModule = (MemorySegment) field.get(wl);
        }
        
        if (hModule == null) {
            System.out.println("Could not get hModule");
            return;
        }
        
        String path = getModuleFileName(hModule);
        System.out.println("[DLL PATH] shaderc_shared.dll loaded from: " + path);
        
        // Test ray tracing support
        System.out.println("[TEST] Testing ray tracing support...");
        Shaderc shaderc = new Shaderc(lib);
        var compiler = shaderc.compilerInitialize();
        var options = shaderc.compileOptionsInitialize();
        shaderc.compileOptionsSetTargetEnv(options, ShadercTargetEnv.VULKAN, ShadercEnvVersion.VULKAN_1_2);
        shaderc.compileOptionsSetTargetSPIRV(options, ShadercSpirvVersion.VERSION_1_5);
        shaderc.compileOptionsSetOptimizationLevel(options, ShadercOptimizationLevel.ZERO);
        shaderc.compileOptionsSetSourceLanguage(options, ShadercSourceLanguage.GLSL);

        String testShader = "#version 460\n" +
                "#extension GL_EXT_ray_tracing : require\n" +
                "layout(binding = 0) uniform accelerationStructureEXT tlas;\n" +
                "layout(location = 0) rayPayloadEXT uint payload;\n" +
                "void main() {\n" +
                "    traceRayEXT(tlas, uint(0), uint(0xFF), uint(0), uint(0), uint(0), vec3(0,0,0), 0.0, vec3(0,0,-1), 100.0, 0);\n" +
                "}";
        
        try (var arena = Arena.ofConfined()) {
            var srcPtr = BytePtr.allocateString(arena, testShader);
            var result = shaderc.compileIntoSPV(compiler, srcPtr, srcPtr.size() - 1,
                    ShadercShaderKind.RAYGEN_SHADER,
                    BytePtr.allocateString(arena, "test.rgen"),
                    BytePtr.allocateString(arena, "main"),
                    options);
            
            long errors = shaderc.resultGetNumErrors(result);
            if (errors > 0) {
                BytePtr errMsg = shaderc.resultGetErrorMessage(result);
                String msg = (errMsg != null && !errMsg.segment().equals(MemorySegment.NULL)) ? errMsg.readString() : "Unknown error";
                System.out.println("[FAIL] Ray tracing NOT supported: " + msg);
            } else {
                System.out.println("[OK] Ray tracing IS supported! ✓");
            }
            shaderc.resultRelease(result);
        }
        
        shaderc.compileOptionsRelease(options);
        shaderc.compilerRelease(compiler);
    }
    
    static String getModuleFileName(MemorySegment hModule) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", arena);
            
            FunctionDescriptor descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
            );
            
            MethodHandle getModuleFileName = linker.downcallHandle(
                    kernel32.find("GetModuleFileNameW").get(),
                    descriptor
            );
            
            int bufferSize = 1024;
            MemorySegment buffer = arena.allocate(ValueLayout.JAVA_SHORT, bufferSize);
            
            int result = (int) getModuleFileName.invokeExact(hModule, buffer, bufferSize);
            
            if (result > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < result; i++) {
                    char c = (char) (buffer.get(ValueLayout.JAVA_SHORT, i * 2L) & 0xFFFF);
                    if (c == 0) break;
                    sb.append(c);
                }
                return sb.toString();
            }
            return "Unknown (error)";
        }
    }
}
