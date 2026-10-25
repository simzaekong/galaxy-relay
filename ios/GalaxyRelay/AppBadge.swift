import SwiftUI
import ImageIO
import UIKit

enum AppIconDecoder {
 private static let cache:NSCache<NSString,UIImage> = {let cache=NSCache<NSString,UIImage>();cache.countLimit=128;return cache}()
 static func image(_ encoded:String?)->UIImage? {
  guard let encoded,encoded.utf8.count<=2048 else{return nil}
  if let image=cache.object(forKey:encoded as NSString){return image}
  guard let data=try? NativeCrypto.bytes(encoded),data.count<=1536,
        let source=CGImageSourceCreateWithData(data as CFData,nil),
        let properties=CGImageSourceCopyPropertiesAtIndex(source,0,nil) as? [CFString:Any],
        let width=properties[kCGImagePropertyPixelWidth] as? Int,
        let height=properties[kCGImagePropertyPixelHeight] as? Int,
        width>0,height>0,width<=64,height<=64,
        let image=UIImage(data:data) else{return nil}
  cache.setObject(image,forKey:encoded as NSString);return image
 }
}

struct AppBadge:View {
 let event:RelayEvent
 var size:CGFloat=46
 private var symbol:String{event.type=="call" ? "phone.fill":event.type=="sms" ? "message.fill":"app.fill"}
 private var color:Color{event.type=="call"||event.type=="sms" ? .green:.blue}
 var body:some View {
  Group{
   if let image=AppIconDecoder.image(event.appIcon){Image(uiImage:image).resizable().scaledToFill()}
   else{Image(systemName:symbol).font(.system(size:size*0.46,weight:.medium)).foregroundStyle(.white).frame(maxWidth:.infinity,maxHeight:.infinity).background(color)}
  }.frame(width:size,height:size).clipShape(RoundedRectangle(cornerRadius:size*0.24,style:.continuous)).accessibilityHidden(true)
 }
}
