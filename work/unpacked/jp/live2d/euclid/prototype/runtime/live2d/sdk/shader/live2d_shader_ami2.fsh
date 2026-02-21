// 網点でフェードさせる方式
// 
// 多値に対応させるため、アルファ値指定ではなく、上限・下限を指定して、
// 範囲内のみ描くようにする
//
// 100%描きたい場合は min,max =  1,16 
//  50%描きたい場合は min,max = 1,8 または、 9,16 または、 間の値を max-min = 7 となるように選ぶ

precision mediump float; 

varying vec2 v_texCoord; 

uniform sampler2D s_texture0;
uniform vec4 	baseColor; 
uniform mat4 	pattern ;// 4x4の各要素を0..1で指定する

void main() 
{ 
	if( pattern[int(gl_FragCoord.x ) % 4][int(gl_FragCoord.y ) % 4] == 0 ) discard ; 

	gl_FragColor = texture2D(s_texture0 , v_texCoord) * baseColor; 
} ;
