attribute vec3		a_position;
attribute vec3	 	a_normal;

uniform   mat4 		u_matrixMVP;
uniform   bool 		u_isEdge;
uniform   float		u_lineWidth;
uniform   float		u_digitalZoomInv ;//デジタルズーム(scale2d)の逆数

varying   vec3 		v_normal;

void main(void){
    vec3 pos    = a_position;
    if(u_isEdge){
    	
    	vec3 tmp = u_matrixMVP * vec4(pos, 1.0) ;
    	
    	// -- 線幅の調整 --
    	// 透視除算前の z 値は0..以上の通常の m 距離値
    	// 距離が離れるほど、線が細くなりすぎる。
    	// 距離に応じて太らせたいが、距離２倍で、線幅も２倍だと一定の幅を保つが、
    	// 一定の幅だと逆に太く感じるため、距離のルートをとって徐々に細っていくようにする
    	
    	// 一方で、デジタルズーム的な拡大をした場合は、距離 tmp.z は変わらないまま
    	// 絵が大きくなる。その場合も線幅が意図せずに太ってしまう。
    	// （離れて太らせた分をキャンセルしないまま拡大してしまうため）
    	// よって、Zoomの分を距離値から割ってやれば良い。
        pos    += a_normal * u_lineWidth * pow( tmp.z * u_digitalZoomInv , 0.5 ) ;
    }
    v_normal     = a_normal;
//    vColor      = color;
    gl_Position = u_matrixMVP * vec4(pos, 1.0);
}
