#extension GL_EXT_gpu_shader4 : enable

#if __VERSION__ >= 130
  #define varying in
  out vec4 mgl_FragColor;
  #define texture2D texture
  #define gl_FragColor mgl_FragColor
#endif

#ifdef GL_ES 
precision mediump float; 
precision mediump int; 
#endif 

uniform  vec4   uniform_Color;

varying vec3	lightIntensity ;


void main (void) 
{
	gl_FragColor = vec4(lightIntensity,1.0) * uniform_Color;
} ;
