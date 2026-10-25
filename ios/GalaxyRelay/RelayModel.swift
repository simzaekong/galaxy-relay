import Foundation
import SwiftUI
import UserNotifications
@MainActor final class RelayModel:NSObject,ObservableObject,UNUserNotificationCenterDelegate {
 let finder=DeviceFinder()
 let history=History();let ble=BLELink();let callAlerts=CallAlerts()
 @Published var settings:PairSettings?
 @Published var notice="";@Published var busy=false
 @Published var openEventId:String?
 @Published var foreground=false
 @Published var hidePreview=UserDefaults.standard.bool(forKey:"hidePreview")
 private var callRequests:[String:(id:String,done:(Bool)->Void)]=[:]
 private var syncing=false
 private var connectionGeneration=UUID()
 private var revoking=false
 private let revokeRequest:(PairSettings)async throws->Void
 init(revokeRequest:@escaping(PairSettings)async throws->Void = {p in _ = try await CloudLink(settings:p).call("/api/native/revoke")}){self.revokeRequest=revokeRequest;super.init();if let data=Secrets.get("pair"),let p=try? JSONDecoder().decode(PairSettings.self,from:data){settings=p;ble.configure(p)};ble.message={[weak self] value in self?.receive(value)};ble.transportLost={[weak self] in self?.callAlerts.stop(.connectionLost)};callAlerts.onHangup={[weak self] id,done in self?.hangupCall(id,completion:done)};callAlerts.onNotice={[weak self] text in self?.notice=text};callAlerts.onFailure={[weak self] event in guard let self,self.settings != nil,self.ble.ready,NativeCrypto.now()-event.timestamp<=CallAlertPolicy.maximumAge else{return};self.postNotification(event)};UNUserNotificationCenter.current().delegate=self}

