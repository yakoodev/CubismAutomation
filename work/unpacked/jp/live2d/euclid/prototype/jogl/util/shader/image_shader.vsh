//---- Vertex Shader ---

attribute vec4   a_position; 
attribute vec2   a_texCoord;

uniform   mat4   modelViewProjectionMatrix;
varying   vec2   v_texCoord;  
  
void main()  
{  
	gl_Position = modelViewProjectionMatrix * a_position;  
	
	v_texCoord = a_texCoord;
}
