import Foundation
import Combine

@MainActor final class RemoteModel:ObservableObject {
 typealias Sender=([String:Any])throws->Void
 @Published private(set) var transaction=RemoteTransaction()
 @Published private(set) var notice=""
 @Published private(set) var loaded=false
 private var timeout:Task<Void,Never>?
 // Background reads do not publish a busy state or occupy the command slot.
 private var refreshID:String?
 private var refreshTimeout:Task<Void,Never>?
 var state:RemoteState{transaction.displayed}
 var busy:Bool{transaction.pending != nil}
 func perform(_ action:String,value:Int?=nil,ble:BLELink){
  guard ble.ready,ble.remoteSupported else{return}
  perform(action,value:value,send:{[weak ble] body in guard let ble,ble.ready else{throw RelayError.unavailable};try ble.send(body)})
 }
 func perform(_ action:String,value:Int?=nil,send:@escaping Sender){
  if action=="state"{refresh(send:send);return}
  guard !busy else{return}
  cancelRefresh() // A read already on the wire may arrive later; its ID is now obsolete.
  let id=NativeCrypto.uuid()
  guard transaction.begin(id:id,action:action,value:value) else{return}
  if !notice.isEmpty{notice=""}
  do{
   var body:[String:Any]=["kind":"remote_request","requestId":id,"action":action,"mediaId":transaction.confirmed.mediaId]
   if let value{body["value"]=value};try send(body)
   timeout?.cancel();timeout=Task{[weak self] in
    do{try await Task.sleep(nanoseconds:5_000_000_000)}catch{return}
    self?.expireCommand(id,send:send)
   }
  }catch{transaction.cancel();notice="요청을 보내지 못해 버튼을 복원했습니다."}
 }
 private func refresh(send:@escaping Sender){
  guard !busy,refreshID==nil else{return}
  let id=NativeCrypto.uuid();refreshID=id
  do{
   try send(["kind":"remote_request","requestId":id,"action":"state","mediaId":transaction.confirmed.mediaId])
   refreshTimeout=Task{[weak self] in
    do{try await Task.sleep(nanoseconds:5_000_000_000)}catch{return}
    self?.expireRefresh(id)
   }
  }catch{expireRefresh(id)}
 }
 private func cancelRefresh(){refreshTimeout?.cancel();refreshTimeout=nil;refreshID=nil}
 func expireRefresh(_ id:String){
  guard refreshID==id else{return};cancelRefresh()
  if !loaded{notice="Galaxy 상태를 확인하지 못했습니다. 다시 확인해주세요."}
 }
 func expireCommand(_ id:String,send:@escaping Sender){
  guard transaction.pending?.id==id else{return};timeout?.cancel();timeout=nil;transaction.cancel();notice="응답을 확인하지 못해 버튼을 복원했습니다.";refresh(send:send)
 }
 func accept(_ body:[String:Any])throws{
  guard let id=body["requestId"] as? String else{return}
  let reading=refreshID==id
  guard reading || id==transaction.pending?.id else{return}
  guard let result=body["result"] as? String,let raw=body["state"] as? [String:Any] else{throw RelayError.invalid}
  let state=try JSONDecoder().decode(RemoteState.self,from:NativeCrypto.json(raw))
  guard state.valid else{throw RelayError.invalid}
  if reading{
   cancelRefresh()
   guard result=="OK" else{
    if result=="DISABLED"{loaded=false;notice="Galaxy 설정에서 iPhone 리모트를 허용해주세요."}
    else if !loaded{notice="Galaxy 상태를 확인하지 못했습니다. 다시 확인해주세요."}
    return
   }
   guard !busy else{return}
   if state != transaction.confirmed || state != transaction.displayed{_ = transaction.refresh(state)}
   if !loaded{loaded=true;notice=""}
   return
  }
  guard let success=transaction.resolve(id:id,state:state,result:result) else{return}
  timeout?.cancel();timeout=nil
  if success{if !loaded{loaded=true};if !notice.isEmpty{notice=""}}
  else{notice=result=="PERMISSION_REQUIRED" ? "Galaxy에서 벨소리 모드 변경 권한을 확인해주세요.":result=="DISABLED" ? "Galaxy 설정에서 iPhone 리모트를 허용해주세요.":"Galaxy에 변경이 적용되지 않아 버튼을 복원했습니다."}
 }
 func invalidResponse(_ id:String?){
  guard let id else{return}
  if refreshID==id{expireRefresh(id);return}
  guard transaction.pending?.id==id else{return}
  timeout?.cancel();timeout=nil;transaction.cancel();notice="Galaxy 상태를 확인하지 못해 버튼을 복원했습니다."
 }
 func disconnected(){timeout?.cancel();timeout=nil;cancelRefresh();transaction.reset();loaded=false;notice="블루투스 연결 후 조작할 수 있습니다."}
 #if targetEnvironment(simulator)
 func preview(){
  guard ProcessInfo.processInfo.arguments.contains("--design-preview") else{return}
  var fixture=RemoteState();fixture.mediaAvailable=true;fixture.mediaId="preview.music";fixture.mediaApp="YouTube Music";fixture.title="한강을 걸으며";fixture.artist="예시 아티스트";fixture.playing=true;fixture.canPlay=true;fixture.canPause=true;fixture.canNext=true;fixture.canPrevious=true;fixture.mediaVolume=53;fixture.ringVolume=67
  _ = transaction.refresh(fixture);loaded=true;notice=""
 }
 #endif
}
