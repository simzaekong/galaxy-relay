#if targetEnvironment(simulator)
import UIKit
/** Synthetic data for CI visual checks. This code is excluded from the iPhone binary. */
@MainActor enum DesignPreview {
 static func prepare(_ model:RelayModel){
  guard ProcessInfo.processInfo.arguments.contains("--design-preview") else{return}
  try? model.history.clear();model.ble.stop()
  let rows:[(String,String,String,String,UIColor)]=[
   ("카카오톡","com.kakao.talk","서연","오늘 저녁에 시간 있어? 같이 밥 먹자 😊",.systemYellow),
   ("Instagram","com.instagram.android","지민","주말에 찍은 사진을 보냈습니다.",.systemPink),
   ("Discord","com.discord","프로젝트 채널","새로운 메시지가 도착했습니다.",.systemIndigo),
   ("카카오톡","com.kakao.talk","서연","7시쯤 어때?",.systemYellow)]
  for (index,row) in rows.enumerated(){
   let format=UIGraphicsImageRendererFormat();format.scale=1
   let icon=UIGraphicsImageRenderer(size:CGSize(width:32,height:32),format:format).jpegData(withCompressionQuality:0.4){context in row.4.setFill();context.fill(CGRect(x:0,y:0,width:32,height:32));let text=String(row.0.prefix(1));(text as NSString).draw(at:CGPoint(x:7,y:5),withAttributes:[.font:UIFont.boldSystemFont(ofSize:18),.foregroundColor:UIColor.white])}
   let value:[String:Any]=["id":NativeCrypto.uuid("evt_"),"type":"notification","timestamp":NativeCrypto.now()-Int64(index+1)*180000,"app":row.0,"packageName":row.1,"title":row.2,"body":row.3,"notificationKey":"preview-\(index)","appIcon":NativeCrypto.b64(icon)]
   if let data=try? NativeCrypto.json(value),let event=try? JSONDecoder().decode(RelayEvent.self,from:data){_ = try? model.history.apply(event)}
  }
  model.settings=PairSettings(pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),galaxyName:"Galaxy S24+",linkKey:"",commandKey:"",backend:"https://example.invalid",deviceId:NativeCrypto.uuid(),token:"fixture-only");model.ble.previewConnection();model.notice="디자인 미리보기 · 예시 알림"
  if ProcessInfo.processInfo.arguments.contains("--design-detail"){model.openEventId=model.history.state.events.first?.id}
 }
}
#endif
