#if __VERSION__ >= 130
  #define attribute in 
  #define varying out 
#endif

#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

uniform mat4    uniform_Projection;
attribute vec3  attribute_Position;
attribute vec3  attribute_Normal;

varying vec3	lightIntensity ;
                       
void main(void)
{
	// dummy & simple impl of shading
	vec3 tnorm = normalize( attribute_Normal ) ;
	vec4 eyeCoords = vec4( attribute_Position , 1.0 ) ;
	vec3 s = normalize( vec3( -1.0 , 1.0 , 1.0 ) ) ;
	vec3 s2 = normalize( vec3( 1.0 , 1.0 , -1.0 ) ) ;
	
	lightIntensity = 
		vec3(0.3) +	// kankyo 
		max( dot( s , tnorm ) , 0.0 ) + // main light
		0.5 * max( dot( s2 , tnorm ) , 0.0 ) ; //back light
	
 	gl_Position = uniform_Projection * vec4(attribute_Position,1.0);
} ;
