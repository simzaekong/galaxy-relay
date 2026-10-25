import Foundation
import Security
enum Secrets {
 static func get(_ name:String)->Data?{var value:CFTypeRef?;let q:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"GalaxyRelay.Native.v2",kSecAttrAccount as String:name,kSecReturnData as String:true,kSecMatchLimit as String:kSecMatchLimitOne];guard SecItemCopyMatching(q as CFDictionary,&value)==errSecSuccess else{return nil};return value as? Data}
 static func put(_ name:String,_ value:Data)throws{let q:[String:Any]=[kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"GalaxyRelay.Native.v2",kSecAttrAccount as String:name];let update:[String:Any]=[kSecValueData as String:value,kSecAttrAccessible as String:kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly];let status=SecItemUpdate(q as CFDictionary,update as CFDictionary);if status==errSecItemNotFound{var added=q;update.forEach{added[$0.key]=$0.value};guard SecItemAdd(added as CFDictionary,nil)==errSecSuccess else{throw RelayError.unavailable}}else if status != errSecSuccess{throw RelayError.unavailable}}
 static func remove(_ name:String){SecItemDelete([kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"GalaxyRelay.Native.v2",kSecAttrAccount as String:name] as CFDictionary)}
 static func historyKey()throws->Data{if let key=get("history-key"){return key};let key=NativeCrypto.random(32);try put("history-key",key);return key}
}

