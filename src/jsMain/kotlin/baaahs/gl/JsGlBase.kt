package baaahs.gl

import com.danielgergely.kgl.Kgl
import com.danielgergely.kgl.KglJs
import com.danielgergely.kgl.WebGL2RenderingContext
import org.w3c.dom.HTMLCanvasElement
import kotlin.browser.document
import kotlin.browser.window

actual object GlBase {
    actual val manager: GlManager by lazy { jsManager }
    val jsManager: JsGlManager by lazy { JsGlManager() }

    class JsGlManager : GlManager() {
        override val available: Boolean by lazy {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            val gl = canvas.getContext("webgl")
            gl != null
        }

        override fun createContext(): GlContext {
            val canvas = document.createElement("canvas") as HTMLCanvasElement
            return createContext(canvas)
        }

        fun createContext(canvas: HTMLCanvasElement): JsGlContext {
            val gl = canvas.getContext("webgl2") as WebGL2RenderingContext?
            if (gl == null) {
                window.alert(
                    "Running GLSL shows on iOS requires WebGL 2.0.\n" +
                            "\n" +
                            "Go to Settings → Safari → Advanced → Experimental Features and enable WebGL 2.0."
                )
                throw Exception("WebGL 2 not supported")
            }
            return JsGlContext(KglJs(gl), "300 es")
        }
    }

    class JsGlContext(kgl: Kgl, glslVersion: String) : GlContext(kgl, glslVersion) {
        override fun <T> runInContext(fn: () -> T): T = fn()
    }
}
