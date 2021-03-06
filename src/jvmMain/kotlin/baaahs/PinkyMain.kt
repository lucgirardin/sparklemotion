package baaahs

import baaahs.dmx.DmxDevice
import baaahs.gl.GlBase
import baaahs.gl.render.ModelRenderer
import baaahs.io.RealFs
import baaahs.net.JvmNetwork
import baaahs.plugin.BeatLinkPlugin
import baaahs.plugin.Plugins
import baaahs.proto.Ports
import baaahs.sim.FakeDmxUniverse
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.content.*
import io.ktor.routing.*
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@ObsoleteCoroutinesApi
fun main(args: Array<String>) {
    mainBody(PinkyMain::class.simpleName) {
        PinkyMain(ArgParser(args).parseInto(PinkyMain::Args)).run()
    }
}

@ObsoleteCoroutinesApi
class PinkyMain(private val args: Args) {
    private val logger = Logger("PinkyMain")
    private val pinkyMainDispatcher = newSingleThreadContext("Pinky Main")

    fun run() {
        logger.info { "Are you pondering what I'm pondering?" }

        GlBase.manager // Need to wake up OpenGL on the main thread.

        val model = Pluggables.loadModel(args.model)

        val network = JvmNetwork()
        val dataDir = File(System.getProperty("user.home")).toPath().resolve("sparklemotion/data")
//        Files.createDirectories(dataDir)

        val fwDir = File(System.getProperty("user.home")).toPath().resolve("sparklemotion/fw")

        val fs = RealFs("Sparkle Motion Data", dataDir)

        val dmxUniverse = findDmxUniverse()

        val clock = SystemClock()
        val beatSource = if (args.enableBeatLink) {
            BeatLinkBeatSource(clock).also { it.start() }
        } else {
            BeatSource.None
        }

        val fwUrlBase = "http://${network.link("pinky").myAddress.address.hostAddress}:${Ports.PINKY_UI_TCP}/fw"
        val daddy = DirectoryDaddy(RealFs("Sparkle Motion Firmware", fwDir), fwUrlBase)
        val soundAnalyzer = JvmSoundAnalyzer()
//  TODO      GlslBase.plugins.add(SoundAnalysisPlugin(soundAnalyzer))

        val plugins = Plugins.safe() + BeatLinkPlugin(beatSource, clock)

        val pinky = runBlocking(pinkyMainDispatcher) {
            val glslContext = GlBase.manager.createContext()
            val glslRenderer = ModelRenderer(glslContext, model)
            Pinky(
                model, network, dmxUniverse, beatSource, clock, fs,
                daddy, soundAnalyzer, switchShowAfterIdleSeconds = args.switchShowAfter,
                adjustShowAfterIdleSeconds = args.adjustShowAfter,
                modelRenderer = glslRenderer,
                plugins = plugins,
                pinkyMainDispatcher = pinkyMainDispatcher
            )
        }

        val ktor = (pinky.httpServer as JvmNetwork.RealLink.KtorHttpServer)
        val resource = Pinky::class.java.classLoader.getResource("baaahs")!!
        if (resource.protocol == "jar") {
            val uri = resource.toURI()!!
            FileSystems.newFileSystem(uri, mapOf("create" to "true"))
            val jsResDir = Paths.get(uri).parent.resolve("htdocs")
            testForIndexDotHtml(jsResDir)

            ktor.application.routing {
                static {
                    resources("htdocs")
                    route("admin") { default("htdocs/admin/index.html") }
                    route("mapper") { default("htdocs/mapper/index.html") }
                    route("ui") { default("htdocs/ui/index.html") }
                    defaultResource("htdocs/ui-index.html")
                }
            }
        } else {
            val classPathBaseDir = Paths.get(resource.file).parent
            val repoDir = classPathBaseDir.parent.parent.parent.parent.parent
            val jsResDir = repoDir.resolve("build/processedResources/js/main")
            testForIndexDotHtml(jsResDir)

            ktor.application.routing {
                static {
                    staticRootFolder = jsResDir.toFile()

                    file("sparklemotion.js",
                        repoDir.resolve("build/distributions/sparklemotion.js").toFile())

                    files(jsResDir.toFile())
                    route("admin") { default("admin/index.html") }
                    route("mapper") { default("mapper/index.html") }
                    route("ui") { default("ui/index.html") }
                    default("ui-index.html")
                }
            }
        }

        ktor.application.install(CallLogging)
        ktor.application.routing {
            static("fw") {
                files(fwDir.toFile())
            }
        }

        val responses = listOf(
            "I think so, Brain, but Lederhosen won't stretch that far.",
            "Yeah, but I thought Madonna already had a steady bloke!",
            "I think so, Brain, but what would goats be doing in red leather turbans?",
            "I think so, Brain... but how would we ever determine Sandra Bullock's shoe size?",
            "Yes, Brain, I think so. But how do we get Twiggy to pose with an electric goose?"
        )
        logger.info { responses.random() }

        runBlocking(pinkyMainDispatcher) {
            pinky.startAndRun(simulateBrains = args.simulateBrains)
        }
    }

    private fun testForIndexDotHtml(jsResDir: Path) {
        val indexHtml = jsResDir.resolve("index.html")
        if (!Files.exists(indexHtml)) {
            throw FileNotFoundException("$indexHtml doesn't exist and it really probably should!")
        }
    }

    private fun findDmxUniverse(): Dmx.Universe {
        val dmxDevices = try {
            DmxDevice.listDevices()
        } catch (e: UnsatisfiedLinkError) {
            logger.warn { "DMX driver not found, DMX will be disabled." }
            e.printStackTrace()
            return FakeDmxUniverse()
        }

        if (dmxDevices.isNotEmpty()) {
            if (dmxDevices.size > 1) {
                logger.warn { "Multiple DMX USB devices found, using ${dmxDevices.first()}." }
            }

            return dmxDevices.first()
        }

        logger.warn { "No DMX USB devices found, DMX will be disabled." }
        return FakeDmxUniverse()
    }

    class Args(parser: ArgParser) {
        val model by parser.storing("model").default(Pluggables.defaultModel)

        val showName by parser.storing("show").default<String?>(null)

        val switchShowAfter by parser.storing(
            "Switch show after no input for x seconds",
            transform = { if (isNullOrEmpty()) null else toInt() })
            .default<Int?>(600)

        val adjustShowAfter by parser.storing(
            "Start adjusting show inputs after no input for x seconds",
            transform = { if (isNullOrEmpty()) null else toInt() })
            .default<Int?>(null)

        val enableBeatLink by parser.flagging("Enable beat detection").default(true)

        val simulateBrains by parser.flagging("Simulate connected brains").default(false)
    }
}
