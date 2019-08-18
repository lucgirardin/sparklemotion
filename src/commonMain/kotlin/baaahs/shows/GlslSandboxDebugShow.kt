package baaahs.shows

object GlslSandboxDebugShow : GlslShow("GLSL Hue Test Pattern") {

    override val program = """
/*
 * Original shader from: https://www.shadertoy.com/view/wt2GWK
 */

#ifdef GL_ES
precision mediump float;
#endif

// glslsandbox uniforms
uniform float time;
uniform vec2 resolution;

// shadertoy emulation
#define iTime time
#define iResolution resolution

#define t iTime

void main(void)
{

    gl_FragColor = vec4(gl_FragCoord.x, gl_FragCoord.y, 0.0, 1.0);

}




"""

}
