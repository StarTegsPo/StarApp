import SwiftUI

/// Темы как в Android-приложении: тёмная (чёрно-красная), светлая, тёмно-красная.
struct Theme: Equatable {
    let id: String
    let title: String
    let light: Bool
    let bg: Color
    let card: Color
    let line: Color
    let text: Color
    let hint: Color
    let a1: Color
    let a2: Color
    let a3: Color
    let soft: Color
    let spark: Color

    static let gold = Color(hex: 0xFFB648)
    static let good = Color(hex: 0x34D399)

    static let dark = Theme(id: "dark", title: "🖤 Тёмная", light: false,
                            bg: Color(hex: 0x070708), card: Color(hex: 0x141416, alpha: 0.8),
                            line: Color.white.opacity(0.12), text: Color(hex: 0xF5F5F6), hint: Color(hex: 0x8E8E96),
                            a1: Color(hex: 0xE50914), a2: Color(hex: 0xFF4D4D), a3: Color(hex: 0x7A0008),
                            soft: Color.white.opacity(0.13), spark: Color(hex: 0xFF6A3D))

    static let light = Theme(id: "light", title: "☀️ Светлая", light: true,
                             bg: Color(hex: 0xF7F2F3), card: Color.white.opacity(0.92),
                             line: Color.black.opacity(0.08), text: Color(hex: 0x1B1B1F), hint: Color(hex: 0x6F6A72),
                             a1: Color(hex: 0xE11D48), a2: Color(hex: 0xFF6B6B), a3: Color(hex: 0xB3122E),
                             soft: Color.black.opacity(0.07), spark: Color(hex: 0xFF8FA3))

    static let wine = Theme(id: "wine", title: "🍷 Тёмно-красная", light: false,
                            bg: Color(hex: 0x1C0509), card: Color(hex: 0x2D0B12, alpha: 0.7),
                            line: Color(hex: 0xFF9AA6, alpha: 0.17), text: Color(hex: 0xFFF0F2), hint: Color(hex: 0xCC9AA2),
                            a1: Color(hex: 0xFF3B4F), a2: Color(hex: 0xFF8A65), a3: Color(hex: 0x8A0F23),
                            soft: Color(hex: 0xFFD1D6, alpha: 0.15), spark: Color(hex: 0xFFB199))

    static let all = [dark, light, wine]

    static func byId(_ id: String) -> Theme {
        all.first { $0.id == id } ?? dark
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }
}
