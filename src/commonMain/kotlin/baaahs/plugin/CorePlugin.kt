package baaahs.plugin

import baaahs.*
import baaahs.gadgets.ColorPicker
import baaahs.gadgets.RadioButtonStrip
import baaahs.gadgets.Slider
import baaahs.gl.GlContext.Companion.GL_RGB32F
import baaahs.gl.glsl.GlslProgram
import baaahs.gl.glsl.GlslProgram.DataFeed
import baaahs.gl.glsl.GlslType
import baaahs.gl.patch.ContentType
import baaahs.gl.render.ModelRenderer
import baaahs.gl.shader.InputPort
import baaahs.glsl.Uniform
import baaahs.show.DataSource
import baaahs.show.DataSourceBuilder
import baaahs.show.mutable.MutableGadgetControl
import com.danielgergely.kgl.FloatBuffer
import com.danielgergely.kgl.GL_FLOAT
import com.danielgergely.kgl.GL_NEAREST
import com.danielgergely.kgl.GL_RGB
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.float

class CorePlugin : Plugin {
    override val packageName: String = id
    override val title: String = "SparkleMotion Core"

    override fun resolveDataSource(inputPort: InputPort): DataSource {
        val pluginRef = inputPort.pluginRef!!
        val dataSourceBuilder = dataSourceBuildersByName[pluginRef.resourceName]
            ?: error("unknown resource \"${pluginRef.resourceName}\"")
        return dataSourceBuilder.build(inputPort)
    }

    override fun suggestContentTypes(inputPort: InputPort): Collection<ContentType> {
        val glslType = inputPort.type
        val isStream = inputPort.glslVar?.isVarying ?: false
        return contentTypesByGlslType[glslType to isStream] ?: emptyList()
    }

    override fun resolveContentType(type: String): ContentType? {
        return when (type) {
            "color-stream" -> ContentType.ColorStream
            else -> null
        }
    }

    override fun suggestDataSources(
        inputPort: InputPort,
        suggestedContentTypes: Set<ContentType>
    ): List<DataSource> {
        val suggestions = (setOf(inputPort.contentType) + suggestedContentTypes).map {
            supportedContentTypes[it]?.build(inputPort)
        }.filterNotNull()

        return if (suggestions.isNotEmpty()) {
            suggestions
        } else {
            supportedContentTypes.values.map { it.suggestDataSources(inputPort) }.flatten()
        }
    }

    override fun findDataSource(
        resourceName: String,
        inputPort: InputPort
    ): DataSource? {
        val dataSourceBuilder = dataSourceBuildersByName[resourceName]
            ?: error("unknown plugin resource $resourceName")
        return dataSourceBuilder.build(inputPort)
    }


    /**
     * Sparkle Motion always uses a resolution of (1, 1), except for previews, which
     * use [PreviewResolutionDataSource] instead.
     */
    @Serializable
    @SerialName("baaahs.Core:Resolution")
    data class ResolutionDataSource(@Transient val `_`: Boolean = true) : DataSource {
        companion object : DataSourceBuilder<ResolutionDataSource> {
            override val resourceName: String get() = "Resolution"
            override fun build(inputPort: InputPort): ResolutionDataSource =
                ResolutionDataSource()
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "Resolution"
        override fun getType(): GlslType = GlslType.Vec2

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed =
            object : DataFeed, RefCounted by RefCounter() {
                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding =
                    GlslProgram.SingleUniformBinding(glslProgram, this@ResolutionDataSource, id, this) { uniform ->
                        uniform.set(1f, 1f)
                    }
            }
    }

    @Serializable
    @SerialName("baaahs.Core:PreviewResolution")
    data class PreviewResolutionDataSource(@Transient val `_`: Boolean = true) : DataSource {
        companion object : DataSourceBuilder<PreviewResolutionDataSource> {
            override val resourceName: String get() = "Preview Resolution"
            override fun build(inputPort: InputPort): PreviewResolutionDataSource =
                PreviewResolutionDataSource()
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "PreviewResolution"
        override fun getType(): GlslType = GlslType.Vec2

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed =
            object : DataFeed, GlslProgram.ResolutionListener, RefCounted by RefCounter() {
                var x = 1f
                var y = 1f

                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding =
                    GlslProgram.SingleUniformBinding(
                        glslProgram,
                        this@PreviewResolutionDataSource,
                        id,
                        this
                    ) { uniform ->
                        uniform.set(x, y)
                    }

                override fun onResolution(x: Float, y: Float) {
                    this.x = x
                    this.y = y
                }
            }
    }

