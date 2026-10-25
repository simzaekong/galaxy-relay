import Foundation
import Combine
import UIKit

@MainActor final class CallAlerts:ObservableObject {
 @Published private(set) var enabled:Bool
 @Published private(set) var diagnostic:CallAlertDiagnostic?
 @Published private(set) var status="꺼짐 · 일반 전화 알림 사용"
 @Published private(set) var connectedAt:Date?
 @Published private(set) var caller=""
 var onHangup:((String,@escaping(Bool)->Void)->Void)?
 private var reported=false,ending=false
 var onNotice:((String)->Void)?;var onFailure:((RelayEvent)->Void)?
 private let factory:@MainActor ()->CallAlertPresenting;private let save:(Bool)->Void
 private let saveDiagnostic:(Data?)->Void
 private var presenter:CallAlertPresenting?,policy=CallAlertPolicy(),timer:Task<Void,Never>?,token=UUID()
 private var backgroundTask=UIBackgroundTaskIdentifier.invalid
 private var current:RelayEvent?,uiId:UUID?,presentedName=""
 init(enabled:Bool=UserDefaults.standard.bool(forKey:"callAlertsEnabled"),factory:@escaping @MainActor ()->CallAlertPresenting={NativeCallAlertProvider()},save:@escaping(Bool)->Void={UserDefaults.standard.set($0,forKey:"callAlertsEnabled")},loadDiagnostic:Data?=UserDefaults.standard.data(forKey:"callAlertDiagnostic"),saveDiagnostic:@escaping(Data?)->Void={if let data=$0{UserDefaults.standard.set(data,forKey:"callAlertDiagnostic")}else{UserDefaults.standard.removeObject(forKey:"callAlertDiagnostic")}}){self.enabled=enabled;self.factory=factory;self.save=save;self.saveDiagnostic=saveDiagnostic;if let data=loadDiagnostic,data.count<=256,let value=try? JSONDecoder().decode(CallAlertDiagnostic.self,from:data),value.valid{diagnostic=value};if enabled{status="켜짐 · Galaxy의 실제 전화 수신을 기다립니다."}}
 func setEnabled(_ value:Bool){enabled=value;save(value);if !value{stop(.disabled)};status=value ? "켜짐 · Bluetooth로 연결된 동안 사용":"꺼짐 · 일반 전화 알림 사용"}
 /// Returns true when a ringing event belongs to the CallKit path (including stale suppression).
 func receive(_ e:RelayEvent,live:Bool,hidePreview:Bool,now:Int64=NativeCrypto.now())->Bool {
  guard e.type=="call" else{return false};let id=e.callId ?? e.id
  let effect=policy.receive(id:id,state:e.state ?? "",timestamp:e.timestamp,now:now,live:live,enabled:enabled)
  switch effect {
  case .start:
   token=UUID();let ticket=token,uuid=UUID();uiId=uuid;current=e;reported=false;caller=name(e,hidePreview);presentedName=caller;status="Galaxy 전화 수신 알림 · 통화는 Galaxy에서 받으세요."
   if presenter==nil{let p=factory();p.onAnswer={[weak self] _ in self?.onNotice?("전화는 Galaxy에서 받아주세요. iPhone은 통화 상태와 종료만 제어합니다.")};p.onEnd={[weak self] id,done in guard let self,self.uiId==id else{done(false);return};self.hangup(done)};p.onReset={[weak self] in self?.stop(.reset)};presenter=p}
   backgroundTask=UIApplication.shared.beginBackgroundTask(withName:"galaxy-call-alert"){[weak self] in Task{@MainActor in guard let self,self.token==ticket else{return};self.stop(.timeout)}}
   guard backgroundTask != .invalid else{failed(.execution);return enabled && e.state=="ringing"}
   let delay=max(0,(policy.expiresAt ?? now)-now)
   timer=Task{[weak self] in do{try await Task.sleep(nanoseconds:UInt64(delay)*1_000_000)}catch{return};guard !Task.isCancelled,let self,self.token==ticket else{return};self.stop(.timeout)}
   presenter?.show(uuid,name:presentedName){[weak self] result in guard let self else{return};guard self.token==ticket,self.uiId==uuid,self.policy.active==id else{if case .success=result{self.presenter?.end(uuid,reason:.ended)};return};switch result{case .success:self.reported=true;self.record(nil);self.presenter?.update(uuid,name:self.presentedName);if let date=self.connectedAt{self.presenter?.connected(uuid,at:date)};case .failure(let failure):self.failed(failure)}}
  case .update:current=e;presentedName=name(e,hidePreview);caller=presentedName;if let uiId{presenter?.update(uiId,name:presentedName)}
  case .connected:
   connectedAt=Date(timeIntervalSince1970:Double(e.timestamp)/1000);timer?.cancel();timer=nil
   status="Galaxy에서 통화 중 · 음성은 Galaxy에서 들립니다."
   if reported,let uiId,let connectedAt{presenter?.connected(uiId,at:connectedAt)}
   releaseBackground()
  case .end(_,let reason):finish(reason)
  case .none:break
  }
  return enabled && e.state=="ringing"
 }
 func hangup(_ completion:@escaping(Bool)->Void={_ in}){guard !ending,let id=current.map({$0.callId ?? $0.id}),let onHangup else{completion(false);return};ending=true;status="Galaxy에 통화 종료를 요청하고 있습니다."
  onHangup(id){[weak self] ok in guard let self else{completion(false);return};guard let current=self.current else{completion(ok);return};guard (current.callId ?? current.id)==id else{completion(false);return};self.ending=false;completion(ok);if ok{self.stop(.ended)}else{self.status="통화 종료를 확인하지 못했습니다. Galaxy에서 확인해주세요.";self.onNotice?(self.status)}}
 }
 private func releaseBackground(){if backgroundTask != .invalid{UIApplication.shared.endBackgroundTask(backgroundTask);backgroundTask = .invalid}}
 private func record(_ failure:CallAlertFailure?){let value=CallAlertDiagnostic(code:failure?.rawValue ?? "CK00",timestamp:NativeCrypto.now());diagnostic=value;saveDiagnostic(try? JSONEncoder().encode(value))}
 private func failed(_ failure:CallAlertFailure){let event=current;stop(.failed);record(failure);status="통화형 알림 표시 실패 · "+failure.rawValue;onNotice?(failure.message+" ("+failure.rawValue+") 일반 알림으로 표시합니다.");if let event{onFailure?(event)}}
 var diagnosticSummary:String{guard let diagnostic else{return "아직 CallKit 요청 결과가 없습니다."};return diagnostic.code+" · "+diagnostic.message+"\n"+Date(timeIntervalSince1970:Double(diagnostic.timestamp)/1000).formatted(date:.abbreviated,time:.standard)}
 func clearDiagnostic(){diagnostic=nil;saveDiagnostic(nil)}
 private func name(_ e:RelayEvent,_ hidden:Bool)->String{let value=hidden ? "전화 수신":(e.contact ?? e.number ?? e.title ?? "발신자 정보 없음");return String(value.prefix(100))+" · Galaxy 알림"}
 func stop(_ reason:CallAlertEnd){if case .end=policy.stop(reason){finish(reason)}}
 private func finish(_ reason:CallAlertEnd){token=UUID();timer?.cancel();timer=nil;releaseBackground();connectedAt=nil;caller="";reported=false;ending=false;if let uiId{presenter?.end(uiId,reason:reason)};uiId=nil;current=nil;status=switch reason{case .answeredElsewhere:"Galaxy에서 전화를 받았습니다.";case .connectionLost:"Bluetooth 연결이 끊겨 수신 알림을 닫았습니다.";case .timeout:"제한 시간이 끝나 수신 알림을 닫았습니다.";case .failed:"통화형 알림을 표시하지 못했습니다.";case .acknowledged:"iPhone 수신 알림을 닫았습니다.";default:"수신 알림이 종료되었습니다."}}
 func privacyChanged(_ hidden:Bool){guard let current,let uiId else{return};presentedName=name(current,hidden);caller=presentedName;presenter?.update(uiId,name:presentedName)}
}