 func permission(){Task{do{let center=UNUserNotificationCenter.current();let reply=UNTextInputNotificationAction(identifier:"REPLY",title:"답장",options:[.authenticationRequired,.foreground],textInputButtonTitle:"보내기",textInputPlaceholder:"메시지 입력");center.setNotificationCategories([UNNotificationCategory(identifier:"MESSAGE_REPLY",actions:[reply],intentIdentifiers:[])]);_ = try await center.requestAuthorization(options:[.alert,.sound,.badge])}catch{notice="iPhone 설정에서 Relay 알림 권한을 확인해주세요."}}}
 private func receive(_ value:[String:Any]){do{
  if value["kind"] as? String=="connected",let id=value["pairId"] as? String{try ble.confirmConnection(id);notice="Galaxy와 연결했습니다. 서버 설정도 완료됐습니다.";return}
  if value["kind"] as? String=="config"{let p=try JSONDecoder().decode(PairSettings.self,from:NativeCrypto.json(value));guard try NativeCrypto.bytes(p.linkKey).count==32,try NativeCrypto.bytes(p.commandKey).count==32,p.pairId.count==36 else{throw RelayError.invalid};try Secrets.put("pair",JSONEncoder().encode(p));settings=p;try ble.activate(p);notice="서버 설정을 받았습니다. Galaxy 연결을 확인하고 있습니다.";return}
  if value["kind"] as? String=="event",let raw=value["event"] as? [String:Any]{let event=try JSONDecoder().decode(RelayEvent.self,from:NativeCrypto.json(raw));try accept(event,liveCall:ble.ready);try ble.send(["kind":"ack","eventId":event.id])}
 }catch{notice="알림을 저장하거나 연결을 확인할 수 없습니다. 기기 저장 공간을 확인해주세요."}}
 private func accept(_ event:RelayEvent,liveCall:Bool=false)throws{if event.offlineOnly==true && (!liveCall || !ble.acceptsOfflineRelay){return};let target=event.targetEventId;let key=history.state.events.first{$0.id==target}?.notificationKey;let removedIds=history.state.events.filter{$0.id==target||key != nil&&$0.notificationKey==key}.map{$0.id};let alreadyEnded=event.type=="call"&&event.state=="ringing"&&history.state.events.contains{ $0.type=="call"&&($0.callId ?? $0.id)==(event.callId ?? event.id)&&["ended","offhook"].contains($0.state ?? "") };let show=try history.apply(event);let callRouted=alreadyEnded || callAlerts.receive(event,live:liveCall,hidePreview:hidePreview);if event.type=="removed"||event.type=="command_result"&&(event.result=="REMOVED"||event.result=="NOT_FOUND"){UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers:removedIds)};if event.type=="call",event.state=="ended"{let id=event.callId ?? event.id;let matching=callRequests.filter{$0.value.id==id};for (key,request) in matching{callRequests.removeValue(forKey:key);request.done(true)}};if event.type=="command_result",event.result != "CALL_END_REQUESTED",let command=event.commandId,let request=callRequests.removeValue(forKey:command){request.done(false);if event.result != "CALL_END_REQUESTED"{notice=event.result=="CALL_PERMISSION_REQUIRED" ? "Galaxy Relay 설정에서 iPhone 전화 거절·끊기 권한을 허용해주세요.":event.result=="CALL_AMBIGUOUS" ? "다중 SIM 또는 모호한 통화 상태에서는 원격 종료를 지원하지 않습니다.":"Galaxy 통화를 종료하지 못했습니다. Galaxy에서 확인해주세요."}};if event.type=="command_result",!(event.result ?? "").hasPrefix("CALL_"){notice=["REPLY_SENT":"Galaxy 원본 앱에 답장을 전달했습니다.","REPLY_REQUIRES_UNLOCK":"Galaxy 잠금을 해제한 뒤 답장해주세요.","REPLY_ACTION_CHANGED":"알림이 갱신됐습니다. 최신 알림에서 답장해주세요.","NOT_FOUND":"원본 알림이 이미 사라졌습니다.","REMOVED":"Galaxy 알림을 지웠습니다.","USER_ACTION_REQUIRED":"Galaxy의 열기 요청 알림을 눌러주세요.","OPEN_SENT":"Galaxy에서 열기 요청을 전달했습니다.","PONG":"Galaxy 연결을 확인했습니다."][event.result ?? ""] ?? "Galaxy가 요청을 처리했습니다."};if event.type=="removed",let id=event.targetEventId{UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers:[id])}
  if event.type=="call",event.state != "ringing"{UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers:[event.callId ?? event.id]);UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers:[event.callId ?? event.id])}
  if show && !foreground && !callRouted{postNotification(event)}
 }
 private func postNotification(_ event:RelayEvent){let content=UNMutableNotificationContent();content.title=hidePreview ? "Galaxy Relay":event.unanswered==true ? "받지 않은 전화 · "+event.heading:event.heading+" · "+event.label;content.body=hidePreview ? "새 알림이 도착했습니다.":event.message;content.sound = .default;content.userInfo=["eventId":event.id];if event.reply != nil{content.categoryIdentifier="MESSAGE_REPLY"};let tag=event.type=="call" ? (event.callId ?? event.id):event.id;let generation=connectionGeneration;UNUserNotificationCenter.current().add(UNNotificationRequest(identifier:tag,content:content,trigger:nil)){[weak self] _ in Task{@MainActor in guard let self else{return};let ended=event.type=="call"&&event.unanswered != true&&self.history.state.events.contains{$0.type=="call"&&($0.callId ?? $0.id)==(event.callId ?? event.id)&&["ended","offhook"].contains($0.state ?? "")};if self.connectionGeneration != generation || ended || event.offlineOnly==true && !self.ble.acceptsOfflineRelay{let center=UNUserNotificationCenter.current();center.removePendingNotificationRequests(withIdentifiers:[tag]);center.removeDeliveredNotifications(withIdentifiers:[tag])}}}}

 func sync()async{
  guard foreground,!ble.ready,!syncing,let p=settings else{return};let generation=connectionGeneration;syncing=true;defer{if connectionGeneration==generation{syncing=false}}
  do{let cloud=CloudLink(settings:p),start=try await cloud.call("/api/native/pull/start");guard connectionGeneration==generation else{return};guard let id=start["requestId"] as? String else{throw RelayError.invalid};let grant:[String:Any]=["requestId":id,"expires":start["expires"] ?? 0,"grant":start["grant"] ?? ""];notice="Galaxy에서 대기 알림을 가져오는 중…"
   for _ in 0..<45{guard foreground,connectionGeneration==generation,!Task.isCancelled else{return};try await Task.sleep(nanoseconds:2_000_000_000);guard connectionGeneration==generation else{return};let result=try await cloud.call("/api/native/pull/result",grant);guard connectionGeneration==generation else{return};if result["ready"] as? Bool==true,let raw=result["packet"] as? [String:Any]{let packet=try JSONDecoder().decode(SealedPacket.self,from:NativeCrypto.json(raw));let decoded=try NativeCrypto.object(NativeCrypto.open(packet,key:NativeCrypto.bytes(p.linkKey),aad:NativeCrypto.cloudAAD(id,p.deviceId)));guard decoded["requestId"] as? String==id,let values=decoded["events"] as? [[String:Any]],values.count<=6 else{throw RelayError.authentication};for value in values{try accept(JSONDecoder().decode(RelayEvent.self,from:NativeCrypto.json(value)))};_ = try await cloud.call("/api/native/pull/ack",grant);guard connectionGeneration==generation else{return};notice=values.isEmpty ? "새 알림이 없습니다.":"대기 알림을 가져왔습니다.";return}}
   notice="Galaxy 응답을 기다리고 있습니다. Galaxy의 인터넷과 Relay 앱을 확인해주세요."
  }catch{guard connectionGeneration==generation else{return};notice="서버로 동기화할 수 없습니다. Galaxy의 인터넷과 Relay 앱을 확인해주세요."}
 }
 // Live BLE only: never persist/retry a destructive call command or send through cloud.
 private func hangupCall(_ id:String,completion:@escaping(Bool)->Void){
  guard ble.ready,let p=settings else{completion(false);return}
  do{let cmd=NativeCrypto.uuid("cmd_"),nonce=NativeCrypto.uuid(),time=String(NativeCrypto.now());var d=["commandId":cmd,"type":"CALL_HANGUP","eventId":id,"timestamp":time,"nonce":nonce,"autoDismiss":""]
   d["signature"]=NativeCrypto.b64(NativeCrypto.mac(try NativeCrypto.bytes(p.commandKey),[cmd,"CALL_HANGUP",id,time,nonce,""].joined(separator:"\n")))
   callRequests[cmd]=(id,completion)
   try ble.send(["kind":"command","command":d])
   Task{[weak self] in try? await Task.sleep(nanoseconds:4_000_000_000);self?.callRequests.removeValue(forKey:cmd)?.done(false)}
  }catch{let callbacks=Array(callRequests.values);callRequests.removeAll();if callbacks.isEmpty{completion(false)}else{callbacks.forEach{$0.done(false)}}}
 }
 func command(_ type:String,_ event:RelayEvent?,replyText:String?=nil)async{
  guard !busy,let p=settings else{return};let eventId=event?.id ?? "";guard !history.pending(eventId) else{notice="이전 요청의 응답을 기다리고 있습니다.";return}
  let generation=connectionGeneration;busy=true;defer{if connectionGeneration==generation{busy=false}}
  do{
   let cmdId=NativeCrypto.uuid("cmd_"),nonce=NativeCrypto.uuid(),timestamp=String(NativeCrypto.now());var reply:[String:Any]?
   if let text=replyText,let action=event?.reply{guard !text.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty,text.utf16.count<=500,text.utf8.count<=1500 else{throw RelayError.invalid};let key=try NativeCrypto.bytes(p.linkKey),keyId=NativeCrypto.fingerprint(key);let aad=["GalaxyRelay/reply/v1",cmdId,eventId,timestamp,nonce,action.actionId,keyId].joined(separator:"\n");let sealed=try NativeCrypto.seal(Data(text.utf8),key:key,aad:aad);reply=["mode":"encrypted","version":1,"actionId":action.actionId,"keyId":keyId,"iv":sealed.iv,"ciphertext":sealed.ciphertext]}
   var d=["commandId":cmdId,"type":type,"eventId":eventId,"timestamp":timestamp,"nonce":nonce,"autoDismiss":""]
   if let r=reply{d["reply"]=String(data:try NativeCrypto.json(r),encoding:.utf8)!}
   var parts=[cmdId,type,eventId,timestamp,nonce,""];if let r=d["reply"]{parts.append(r)};d["signature"]=NativeCrypto.b64(NativeCrypto.mac(try NativeCrypto.bytes(p.commandKey),parts.joined(separator:"\n")))
   try history.begin(cmdId,eventId)
   if ble.ready{try ble.send(["kind":"command","command":d])}else{var body:[String:Any]=["commandId":cmdId,"type":type,"timestamp":Int64(timestamp)!,"nonce":nonce];if !eventId.isEmpty{body["eventId"]=eventId};if let r=reply{body["reply"]=r};_ = try await CloudLink(settings:p).call("/api/native/command",body)}
   guard connectionGeneration==generation else{return};notice=type=="REPLY" ? "Galaxy에 답장을 요청했습니다.":"Galaxy에 요청을 보냈습니다."
   if type=="DISMISS",let e=event{let ids=Set(history.state.events.filter{$0.id==e.id||e.notificationKey != nil&&$0.notificationKey==e.notificationKey}.map{$0.id});try history.deleteLocal(ids)}
  }catch{guard connectionGeneration==generation else{return};notice="요청 결과를 확인할 수 없습니다. 자동 재전송하지 않습니다. Galaxy에서 결과를 확인해주세요."}
 }
 func dismissEvents(_ events:[RelayEvent])async{
  var sent=Set<String>()
  for event in events{
   if let key=event.notificationKey{
    if !sent.insert(key).inserted{continue}
    if let active=history.state.events.first(where:{$0.notificationKey==key&&$0.androidState=="active"}){await command("DISMISS",active)}
   }else{try? history.deleteLocal([event.id])}
  }
 }
 private func detach(){
  if let p=settings{
   var pending=(Secrets.get("pending-revocations").flatMap{try? JSONDecoder().decode([PairSettings].self,from:$0)}) ?? []
   if !pending.contains(where:{$0.deviceId==p.deviceId}){pending.append(p)}
   if let data=try? JSONEncoder().encode(pending){try? Secrets.put("pending-revocations",data)}
  }
  finder.clear();connectionGeneration=UUID();callAlerts.stop(.disabled);ble.stop();ble.disconnectLocation.clear();settings=nil;busy=false;syncing=false;Secrets.remove("pair")
 }
 func pair(_ qr:PairQR)throws{detach();try ble.pair(qr);notice="QR을 인식했습니다. Galaxy와 연결하는 중입니다.";Task{await retryRevocations()}}
 func unpair()async{detach();notice="이 iPhone의 연결을 해제했습니다.";await retryRevocations()}
 func retryRevocations()async{
  guard !revoking else{return};revoking=true;defer{revoking=false}
  let pending=(Secrets.get("pending-revocations").flatMap{try? JSONDecoder().decode([PairSettings].self,from:$0)}) ?? []
  for p in pending{
   do{try await revokeRequest(p)}catch{if case CloudFailure.http(let status)=error,status==401||status==403{}else{continue}}
   var latest=(Secrets.get("pending-revocations").flatMap{try? JSONDecoder().decode([PairSettings].self,from:$0)}) ?? []
   latest.removeAll{$0.deviceId==p.deviceId};if let data=try? JSONEncoder().encode(latest){try? Secrets.put("pending-revocations",data)}
  }
 }
 nonisolated func userNotificationCenter(_ center:UNUserNotificationCenter,didReceive response:UNNotificationResponse,withCompletionHandler completionHandler:@escaping()->Void){Task{@MainActor in defer{completionHandler()};guard let id=response.notification.request.content.userInfo["eventId"] as? String else{return};history.read(id);if response.actionIdentifier==UNNotificationDefaultActionIdentifier{openEventId=id};if let text=response as? UNTextInputNotificationResponse,let event=history.state.events.first(where:{$0.id==id}){await command("REPLY",event,replyText:text.userText)}}}
}