    @Serializable
    @SerialName("baaahs.Core:Time")
    data class TimeDataSource(@Transient val `_`: Boolean = true) : DataSource {
        companion object : DataSourceBuilder<TimeDataSource> {
            override val resourceName: String get() = "Time"
            override fun build(inputPort: InputPort): TimeDataSource =
                TimeDataSource()
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "Time"
        override fun getType(): GlslType = GlslType.Float

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed =
            object : DataFeed, RefCounted by RefCounter() {
                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding =
                    GlslProgram.SingleUniformBinding(glslProgram, this@TimeDataSource, id, this) { uniform ->
                        val thisTime = (getTimeMillis() and 0x7ffffff).toFloat() / 1000.0f
                        uniform.set(thisTime)
                    }
            }
    }

    @Serializable
    @SerialName("baaahs.Core:PixelCoordsTexture")
    data class PixelCoordsTextureDataSource(@Transient val `_`: Boolean = true) : DataSource {
        companion object : DataSourceBuilder<PixelCoordsTextureDataSource> {
            override val resourceName: String get() = "PixelCoords"
            override fun build(inputPort: InputPort): PixelCoordsTextureDataSource =
                PixelCoordsTextureDataSource()
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "Pixel Coordinates Texture"
        override fun getType(): GlslType = GlslType.Sampler2D
        override fun suggestId(): String = "pixelCoordsTexture"

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed =
            object : DataFeed, ModelRenderer.ArrangementListener, RefCounted by RefCounter() {
                private val gl = showPlayer.glContext
                private val uvCoordTextureUnit = gl.getTextureUnit(PixelCoordsTextureDataSource::class)
                private val uvCoordTexture = gl.check { createTexture() }

                override fun onArrangementChange(arrangement: ModelRenderer.Arrangement) {
                    if (arrangement.pixelCoords.isEmpty()) return

                    val pixWidth = arrangement.pixWidth
                    val pixHeight = arrangement.pixHeight
                    val floatBuffer = FloatBuffer(arrangement.pixelCoords)

                    with(uvCoordTextureUnit) {
                        bindTexture(uvCoordTexture)
                        configure(GL_NEAREST, GL_NEAREST)
                        uploadTexture(0, GL_RGB32F, pixWidth, pixHeight, 0, GL_RGB, GL_FLOAT, floatBuffer)
                    }
                }

                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding =
                    GlslProgram.SingleUniformBinding(glslProgram, this@PixelCoordsTextureDataSource, id, this) { uniform ->
                        uniform.set(uvCoordTextureUnit)
                    }

                override fun onFullRelease() {
                    gl.check { deleteTexture(uvCoordTexture) }
                }
            }
    }

    @Serializable
    @SerialName("baaahs.Core:ScreenUvCoord")
    data class ScreenUvCoordDataSource(@Transient val `_`: Boolean = true) : DataSource {
        companion object : DataSourceBuilder<ScreenUvCoordDataSource> {
            override val resourceName: String get() = "U/V Coordinate"
            override fun build(inputPort: InputPort): ScreenUvCoordDataSource =
                ScreenUvCoordDataSource()
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "U/V Coordinate"
        override fun getType(): GlslType = GlslType.Vec2
        override fun isImplicit(): Boolean = true
        override fun getVarName(id: String): String = "gl_FragCoord"

        override fun getRenderType(): String? = null

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed {
            return object : DataFeed, RefCounted by RefCounter() {

                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding {
                    val dataFeed = this
                    return object : GlslProgram.Binding {
                        override val dataFeed: DataFeed
                            get() = dataFeed
                        override val isValid: Boolean get() = true

                        override fun setOnProgram() {
                            // No-op.
                        }
                    }
                }
            }
        }
    }

