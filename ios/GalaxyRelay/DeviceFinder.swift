import Foundation
import Combine
import MapKit

struct FoundDevice:Decodable {
 let requestId:String,timestamp:Int64,status:String
 let latitude:Double?,longitude:Double?,accuracy:Double?,measuredAt:Int64?
 var valid:Bool{
  guard status=="OK",let latitude,let longitude,let accuracy,let measuredAt else{return false}
  return latitude.isFinite&&abs(latitude)<=90&&longitude.isFinite&&abs(longitude)<=180&&accuracy.isFinite&&accuracy>=0&&accuracy<=1000000&&measuredAt>0
 }
}
@MainActor final class DeviceFinder:ObservableObject {
 @Published private(set) var busy=false
 @Published private(set) var status="BLE 연결 없이도 Galaxy의 현재 위치를 요청할 수 있습니다."
 @Published private(set) var found:FoundDevice?
 private var generation=UUID()
 func clear(){generation=UUID();busy=false;found=nil;status="BLE 연결 없이도 Galaxy의 현재 위치를 요청할 수 있습니다."}
 func locate(_ settings:PairSettings)async{
  guard !busy else{return};let ticket=generation;busy=true;found=nil;status="Galaxy에 위치 요청을 보내는 중…"
  defer{if generation==ticket{busy=false}}
  do{
   let cloud=CloudLink(settings:settings),start=try await cloud.call("/api/native/locate/start")
   guard generation==ticket,!Task.isCancelled else{return}
   guard let id=start["requestId"] as? String,let expires=start["expires"] as? NSNumber,let grant=start["grant"] as? String else{throw RelayError.invalid}
   let request:[String:Any]=["requestId":id,"expires":expires,"grant":grant]
   status="Galaxy에서 위치를 확인하고 있습니다…"
   for _ in 0..<24{
    try await Task.sleep(nanoseconds:4_000_000_000);guard generation==ticket,!Task.isCancelled else{return}
    guard NativeCrypto.now()<expires.int64Value else{break}
    let response=try await cloud.call("/api/native/locate/result",request)
    guard generation==ticket,!Task.isCancelled else{return}
    guard response["ready"] as? Bool==true,let raw=response["packet"] as? [String:Any] else{continue}
    let packet=try JSONDecoder().decode(SealedPacket.self,from:NativeCrypto.json(raw))
    let decoded=try NativeCrypto.open(packet,key:NativeCrypto.bytes(settings.linkKey),aad:NativeCrypto.locationAAD(id,settings.deviceId))
    let point=try JSONDecoder().decode(FoundDevice.self,from:decoded)
    guard point.requestId==id,abs(NativeCrypto.now()-point.timestamp)<120000 else{throw RelayError.authentication}
    if point.status=="OK"{guard point.valid,let measured=point.measuredAt,abs(NativeCrypto.now()-measured)<150000 else{throw RelayError.invalid};found=point;status="Galaxy의 위치를 확인했습니다."}
    else{status=["DISABLED":"Galaxy 설정에서 서버를 통한 내 기기 위치 찾기를 켜주세요.","PERMISSION_REQUIRED":"Galaxy 위치 권한을 항상 허용으로 설정해주세요.","LOCATION_OFF":"Galaxy 위치 서비스가 꺼져 있습니다.","BACKGROUND_RESTRICTED":"Galaxy가 백그라운드 위치 조회를 제한했습니다. 앱을 열어 설정을 확인해주세요.","NO_FIX":"Galaxy에서 현재 위치를 확인하지 못했습니다.","BUSY":"Galaxy가 다른 위치 요청을 처리 중입니다."][point.status] ?? "Galaxy 위치를 확인하지 못했습니다."}
    _ = try? await cloud.call("/api/native/locate/ack",request);return
   }
   status="Galaxy 응답이 없습니다. Galaxy의 인터넷·전원·Relay 실행 상태를 확인해주세요."
  }catch{
   guard generation==ticket else{return}
   status=Task.isCancelled ? "위치 요청 대기를 종료했습니다.":"위치 요청을 완료하지 못했습니다. 인터넷 연결과 서버 업데이트 상태를 확인해주세요."
  }
 }
 func openMap(){guard let p=found,p.valid,let lat=p.latitude,let lon=p.longitude else{return};let item=MKMapItem(placemark:MKPlacemark(coordinate:CLLocationCoordinate2D(latitude:lat,longitude:lon)));item.name="내 Galaxy";item.openInMaps()}
}
