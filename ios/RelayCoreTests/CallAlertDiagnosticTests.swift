import XCTest
@testable import RelayCore
final class CallAlertDiagnosticTests:XCTestCase {
 func testKnownIncomingErrorsHaveDistinctCodes(){let domain="com.apple.CallKit.error.incomingcall";XCTAssertEqual(CallAlertFailure.classify(domain:domain,code:1),.unentitled);XCTAssertEqual(CallAlertFailure.classify(domain:domain,code:2),.duplicate);XCTAssertEqual(CallAlertFailure.classify(domain:domain,code:3),.focus);XCTAssertEqual(CallAlertFailure.classify(domain:domain,code:4),.blocked)}
 func testOtherDomainCannotMasqueradeAsCallKitPermissionFailure(){XCTAssertEqual(CallAlertFailure.classify(domain:"other",code:1),.unknown);XCTAssertEqual(CallAlertFailure.classify(domain:"com.apple.CallKit.error.requesttransaction",code:1),.unentitled);XCTAssertEqual(CallAlertFailure.classify(domain:"com.apple.CallKit.error.incomingcall",code:999),.unknown)}
 func testOnlyFixedCodesAndTimestampAreValid()throws{let value=CallAlertDiagnostic(code:"CK01",timestamp:12345);let data=try JSONEncoder().encode(value);let object=try XCTUnwrap(JSONSerialization.jsonObject(with:data) as? [String:Any]);XCTAssertEqual(Set(object.keys),["code","timestamp"]);XCTAssertEqual(try JSONDecoder().decode(CallAlertDiagnostic.self,from:data),value);XCTAssertFalse(CallAlertDiagnostic(code:"arbitrary",timestamp:0).valid);XCTAssertFalse(CallAlertDiagnostic(code:"CK01",timestamp:-1).valid)}
}
