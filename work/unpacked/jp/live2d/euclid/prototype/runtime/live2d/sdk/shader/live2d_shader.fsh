precision mediump float; 
varying vec2 v_texCoord; 
uniform sampler2D s_texture0; 
uniform vec4 baseColor; 

uniform float opacity ; 
uniform int   invertPattern ;

mat4 pattern = mat4(
		 0 , 8 , 2 ,10 ,
		12 , 4 ,14 , 6 ,
		 3 ,11 , 1 , 9 ,
		15 , 7 ,13 , 5
	) ;

void main() 
{ 
	int x = int(gl_FragCoord.x ) % 4 ; 
	int y = int(gl_FragCoord.y ) % 4 ; 
	
	int val = 1 + pattern[x][y] ;   			// 0..15 >> 1..16
	int alphaInt = int( (1.0-opacity)*16 + 0.5 ) ; 	// scale & round off 0.5 , 1.5 .. 15.5

	if( invertPattern == 0 ){
		if( val <= alphaInt ) discard ; 
	}else{
		if( 17-val <= alphaInt ) discard ; 
	}
//	if( val <= alphaInt ) discard ; 

	gl_FragColor = texture2D(s_texture0 , v_texCoord) * baseColor; 
} ;
	