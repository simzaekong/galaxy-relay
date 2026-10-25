import AppKit
import Foundation
let image=NSImage(size:NSSize(width:1024,height:1024))
image.lockFocus()
NSColor(calibratedRed:0.40,green:0.32,blue:0.85,alpha:1).setFill()
NSBezierPath(roundedRect:NSRect(x:0,y:0,width:1024,height:1024),xRadius:210,yRadius:210).fill()
NSColor.white.setStroke()
let path=NSBezierPath();path.lineWidth=65;path.lineCapStyle = .round;path.lineJoinStyle = .round
path.move(to:NSPoint(x:250,y:660));path.line(to:NSPoint(x:760,y:660))
path.move(to:NSPoint(x:635,y:790));path.line(to:NSPoint(x:765,y:660));path.line(to:NSPoint(x:635,y:530))
path.move(to:NSPoint(x:774,y:364));path.line(to:NSPoint(x:264,y:364))
path.move(to:NSPoint(x:389,y:494));path.line(to:NSPoint(x:259,y:364));path.line(to:NSPoint(x:389,y:234));path.stroke()
image.unlockFocus()
let root=URL(fileURLWithPath:FileManager.default.currentDirectoryPath).appendingPathComponent("ios/GalaxyRelay/Assets.xcassets")
let output=root.appendingPathComponent("AppIcon.appiconset")
try FileManager.default.createDirectory(at:output,withIntermediateDirectories:true)
let bitmap=NSBitmapImageRep(data:image.tiffRepresentation!)!
try bitmap.representation(using:.png,properties:[:])!.write(to:output.appendingPathComponent("icon.png"))
try Data(#"{"info":{"author":"xcode","version":1}}"#.utf8).write(to:root.appendingPathComponent("Contents.json"))
try Data(#"{"images":[{"filename":"icon.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"author":"xcode","version":1}}"#.utf8).write(to:output.appendingPathComponent("Contents.json"))

