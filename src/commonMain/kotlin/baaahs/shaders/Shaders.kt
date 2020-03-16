package baaahs.shaders

import baaahs.Color
import baaahs.Surface
import baaahs.io.ByteArrayReader
import baaahs.io.ByteArrayWriter

enum class ShaderId(val reader: ShaderReader<*>) {
    SOLID(SolidShader),
    PIXEL(PixelShader),
    SINE_WAVE(SineWaveShader),
    COMPOSITOR(CompositorShader),
    SPARKLE(SparkleShader),
    SIMPLE_SPATIAL(SimpleSpatialShader),
    HEART(HeartShader),
    RANDOM(RandomShader),
    GLSL_SHADER(GlslShader);

    companion object {
        val values = values()
        fun get(i: Byte): ShaderId {
            if (i > values.size || i < 0) {
                throw Throwable("bad index for ShaderId: $i")
            }
            return values[i.toInt()]
        }
    }
}

interface ShaderReader<T : Shader<*>> {
    fun parse(reader: ByteArrayReader): T
}

interface RenderContext {
    fun <T : PooledRenderer> registerPooled(key: Any, fn: () -> T): T
}

abstract class Shader<B : Shader.Buffer>(val id: ShaderId) {
    open fun createRenderer(surface: Surface, renderContext: RenderContext): Renderer<B> {
        return createRenderer(surface)
    }

    abstract fun createRenderer(surface: Surface): Renderer<B>

    abstract fun createBuffer(surface: Surface): B

    val descriptorBytes: ByteArray by lazy { toBytes() }

    fun serialize(writer: ByteArrayWriter) {
        writer.writeByte(id.ordinal.toByte())
        serializeConfig(writer)
    }

    /** Override if your shader has static configuration that needs to be shared with the Renderer. */
    open fun serializeConfig(writer: ByteArrayWriter) {
    }

    private fun toBytes(): ByteArray {
        val writer = ByteArrayWriter()
        serialize(writer)
        return writer.toBytes()
    }

    abstract fun readBuffer(reader: ByteArrayReader): B

    companion object {
        fun parse(reader: ByteArrayReader): Shader<*> {
            val shaderTypeI = reader.readByte()
            val shaderType = ShaderId.get(shaderTypeI)
            return shaderType.reader.parse(reader)
        }
    }

    interface Buffer {
        val shader: Shader<*>

        fun serialize(writer: ByteArrayWriter)

        /**
         * Read new data into an existing buffer, as efficiently as possible.
         */
        fun read(reader: ByteArrayReader)
    }

    interface Renderer<B : Buffer> {
        fun beginFrame(buffer: B, pixelCount: Int) {}
        fun draw(buffer: B, pixelIndex: Int): Color
        fun endFrame() {}
        fun release() {}
    }
}

/**
 * If a [Shader.Renderer] implements [PooledRenderer] and pixel prerendering is enabled on Pinky,
 * then the drawing cycle will look like this:
 *
 * - shader.createRenderer()
 * - rendererN*.beginFrame()
 * - pooledRenderer.preDraw()
 * - rendererN*.draw()
 * - rendererN*.endFrame()
 */
interface PooledRenderer {
    fun preDraw()
}

interface Pixels : Iterable<Color> {
    val size: Int

    val indices: IntRange
        get() = IntRange(0, size - 1)

    operator fun get(i: Int): Color
    operator fun set(i: Int, color: Color)

    fun set(colors: Array<Color>)

    fun finishedFrame() {}

    override fun iterator(): Iterator<Color> {
        return object : Iterator<Color> {
            private var i = 0

            override fun hasNext(): Boolean = i < size

            override fun next(): Color = get(i++)
        }
    }
}