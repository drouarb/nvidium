package me.cortex.nvidium.gl;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.JNI;

public class EXTMeshShader {
    public static final int
            GL_MESH_SHADER_EXT = 0x9559,
            GL_TASK_SHADER_EXT = 0x955A;

    private static final long glDrawMeshTasksEXT_ptr;
    private static final long glMultiDrawMeshTasksIndirectEXT_ptr;
    static {
        if (GL.getFunctionProvider() == null) {
            throw new IllegalStateException("Class must be initalized after gl context has been created");
        }
        glDrawMeshTasksEXT_ptr = GL.getFunctionProvider().getFunctionAddress("glDrawMeshTasksEXT");
        glMultiDrawMeshTasksIndirectEXT_ptr = GL.getFunctionProvider().getFunctionAddress("glMultiDrawMeshTasksIndirectEXT");
    }

    public static void glDrawMeshTasksEXT(int num_groups_x, int num_groups_y, int num_groups_z) {
        if (glDrawMeshTasksEXT_ptr == 0) {
            throw new IllegalStateException("glDrawMeshTasksEXT not supported");
        }
        //System.out.println("glDrawMeshTasksEXT " + num_groups_x + " " + num_groups_y + " " + num_groups_z);
        JNI.invokeI(num_groups_x, num_groups_y, num_groups_z, glDrawMeshTasksEXT_ptr);
    }

    public static void glMultiDrawMeshTasksIndirectEXT(long indirect, int draw_count, int stride) {
        if (glDrawMeshTasksEXT_ptr == 0) {
            throw new IllegalStateException("glDrawMeshTasksEXT not supported");
        }
        //System.out.println("glDrawMeshTasksEXT " + num_groups_x + " " + num_groups_y + " " + num_groups_z);
        JNI.invokePI(indirect, draw_count, stride, glMultiDrawMeshTasksIndirectEXT_ptr);
    }
}
