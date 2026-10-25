// swift-tools-version: 5.9
import PackageDescription
let package=Package(name:"RelayProtocol",platforms:[.macOS(.v13)],products:[.library(name:"RelayCore",targets:["RelayCore"])],targets:[.target(name:"RelayCore",path:"RelayCore"),.testTarget(name:"RelayCoreTests",dependencies:["RelayCore"],path:"RelayCoreTests")])

