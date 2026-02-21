attribute vec3		a_position;
attribute vec3	 	a_normal;
attribute vec2   	a_texCoord;

uniform   mat4 		u_matrixMVP;
uniform   bool 		u_isEdge;
uniform   float		u_lineWidth;

varying   vec3 		v_normal;
varying   vec2  	v_texCoord;  

void main(void){
    vec3 pos    = a_position;
    if(u_isEdge){
    	vec3 tmp = u_matrixMVP * vec4(pos, 1.0) ;
    	
        pos    += a_normal * u_lineWidth * pow( tmp.z , 0.5 ) ;
    }
    v_normal     = a_normal;
    gl_Position = u_matrixMVP * vec4(pos, 1.0);
	v_texCoord = a_texCoord;
}
