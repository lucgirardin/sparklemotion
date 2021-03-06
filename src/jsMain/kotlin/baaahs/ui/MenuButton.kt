package baaahs.ui

import kotlinext.js.jsObject
import kotlinx.html.js.onClickFunction
import materialui.Icon
import materialui.components.button.button
import materialui.components.clickawaylistener.clickAwayListener
import materialui.components.iconbutton.iconButton
import materialui.components.menu.menu
import materialui.components.menuitem.menuItem
import materialui.components.typography.typography
import materialui.icon
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import react.*
import react.dom.div

private val MenuButton = functionalComponent<MenuButtonProps> { props ->
    var anchorEl by useState<Element?> { null }

    val handleButtonClick = useCallback() { event: Event ->
        anchorEl = event.currentTarget as Element
    }

    val handleClickAway = useCallback { event: Event ->
        anchorEl = null
    }

    clickAwayListener {
        attrs { onClickAway = handleClickAway }
        div {
            if (props.icon != null) {
                iconButton {
                    icon(props.icon!!)
                    props.label?.let { typography { +it } }
                    attrs.onClickFunction = handleButtonClick
                }
            } else {
                button {
                    props.label?.let { typography { +it } }
                    attrs.onClickFunction = handleButtonClick
                }
            }

            val items = props.items ?: emptyList()
            menu {
                attrs.getContentAnchorEl = null
                attrs.anchorEl(anchorEl)
                attrs.anchorOrigin = jsObject { horizontal = "left"; vertical = "bottom" }
                attrs.keepMounted = true
                attrs.open = anchorEl != null

                items.forEach { menuItem ->
                    val handleMenuClick = useCallback(menuItem.callback) { event: Event ->
                        anchorEl = null
                        menuItem.callback()
                    }

                    menuItem {
                        +menuItem.name
                        attrs.onClickFunction = handleMenuClick
                    }
                }
            }
        }
    }
}

class MenuItem(val name: String, val callback: () -> Unit)

fun RBuilder.menuButton(handler: RHandler<MenuButtonProps>): ReactElement =
    child(MenuButton, handler = handler)

external interface MenuButtonProps : RProps {
    var label: String?
    var icon: Icon?
    var items: List<MenuItem>?
}