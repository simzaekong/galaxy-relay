import Foundation
final class NoRedirect:NSObject,URLSessionTaskDelegate {
 func urlSession(_ session:URLSession,task:URLSessionTask,willPerformHTTPRedirection response:HTTPURLResponse,newRequest request:URLRequest,completionHandler:@escaping(URLRequest?)->Void){completionHandler(nil)}
}
enum CloudFailure:Error{case http(Int)}
struct CloudLink {
 let settings:PairSettings
 func call(_ path:String,_ value:[String:Any]=[:]) async throws -> [String:Any]{
  guard let base=URLComponents(string:settings.backend),base.scheme=="https",base.host != nil,base.user==nil,base.password==nil,base.query==nil,base.fragment==nil,(base.path==""||base.path=="/"),let url=URL(string:settings.backend)?.appendingPathComponent(String(path.dropFirst())),!settings.token.isEmpty else{throw RelayError.unavailable}
  var body=value;body["timestamp"]=body["timestamp"] ?? NativeCrypto.now();body["nonce"]=body["nonce"] ?? NativeCrypto.uuid()
  var req=URLRequest(url:url);req.httpMethod="POST";req.timeoutInterval=15;req.setValue("Bearer "+settings.token,forHTTPHeaderField:"Authorization");req.setValue("application/json",forHTTPHeaderField:"Content-Type");req.httpBody=try NativeCrypto.json(body)
  let config=URLSessionConfiguration.ephemeral;config.urlCache=nil;let session=URLSession(configuration:config,delegate:NoRedirect(),delegateQueue:nil);defer{session.invalidateAndCancel()}
  let (stream,response)=try await session.bytes(for:req);guard let http=response as? HTTPURLResponse else{throw RelayError.unavailable};guard (200..<300).contains(http.statusCode) else{throw CloudFailure.http(http.statusCode)}
  var data=Data();for try await byte in stream{guard data.count<65536 else{throw RelayError.invalid};data.append(byte)};return try NativeCrypto.object(data)
 }
}

