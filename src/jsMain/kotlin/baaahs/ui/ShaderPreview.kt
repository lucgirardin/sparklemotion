package baaahs.ui

import baaahs.app.ui.appContext
import baaahs.gl.GlBase
import baaahs.gl.GlContext
import baaahs.gl.preview.GadgetAdjuster
import baaahs.gl.preview.PreviewShaderBuilder
import baaahs.gl.render.QuadPreview
import baaahs.jsx.useResizeListener
import baaahs.show.Shader
import external.IntersectionObserver
import kotlinx.css.*
import kotlinx.html.js.onClickFunction
import materialui.Warning
import materialui.components.divider.divider
import materialui.components.popover.enums.PopoverStyle
import materialui.components.popover.popover
import materialui.components.typography.enums.TypographyDisplay
import materialui.components.typography.typography
import materialui.icon
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.EventTarget
import react.*
import react.dom.*
import styled.StyleSheet
import styled.css
import styled.styledDiv
import kotlin.collections.set

val ShaderPreview = xComponent<ShaderPreviewProps>("ShaderPreview") { props ->
    val appContext = useContext(appContext)
    val canvas = ref<HTMLCanvasElement?> { null }
    var gl by state<GlContext?> { null }
    var glslPreview by state<QuadPreview?> { null }
    var errorPopupAnchor by state<EventTarget?> { null }
    val preRenderHook = ref { {} }

    val width = props.width ?: 150.px
    val height = props.height ?: 150.px

    onMount(canvas.current) {
        val canvasEl = canvas.current ?: return@onMount
        val glslContext = GlBase.jsManager.createContext(canvasEl)
        gl = glslContext

        val preview = QuadPreview(glslContext, canvasEl.width, canvasEl.height) {
            preRenderHook.current()
        }

        val intersectionObserver = IntersectionObserver { entries ->
            if (entries.any { it.isIntersecting }) {
                preview.start()
            } else {
                preview.stop()
            }
        }
        intersectionObserver.observe(canvasEl)

        glslPreview = preview

        withCleanup {
            intersectionObserver.disconnect()
            preview.destroy()
        }
    }

    val builder = memo(gl, props.shader, props.previewShaderBuilder) {
        gl?.let { gl ->
            props.previewShaderBuilder
                ?: PreviewShaderBuilder(props.shader!!, appContext.autoWirer)
        }
    }

    onChange("different builder", gl, glslPreview, builder) {
        if (gl == null) return@onChange
        if (glslPreview == null) return@onChange
        if (builder == null) return@onChange

        val observer = builder.addObserver {
            when (it.state) {
                PreviewShaderBuilder.State.Linked -> {
                    it.startCompile(gl!!)
                }
                PreviewShaderBuilder.State.Success -> {
                    val gadgetAdjuster =
                        GadgetAdjuster(builder.gadgets, appContext.clock)
                    preRenderHook.current = { gadgetAdjuster.adjustGadgets() }

                    glslPreview!!.setProgram(it.glslProgram!!)
                }
                else -> {
                }
            }
            forceRender()
        }
        withCleanup { observer.remove() }

        if (builder.state == PreviewShaderBuilder.State.Unbuilt) {
            builder.startBuilding()
        }
    }

    useResizeListener(canvas) {
        // Tell Kotlin controller the window was resized
        glslPreview?.resize(canvas.current!!.width, canvas.current!!.height)
    }

    styledDiv {
        css { +ShaderPreviewStyles.container }
        css.width = width
        css.height = height
        css.position = Position.relative

        canvas {
            ref = canvas
            attrs.width = width.toString()
            attrs.height = height.toString()
        }

        println("${builder?.shader?.title} is ${builder?.state}")
        when (builder?.state ?: PreviewShaderBuilder.State.Unbuilt) {
            PreviewShaderBuilder.State.Unbuilt,
            PreviewShaderBuilder.State.Linking,
            PreviewShaderBuilder.State.Linked,
            PreviewShaderBuilder.State.Compiling -> {
                div { +"Building..." }
            }

            PreviewShaderBuilder.State.Success -> {
            }

            PreviewShaderBuilder.State.Errors -> {
                div(+ShaderPreviewStyles.errorBox) {
                    attrs.onClickFunction = { event ->
                        errorPopupAnchor = event.currentTarget
                        event.stopPropagation()
                    }

                    icon(Warning)
                    typography {
                        attrs.display = TypographyDisplay.block
                        +"Preview failed."
                    }
                }

                popover(ShaderPreviewStyles.errorPopup on PopoverStyle.paper) {
                    attrs.open = errorPopupAnchor != null
                    attrs.anchorEl(errorPopupAnchor)
                    attrs.onClose = { event, _ ->
                        errorPopupAnchor = null
                        event.stopPropagation()
                    }

                    header { +"Errors:" }

                    div {
                        if (errorPopupAnchor != null) {
                            pre(+ShaderPreviewStyles.errorMessage) {
                                +(builder?.glslErrors?.joinToString("\n") ?: "No errors!?")
                            }

                            divider {}

                            pre(+ShaderPreviewStyles.errorSourceCode) {
                                (builder?.linkedPatch?.toFullGlsl("x") ?: "No source!?")
                                    .split("\n")
                                    .forEach { code { +it }; +"\n" }
                            }
                        }
                    }
                }
            }
        }
    }
}

object ShaderPreviewStyles : StyleSheet("ui-ShaderPreview", isStatic = true) {
    val container by css {
        position = Position.relative

        child("canvas") {
            position = Position.absolute
        }
        child("div") {
            position = Position.absolute
            width = 100.pct
            height = 100.pct
        }
    }

    val errorBox by css {
        backgroundColor = Color("#ccaaaacc")
        display = Display.flex
        alignItems = Align.center
        justifyContent = JustifyContent.center
        flexDirection = FlexDirection.column
        padding(1.em)
    }

    val errorPopup by css {
        child("div") {
            padding(0.em, 1.em)
        }
    }

    val errorMessage by css {
    }

    val errorSourceCode by css {
        maxHeight = 30.vh
        overflow = Overflow.scroll

        declarations["counter-reset"] = "line"

        child("code") {
            before {
                declarations["counter-increment"] = "line"
                declarations["content"] = "counter(line)"
                declarations["-webkit-user-select"] = "none"
                marginRight = 1.em
                borderRight = "1px solid black"
                paddingRight = 1.em
                width = 4.em
                display = Display.inlineBlock
                textAlign = TextAlign.right
            }
        }
    }
}

external interface ShaderPreviewProps : RProps {
    var shader: Shader?
    var previewShaderBuilder: PreviewShaderBuilder?
    var width: LinearDimension?
    var height: LinearDimension?
}

fun RBuilder.shaderPreview(handler: RHandler<ShaderPreviewProps>) =
    child(ShaderPreview, handler = handler)