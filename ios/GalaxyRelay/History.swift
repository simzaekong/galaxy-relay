import Foundation
import Combine
struct ReplyCapability:Codable {let actionId:String;let label:String}
struct RelayEvent:Codable,Identifiable {
 let id:String;let type:String;let timestamp:Int64
 var appIcon:String?;var app:String?;var packageName:String?;var title:String?;var body:String?;var notificationKey:String?;var number:String?;var contact:String?;var sender:String?;var state:String?;var callId:String?;var replacesEventId:String?;var targetEventId:String?;var commandId:String?;var result:String?;var reply:ReplyCapability?
 var offlineOnly:Bool?;var unanswered:Bool?;var read:Bool?;var androidState:String?
 var label:String{app ?? (type=="call" ? "전화":type=="sms" ? "문자":"Galaxy")}
 var heading:String{type=="call" ? (contact ?? number ?? "알 수 없는 번호"):title ?? sender ?? "Galaxy 알림"}
 var message:String{if type=="call",unanswered==true{return "Galaxy에서 받지 않은 전화가 있습니다."};return type=="call" ? (state=="ringing" ? "Galaxy로 전화가 오고 있습니다.":state=="offhook" ? "통화가 연결되었습니다.":"통화가 종료되었습니다."):body ?? ""}
 var groupKey:String{(packageName ?? label)+"\n"+(sender ?? title ?? contact ?? number ?? "")}
}
struct PendingCommand:Codable {let commandId:String;let eventId:String;let timestamp:Int64;var result:String?}
struct HistoryState:Codable {var events:[RelayEvent]=[];var seen:[String]=[];var removed:[String]=[];var commands:[PendingCommand]=[]}
@MainActor final class History:ObservableObject {
 @Published private(set) var state=HistoryState()
 private var loadFailed=false
 private let file:URL
 init(){file=FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("relay-history-v2.json");do{if FileManager.default.fileExists(atPath:file.path){let size=(try FileManager.default.attributesOfItem(atPath:file.path)[.size] as? NSNumber)?.intValue ?? 0;guard size<=70*1024*1024 else{throw RelayError.invalid};let packet=try JSONDecoder().decode(SealedPacket.self,from:Data(contentsOf:file));state=try JSONDecoder().decode(HistoryState.self,from:NativeCrypto.open(packet,key:Secrets.historyKey(),aad:"GalaxyRelay/history/v2",maximumCiphertext:48*1024*1024))}}catch{loadFailed=true}}
 func save()throws{let plain=try JSONEncoder().encode(state);guard plain.count<=48*1024*1024-16 else{throw RelayError.invalid};let packet=try NativeCrypto.seal(plain,key:Secrets.historyKey(),aad:"GalaxyRelay/history/v2");try FileManager.default.createDirectory(at:file.deletingLastPathComponent(),withIntermediateDirectories:true);try JSONEncoder().encode(packet).write(to:file,options:[.atomic,.completeFileProtectionUntilFirstUserAuthentication])}
 func apply(_ event:RelayEvent)throws->Bool {
  guard !loadFailed else{throw RelayError.unavailable}
  guard UUID(uuidString:String(event.id.dropFirst(4))) != nil,event.id.hasPrefix("evt_"),["notification","sms","call","removed","command_result"].contains(event.type),event.timestamp<=NativeCrypto.now()+300000,event.timestamp>=NativeCrypto.now()-86400000 else{throw RelayError.invalid}
  if state.seen.contains(event.id){return false}
  var next=state;var display=false;next.seen.append(event.id);if next.seen.count>10000{next.seen.removeFirst(next.seen.count-10000)}
  if event.type=="removed",let target=event.targetEventId{
   let keys=next.events.filter{$0.id==target}.compactMap{$0.notificationKey};next.events.removeAll{$0.id==target || $0.notificationKey.map{keys.contains($0)}==true};next.removed.append(target)
  }else if event.type=="command_result"{
   if let command=event.commandId,let index=next.commands.firstIndex(where:{$0.commandId==command}){next.commands[index].result=event.result}
   if event.result=="REMOVED"||event.result=="NOT_FOUND",let target=event.targetEventId{let keys=next.events.filter{$0.id==target}.compactMap{$0.notificationKey};next.events.removeAll{$0.id==target||$0.notificationKey.map{keys.contains($0)}==true};next.removed.append(target)}
  }else if let predecessor=event.replacesEventId,next.removed.contains(predecessor){next.removed.append(event.id)
  }else if event.type=="call",next.events.contains(where:{$0.type=="call"&&($0.callId ?? $0.id)==(event.callId ?? event.id)&&$0.timestamp>event.timestamp}){/* A delayed ring must not revive an ended call. */
  }else if !next.removed.contains(event.id){
   if let old=event.replacesEventId,let i=next.events.firstIndex(where:{$0.id==old}){next.events[i].androidState="updated"}
   if event.type=="call",let call=event.callId {next.events.removeAll{$0.type=="call"&&($0.callId ?? $0.id)==call}}
   display=event.type=="notification"||event.type=="sms"||event.type=="call"&&(event.state=="ringing"||event.unanswered==true&&NativeCrypto.now()-event.timestamp<300000);var record=event;record.read=false;record.androidState=record.notificationKey==nil ? "unavailable":"active";next.events.append(record);next.events.sort{$0.timestamp>$1.timestamp};if next.events.count>5000{next.events.removeLast(next.events.count-5000)}
  }
  if next.removed.count>5000{next.removed.removeFirst(next.removed.count-5000)}
  let previous=state;state=next;do{try save()}catch{state=previous;throw error};return display
 }
 func read(_ id:String){if let i=state.events.firstIndex(where:{$0.id==id}){state.events[i].read=true;try? save()}}
 func pending(_ id:String)->Bool{state.commands.contains{$0.eventId==id&&$0.result==nil&&NativeCrypto.now()-$0.timestamp<300000}}
 func begin(_ id:String,_ event:String)throws{guard !pending(event) else{throw RelayError.unavailable};state.commands.append(PendingCommand(commandId:id,eventId:event,timestamp:NativeCrypto.now()));state.commands=Array(state.commands.suffix(256));try save()}
 func deleteLocal(_ ids:Set<String>)throws{state.events.removeAll{ids.contains($0.id)};state.removed.append(contentsOf:ids);state.removed=Array(state.removed.suffix(5000));try save()}
 func clear()throws{loadFailed=false;state=HistoryState();try save()}
}

