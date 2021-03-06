package baaahs.app.ui

import baaahs.Logger
import baaahs.camelize
import baaahs.util.UniqueIds
import external.DraggableLocation
import external.DropReason
import external.DropResult
import external.ResponderProvided

class DragNDrop {
    private val dropTargets = UniqueIds<DropTarget>()

    fun onDragEnd(dropResult: DropResult, responderProvided: ResponderProvided) {
        if (dropResult.reason == DropReason.DROP.name) {
            val dropSourceLoc = dropResult.source
            val dropDestLoc = dropResult.destination ?: return

            val source = findTarget(dropSourceLoc) ?: return
            val dest = findTarget(dropDestLoc) ?: return
            val sourceIndex = dropSourceLoc.index
            val destIndex = dropDestLoc.index

            if (source == dest) {
                if (sourceIndex != destIndex) {
                    dest.moveDraggable(sourceIndex, destIndex)
                }
            } else {
                val draggable = source.getDraggable(sourceIndex)
                if (dest.willAccept(draggable) && draggable.willMoveTo(dest)) {
                    source.removeDraggable(draggable)
                    dest.insertDraggable(draggable, destIndex)
                    draggable.onMove()
                }
            }
        }
    }

    private fun findTarget(location: DraggableLocation): DropTarget? {
        val dropTarget = dropTargets[location.droppableId]
        if (dropTarget == null) {
            logger.warn { "No such drop target ${location.droppableId}" }
        }
        return dropTarget
    }

    fun addDropTarget(dropTarget: DropTarget): String {
        return dropTargets.idFor(dropTarget) { dropTarget.suggestId() }
    }

    fun removeDropTarget(dropTarget: DropTarget) {
        dropTargets.remove(dropTarget) || throw IllegalStateException("Unregistered drop target.")
    }

    fun removeDropTarget(id: String) {
        dropTargets.removeId(id) || throw IllegalStateException("Unregistered drop target.")
    }

    fun reset() {
        dropTargets.clear()
    }

    companion object {
        private val logger = Logger("DragNDrop")
    }
}

interface DropTarget{
    val type: String

    fun suggestId(): String = type.camelize()
    fun moveDraggable(fromIndex: Int, toIndex: Int)
    fun willAccept(draggable: Draggable): Boolean
    fun getDraggable(index: Int): Draggable
    fun insertDraggable(draggable: Draggable, index: Int)
    fun removeDraggable(draggable: Draggable)
}

interface Draggable {
    fun willMoveTo(destination: DropTarget): Boolean = true
    fun onMove() {}
}