    @Serializable
    @SerialName("baaahs.Core:ModelInfo")
    data class ModelInfoDataSource(@Transient val `_`: Boolean = true) : DataSource {
        companion object : DataSourceBuilder<ModelInfoDataSource> {
            override val resourceName: String get() = "Model Info"
            private val modelInfoType = ContentType.ModelInfo.glslType

            // TODO: dataType should be something like "{vec3,vec3}" probably.
            override fun looksValid(inputPort: InputPort): Boolean =
                inputPort.type == modelInfoType || inputPort.contentType == ContentType.ModelInfo

            override fun build(inputPort: InputPort): ModelInfoDataSource =
                ModelInfoDataSource()
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "Model Info"
        override fun getType(): GlslType = modelInfoType
        override fun getRenderType(): String? = null

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed {
            return object : DataFeed, RefCounted by RefCounter() {
                private val varPrefix = getVarName(id)
                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding {
                    val dataFeed = this
                    val modelInfo = showPlayer.modelInfo
                    val center by lazy { modelInfo.center }
                    val extents by lazy { modelInfo.extents }

                    return object : GlslProgram.Binding {
                        val centerUniform = glslProgram.getUniform("${varPrefix}.center")
                        val extentsUniform = glslProgram.getUniform("${varPrefix}.extents")

                        override val dataFeed: DataFeed
                            get() = dataFeed

                        override val isValid: Boolean
                            get() = centerUniform != null && extentsUniform != null

                        override fun setOnProgram() {
                            centerUniform?.set(center)
                            extentsUniform?.set(extents)
                        }
                    }
                }
            }
        }
    }

    interface GadgetDataSource<T : Gadget> : DataSource {
        val title: String

        override fun buildControl(): MutableGadgetControl? {
            return MutableGadgetControl(createGadget(), this)
        }

        fun createGadget(): T

        fun set(gadget: T, uniform: Uniform)

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed {
            val gadget = createGadget()
            showPlayer.createdGadget(id, gadget)
            return object : GadgetDataFeed, RefCounted by RefCounter() {
                override val id: String = id
                override val gadget: Gadget = gadget

                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding {
                    return GlslProgram.SingleUniformBinding(glslProgram, this@GadgetDataSource, id, this) { uniform ->
                        this@GadgetDataSource.set(gadget, uniform)
                    }
                }
            }
        }
    }

    interface GadgetDataFeed : DataFeed {
        val id: String
        val gadget: Gadget
    }

