package jp.live2d.anime.ctrl

import static i18n.I18N_cn_cubism_proto.tr

import javax.swing.JOptionPane

import jp.live2d.anime.CubismAnimeAppCtrl
import jp.live2d.anime.CubismAnimeAppModel
import jp.live2d.anime.L2DAnimeDoc
import jp.noids.framework.ID
import jp.noids.framework.ID.Param
import jp.noids.model3Dp.param.ParamDefDouble
import jp.noids.movie.core.MvTime
import jp.noids.movie.effect.attr.IMvAttr
import jp.noids.movie.effect.attr.MvAttrF
import jp.noids.movie.effect.attr.UtSequence
import jp.noids.movie.effect.attr.value.IValueSequence
import jp.noids.movie.effect.attr.value.OriginalSequence
import jp.noids.movie.scene.MvScene
import jp.noids.movie.scene.event.SceneChangeEvent
import jp.noids.movie.track.IMvTrack
import jp.noids.movie.track.MvTrack_Group
import jp.noids.movie.track.MvTrack_Live2DModel
import jp.noids.movie.ui.edit.MvTrackSelector
import jp.noids.util.UtGui
import jp.noids.util.UtString

class GCubismAnimeCtrl_util {
	CubismAnimeAppCtrl 	ctrl ;
	CubismAnimeAppModel appModel ;


	GCubismAnimeCtrl_util( CubismAnimeAppModel appModel , CubismAnimeAppCtrl 	ctrl ){
		this.appModel = appModel ;
		this.ctrl = ctrl ;
	}

	/**
	 * モーションをロードする
	 *
	 * @param file
	 * @param pg
	 * @param loop ループ回数（０なら無限に繰り返す）
	 */
	public void readMotionData_Text( File file ) {
		try {
println "abc"
			//--------- 前処理 ---------
			final L2DAnimeDoc doc = appModel.getCurDoc() ;
			if( doc == null ){
				System.out.println( "no current document" ) ;
				return ;
			}
			final MvScene scene = doc.getScene() ;

			//--------- 出力するトラックの決定 ---------
			IMvTrack track = null ;
			if( true ){
				MvTrackSelector ts = (MvTrackSelector) doc.getSelectionManager().get(MvTrackSelector.ID) ;
				IMvTrack[] selected = ts.getSelected() ;

				if( selected.length == 1 ){
					track = selected[0] ;
				}
				else if( selected.length > 1 ){
					JOptionPane.showMessageDialog( UtGui.getTopWindow()
							,  tr("CUBI-0039")  ) ;	// tr("出力するトラックを一つだけ選択した状態で呼び出して下さい")
					return ;
				}
				else{
					MvTrack_Group root = doc.getScene().getRootTrack() ;
					if( root.getChildCount() == 1 && ! root.getChildTrack(0).isParent() ){
						track = root.getChildTrack(0) ;
					}
					else{
						if( root.getChildCount() > 0 ){
							JOptionPane.showMessageDialog( UtGui.getTopWindow()
									,  tr("CUBI-0039")  ) ;	// tr("出力するトラックを一つだけ選択した状態で呼び出して下さい")
							return ;
						}
						else{
							JOptionPane.showMessageDialog( UtGui.getTopWindow() ,  tr("CUBI-0040")  ) ;	// tr("出力するトラックがありません")
							return ;
						}
					}
				}
			}//出力トラックの決定終了
			final IMvTrack selectedTrac = track ;


			//--------- モーションのデータを抽出する ---------
			String txt = UtString.readText(file) ;
			String[] lines = txt.split("\n") ;
			HashMap<String,double[]> keyValueMap = new HashMap<String, double[]>() ;
			for (int i = 0; i < lines.length; i++) {
				String s = lines[i] ;

				if( s.indexOf('=') > 0 ){
					String[] keyValues = s.split("=") ;
					if( keyValues == null || keyValues.length != 2  ) continue ;

					String key = keyValues[0].trim() ;
					String[] values = keyValues[1].split(",") ;
					double[] vlist = new double[ values.length ] ;

					for (int j = 0; j < vlist.length; j++) {
						vlist[j] = Double.parseDouble( values[j].trim() ) ;
					}

					keyValueMap.put(key , vlist) ;
				}
			}




			//--------- 値をトラックに反映する ---------
			if( selectedTrac instanceof MvTrack_Live2DModel ){
				MvTrack_Live2DModel mt = (MvTrack_Live2DModel) selectedTrac ;
				IMvAttr[] attrs = mt.getAttrForLive2DModel() ;
				for (int i = 0; i < attrs.length; i++) {
					attrs[i].clearValue() ;
				}
				Set k =keyValueMap.keySet() ;
				int maxLen = 0 ;
				for (Iterator i = k.iterator(); i.hasNext(); ) {
					String key = (String) i.next();
					ID.Param id = new Param(key) ;
					int localPos = mt.getLocalFramePos(new MvTime(0)) ;
					MvAttrF attr = mt.getAttr(id) ;
					if( attr != null ){


						double[] values = keyValueMap.get(key) ;

						if( maxLen < values.length ) maxLen = values.length ;

//						attr.addFrame(, frameCount)
						for (int j = 0; j < values.length; j++) {
							attr.setValueAuto( localPos + j , values[j]) ;
						}
//
//						if( true ){
//							//--------- 無駄な点を削除する ---------
//							IValueSequence vs = attr.getValueData() ;
//							ParamDefDouble defx = (ParamDefDouble) mt.getModel().getParamDefSet().getParamDef(id) ;
//							double range = defx.getMaxValue() - defx.getMinValue() ;
//
//							UtSequence.smooth(vs, 0, 10000, range/30 ) ;//とりあえず！！
//							printf("y")
//						}
					}
				}

				mt.setDuration(maxLen) ;
			}


			scene.fireSceneChangeEvent( this , scene, SceneChangeEvent.STRUCTURE, false ) ;

			appModel.getUpdator().updateCanvas() ;
			appModel.getUpdator().updateField() ;


		} catch (Exception e) {

			e.printStackTrace();
			JOptionPane.showMessageDialog( UtGui.getTopWindow()
					,  tr("CUBI-0046")  + e.toString() ) ;	// tr("モーションデータのロードに失敗しました\n\n")
		}
	}

//	//============================================================
//	def optimizePoints( double[] vlist )
//	{
//		double[] pts = new double[ vlist.length * 2 ] ;
//		vlist.length.times{
//			no = it
//			pts[no*2  ] = no ;
//			pts[no*2+1] = vlist[no] ;
//		}
//
//
//		//変曲点を見つけて、マークする？
//		//補間曲線を生成する
//		OriginalSequence os = OriginalSequence.createSample(pts) ;
//		error = (os.getCurMax() - os.getCurMin()) * 0.1 ;
//		UtSequence.smooth(os, 0, vlist.length , error ) ;
//
//
//	}

}
