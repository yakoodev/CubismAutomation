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
uniform  vec4   uniform_Shadow;

varying vec3	lightIntensity ;


void main (void) 
{
	//simple toon
	float sc = length(lightIntensity) ;
	
	// ( 0.8 , 1 ,  0.6, 0 )
//	float blend = (sc-0.6)/(0.8-0.6) ;
//	if( blend < 0 ) blend = 0;
//	if( blend > 1 ) blend = 1 ;
//	gl_FragColor = blend*uniform_Color + (1-blend)*uniform_Shadow ;
	 
	if( sc < 0.8 )	gl_FragColor = uniform_Shadow; 
	else			gl_FragColor = uniform_Color; 
	
//	gl_FragColor = vec4(lightIntensity,1.0) * uniform_Color;
} ;