    @Serializable
    @SerialName("baaahs.Core:Slider")
    data class SliderDataSource(
        override val title: String,
        val initialValue: Float,
        val minValue: Float,
        val maxValue: Float,
        val stepValue: Float?
    ) : GadgetDataSource<Slider> {
        companion object : DataSourceBuilder<SliderDataSource> {
            override val resourceName: String get() = "Slider"

            override fun looksValid(inputPort: InputPort): Boolean =
                inputPort.dataTypeIs(GlslType.Float)

            override fun build(inputPort: InputPort): SliderDataSource {
                val config = inputPort.pluginConfig
                return SliderDataSource(
                    inputPort.title,
                    initialValue = config?.get("default")?.float ?: 1f,
                    minValue = config?.get("min")?.float ?: 0f,
                    maxValue = config?.get("max")?.float ?: 1f,
                    stepValue = config?.get("step")?.float
                )
            }
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "$title $resourceName"
        override fun getType(): GlslType = GlslType.Float
        override fun getRenderType(): String? = "Slider"
        override fun suggestId(): String = "$title Slider".camelize()

        override fun createGadget(): Slider =
            Slider(title, initialValue, minValue, maxValue, stepValue)

        override fun set(gadget: Slider, uniform: Uniform) {
            uniform.set(gadget.value)
        }
    }

    @Serializable
    @SerialName("baaahs.Core:XyPad")
    data class XyPadDataSource(
        val title: String,
        val varPrefix: String
    ) : DataSource {
        companion object : DataSourceBuilder<XyPadDataSource> {
            override val resourceName: String get() = "XyPad"

            override fun looksValid(inputPort: InputPort): Boolean =
                inputPort.dataTypeIs(GlslType.Vec2)

            override fun build(inputPort: InputPort): XyPadDataSource =
                XyPadDataSource(inputPort.title, inputPort.suggestVarName())
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "XY Pad"
        override fun getType(): GlslType = GlslType.Vec2
        override fun suggestId(): String = "$title XY Pad".camelize()

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed {
            return object : DataFeed, RefCounted by RefCounter() {
//                val xControl = showPlayer.useGadget<Slider>("${varPrefix}_x")
//                val yControl = showPlayer.useGadget<Slider>("${varPrefix}_y")

                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding {
                    val dataFeed = this
                    return object : GlslProgram.Binding {
                        override val dataFeed: DataFeed
                            get() = dataFeed

                        override val isValid: Boolean
                            get() = false

                        override fun setOnProgram() {
                            //                            uniform.set(xControl.value, yControl.value)
                        }
                    }
                }
            }
        }
    }

    @Serializable
    @SerialName("baaahs.Core:ColorPicker")
    data class ColorPickerDataSource(
        override val title: String,
        val initialValue: Color
    ) : GadgetDataSource<ColorPicker> {
        companion object : DataSourceBuilder<ColorPickerDataSource> {
            override val resourceName: String get() = "ColorPicker"

            override fun looksValid(inputPort: InputPort): Boolean =
                inputPort.dataTypeIs(GlslType.Vec4)

            override fun build(inputPort: InputPort): ColorPickerDataSource {
                val default = inputPort.pluginConfig?.get("default")?.primitive?.contentOrNull

                return ColorPickerDataSource(
                    inputPort.title,
                    initialValue = default?.let { Color.from(it) } ?: Color.WHITE
                )
            }
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "$title ${SliderDataSource.resourceName}"
        override fun getType(): GlslType = GlslType.Vec4
        override fun getRenderType(): String? = "ColorPicker"
        override fun suggestId(): String = "$title Color Picker".camelize()

        override fun createGadget(): ColorPicker = ColorPicker(title, initialValue)

        override fun set(gadget: ColorPicker, uniform: Uniform) {
            val color = gadget.color
//            when (inputPortRef.type) {
//                GlslType.Vec3 -> uniform.set(color.redF, color.greenF, color.blueF)
//                GlslType.Vec4 ->
            uniform.set(color.redF, color.greenF, color.blueF, color.alphaF)
//            }
        }
    }

    @Serializable
    @SerialName("baaahs.Core:RadioButtonStrip")
    data class RadioButtonStripDataSource(
        override val title: String,
        val options: List<String>,
        val initialSelectionIndex: Int
    ) : GadgetDataSource<RadioButtonStrip> {
        companion object : DataSourceBuilder<RadioButtonStripDataSource> {
            override val resourceName: String get() = "Radio Button Strip"

            override fun looksValid(inputPort: InputPort): Boolean =
                inputPort.dataTypeIs(GlslType.Int)

            override fun build(inputPort: InputPort): RadioButtonStripDataSource {
                val config = inputPort.pluginConfig

                val initialSelectionIndex = config?.getPrimitive("default")?.int ?: 0

                val options = config
                    ?.getArrayOrNull("options")
                    ?.map { it.primitive.content }
                    ?: error("no options given")

                return RadioButtonStripDataSource(
                    inputPort.title,
                    options,
                    initialSelectionIndex
                )
            }
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = resourceName
        override fun getType(): GlslType = GlslType.Int

        override fun createGadget(): RadioButtonStrip {
            return RadioButtonStrip(title, options, initialSelectionIndex)
        }

        override fun set(gadget: RadioButtonStrip, uniform: Uniform) {
            TODO("not implemented")
        }
    }

    @Serializable
    @SerialName("baaahs.Core:Image")
    data class ImageDataSource(val title: String) : DataSource {
        companion object : DataSourceBuilder<ImageDataSource> {
            override val resourceName: String get() = "Image"
            override fun looksValid(inputPort: InputPort): Boolean =
                inputPort.dataTypeIs(GlslType.Sampler2D)

            override fun build(inputPort: InputPort): ImageDataSource =
                ImageDataSource(inputPort.title)
        }

        override val pluginPackage: String get() = id
        override val dataSourceName: String get() = "Image"
        override fun getType(): GlslType = GlslType.Sampler2D
        override fun suggestId(): String = "$title Image".camelize()

        override fun createFeed(showPlayer: ShowPlayer, plugin: Plugin, id: String): DataFeed =
            object : DataFeed, RefCounted by RefCounter() {
                override fun bind(glslProgram: GlslProgram): GlslProgram.Binding =
                    GlslProgram.SingleUniformBinding(glslProgram, this@ImageDataSource, id, this) { uniform ->
                        // no-op
                    }
            }
    }

    companion object {
        val id = "baaahs.Core"

        val contentTypesByGlslType =
            ContentType.coreTypes.filter { it.suggest }.groupBy({ it.glslType to it.isStream }, { it })

        val supportedContentTypes = mapOf(
            ContentType.PixelCoordinatesTexture to PixelCoordsTextureDataSource,
            ContentType.UvCoordinateStream to ScreenUvCoordDataSource,
            ContentType.ModelInfo to ModelInfoDataSource,
//            UvCoordinate,
            ContentType.Mouse to XyPadDataSource,
//            XyzCoordinate,
            ContentType.Color to ColorPickerDataSource,
            ContentType.Time to TimeDataSource,
            ContentType.Resolution to ResolutionDataSource,
            ContentType.PreviewResolution to PreviewResolutionDataSource,
            ContentType.Float to SliderDataSource
//            Int,
//            Unknown
        )
        val dataSourceBuildersByName = supportedContentTypes.values.associateBy { it.resourceName }
    }
}
