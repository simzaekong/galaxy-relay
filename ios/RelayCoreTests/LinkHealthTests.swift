import XCTest
@testable import RelayCore
final class LinkHealthTests:XCTestCase {
 func testSparseProbe(){var h=LinkHealth();h.reset(now:0);XCTAssertFalse(h.due(now:299));XCTAssertTrue(h.due(now:300))}
 func testOnlyOneAndMatchingResponse(){var h=LinkHealth();XCTAssertTrue(h.begin("one",now:300));XCTAssertFalse(h.begin("two",now:301));XCTAssertFalse(h.acknowledge("wrong",now:302));XCTAssertFalse(h.expired(now:329));XCTAssertTrue(h.expired(now:330));XCTAssertTrue(h.acknowledge("one",now:331));XCTAssertFalse(h.due(now:630));XCTAssertTrue(h.due(now:631))}
 func testNewSessionDiscardsOldProbe(){var h=LinkHealth();_ = h.begin("old",now:1);h.reset(now:2);XCTAssertFalse(h.acknowledge("old",now:3));XCTAssertFalse(h.expired(now:999))}
}
