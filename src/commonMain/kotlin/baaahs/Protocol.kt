package baaahs

interface Ports {
    companion object {
        val MAPPER = 8001
        val PINKY = 8002
        val BRAIN = 8003
    }
}

enum class Type {
    BRAIN_HELLO,
    BRAIN_PANEL_SHADE,
    MAPPER_HELLO,
    PINKY_PONG
}

fun parse(bytes: ByteArray): Message {
    val reader = ByteArrayReader(bytes)
    return when (Type.values()[reader.readByte().toInt()]) {
        Type.BRAIN_HELLO -> BrainHelloMessage()
        Type.BRAIN_PANEL_SHADE -> BrainShaderMessage.parse(reader)
        Type.MAPPER_HELLO -> MapperHelloMessage()
        Type.PINKY_PONG -> PinkyPongMessage.parse(reader)
    }
}

class BrainHelloMessage : Message(Type.BRAIN_HELLO)

class BrainShaderMessage(val color: Color) : Message(Type.BRAIN_PANEL_SHADE) {
    companion object {
        fun parse(reader: ByteArrayReader) = BrainShaderMessage(Color.parse(reader))
    }
    override fun serialize(writer: ByteArrayWriter) {
        color.serialize(writer)
    }
}

class MapperHelloMessage : Message(Type.MAPPER_HELLO)

class PinkyPongMessage(val brainIds: List<String>) : Message(Type.PINKY_PONG) {
    companion object {
        fun parse(reader: ByteArrayReader): PinkyPongMessage {
            val brainCount = reader.readInt();
            val brainIds = mutableListOf<String>()
            for (i in 0..brainCount) {
                brainIds.add(reader.readString())
            }
            return PinkyPongMessage(brainIds)
        }
    }

    override fun serialize(writer: ByteArrayWriter) {
        writer.writeInt(brainIds.size)
        brainIds.forEach { writer.writeString(it) }
    }
}

open class Message(val type: Type) {
    fun toBytes(): ByteArray {
        val writer = ByteArrayWriter(1 + size())
        writer.writeByte(type.ordinal.toByte())
        serialize(writer)
        return writer.toBytes()
    }

    open fun serialize(writer: ByteArrayWriter) {
    }

    open fun size(): Int = 127
}