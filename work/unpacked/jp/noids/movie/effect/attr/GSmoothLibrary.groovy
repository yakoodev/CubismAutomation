//package jp.noids.movie.effect.attr
//
//import java.awt.Color
//import java.awt.Graphics2D
//import java.awt.event.ActionEvent
//import java.awt.event.ActionListener
//
//import jp.noids.framework.property.DoubleEditor
//import jp.noids.framework.property.PropertyEditorPanel
//import jp.noids.graphics.UtDraw
//import jp.noids.movie.effect.attr.value.OriginalSequence
//import jp.noids.movie.ui.timeline.curve.debug.DebugDrawer
//import jp.noids.movie.ui.timeline.curve.debug.DebugTimeLine
//import jp.noids.movie.ui.timeline.curve.debug.main.DebugCurveEditorUI.DrawInfo
//import jp.noids.util.UtDebug
//import jp.noids.value.event.ValueChangeEvent
//import jp.noids.value.event.ValueChangeListener
//
//class GSmoothLibrary implements DebugDrawer {
//	
//	def threasholdScale = 0.05 ;	//補正に使う誤差
//	
//	double[] 	points0 = [ 0,0.02,0.04,0.09,0.17,0.26,0.34,0.43,0.50,0.55,0.58,0.59,0.56,0.50,0.41,0.31,0.20,0.09,0,-0.07,-0.10,-0.10,-0.07,0,0.10,0.21,0.34,0.47,0.59,0.70,0.79,0.84,0.86,0.85,0.80,0.71,0.58,0.43,0.26,0.08,-0.10,-0.28,-0.46,-0.60,-0.71,-0.80,-0.85,-0.85,-0.83,-0.76,-0.67,-0.54,-0.42,-0.28,-0.15,-0.03,0.08,0.17,0.23,0.27,0.29,0.28,0.27,0.27,0.26,0.28,0.30,0.35,0.42,0.50,0.60,0.70,0.81,0.89,0.97,1.00,1.00,1.00,1.00,0.99,0.92,0.84,0.76,0.67,0.59,0.52,0.46,0.43,0.42,0.42,0.45,0.49,0.54,0.61,0.67,0.74,0.80,0.84,0.88,0.90,0.90,0.89,0.87,0.83,0.79,0.76,0.74,0.73,0.72,0.70,0.68,0.63,0.57,0.48,0.37,0.24,0.09,-0.07,-0.25,-0.40,-0.55,-0.68,-0.78,-0.84,-0.85,-0.81,-0.72,-0.60,-0.42,-0.23,0,0.22,0.45,0.65,0.84,0.99,1.00,1.00,1.00,1.00,1.00,1.00,0.95,0.85,0.73,0.62,0.48,0.34,0.20,0.04,-0.12,-0.28,-0.45,-0.60,-0.73,-0.83,-0.90,-0.91,-0.86,-0.76,-0.60,-0.41,-0.22,-0.01,0.18,0.34,0.47,0.57,0.61,0.61,0.57,0.50,0.39,0.25,0.11,-0.03,-0.16,-0.28,-0.36,-0.42,-0.43,-0.41,-0.36,-0.29,-0.21,-0.09,0.04,0.19,0.37,0.55,0.74,0.94,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,0.86,0.72,0.60,0.49,0.41,0.35,0.32,0.31,0.32,0.33,0.35,0.38,0.40,0.41,0.42,0.42,0.41,0.40,0.38,0.37,0.34,0.32,0.30,0.29,0.27,0.27,0.26,0.26,0.27,0.28,0.29,0.31,0.33,0.34,0.36,0.37,0.38,0.38,0.38,0.38,0.37,0.36,0.35,0.34,0.33,0.32,0.31,0.30,0.29,0.29,0.29,0.28,0.27,0.24,0.21,0.16,0.10,0.02,-0.05,-0.11,-0.16,-0.19,-0.18,-0.14,-0.07,0.04,0.17,0.33,0.49,0.66,0.82,0.95,1.00,1.00,1.00,1.00,1.00,1.00,0.92,0.80,0.68,0.56,0.45,0.36,0.30,0.26,0.25,0.26,0.30,0.36,0.43,0.50,0.57,0.62,0.65,0.66,0.65,0.62,0.57,0.51,0.44,0.37,0.30,0.24,0.19,0.16,0.18,0.24,0.30,0.35,0.37,0.35,0.29,0.20,0.08,-0.05,-0.17,-0.29,-0.38,-0.45,-0.50,-0.51,-0.50,-0.46,-0.40,-0.32,-0.22,-0.13,-0.03,0.08,0.17,0.26,0.34,0.41,0.48,0.54,0.59,0.64,0.69,0.74,0.79,0.84,0.89,0.92,0.95,0.97,0.98,0.98,0.96,0.94,0.90,0.87,0.83,0.80,0.76,0.74,0.72,0.71,0.71,0.71,0.73,0.75,0.78,0.80,0.83,0.86,0.88,0.90,0.91,0.92,0.91,0.90,0.89,0.87,0.85,0.82,0.80,0.76,0.73,0.70,0.66,0.63,0.60,0.57,0.55,0.54,0.53 ] ;
//	//double[] 	points1 = [ 0,0.02,0.04,0.09,0.17,0.26,0.34,0.43,0.50,0.55,0.58,0.59,0.56,0.50,0.41,0.31,0.20,0.09,0,-0.07,-0.10,-0.10,-0.07,0,0.10,0.21,0.34,0.47,0.59,0.70,0.79,0.84,0.86,0.85,0.80,0.71,0.58,0.43,0.26,0.08,-0.10,-0.28,-0.46,-0.60,-0.71,-0.80,-0.85,-0.85,-0.83,-0.76,-0.67,-0.54,-0.42,-0.28,-0.15,-0.03,0.08,0.17,0.23,0.27,0.29,0.28,0.27,0.27,0.26,0.28,0.30,0.35,0.42,0.50,0.60,0.70,0.81,0.89,0.97,1.00,1.00,1.00,1.00,0.99,0.92,0.84,0.76,0.67,0.59,0.52,0.46,0.43,0.42,0.42,0.45,0.49,0.54,0.61,0.67,0.74,0.80,0.84,0.88,0.90,0.90,0.89,0.87,0.83,0.79,0.76,0.74,0.73,0.72,0.70,0.68,0.63,0.57,0.48,0.37,0.24,0.09,-0.07,-0.25,-0.40,-0.55,-0.68,-0.78,-0.84,-0.85,-0.81,-0.72,-0.60,-0.42,-0.23,0,0.22,0.45,0.65,0.84,0.99,1.00,1.00,1.00,1.00,1.00,1.00,0.95,0.85,0.73,0.62,0.48,0.34,0.20,0.04,-0.12,-0.28,-0.45,-0.60,-0.73,-0.83,-0.90,-0.91,-0.86,-0.76,-0.60,-0.41,-0.22,-0.01,0.18,0.34,0.47,0.57,0.61,0.61,0.57,0.50,0.39,0.25,0.11,-0.03,-0.16,-0.28,-0.36,-0.42,-0.43,-0.41,-0.36,-0.29,-0.21,-0.09,0.04,0.19,0.37,0.55,0.74,0.94,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,1.00,0.86,0.72,0.60,0.49,0.41,0.35,0.32,0.31,0.32,0.33,0.35,0.38,0.40,0.41,0.42,0.42,0.41,0.40,0.38,0.37,0.34,0.32,0.30,0.29,0.27,0.27,0.26,0.26,0.27,0.28,0.29,0.31,0.33,0.34,0.36,0.37,0.38,0.38,0.38,0.38,0.37,0.36,0.35,0.34,0.33,0.32,0.31,0.30,0.29,0.29,0.29,0.28,0.27,0.24,0.21,0.16,0.10,0.02,-0.05,-0.11,-0.16,-0.19,-0.18,-0.14,-0.07,0.04,0.17,0.33,0.49,0.66,0.82,0.95,1.00,1.00,1.00,1.00,1.00,1.00,0.92,0.80,0.68,0.56,0.45,0.36,0.30,0.26,0.25,0.26,0.30,0.36,0.43,0.50,0.57,0.62,0.65,0.66,0.65,0.62,0.57,0.51,0.44,0.37,0.30,0.24,0.19,0.16,0.18,0.24,0.30,0.35,0.37,0.35,0.29,0.20,0.08,-0.05,-0.17,-0.29,-0.38,-0.45,-0.50,-0.51,-0.50,-0.46,-0.40,-0.32,-0.22,-0.13,-0.03,0.08,0.17,0.26,0.34,0.41,0.48,0.54,0.59,0.64,0.69,0.74,0.79,0.84,0.89,0.92,0.95,0.97,0.98,0.98,0.96,0.94,0.90,0.87,0.83,0.80,0.76,0.74,0.72,0.71,0.71,0.71,0.73,0.75,0.78,0.80,0.83,0.86,0.88,0.90,0.91,0.92,0.91,0.90,0.89,0.87,0.85,0.82,0.80,0.76,0.73,0.70,0.66,0.63,0.60,0.57,0.55,0.54,0.53 ] ;
//	
//	int 		LEN = points0.length ;
//	
//	class Group{
//		double[] point = null ;
//		Color	lineColor = Color.RED ;
//		Color	ptColor  = Color.BLUE ;
//		int		offx = 0 ;
//		int 	offy = 0 ;
//	}
//	Group[] 	groups ;
//	
//	
//	void setup(){
//		System.out.printf( "--- setup ---\n" ) ;
//		groups = [
//			new Group( point:points0 , lineColor:Color.GREEN) 	,
////			new Group( point:points1 , offx:0 , offy:0 , lineColor:Color.GREEN) 	,
//		] ;
//	}
//	
//	double 			min , max ;
//	double 			duration  ;
//
//	DebugTimeLine 	timeline ;
//
//	static def 			values = [:] ;
//	
//	//=========================================================
//	
//	//=========================================================
//	// JRebelを走らせるために、Javaから起動する！！！　_GSmooth_Launcherを使う
//	public static void main(String[] args){
//		GSmoothLibrary sv = new GSmoothLibrary() ;
//		sv.show() ;
//	}
//
//	//=========================================================
//	GSmoothLibrary(){
//		timeline = new DebugTimeLine() ;
//		timeline.addDrawer(this) ;
//		
//		 
//		UtDebug.setJRebelReloadCallback(300, new ActionListener(){
//			public void actionPerformed(ActionEvent arg0) {
//				update() ;
//			};
//		}) ;
//
//		// 値の編集用UI		
//		PropertyEditorPanel pv = PropertyEditorPanel.getDefaultPropertyViewer() ;
//		pv.addEditor(new DoubleEditor("threasholdScale" , threasholdScale , 0.01 , 0.1 , new ValueChangeListener() {
//			public void valueChanged(ValueChangeEvent e) {
//				threasholdScale = (Double)e.getNewValue() ;
//				update() ;
//			}
//		})) ;
//	
//	}
//	
//	//=========================================================
//	public void show(){
//		initValue() ;
//		update() ; 
//
//		timeline.showFrame("Smooth Test") ;
//		timeline.startAutoUpdate(300) ;
//	}
//
//	void initValue(){
//		min = Double.POSITIVE_INFINITY ; max = Double.NEGATIVE_INFINITY ;
//		points0.eachWithIndex {
//			o, i ->
//			if( min > o ) min = o ;
//			if( max < o ) max = o ;
//		}
//
//		//-1..1は必ず含むようにする		
//		if( max <  1 ) max = 1 ;
//		if( min > -1 ) min = -1 ;
//		
//		
//		println "${min} , ${max}"
//
//		timeline.setRange(min, max) ;
//		timeline.setDuration(points0.length) ;
//	}
//	
//	public void update(){
//		setup() ;
//		
//		calcKeys(points0 , threasholdScale) ;
//	}
//
//	//=========================================================
//	/**
//	 * 処理内容（予定） p(i)を各点の値とする
//	 * ・計算しやすいように各値を小さくスムージングする
//	 *
//	 * ・速度、加速度を求める
//	 * 		p(i)
//	 * 		v(i) = p(i+1)-p(i)		(i=0,,LEN-2 , 最後[LEN-1]=[LEN-2]としても良い
//	 * 		a(i) = v(i)-v(i-1)		
//	 *
//	 * ・極値を求める。負号が反転  sign(v(i)) != sign( v(i+1) )
//	 * ・最大最小到達点（値域の限界になる点）を求める（あふれた場合などは最大最小の値が続く）
//	 *
//	 * 最初、最後および、極値と、最大最小到達点を固定キーとして割り当てる
//	 *
//	 * ・変曲点を求める sign( a(i) ) != sign( a(i+1) ) .. v'は速度
//	 * 　変曲点が連続した場合は、変曲点の間にキーとなる点が必要である可能性が高い
//	 *
//	 * ----
//	 * 評価関数
//	 *
//	 * ・極値、変曲点の値が変わらない
//	 * ・全体の誤差を最小にする
//	 * ・最大の誤差を最小にする
//	 * ・誤差の平均を最小にする
//	 * 
//	 * @return boolean[] 点列のどこにキーを打つべきかを返す
//	 */
//	public static boolean[] calcKeys(double[] srcPts , double _threasholdScale){
////		setup() ;
//				
//		int LEN = srcPts.length ;
//		double[] points1 = values.points1 = new double[LEN] ;
//		
//		double min = Double.POSITIVE_INFINITY ;
//		double max = Double.NEGATIVE_INFINITY ;
//		srcPts.eachWithIndex {
//			o, i ->
//			if( min > o ) min = o ;
//			if( max < o ) max = o ;
//		}
//				
//		// キー設定処理
//		print "update!"
//	
//		// 最初、最後のキーを設定
//		for(int i=1 ;i< LEN-1 ; i++){
//			points1[i] = 0.32*srcPts[i-1] + 0.36*srcPts[i] + 0.32*srcPts[i+1] ;
////			points1[i] = 0.25*points0[i-1] + 0.5*points0[i] + 0.25*points0[i+1] ;
////			points1[i] = points0[i] ;
//		}
//		points1[0    ] = srcPts[0    ] ;
//		points1[LEN-1] = srcPts[LEN-1] ;
//		
//		// 速度・加速度を求める
//		def p = values.p = points1 ;// << 平均化済みか、元の
//		def v = values.v = new double[LEN] ;
//		def a = values.a = new double[LEN] ;
//		
//		// 速度を求める
//		for(int i=1 ; i < LEN-1 ; i++){
//			v[i] = p[i+1] - p[i-1] ;
////			v[i] = p[i+1] - p[i] ;
//		}
//		v[0    ] = v[1] ;	//最後は慣性で同じ動きをするものとする
//		v[LEN-1] = v[LEN-2] ;//最後は慣性で同じ動きをするものとする
//		
//		// 加速度を求める
//		for(int i=1 ; i < LEN-1 ; i++){
//			a[i] = v[i+1] - v[i-1] ;
////			a[i] = v[i] - v[i-1] ;
//		}
//		a[0] = a[1] ;	//最初は等加速度とする
//		a[LEN-1] = a[LEN-2] ;//最後は等加速度とする
//		
//		// 極値を求める
//		def ev = values.extreamValue 	= new boolean[LEN] ;// 極大・極小
//		for(int i=0 ; i < LEN-1 ; i++){
//
//			if( v[i+1]*v[i] < 0 ){// 前後で負号が反転する時に極値
//
//				if( (v[i] > 0) == (srcPts[i] < srcPts[i+1]) ){ // v[i]>0(極大)なら大きい値、極小なら小さい値を選ぶ
//					ev[i+1] = true ;
//				}else{
//					ev[i  ] = true ;
//				}
//				i++ ;//一つ飛ばす
//			}
//		}
//		
//		// 変曲点を求める
//		def ip = values.inflectionPoint = new boolean[LEN] ;// 変曲点
//
//				for(int i=0 ; i < LEN-1 ; i++){			
//			if( a[i+1]*a[i] < 0 ) ip[i] = true ;
//		}
//
//		// キーをリストアップする
//		boolean[] keys = values.keys = new boolean[LEN] ;
//		keys[0    ] = true ;	// 最初
//		keys[LEN-1] = true ;	// 最後
//
//		// 最小値、最大値になる位置、外れる位置に打つ
//		// 極値に打つ（但し、前後共に最大値（または最小値）の場合は打たない） 
//		for( int i = 1 ; i < LEN-1 ; i++ ){
//			//
//			if( srcPts[i] == max ){
//				println( "max key:" + i ) ;
//				keys[i] = true ;
//				for( i++ ; i < LEN && srcPts[i] == max  ; i++ ){}//最大を取る範囲をスキップする。 p[i] !=max または i=LEN で抜ける
//				keys[i-1] = true ;//最大値の終了点(全体の終了点でもつじつまが合う）
//				i-- ;//次の処理の前に一つ戻す(forで再度足される)
//			}
//			else if( srcPts[i] == min ){
//				keys[i] = true ;
//				for( i++ ; i < LEN && srcPts[i] == min  ; i++ ){}//最小を取る範囲をスキップする。 p[i] !=minまたは i=LEN で抜ける
//				keys[i-1] = true ;//最小値の終了点(全体の終了点でもつじつまが合う）
//				i-- ;//次の処理の前に一つ戻す(forで再度足される)
//			}
//			else if( ev[i] ){
//				keys[i] = true ;
//			}
//		}
//		
//		int j ;
//		// 変曲点 -> 変曲点の時は間にキーを打つ。どの点に打つかは、要調整だが、とりあえず中間に打つ
//		for( int i = 1 ; i < LEN-1 ; i++ ){
//			if( ip[i] && ( srcPts[i] != max && srcPts[i] != min ) ){//変曲点１つめを発見
//				// 次のキーより先に変曲点が見つかるか確認
//				boolean found =false ;
//				for( j = i+1 ; j < LEN-1 ; j++ ){
//					if( keys[j] ) break ;
//					if( ip[j] ){
//						found = true ;
//						break ;
//					} 
//				}
//				
//				// 見つかった場合は i と j が変曲点。
//				if( found ){
//					if( j - i >= 2 ){
//						int pos = (i+j)/2 ;//中点を取る（曲線の形状によってずらすのが良い）
//						keys[pos] = true ;
//					}
//				}
//				i = j-1 ;//再度同じJからループする
//			}
//		}
//		MvAttrF attr = null ;//仮
//		OriginalSequence seq0 = values.seq0 = new OriginalSequence(attr , keys , srcPts) ;
//		
//		boolean[] keys2 = values.keys2 = new boolean[LEN] ;
//		keys.eachWithIndex { o, i -> keys2[i] = o }
//		
//		//-- オリジナル曲線に適用する --
//		// ずれ量を計算して、補正していく
//		boolean seqUpdated = true ; 
//		while( seqUpdated ){
//			OriginalSequence seq1 = values.seq1 = new OriginalSequence(attr , keys2 , srcPts) ;
//			println values.seq1  ;
//			
//			//-- オリジナル曲線を補正する --
//			// 閾値以上離れている点が見つかった場合は、キーポイント間に1点追加する
//			def threasholdDist = (max - min)*_threasholdScale ; 
//
//			int prev , post ;
//			seqUpdated = false ;
//			for( int i = 1 ; i < LEN-1 ; i++ ){
//				double dist = Math.abs( srcPts[i] - seq1.getDoubleValue(i))
//				
//				if( dist > threasholdDist ){
//					for( prev = i ; prev >= 0 ; prev-- ){//前のキーを見つける
//						if( keys2[prev] ) break ; 
//					}
//					for( post = i+1; post < LEN ; post++){// 後ろのキーを見つける
//						if( keys2[post] ) break ;
//					}
//					
//					if( post - prev >= 2 ){
//						int newKeyPos = (prev+post)/2 ;
//						keys2[newKeyPos] = true ;
//						seqUpdated = true ;
//					}
//					
//					i = post ;
//				}					
//			}
//		}
//		
//		return keys2 ;
//	}
//
//		
//	//=========================================================
//	//	Draw
//	//=========================================================
//	@Override
//	public void draw( Graphics2D g , DrawInfo di ){
//		UtDraw.drawLine(g, di.cx(0) ,  di.cy(0) , di.cx(LEN) , di.cy(0) , Color.LIGHT_GRAY) ;
//
//		// 生成されたキーを描画
//		def keys2 = values.keys2 ;
//		for( int pos = 0 ; pos < LEN ; pos++ ){
//			if( keys2[pos]){
//				double val = points0[pos] ;
//				UtDraw.fillCircle(g, di.cx(pos) ,  di.cy(val) , 4 , Color.BLUE) ;
//			}
//		}
//		
//		double[] points1 = values.points1 ;
//		
//		// 生成されたキーを描画
//		def keys = values.keys ;
//		for( int pos = 0 ; pos < LEN ; pos++ ){
//			if( keys[pos]){
//				double val = points0[pos] ;
//				UtDraw.fillCircle(g, di.cx(pos) ,  di.cy(val) , 4 , Color.RED) ;
//			}
//		}
// 
//		// 点列を描く
//		groups.each{
//			Group gp = it ;
//			def points = gp.point ;
//			
//			for( int pos = 0 ; pos < LEN-1 ; pos++ ){
//				def p0 = points[pos]   ;
//				def p1 = points[pos+1];
//	
//				UtDraw.drawLine(g, di.cx(pos) + gp.offx ,  di.cy(p0) + gp.offy , di.cx(pos+1) + gp.offx, di.cy(p1) + gp.offy , gp.lineColor ) ;
//				UtDraw.drawPt(g, di.cx(pos) + gp.offx,  di.cy(p0) + gp.offy , 3 , gp.ptColor) ;
//			}
//		}
//		
//		// 速度、加速度をダンプ
//		if( false ){
//			def v = values.v
//			def a = values.a
//			
//			for( int pos = 0 ; pos < LEN-1 ; pos++ ){
//				def v0 = v[pos] ;
//				def v1 = v[pos+1] ;
//				def a0 = a[pos] ;
//				def a1 = a[pos+1] ;
//				
//				
//				UtDraw.drawLine(g, di.cx(pos) ,  di.cy(v0) , di.cx(pos+1) , di.cy(v1) , Color.RED) ;
//				UtDraw.drawLine(g, di.cx(pos) ,  di.cy(a0) , di.cx(pos+1) , di.cy(a1) , Color.BLUE) ;
//			}		 
//		}
//		
//		// 極値、変曲点を描画
//		if( false ){
//			def ev = values.extreamValue 	;
//			def ip = values.inflectionPoint ;
//			
//			for( int pos = 0 ; pos < LEN-1 ; pos++ ){
//				if( ev[pos] ) UtDraw.drawLine(g, di.cx(pos)   ,  di.cy(min) , di.cx(pos)   , di.cy(max) , Color.PINK) ;
//				if( ip[pos] ) UtDraw.drawLine(g, di.cx(pos)+1 ,  di.cy(min) , di.cx(pos)+1 , di.cy(max) , Color.CYAN) ;
//			}
//		}
//		
//		// オリジナルの曲線を描く
//		if( false ){
//			OriginalSequence os = values.seq0 ;// 
//			for( int pos = 0 ; pos < LEN -1 ; pos++ ){
//				def v0 = os.getDoubleValue(pos) ;
//				def v1 = os.getDoubleValue(pos+1) ;
//				
//				UtDraw.drawLine(g, di.cx(pos) ,  di.cy(v0) , di.cx(pos+1) , di.cy(v1) , Color.GREEN) ;
//			}
//		}
//			
//		// オリジナルの曲線を描く
//		OriginalSequence os1 = values.seq1 ;// 
//		for( int pos = 0 ; pos < LEN -1 ; pos++ ){
//			def v0 = os1.getDoubleValue(pos) ;
//			def v1 = os1.getDoubleValue(pos+1) ;
//
//			UtDraw.drawLine(g, di.cx(pos) ,  di.cy(v0) , di.cx(pos+1) , di.cy(v1) , Color.RED) ;
//		}
//			
//	}
//
//
//}
