package baaahs.glshaders

import baaahs.Logger
import baaahs.getTimeMillis
import baaahs.glsl.CompiledShader
import baaahs.glsl.GlslContext
import baaahs.glsl.Uniform
import com.danielgergely.kgl.GL_LINK_STATUS
import com.danielgergely.kgl.GL_TRUE

class GlslProgram(private val gl: GlslContext, val patch: Patch) {
    private val id = gl.runInContext { gl.check { createProgram() ?: throw IllegalStateException() } }

    private val vertexShader = gl.runInContext {
        gl.createVertexShader(
            """
            #version ${gl.glslVersion}
                
            precision lowp float;
            
            // xy = vertex position in normalized device coordinates ([-1,+1] range).
            in vec2 Vertex;
            
            const vec2 scale = vec2(0.5, 0.5);
            
            void main()
            {
                vec2 vTexCoords  = Vertex * scale + scale; // scale vertex attribute to [0,1] range
                gl_Position = vec4(Vertex, 0.0, 1.0);
            }
            """.trimIndent()
        )
    }

    private val fragShader = gl.runInContext {
        gl.createFragmentShader("#version ${gl.glslVersion}\n\n${patch.toGlsl()}\n")
    }

    init {
        gl.runInContext {
            gl.check { attachShader(id, vertexShader.id) }
            gl.check { attachShader(id, fragShader.id) }
            gl.check { linkProgram(id) }
            if (gl.check { getProgramParameter(id, GL_LINK_STATUS) } != GL_TRUE) {
                val infoLog = gl.check { getProgramInfoLog(id) }
                throw CompiledShader.CompilationException(infoLog ?: "huh?")
            }
        }
    }

    val vertexAttribLocation: Int = gl.runInContext {
        gl.check { getAttribLocation(id, "Vertex") }
    }

    val bindings = patch.uniformInputs.map { uniformInput ->
        val providerFactory = if (uniformInput is StockUniformInput) {
            uniformInput.providerFactory
        } else {
            defaultBindings["${uniformInput.type}:${uniformInput.name}"]
        }
        Binding(uniformInput, providerFactory)
    }

    fun bind() {
        gl.runInContext { gl.check { useProgram(id) } }
        bindings.forEach { it.bind() }
    }

    fun release() {
//        TODO gl.runInContext { gl.check { deleteProgram } }
    }

    inner class Binding(
        val uniformInput: UniformInput,
        providerFactory: (() -> Provider)?
    ) {
        private val uniformLocation by lazy {
            gl.runInContext {
                gl.check {
                    getUniformLocation(id, uniformInput.varName)?.let { Uniform(gl, it) }
                }
            }
        }
        private val provider = providerFactory?.invoke()

        fun bind() {
            uniformLocation?.let { uniformLocation ->
                gl.runInContext { provider?.set(uniformLocation) }
            }
        }
    }

    interface Provider {
        fun set(uniform: Uniform)
    }

    class ResolutionProvider : Provider {
        override fun set(uniform: Uniform) {
            uniform.set(320f, 150f) // TODO: these need to match the canvas size
        }
    }

    class TimeProvider : Provider {
        override fun set(uniform: Uniform) {
            val thisTime = (getTimeMillis() and 0x7ffffff).toFloat() / 1000.0f
            uniform.set(thisTime)
        }
    }

    class UvCoordProvider : Provider {
        override fun set(uniform: Uniform) {
            // No-op.
        }
    }

    interface Port {
        val shaderId: String?
    }

    object Resolution : StockUniformInput("vec2", "resolution", ::ResolutionProvider)
    object Time : StockUniformInput("float", "time", ::TimeProvider)
    object UvCoord : StockUniformInput("vec4", "gl_FragCoord", ::UvCoordProvider) {
        override val varName: String = name
        override val isImplicit = true
    }

    object PixelColor : Port {
        override val shaderId: String? = null
    }

    open class StockUniformInput(
        type: String, name: String, val providerFactory: () -> Provider
    ) : UniformInput(type, name) {
        override val shaderId: String? = null
    }

    open class UniformInput(val type: String, val name: String) : Port {
        override val shaderId: String? = null

        open val varName: String get() = "in_$name"
        open val isImplicit = false
    }

    class ShaderPort(override val shaderId: String, val portName: String) : Port

    class Patch(
        private val shaderFragments: Map<String, ShaderFragment>,
        private val links: List<Link>
    ) {
        private val fromById = hashMapOf<String?, ArrayList<Link>>()
        private val toById = hashMapOf<String?, ArrayList<Link>>()

        init {
            links.forEach { link ->
                val (from, to) = link
                fromById.getOrPut(from.shaderId) { arrayListOf() }.add(link)
                toById.getOrPut(to.shaderId) { arrayListOf() }.add(link)
            }
        }

        val fromGlobal: List<Link> = fromById[null] ?: emptyList()
        val toGlobal: List<Link> = toById[null] ?: emptyList()

        val uniformInputs: List<UniformInput>
            get() = fromGlobal.mapNotNull { (from, _) -> from as? UniformInput }

        fun toGlsl(): String {
            val buf = StringBuilder()
            buf.append("#ifdef GL_ES\n")
            buf.append("precision mediump float;\n")
            buf.append("#endif\n")
            buf.append("\n")
            buf.append("// SparkleMotion generated GLSL\n")
            buf.append("\n")
            buf.append("layout(location = 0) out vec4 sm_pixelColor;\n")
            buf.append("\n")

            uniformInputs.forEach {
                if (!it.isImplicit)
                    buf.append("uniform ${it.type} ${it.varName};\n")
            }
            buf.append("\n")

            shaderFragments.entries.forEachIndexed { i, (shaderId, shaderFragment) ->
                val nsPrefix = "p$i"
                val namespace = GlslCode.Namespace(nsPrefix)
                buf.append("// Shader ID: ", shaderId, "; namespace: ", nsPrefix, "\n")
                buf.append("// ", shaderFragment.name, "\n")
                val portMap = hashMapOf<String, String>()
                toById[shaderId]?.forEach { (from, to) ->
                    if (to is ShaderPort && from is UniformInput) {
                        portMap[to.portName] = from.varName
                    }
                }
                portMap["gl_FragColor"] = "sm_pixelColor"
                buf.append(shaderFragment.toGlsl(namespace, portMap), "\n")
            }

            buf.append("\n#line 10001\n")
            buf.append("void main() {\n")
            shaderFragments.values.forEachIndexed { i, shaderFragment ->
                val namespace = GlslCode.Namespace("p$i")
                buf.append(shaderFragment.invocationGlsl(namespace))
            }
            buf.append("}\n")
            return buf.toString()
        }
    }

    companion object {
        val logger = Logger("GlslProgram")

        private val defaultBindings = mapOf<String, () -> Provider>(
            "float:time" to { TimeProvider() },
            "float:iTime" to { TimeProvider() },
            "vec2:resolution" to { ResolutionProvider() },
            "vec2:iResolution" to { ResolutionProvider() }
        )
    }
}

typealias Link = Pair<GlslProgram.Port, GlslProgram.Port>
