package baaahs.sim.ui

import baaahs.Brain
import baaahs.SheepSimulator
import baaahs.show.PatchSet
import baaahs.ui.BComponent
import baaahs.ui.Observable
import baaahs.ui.Observer
import baaahs.ui.SimulatorStyles
import baaahs.ui.SimulatorStyles.console
import materialui.components.typography.typographyH6
import react.RBuilder
import react.RProps
import react.RState
import react.dom.b
import react.dom.div
import react.dom.hr
import react.dom.pre
import react.setState
import styled.css
import styled.styledDiv

class Console(props: ConsoleProps) : BComponent<ConsoleProps, ConsoleState>(props), Observer {
    override fun observing(props: ConsoleProps, state: ConsoleState): List<Observable?> {
        return listOf(props.simulator, props.simulator.pinky.stageManager)
    }

    private fun brainSelectionListener(brain: Brain.Facade) =
        setState { selectedBrain = brain }

    override fun RBuilder.render() {
        val simulator = props.simulator

        styledDiv {
            css { +console }

            networkPanel {
                attrs.network = simulator.network
            }

            frameratePanel {
                attrs.pinkyFramerate = simulator.pinky.framerate
                attrs.visualizerFramerate = simulator.visualizer.framerate
            }

            pinkyPanel {
                attrs.pinky = simulator.pinky
            }

            styledDiv {
                css { +SimulatorStyles.section }
                b { +"Brains:" }
                div {
                    simulator.brains.forEach { brain ->
                         brainIndicator {
                             attrs.brain = brain
                             attrs.brainSelectionListener = ::brainSelectionListener
                         }
                    }
                }
                div {
                    val selectedBrain = state.selectedBrain
                    if (selectedBrain != null) {
                        hr {}
                        b { +"Brain ${selectedBrain.id}" }
                        div { +"Surface: ${selectedBrain.surface.describe()}" }
                    }
                }

                div {
                    simulator.visualizer.selectedSurface?.let {
                        +"Selected: ${it.name}"
                    }
                }
            }

            styledDiv {
                css { +SimulatorStyles.section }
                b { +"GLSL:" }

                simulator.pinky.stageManager.currentGlsl?.forEach { (surfaces, glsl) ->
                    typographyH6 { +surfaces.name }
                    pre { +glsl }
                }
            }
        }
    }
}

external interface ConsoleProps : RProps {
    var simulator: SheepSimulator.Facade
}

external interface ConsoleState : RState {
    var selectedPatchSet: PatchSet
    var selectedBrain: Brain.Facade?
}