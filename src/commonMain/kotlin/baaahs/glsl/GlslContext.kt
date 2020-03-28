package baaahs.glsl

import com.danielgergely.kgl.Kgl

abstract class GlslContext(private val kgl: Kgl, private val glslVersion: String) {
    abstract fun <T> runInContext(fn: () -> T): T

    fun createProgram(fragShader: String): Program {
        return runInContext {
            Program(kgl, fragShader, glslVersion, GlslBase.plugins)
        }
    }

    fun createRenderer(
        program: Program,
        uvTranslator: UvTranslator
    ): GlslRenderer {
        return runInContext {
            GlslRenderer(kgl, object : GlslRenderer.ContextSwitcher {
                override fun <T> inContext(fn: () -> T): T = runInContext(fn)
            }, program, uvTranslator)
        }
    }
}