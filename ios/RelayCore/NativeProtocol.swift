import Foundation
import CryptoKit
import Security

enum RelayError: Error { case invalid, expired, authentication, unavailable }
struct SealedPacket: Codable { var version: Int = 2; let iv: String; let ciphertext: String }
struct PairQR: Codable {
 let version: Int; let pairId: String; let serviceUUID: String; let secret: String; let expires: Int64; let name: String
 static func parse(_ text: String) throws -> PairQR {
  guard text.utf8.count <= 2048 else { throw RelayError.invalid }
  let q=try JSONDecoder().decode(PairQR.self,from:Data(text.utf8))
  guard q.version==2,UUID(uuidString:q.pairId) != nil,UUID(uuidString:q.serviceUUID) != nil,try NativeCrypto.bytes(q.secret).count==32 else { throw RelayError.invalid }
  guard q.expires>NativeCrypto.now(),q.expires<=NativeCrypto.now()+300_000 else { throw RelayError.expired };return q
 }
}
struct PairSettings: Codable {
 var capabilities:[String]?=nil
 let pairId: String; let serviceUUID: String; let galaxyName: String; let linkKey: String; let commandKey: String; let backend: String; let deviceId: String; let token: String
}
enum NativeCrypto {
 static func now() -> Int64 { Int64(Date().timeIntervalSince1970*1000) }
 static func uuid(_ prefix:String="") -> String { prefix+UUID().uuidString.lowercased() }
 static func bytes(_ s:String) throws -> Data { let padded=s.replacingOccurrences(of:"-",with:"+").replacingOccurrences(of:"_",with:"/");guard let d=Data(base64Encoded:padded+String(repeating:"=",count:(4-padded.count%4)%4)) else{throw RelayError.invalid};return d }
 static func b64(_ data:Data) -> String {data.base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"")}
 static func random(_ count:Int) -> Data {var d=Data(count:count);let result=d.withUnsafeMutableBytes{SecRandomCopyBytes(kSecRandomDefault,count,$0.baseAddress!)};precondition(result==errSecSuccess);return d}
 static func mac(_ key:Data,_ value:String)->Data{Data(HMAC<SHA256>.authenticationCode(for:Data(value.utf8),using:SymmetricKey(data:key)))}
 static func verify(_ key:Data,_ value:String,_ proof:String)->Bool {guard let p=try? bytes(proof) else{return false};return HMAC<SHA256>.isValidAuthenticationCode(p,authenticating:Data(value.utf8),using:SymmetricKey(data:key))}
 static func session(privateKey:P256.KeyAgreement.PrivateKey,remote:String,root:Data,transcript:String)throws->Data{
  let pub=try P256.KeyAgreement.PublicKey(x963Representation:bytes(remote)),shared=try privateKey.sharedSecretFromKeyAgreement(with:pub)
  let key=shared.hkdfDerivedSymmetricKey(using:SHA256.self,salt:mac(root,transcript),sharedInfo:Data("GalaxyRelay BLE v2 session".utf8),outputByteCount:32)
  return key.withUnsafeBytes{Data($0)}
 }
 static func seal(_ value:Data,key:Data,aad:String)throws->SealedPacket{guard key.count==32 else{throw RelayError.invalid};let sealed=try AES.GCM.seal(value,using:SymmetricKey(data:key),authenticating:Data(aad.utf8));return SealedPacket(iv:b64(Data(sealed.nonce)),ciphertext:b64(sealed.ciphertext+sealed.tag))}
 static func open(_ packet:SealedPacket,key:Data,aad:String,maximumCiphertext:Int=33000)throws->Data{let iv=try bytes(packet.iv),cipher=try bytes(packet.ciphertext);guard packet.version==2,iv.count==12,cipher.count>=16,cipher.count<=maximumCiphertext else{throw RelayError.invalid};return try AES.GCM.open(AES.GCM.SealedBox(nonce:AES.GCM.Nonce(data:iv),ciphertext:cipher.dropLast(16),tag:cipher.suffix(16)),using:SymmetricKey(data:key),authenticating:Data(aad.utf8))}
 static func json(_ value:[String:Any]) throws -> Data {try JSONSerialization.data(withJSONObject:value,options:[.sortedKeys])}
 static func object(_ data:Data)throws->[String:Any]{guard let v=try JSONSerialization.jsonObject(with:data) as? [String:Any] else{throw RelayError.invalid};return v}
 static func wireAAD(_ id:String,_ direction:String)->String{"GalaxyRelay/ble/v2\n"+id+"\n"+direction}
 static func cloudAAD(_ request:String,_ device:String)->String{"GalaxyRelay/native-cloud/v2\n"+request+"\n"+device}
 static func locationAAD(_ request:String,_ device:String)->String{"GalaxyRelay/native-location/v1\n"+request+"\n"+device}
 static func fingerprint(_ key:Data)->String{SHA256.hash(data:key).prefix(16).map{String(format:"%02x",$0)}.joined()}
 static func command(type:String,eventId:String,reply:[String:Any]?,settings:PairSettings)throws->[String:String]{
  var d=["commandId":uuid("cmd_"),"type":type,"eventId":eventId,"timestamp":String(now()),"nonce":uuid(),"autoDismiss":""]
  if let reply {d["reply"]=String(data:try json(reply),encoding:.utf8)!}
  var parts=[d["commandId"]!,type,eventId,d["timestamp"]!,d["nonce"]!,""];if let r=d["reply"]{parts.append(r)}
  d["signature"]=b64(mac(try bytes(settings.commandKey),parts.joined(separator:"\n")));return d
 }
}
struct FrameBuffer {
 private var bytes=Data();private var started:Date?
 mutating func feed(_ part:Data)throws->Data? {
  if let start=started,Date().timeIntervalSince(start)>30{bytes.removeAll();started=nil}
  if started==nil{started=Date()};guard !part.isEmpty,part.count<=512 else{throw RelayError.invalid};bytes.append(part)
  guard bytes.count<=65540 else{throw RelayError.invalid};guard bytes.count>=4 else{return nil}
  let count=bytes.prefix(4).reduce(0){($0<<8)|Int($1)};guard count>=2,count<=65536,bytes.count<=count+4 else{throw RelayError.invalid}
  guard bytes.count==count+4 else{return nil};let result=Data(bytes.dropFirst(4));bytes.removeAll();started=nil;return result
 }
 static func pack(_ data:Data)throws->[Data]{guard data.count>=2,data.count<=65536 else{throw RelayError.invalid};var count=UInt32(data.count).bigEndian;var out=withUnsafeBytes(of:&count){Data($0)};out.append(data);return stride(from:0,to:out.count,by:20).map{Data(out[$0..<min($0+20,out.count)])}}
}

