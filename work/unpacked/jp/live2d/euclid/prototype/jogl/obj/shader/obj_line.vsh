#if __VERSION__ >= 130
  #define attribute in 
  #define varying out 
#endif

#ifdef GL_ES
precision mediump float;
precision mediump int;
#endif

//uniform mat4    u_projection;	// local to view (usual matrix)
uniform mat4    u_localToWorld;	//
uniform mat4    u_worldToView;	// world to view
uniform mat4    u_worldToCameraRotate;// world to camera の回転行列 (not view)
uniform vec4    u_eyepos;		// onWorld coordinate

uniform float	u_lineWidth ;	

attribute vec3  a_vertex;
attribute vec3  a_normal;



varying vec3	normalOnCamera ;// 頂点の法線をカメラ座標で表現
   
void main(void)
{
	// Line付けの原理
	// 法線方向にポリゴンを広げる。広げる量は絵として見せたい線幅を想定する必要がある。
	// よって、ワールド座標上に変換した頂点 v , 法線 n , カメラの視点 e とすると、
	// a = ev * n , n' = unit(a * ev) ,の n'を使って幅を広げる
	
	// 距離に応じて線幅が細くなって良い場合は
	// カメラを考慮せずに、法線n' 方向に、一定量 strokeW 分広げる

	// 距離の影響を受けずに一定幅にしたい場合は、
	// カメラとの距離に反比例する形で法線n'を広げる割合を上げる
	
	// 法線n'を verying 変数に設定する。
	// すると、３角パッチ内の位置に応じて法線n'が補間されて、フラグメントシェーダに入る
	// 補間された結果の向きがカメラに対して90度以上（内積が負）の場合だけ、
	// 線の色で描画する。
	
	// 表面は普通に描いて良い。カリングしなくても一応問題はないと思われる。

	vec4 v_world = u_localToWorld * vec4(a_vertex,1.0) ;
	vec4 n_world = normalize( u_localToWorld * vec4( a_normal,1.0 ) ) ;
	vec4 dir = v_world - u_eyepos ;
	vec3 nn = cross( dir , n_world ) ;//vec4->vec3 で.xyzを使う？
	vec3 n_x_camera = cross( nn , dir ) ;// カメラ方向と直交する法線
	vec3 newV_onWorld = ( v_world + u_lineWidth * n_x_camera ) ; 

	normalOnCamera = u_worldToCameraRotate * vec4( n_world
	gl_Position = u_worldToView * vec4(newV_onWorld,1.0);
//	gl_Position = u_projection * vec4(a_vertex,1.0);
} ;
