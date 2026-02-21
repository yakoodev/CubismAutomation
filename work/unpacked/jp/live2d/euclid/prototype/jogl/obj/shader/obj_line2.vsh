attribute vec3		a_position;
attribute vec3	 	a_normal;

uniform   mat4 		u_matrixMVP;
uniform   bool 		u_isEdge;
uniform   float		u_lineWidth;

varying   vec3 		v_normal;

void main(void){
    vec3 pos    = a_position;
    if(u_isEdge){
    	vec3 tmp = u_matrixMVP * vec4(pos, 1.0) ;
    	
        pos    += a_normal * u_lineWidth * pow( tmp.z , 0.5 ) ;
    }
    v_normal     = a_normal;
//    vColor      = color;
    gl_Position = u_matrixMVP * vec4(pos, 1.0);
}
