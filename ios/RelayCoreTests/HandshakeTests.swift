import XCTest
import CryptoKit
@testable import RelayCore
final class HandshakeTests:XCTestCase {
 func testJavaServerAndActualSwiftClientHandshake()throws{
  let pairId="550e8400-e29b-41d4-a716-446655440000"
  let root="ERERERERERERERERERERERERERERERERERERERERERE"
  let nonce="IiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiI"
  let publicKey="BGsX0fLhLEJH-Lzm5WOkQPJ3A32BLeszoPShOUXYmMKWT-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU"
  let helloProof="I956B-zt50JKfCbYpA-SCK5o8YV-Wp4L2vih8jKsngg"
  let serverNonce="MzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzMzM"
  let serverPublicKey="BOn-gONad3Y-m3cKi8Lpdhi0-j-CFX7eWWO6Uocu-I2UDxOawXCaWxRBA7vYxN-NIGtvtOorTRQl9QhwQqRbGhs"
  let welcomeProof="2g5A2r7jdxIGnoNwVNybtXasMmyDjGxfMt553CbsEUM"
  let session="25eSZ7uX1wiFEqUypynlZrDdtYujZu5dmiy2ADE4Kd0"
  let iv="71O0EZYLnVdrEXKW"
  let ciphertext="nzEW5xNmLGkgwk69QVXLfvZHUhG-9EHATiKkGVUNIrSws5N-GFB48HhQ_ik"
  let privateBytes=Data(repeating:0,count:31)+Data([1])
  var client=try ClientHandshake(pairId:pairId,root:NativeCrypto.bytes(root),privateKey:P256.KeyAgreement.PrivateKey(rawRepresentation:privateBytes),nonce:NativeCrypto.bytes(nonce))
  let hello=try XCTUnwrap(client.hello())
  XCTAssertEqual(hello["publicKey"] as? String,publicKey);XCTAssertEqual(hello["proof"] as? String,helloProof)
  // A duplicate notification callback must preserve the hello's nonce and key.
  XCTAssertNil(client.hello())
  let wire:[String:Any]=["kind":"welcome","nonce":serverNonce,"publicKey":serverPublicKey,"proof":welcomeProof]
  let key=try client.welcome(wire);XCTAssertEqual(key,try NativeCrypto.bytes(session))
  let plain=try NativeCrypto.open(SealedPacket(iv:iv,ciphertext:ciphertext),key:key,aad:NativeCrypto.wireAAD(pairId,"android-to-ios"))
  XCTAssertEqual(String(data:plain,encoding:.utf8),"fixture server configuration")
  XCTAssertNil(client.hello());XCTAssertThrowsError(try client.welcome(wire))
 }
 func testWelcomeMustBeAuthenticated()throws{
  let root=Data(repeating:7,count:32);var client=try ClientHandshake(pairId:"fixture",root:root);_ = client.hello()
  let wire:[String:Any]=["nonce":NativeCrypto.b64(Data(repeating:8,count:32)),"publicKey":NativeCrypto.b64(P256.KeyAgreement.PrivateKey().publicKey.x963Representation),"proof":NativeCrypto.b64(Data(repeating:0,count:32))]
  XCTAssertThrowsError(try client.welcome(wire)){error in guard case HandshakeFailure.proofMismatch=error else{return XCTFail("Expected proof rejection")}}
 }
}
