import CryptoKit
import Foundation

/// Проверка кода Premium — те же коды, что и в Android-приложении и боте @Vibe2Scool_bot.
enum Premium {
    static let botURL = URL(string: "https://t.me/Vibe2Scool_bot")!
    static let buyURL = URL(string: "https://t.me/Vibe2Scool_bot?start=premium")!
    static let apkURL = "https://github.com/StarTegsPo/StarApp/releases/download/warmup-latest/TikTokWarmup.apk"

    private static let hashes: Set<String> = {
        guard let url = Bundle.main.url(forResource: "codes", withExtension: "sha"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return Set(text.split(whereSeparator: \.isNewline).map { String($0).trimmingCharacters(in: .whitespaces) })
    }()

    /// Нормализованный код (STAR-XXXX-XXXX-XXXX) или nil, если код неверный.
    static func validate(_ raw: String) -> String? {
        let norm = raw.uppercased().filter { ("A"..."Z").contains($0) || ("0"..."9").contains($0) }
        guard !norm.isEmpty else { return nil }
        let digest = SHA256.hash(data: Data(("tw-premium-v1:" + norm).utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        guard hashes.contains(hex) else { return nil }
        let chars = Array(norm)
        guard chars.count == 16 else { return norm }
        return "\(String(chars[0..<4]))-\(String(chars[4..<8]))-\(String(chars[8..<12]))-\(String(chars[12..<16]))"
    }
}
