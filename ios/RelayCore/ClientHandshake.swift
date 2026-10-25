import Foundation
import CryptoKit

enum HandshakeFailure:Error {case invalidWelcome,proofMismatch,invalidPublicKey}
/// One hello per physical connection. Duplicate notification callbacks must not rotate keys.
struct ClientHandshake {
 let pairId:String
 private let root:Data
 private var privateKey:P256.KeyAgreement.PrivateKey?
 private let nonce:String
 private let publicKey:String
 private var started=false
 init(pairId:String,root:Data,privateKey:P256.KeyAgreement.PrivateKey=P256.KeyAgreement.PrivateKey(),nonce:Data=NativeCrypto.random(32))throws{
  guard root.count==32,nonce.count==32 else{throw RelayError.invalid}
  self.pairId=pairId;self.root=root;self.privateKey=privateKey;self.nonce=NativeCrypto.b64(nonce);self.publicKey=NativeCrypto.b64(privateKey.publicKey.x963Representation)
 }
 mutating func hello()->[String:Any]?{
  guard !started else{return nil};started=true
  let transcript="hello\n"+pairId+"\n"+nonce+"\n"+publicKey
  return ["kind":"hello","pairId":pairId,"nonce":nonce,"publicKey":publicKey,"proof":NativeCrypto.b64(NativeCrypto.mac(root,transcript))]
 }
 mutating func welcome(_ wire:[String:Any])throws->Data{
  guard started,let key=privateKey,let serverNonce=wire["nonce"] as? String,let pub=wire["publicKey"] as? String,let proof=wire["proof"] as? String,(try? NativeCrypto.bytes(serverNonce).count)==32 else{throw HandshakeFailure.invalidWelcome}
  let transcript="welcome\n"+pairId+"\n"+nonce+"\n"+serverNonce+"\n"+publicKey+"\n"+pub
  guard NativeCrypto.verify(root,transcript,proof) else{throw HandshakeFailure.proofMismatch}
  let session:Data
  do{session=try NativeCrypto.session(privateKey:key,remote:pub,root:root,transcript:transcript)}catch{throw HandshakeFailure.invalidPublicKey}
  privateKey=nil;return session
 }
}
