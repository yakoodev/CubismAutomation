precision mediump float;

uniform mat4    	u_matrixInv;
uniform vec3    	u_lightDir;

uniform vec4 		u_mainColor;
uniform vec4 		u_shadowColor;
uniform vec4    	u_edgeColor;
uniform bool 		u_isEdge;

varying vec3    	v_normal;		


void main(void){
    if(u_isEdge){
        gl_FragColor   = u_edgeColor;
    }else{
        vec3  invLight = normalize(u_matrixInv * vec4(u_lightDir, 0.0)).xyz;
        float diffuse  = clamp(dot(v_normal, invLight), 0.0, 1.0);
		vec4 color ;
		
//		if( invLight.z > 0.0 ){
		if( diffuse < 0.01 ){
			color = u_shadowColor ;
		}
		else{
			color = u_mainColor ; 
		}
		
        gl_FragColor   = color ;
///        gl_FragColor   = vColor * smpColor;
    }
}
