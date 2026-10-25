import XCTest
import CryptoKit
@testable import RelayCore
final class ProtocolTests:XCTestCase{
 func testLocationPacketIsBoundToRequestDeviceAndProtocol()throws{
  let key=Data(repeating:9,count:32),plain=Data("one-shot location".utf8),aad=NativeCrypto.locationAAD("request","device")
  let packet=try NativeCrypto.seal(plain,key:key,aad:aad)
  XCTAssertEqual(try NativeCrypto.open(packet,key:key,aad:aad),plain)
  for wrong in [NativeCrypto.locationAAD("other","device"),NativeCrypto.locationAAD("request","other"),NativeCrypto.cloudAAD("request","device")]{XCTAssertThrowsError(try NativeCrypto.open(packet,key:key,aad:wrong))}
 }
 func testAuthenticatedSessionAndDirection()throws{
  let a=P256.KeyAgreement.PrivateKey(),b=P256.KeyAgreement.PrivateKey(),root=Data(repeating:17,count:32),transcript="welcome\nfixture"
  let key=try NativeCrypto.session(privateKey:a,remote:NativeCrypto.b64(b.publicKey.x963Representation),root:root,transcript:transcript)
  let other=try NativeCrypto.session(privateKey:b,remote:NativeCrypto.b64(a.publicKey.x963Representation),root:root,transcript:transcript);XCTAssertEqual(key,other)
  let aad=NativeCrypto.wireAAD("pair","android-to-ios"),plain=Data("안녕 🙂".utf8),packet=try NativeCrypto.seal(plain,key:key,aad:aad)
  XCTAssertEqual(try NativeCrypto.open(packet,key:key,aad:aad),plain);XCTAssertThrowsError(try NativeCrypto.open(packet,key:key,aad:NativeCrypto.wireAAD("pair","ios-to-android")))
  XCTAssertFalse(NativeCrypto.verify(root,transcript+"tampered",NativeCrypto.b64(NativeCrypto.mac(root,transcript))))
 }
 func testFramesAndBounds()throws{let data=Data(repeating:71,count:3072);var buffer=FrameBuffer(),result:Data?;for chunk in try FrameBuffer.pack(data){result=try buffer.feed(chunk)};XCTAssertEqual(result,data);XCTAssertThrowsError(try buffer.feed(Data([127,127,127,127])))}
 func testQRExpirationAndShape()throws{
  let qr=PairQR(version:2,pairId:NativeCrypto.uuid(),serviceUUID:NativeCrypto.uuid(),secret:NativeCrypto.b64(Data(repeating:1,count:32)),expires:NativeCrypto.now()-1,name:"fixture")
  let text=String(data:try JSONEncoder().encode(qr),encoding:.utf8)!;XCTAssertThrowsError(try PairQR.parse(text));XCTAssertThrowsError(try PairQR.parse("{}"))
 }
 func testNodeAndJavaCompatibleCiphertext()throws{
  let packet=SealedPacket(iv:"FxcXFxcXFxcXFxcX",ciphertext:"aY_BwSfSw6xJy7p7Rthc-glkro7z7JnxaPTpEqqAtk7sOQr-t98")
  let plain=try NativeCrypto.open(packet,key:NativeCrypto.bytes("ERERERERERERERERERERERERERERERERERERERERERE"),aad:"GalaxyRelay/native-cloud/v2\nfixture-request\nfixture-device")
  XCTAssertEqual(String(data:plain,encoding:.utf8),"native interop fixture")
 }
}
