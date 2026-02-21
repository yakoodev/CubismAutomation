precision mediump float;

uniform mat4    	u_matrixInv;
uniform vec3    	u_lightDir;

uniform vec4 		u_mainColor;
uniform vec4 		u_shadowColor;
uniform vec4    	u_edgeColor;
uniform bool 		u_isEdge;

varying vec3    	v_normal;		
varying vec2 		v_texCoord;  

uniform sampler2D 	s_texture0;



void main(void){
    if(u_isEdge){
        gl_FragColor   = u_edgeColor;
    }else{
        vec3  invLight = normalize(u_matrixInv * vec4(u_lightDir, 0.0)).xyz;
        float diffuse  = clamp(dot(v_normal, invLight), 0.0, 1.0);
		vec4 color ;
		
		if( diffuse < 0.01 ){
			color = u_shadowColor ;
		}
		else{
			color = u_mainColor ; 
		}
		
		vec4 texC = texture2D(s_texture0 , v_texCoord) ;
		//texC = mix( texC , color , 1-texC.a ) ;
		texC.rgb = texC.rgb + color.rgb*(1-texC.a) ;
		texC.a = 1 ;
		gl_FragColor = texC ;
		//gl_FragColor = mix( texC , color , texC.a ) ;
		// gl_FragColor = texture2D(s_texture0 , v_texCoord) * color ; 
		
		//gl_FragColor = texture2D(s_texture0 , v_texCoord) * baseColor; 
      	//gl_FragColor   = color ;
    }
}
