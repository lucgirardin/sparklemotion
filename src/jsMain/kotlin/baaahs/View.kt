package baaahs

import org.w3c.dom.DOMTokenList
import org.w3c.dom.Element
import org.w3c.dom.ItemArrayLike
import kotlin.dom.appendElement
import kotlin.dom.appendText

var Element.disabled: Boolean
    get() = getAttribute("disabled") == "disabled"
    set(value) {
        if (value) {
            setAttribute("disabled", "disabled")
        } else {
            removeAttribute("disabled")
        }
    }

fun <T> ItemArrayLike<T>.forEach(action: (T) -> Unit) {
    for (i in 0 until length) {
        action(item(i)!!)
    }
}

fun DOMTokenList.clear() {
    while (length > 0) {
        remove(item(0)!!)
    }
}

open class Button<T>(val data: T, val element: Element) {
    lateinit var allButtons: List<Button<T>>
    var onSelect: ((T) -> Unit)? = null

    init {
        element.addEventListener("click", { onClick() })
    }

    fun setSelected(isSelected: Boolean) {
        element.classList.toggle("selected", isSelected)
    }

    fun onClick() {
        setSelected(true)
        allButtons.forEach { it.setSelected(false) }
        onSelect?.invoke(data)
    }
}

class ColorPickerView(element: Element, onSelect: (Color) -> Unit) {
    val colors = listOf(Color.WHITE, Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.BLUE, Color.PURPLE)
    private val colorButtons: List<ColorButton>

    init {
        val colorsDiv = element.appendElement("div") {
            className = "colorsDiv"
            appendElement("b") { appendText("Colors: ") }
            appendElement("br") {}
        }


        colorButtons = colors.map { color ->
            ColorButton(color, colorsDiv.appendElement("span") { }).also { button ->
                button.element.setAttribute("style", "background-color: ${button.data.toHexString()}")
                button.onSelect = { onSelect(it) }
            }
        }
        colorButtons.forEach { it.allButtons = colorButtons }
    }

    fun pickRandom() {
        colorButtons.random()!!.onClick()
    }

    fun setColor(color: Color?) {
        for (colorButton in colorButtons) {
            colorButton.setSelected(colorButton.color == color)
        }
    }

    private class ColorButton(val color: Color, element: Element) : Button<Color>(color, element)
}

interface DomContainer {
    fun getFrame(
        name: String,
        element: Element,
        onClose: () -> Unit,
        onResize: (width: Int, height: Int) -> Unit
    ): Frame

    interface Frame {
        @JsName("containerNode")
        val containerNode: Element

        @JsName("close")
        fun close()
    }
}

class FakeDomContainer : DomContainer {
    override fun getFrame(
        name: String,
        content: Element,
        onClose: () -> Unit,
        onResize: (width: Int, height: Int) -> Unit
    ): DomContainer.Frame = js("document.createFakeClientDevice")(name, content, onClose, onResize)
}