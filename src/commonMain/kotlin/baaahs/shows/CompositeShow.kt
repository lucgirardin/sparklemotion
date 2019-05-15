package baaahs.shows

import baaahs.*
import baaahs.shaders.CompositingMode
import baaahs.shaders.CompositorShader
import baaahs.shaders.SineWaveShader
import baaahs.shaders.SolidShader
import kotlin.math.PI
import kotlin.random.Random

val CompositeShow = object : Show.MetaData("Composite") {
    override fun createShow(sheepModel: SheepModel, showRunner: ShowRunner) = object : Show {
        private val colorPicker = showRunner.getColorPicker()

        val solidShader = SolidShader()
        val sineWaveShader = SineWaveShader()
        val compositorShader = CompositorShader(solidShader, sineWaveShader)

        private val shaderBufs = sheepModel.allPanels.map { panel ->
            val solidShaderBuffer = showRunner.getShaderBuffer(panel, solidShader)
            val sineWaveShaderBuffer = showRunner.getShaderBuffer(panel, sineWaveShader).apply {
                density = Random.nextFloat() * 20
            }

            val compositorShaderBuffer =
                showRunner.getCompositorBuffer(panel, solidShaderBuffer, sineWaveShaderBuffer, CompositingMode.ADD)

            ShaderBufs(solidShaderBuffer, sineWaveShaderBuffer, compositorShaderBuffer)
        }

        private val movingHeadBuffers = sheepModel.eyes.map { showRunner.getMovingHead(it) }

        init {
//        println("Created new CompositeShow, we have ${shaderBufs.size} buffers")
        }

        override fun nextFrame() {
            val theta = ((getTimeMillis() % 10000 / 1000f) % (2 * PI)).toFloat()

            var i = 0
            shaderBufs.forEach { shaderBuffer ->
                shaderBuffer.solidShaderBuffer.color = colorPicker.color
                shaderBuffer.sineWaveShaderBuffer.color = Color.WHITE
                shaderBuffer.sineWaveShaderBuffer.theta = theta + i++
                shaderBuffer.compositorShaderBuffer.mode = CompositingMode.ADD
                shaderBuffer.compositorShaderBuffer.fade = 1f
            }

            movingHeadBuffers.forEach { buf ->
                buf.colorWheel = buf.closestColorFor(colorPicker.color)
                buf.pan = PI.toFloat() / 2
                buf.tilt = theta / 2
            }
        }
    }

    inner class ShaderBufs(
        val solidShaderBuffer: SolidShader.Buffer,
        val sineWaveShaderBuffer: SineWaveShader.Buffer,
        val compositorShaderBuffer: CompositorShader.Buffer
    )
}