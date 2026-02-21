//package jp.live2d.cubism.ui.morphEditor.script
//
//import java.awt.Frame
//import javax.swing.JLabel
//import javax.swing.JList
//import javax.swing.JPanel
//import javax.swing.JTextField
//import jp.live2d.cubism.ui.morphEditor.TextureImage
//import jp.live2d.cubism.ui.morphEditor.TextureManager
//import jp.live2d.type_editor.image.LDImage
//import jp.noids.model3Dp.exportSetting.ExportTarget
//import jp.noids.ui.layout.LineLayout
//import jp.noids.ui.swing.OkCancelDialog
//import jp.noids.util.UtGui
//
////TextureManager tm = tm ;
//a = "abc" ;
//
//return showDialog(tex ,tm ) ;
//
////------------------------
//def boolean showDialog( TextureImage tex , TextureManager tm){
//	
//	MyDialog dlg = new MyDialog(UtGui.getTopFrame() , tex , tm ) ;
//
//	
//	dlg.setVisible(true) ;
//	
//	
//	tex.setTextureName( "converted !!") ;
//	
//	
//	LDImage img = tex.getImage() ;
//	println img.getWidth() ;
//	
//	
//	return dlg.isOK() ;
//}
//
//
////==========================================================
////==========================================================
////	Dialog
////==========================================================
////==========================================================
////==========================================================
//class MyDialog extends OkCancelDialog{
//	JTextField tfTexName = new JTextField(20) ;
//	TextureImage tex ;
//	TextureManager tm ;
//	JList listTarget = new JList() ;
//	
//	MyDialog(Frame f , TextureImage _tex , TextureManager tm){
//		super( f , "設定" ) ;
//		this.tm  = tm ;
//		this.tex = _tex ;
//		initGui() ;
//	}
//	
//	private void initGui() {
//		//----------------- 初期化 -----------------
//		this.setSize( 600 , 500 ) ;
//		
//		tfTexName.setText(tex.getTextureName()) ;
//		
//		
//		TextureExport[] exportList = tex.getExportList() ;
//		for (int i = 0; i < exportList.length; i++) {
//			TextureExport te = exportList[i];
//			ExportTarget etarget = te.getExportTarget() ;
//			String exportName = etarget.getExportName() ;
//			
//			
//		}
//		
//		//---------------- イベント ----------------
//		
//		//------------------ 配置 ------------------
//		JPanel main = new JPanel() ;
//		
//		LineLayout la = new LineLayout(300,LineLayout.CENTER) ;
//		main.setLayout(la) ;
//		
//		String RULE1 = "10,[80],10,[100%],20" ;//設定項目
//		String RULE2 = "10,[80],10,[50],10,[70],20" ;//郵便番号
//		String RULE3 = "100,[100%],20" ;//チェックボックス
//		
//		//----------------------------------------------------
//		la.setRule(RULE1) ;
//		
//		main.add(new JLabel("テクスチャ名称",JLabel.RIGHT )) ;
//		main.add( tfTexName ) ;
//		
//		la.setRule(RULE2) ;
//		la.newLine() ;
//		
//		main.add(new JLabel("hello")) ;
//		
//		
//		this.setMainPanel(main) ;
//	}
//
//	@Override
//	protected boolean accept(boolean showAlart) {
//
//		//--- 他と名前が重複していないか確認 ---		
//		String t = tfTexName.text ;
//		ArrayList tlist = tm.getTextureList() ;
//		boolean found = false ;
//		tlist.each{
//			TextureImage ti = it ;
//			if( ti.textureName == t && tex.textureName != t ) found = true ; 
//		}
//		if( found ) return false ;
//		
//		//---
//		
//		return super.accept(showAlart);
//	}
//
//		
//	
//}
//
//
//public static final boolean test(){
//	println ("hello test!!") ;
//	
//}
