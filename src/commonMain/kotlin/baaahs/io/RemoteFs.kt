package baaahs.io

import baaahs.PubSub
import baaahs.sim.FakeFs
import baaahs.sim.MergedFs
import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.modules.SerialModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass

@Serializable
data class RemoteFs(
    override val name: String,
    internal val fsId: Int,
    private val remoteFsBackend: RemoteFsBackend
) : Fs by remoteFsBackend

class FsServerSideSerializer : KSerializer<Fs>, RemoteFsSerializer {
    private val fses = mutableListOf<Fs>()

    override val descriptor: SerialDescriptor =
        SerialDescriptor("baaahs.io.Fs", StructureKind.CLASS) {
            element("name", String.serializer().descriptor)
            element("fsId", Int.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): Fs {
        return fses[decoder.decodeInt()]
    }

    override fun serialize(encoder: Encoder, value: Fs) {
        var fsId = fses.indexOf(value)
        if (fsId == -1) {
            fsId = fses.size
            fses.add(value)
        }

        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name)
            encodeIntElement(descriptor, 1, fsId)
        }

    }

    override val serialModule: SerialModule = SerializersModule {
        knownFsClasses.forEach {
            @Suppress("UNCHECKED_CAST")
            contextual(it as KClass<Fs>, this@FsServerSideSerializer)
        }
    }
}

abstract class FsClientSideSerializer : KSerializer<RemoteFs>, RemoteFsSerializer {
    abstract val backend: RemoteFsBackend

    override val descriptor: SerialDescriptor =
        SerialDescriptor("baaahs.io.Fs", StructureKind.CLASS) {
            element("name", String.serializer().descriptor)
            element("fsId", Int.serializer().descriptor)
        }

    override fun deserialize(decoder: Decoder): RemoteFs {
        return decoder.decodeStructure(descriptor) {
            var name: String? = null
            var fsId: Int? = null
            loop@ while (true) {
                when (val i = decodeElementIndex(descriptor)) {
                    CompositeDecoder.READ_DONE -> break@loop
                    0 -> name = decodeStringElement(descriptor, 0)
                    1 -> fsId = decodeIntElement(descriptor, 1)
                    else -> throw SerializationException("Unknown index $i")
                }
            }
            RemoteFs(
                name ?: throw MissingFieldException("name"),
                fsId ?: throw MissingFieldException("fsId"),
                backend
            )
        }
    }

    override fun serialize(encoder: Encoder, value: RemoteFs) {
        encoder.encodeInt(value.fsId)
    }

    override val serialModule: SerialModule = SerializersModule {
        knownFsClasses.forEach {
            @Suppress("UNCHECKED_CAST")
            contextual(it as KClass<RemoteFs>, this@FsClientSideSerializer)
        }
    }
}

interface RemoteFsSerializer {
    val serialModule: SerialModule

    @Suppress("UNCHECKED_CAST")
    val asSerializer: KSerializer<Fs>
        get() = this as KSerializer<Fs>

    fun createCommandPort(): PubSub.CommandPort<RemoteFsOp, RemoteFsOp.Response> {
        return PubSub.CommandPort(
            "pinky/remoteFs",
            RemoteFsOp.serializer(),
            RemoteFsOp.Response.serializer(),
            SerializersModule {
                polymorphic(RemoteFsOp::class)
                polymorphic(RemoteFsOp.Response::class)
                include(serialModule)
            }
        )
    }
}

interface RemoteFsBackend : Fs

expect val platformFsClasses: Set<KClass<out Fs>>
val knownFsClasses: Set<KClass<out Fs>>
    get() = platformFsClasses + setOf(Fs::class, FakeFs::class, MergedFs::class, RemoteFs::class)
