import Foundation
import CallKit

@MainActor protocol CallAlertPresenting:AnyObject {
 var onEnd:((UUID,@escaping(Bool)->Void)->Void)?{get set}
 var onAnswer:((UUID)->Void)?{get set}
 var onReset:(()->Void)?{get set}
 func show(_ id:UUID,name:String,completion:@escaping(Result<Void,CallAlertFailure>)->Void)
 func update(_ id:UUID,name:String)
 func connected(_ id:UUID,at:Date)
 func end(_ id:UUID,reason:CallAlertEnd)
}
/// Call state UI and explicit remote hangup. No microphone, call audio or remote answer.
@MainActor final class NativeCallAlertProvider:NSObject,CallAlertPresenting,@preconcurrency CXProviderDelegate {
 var onEnd:((UUID,@escaping(Bool)->Void)->Void)?;var onAnswer:((UUID)->Void)?;var onReset:(()->Void)?
 private let controller=CXCallController()
 private var connections:[UUID:Date]=[:]
 private let provider:CXProvider
 private let observer=CXCallObserver()
 private var reported=Set<UUID>()
 override init(){let c=CXProviderConfiguration();c.supportsVideo=false;c.maximumCallGroups=1;c.maximumCallsPerCallGroup=1;c.supportedHandleTypes=[.generic];c.includesCallsInRecents=false;provider=CXProvider(configuration:c);super.init();provider.setDelegate(self,queue:.main)}
 private func content(_ name:String)->CXCallUpdate{let c=CXCallUpdate();c.remoteHandle=CXHandle(type:.generic,value:"Galaxy 수신 알림");c.localizedCallerName=name;c.hasVideo=false;c.supportsHolding=false;c.supportsDTMF=false;c.supportsGrouping=false;c.supportsUngrouping=false;return c}
 func show(_ id:UUID,name:String,completion:@escaping(Result<Void,CallAlertFailure>)->Void){guard !observer.calls.contains(where:{!$0.hasEnded}) else{completion(.failure(.busy));return};reported.insert(id);provider.reportNewIncomingCall(with:id,update:content(name)){error in let failure=error.map{CallAlertFailure.classify(domain:($0 as NSError).domain,code:($0 as NSError).code)};Task{@MainActor in if let failure{completion(.failure(failure))}else{completion(.success(()))}}}}
 func update(_ id:UUID,name:String){guard reported.contains(id) else{return};provider.reportCall(with:id,updated:content(name))}
 func connected(_ id:UUID,at:Date){guard reported.contains(id),connections[id]==nil else{return};guard !observer.calls.contains(where:{!$0.hasEnded && $0.uuid != id}) else{end(id,reason:.answeredElsewhere);return};connections[id]=at;controller.request(CXTransaction(action:CXAnswerCallAction(call:id))){[weak self] error in if error != nil{Task{@MainActor in self?.end(id,reason:.answeredElsewhere)}}}}
 func end(_ id:UUID,reason:CallAlertEnd){reported.remove(id);connections.removeValue(forKey:id);let ended:CXCallEndedReason=reason == .answeredElsewhere ? .answeredElsewhere:reason == .failed ? .failed:reason == .timeout ? .unanswered:.remoteEnded;provider.reportCall(with:id,endedAt:Date(),reason:ended)}
 func providerDidReset(_ provider:CXProvider){reported.removeAll();connections.removeAll();onReset?()}
 func provider(_ provider:CXProvider,perform action:CXAnswerCallAction){guard reported.contains(action.callUUID),let date=connections[action.callUUID] else{action.fail();onAnswer?(action.callUUID);return};action.fulfill(withDateConnected:date)}
 func provider(_ provider:CXProvider,perform action:CXEndCallAction){guard reported.contains(action.callUUID),let onEnd else{action.fail();return};onEnd(action.callUUID){ok in if ok{action.fulfill()}else{action.fail()}}}
 func provider(_ provider:CXProvider,timedOutPerforming action:CXAction){action.fail();onReset?()}
}
