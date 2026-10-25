import XCTest
@testable import RelayCore
final class PhoneNetworkTests:XCTestCase {
 func testUncertainPathDoesNotBecomeOffline(){XCTAssertEqual(PhoneNetwork.classify(available:false,unavailable:false),.unknown);XCTAssertEqual(PhoneNetwork.classify(available:true,unavailable:false),.online);XCTAssertEqual(PhoneNetwork.classify(available:false,unavailable:true),.offline)}
 func testWireValueIsOnlyConnectivityState()throws{let data=try JSONEncoder().encode(PhoneNetwork.offline);XCTAssertEqual(String(decoding:data,as:UTF8.self),"\"offline\"");XCTAssertThrowsError(try JSONDecoder().decode(PhoneNetwork.self,from:Data("\"wifi-name\"".utf8)))}
}
