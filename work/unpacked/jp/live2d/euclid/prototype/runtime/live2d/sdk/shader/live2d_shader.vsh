//---- Vertex Shader ---

attribute vec4   a_position; 
attribute vec2   a_texCoord;

uniform   mat4   modelViewProjectionMatrix;
uniform   float  u_z;

varying   vec2   v_texCoord;  
  
void main()  
{  
	vec4 tmp = modelViewProjectionMatrix * a_position; 
	tmp.z = u_z ;  
//	tmp.z = tmp.z + u_z - u_z ;  
	
	gl_Position = tmp ;  
	//gl_Position = modelViewProjectionMatrix * a_position;  
	
	v_texCoord = a_texCoord;
	  
}
  