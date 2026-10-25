import XCTest
@testable import GalaxyRelay
@MainActor final class DisconnectLocationRecorderTests:XCTestCase {
 private func fix(_ pair:String)->DisconnectFix{DisconnectFix(pairId:pair,latitude:37,longitude:127,accuracy:100,triggeredAt:100000,measuredAt:100500)}
 func testLocationCacheIsBoundToPairAndUnpairHidesIt()throws{let data=try JSONEncoder().encode(fix("first"));let r=DisconnectLocationRecorder(load:{data},store:{_ in XCTFail("No capture during bind")},erase:{})
  r.bind("first");XCTAssertEqual(r.record?.pairId,"first");r.bind("other");XCTAssertNil(r.record);r.bind("first");r.detached();XCTAssertNil(r.record)
 }
 func testClearErasesProtectedRecordWithoutRequestingLocation()throws{var erased=false;let data=try JSONEncoder().encode(fix("first"));let r=DisconnectLocationRecorder(load:{data},store:{_ in XCTFail("Unexpected query")},erase:{erased=true});r.bind("first");r.clear();XCTAssertTrue(erased);XCTAssertNil(r.record)}
 func testAppOpenAndUnconfirmedLossNeverCapture()async throws{var writes=0;let r=DisconnectLocationRecorder(load:{nil},store:{_ in writes+=1},erase:{});r.bind("fixture");r.foregroundChanged(true);r.connectionLost();try await Task.sleep(nanoseconds:3_200_000_000);XCTAssertEqual(writes,0);XCTAssertNil(r.record);r.detached()}
 func testInvalidProtectedFixIsNotDisplayed()throws{let value=DisconnectFix(pairId:"fixture",latitude:91,longitude:127,accuracy:100,triggeredAt:100000,measuredAt:100500);let data=try JSONEncoder().encode(value);let r=DisconnectLocationRecorder(load:{data},store:{_ in XCTFail("Unexpected capture")},erase:{});r.bind("fixture");XCTAssertNil(r.record)}
}
