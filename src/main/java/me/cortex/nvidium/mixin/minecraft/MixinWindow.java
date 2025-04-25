package me.cortex.nvidium.mixin.minecraft;

import me.cortex.nvidium.Nvidium;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.MonitorTracker;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class MixinWindow {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;", shift = At.Shift.AFTER), remap = false)
    private void init(WindowEventHandler eventHandler, MonitorTracker monitorTracker, WindowSettings settings, String videoMode, String title, CallbackInfo ci) {
        Nvidium.checkSystemIsCapable();
        GL43.glEnable(GL43.GL_DEBUG_OUTPUT);
        GL43.glEnable(GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);

        int[] contextFlags = new int[1];
        GL11.glGetIntegerv(GL43.GL_CONTEXT_FLAGS, contextFlags);

        boolean isDebugContext = (contextFlags[0] & GL43.GL_CONTEXT_FLAG_DEBUG_BIT) != 0;
        System.out.println("Is OpenGL debug context: " + isDebugContext);

        GL43.glDebugMessageCallback(new GLDebugMessageCallback() {
            @Override
            public void invoke(int source, int type, int id, int severity, int length, long message, long userParam) {
                String msg = MemoryUtil.memUTF8(message, length);
                System.err.printf("OpenGL Debug: [%d] %s%n", id, msg);
            }
        }, 0);

        // Now insert a test message
        GL43.glDebugMessageInsert(
                GL43.GL_DEBUG_SOURCE_APPLICATION,
                GL43.GL_DEBUG_TYPE_MARKER,
                1,
                GL43.GL_DEBUG_SEVERITY_NOTIFICATION,
                "=======================================================Debug output initialized!"
        );
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private void init2(WindowEventHandler eventHandler, MonitorTracker monitorTracker, WindowSettings settings, String videoMode, String title, CallbackInfo ci) {
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);
    }
}
