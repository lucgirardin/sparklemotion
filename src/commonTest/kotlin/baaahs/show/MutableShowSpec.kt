package baaahs.show

import baaahs.ShowState
import baaahs.gl.override
import baaahs.gl.patch.AutoWirer
import baaahs.glsl.Shaders.cylindricalProjection
import baaahs.plugin.Plugins
import baaahs.show.mutable.MutableButtonGroupControl
import baaahs.show.mutable.MutablePatch
import baaahs.show.mutable.MutableShow
import baaahs.show.mutable.ShowBuilder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.expect

object MutableShowSpec : Spek({
    describe("MutableShow") {
        val autoWirer by value { AutoWirer(Plugins.safe()) }

        val shader1a by value { autoWirer.testPatch("shader 1a") }
        val shader2a by value { autoWirer.testPatch("shader 2a") }

        val baseMutableShow by value {
            MutableShow("test show").apply {
                addScene("scene 1") {
                    addPatchSet("patchset 1a") { addPatch(shader1a) }
                }
                addScene("scene 2") {
                    addPatchSet("patchset 2a") { addPatch(shader2a) }
                    addPatchSet("patchset 2b") { addPatch(autoWirer.testPatch("shader 2b")) }
                    addPatchSet("patchset 2c") { addPatch(autoWirer.testPatch("shader 2c")) }
                }
                addControl("Scenes", MutableButtonGroupControl("Scenes"))
            }
        }
        val baseShow by value { baseMutableShow.build(ShowBuilder()) }
        fun Show.showState() = ShowState.forShow(this).selectScene(1).selectPatchSet(1)
        val baseShowState by value { baseShow.showState() }
        val mutableShow by value { MutableShow(baseShow, baseShowState) }
        val show by value { mutableShow.build(ShowBuilder()) }
        val showState by value { mutableShow.getShowState() }

        context("base show") {
            it("has the expected initial scenes and patchsets") {
                expect(
                    listOf("scene 1 (patchset 1a)", "scene 2 (patchset 2a, patchset 2b, patchset 2c)")
                ) { show.desc() }
            }

            it("has the expected initial shaders") {
                expect(
                    setOf("Cylindrical Projection", "shader 1a", "shader 2a", "shader 2b", "shader 2c")
                ) { show.shaders.values.map { it.title }.toSet() }
            }

            it("has the expected initial datasources") {
                expect(
                    setOf("Pixel Coordinates Texture", "Model Info", "Time", "Resolution", "Blueness Slider")
                ) { show.dataSources.values.map { it.dataSourceName }.toSet() }
            }

            it("has the expected initial state") {
                expect(ShowState(1, listOf(0, 1))) { showState }
            }
        }

        it("leaves everything as-is if no changes are made") {
            expect(show) { baseShow }
            expect(showState) { baseShowState }
        }

        context("adding a patchset") {
            beforeEachTest {
                mutableShow.apply {
                    editScene(1) {
                        addPatchSet("patchset 2b") { addPatch(autoWirer.testPatch("shader 2b")) }
                    }
                }
            }

            it("collects shaders") {
                expect(
                    setOf("Cylindrical Projection", "shader 1a", "shader 2a", "shader 2b", "shader 2c")
                ) { show.shaders.values.map { it.title }.toSet() }
            }
        }

        context("editing a patchset") {
            beforeEachTest {
                mutableShow.editScene(1) {
                    editPatchSet(1) { title = "modified $title" }
                }
            }

            it("applies changes") {
                expect(
                    listOf("scene 1 (patchset 1a)", "scene 2 (patchset 2a, modified patchset 2b, patchset 2c)")
                ) { show.desc() }
            }
        }

        context("reordering scenes") {
            val fromIndex = 1
            val toIndex = 0

            beforeEachTest {
                baseMutableShow.apply {
                    addScene("scene 3") {
                        addPatchSet("patchset 3a") { addPatch(autoWirer.testPatch("shader 3a")) }
                    }
                }

                mutableShow.moveScene(fromIndex, toIndex)
            }

            it("reorders scenes") {
                expect(
                    listOf(
                        "scene 2 (patchset 2a, patchset 2b, patchset 2c)",
                        "scene 1 (patchset 1a)",
                        "scene 3 (patchset 3a)"
                    )
                ) { show.desc() }
            }

            it("reorders patchset selections in show state") {
                expect(listOf(1, 0, 0)) { showState.patchSetSelections }
            }

            context("if the 'from' scene was selected for that scene") {
                override(baseShowState) { baseShow.showState().selectScene(fromIndex) }
                it("keeps the same scene selected") {
                    expect(ShowState(toIndex, listOf(1, 0, 0))) { showState }
                }
            }

            context("if the 'to' scene was selected for that scene") {
                override(baseShowState) { baseShow.showState().selectScene(toIndex) }
                it("keeps the same scene selected") {
                    expect(ShowState(fromIndex, listOf(1, 0, 0))) { showState }
                }
            }

            context("if some other scene was selected") {
                override(baseShowState) { baseShow.showState().selectScene(2) }
                it("doesn't update show state") {
                    expect(ShowState(2, listOf(1, 0, 0))) { showState }
                }
            }

        }

        context("reordering patchsets") {
            val fromIndex = 1
            val toIndex = 2

            override(baseShowState) { ShowState(1, listOf(2, 0)) }

            beforeEachTest {
                mutableShow.apply {
                    editScene(1) { movePatchSet(fromIndex, toIndex) }
                }
            }

            it("reorders patchsets") {
                expect(
                    listOf("scene 1 (patchset 1a)", "scene 2 (patchset 2a, patchset 2c, patchset 2b)")
                ) { show.desc() }
            }

            context("if the 'from' patchset was selected for that scene") {
                override(baseShowState) { baseShow.showState().selectPatchSet(fromIndex) }
                it("keeps the same patchset selected") {
                    expect(ShowState(1, listOf(0, toIndex))) { showState }
                }
            }

            context("if the 'to' patchset was selected for that scene") {
                override(baseShowState) { baseShow.showState().selectPatchSet(toIndex) }
                it("keeps the same patchset selected") {
                    expect(ShowState(1, listOf(0, fromIndex))) { showState }
                }
            }

            context("if some other patchset was selected") {
                override(baseShowState) { baseShow.showState().selectPatchSet(0) }
                it("doesn't update show state") {
                    expect(ShowState(1, listOf(0, 0))) { showState }
                }
            }
        }

        context("editing MutablePatchHolders") {
            it("adds to existing patch for the given surface, if it exists") {
                mutableShow.addPatch(autoWirer.testPatch("show shader 1a"))
                mutableShow.addPatch(autoWirer.testPatch("show shader 1b"))

                expect(
                    mapOf(Surfaces.AllSurfaces to listOf(
                        "Cylindrical Projection", "show shader 1a",
                        "Cylindrical Projection", "show shader 1b"
                    ))
                ) {
                    show.patches.map { patch ->
                        patch.surfaces to
                                patch.shaderInstanceIds.map {
                                    show.shaderInstances[it]?.shaderId?.let { shaderId ->
                                        show.shaders[shaderId]?.title
                                    } ?: "?!?"
                                }
                    }.associate { it }
                }
            }
        }
    }
})

private fun AutoWirer.testPatch(title: String): MutablePatch {
    val shader = Shader(
        title, ShaderType.Paint, """
            // $title
            uniform float time;
            uniform vec2  resolution;
            uniform float blueness;
            int someGlobalVar;
            const int someConstVar = 123;
            
            int anotherFunc(int i) { return i; }
            
            void main( void ) {
                vec2 uv = gl_FragCoord.xy / resolution.xy;
                someGlobalVar = anotherFunc(someConstVar);
                gl_FragColor = vec4(uv.xy, blueness, 1.);
            }
        """.trimIndent()
    )

    return autoWire(cylindricalProjection, shader)
        .acceptSymbolicChannelLinks()
        .resolve()
}

private fun Show.desc(): List<String> =
    scenes.map { "${it.title} (${it.patchSets.joinToString(", ") { it.title }})" }