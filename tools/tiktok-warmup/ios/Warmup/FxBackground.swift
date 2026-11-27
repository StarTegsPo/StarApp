import SwiftUI

/// Живой фон: плавающие светящиеся пятна в цветах темы и поднимающиеся искры.
struct FxBackground: View {
    let theme: Theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private struct Spark {
        let x: Double, y: Double, vx: Double, vy: Double, phase: Double, size: Double, accent: Bool
    }

    private let sparks: [Spark] = (0..<30).map { i in
        var g = SeededRandom(seed: UInt64(i + 1) * 7919)
        return Spark(x: g.next(), y: g.next(), vx: (g.next() - 0.5) * 0.010, vy: -0.008 - g.next() * 0.018,
                     phase: g.next() * 6.28, size: 1.0 + g.next() * 1.8, accent: i % 4 == 0)
    }

    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { ctx in
            Canvas { g, size in
                let t = reduceMotion ? 0 : ctx.date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 100_000)
                let w = Double(size.width), h = Double(size.height)
                g.fill(Path(CGRect(origin: .zero, size: size)), with: .color(theme.bg))
                let k = theme.light ? 0.55 : 1.0
                blob(&g, CGPoint(x: w * (0.15 + 0.10 * sin(t * 0.21)), y: h * (0.10 + 0.05 * cos(t * 0.17))),
                     w * 0.85, theme.a1, 0.40 * k)
                blob(&g, CGPoint(x: w * (0.90 + 0.08 * cos(t * 0.15)), y: h * (0.45 + 0.07 * sin(t * 0.19))),
                     w * 0.70, theme.a3, 0.33 * k)
                blob(&g, CGPoint(x: w * (0.25 + 0.12 * sin(t * 0.13 + 1)), y: h * (0.88 + 0.05 * sin(t * 0.23))),
                     w * 0.75, theme.a2, 0.24 * k)
                for s in sparks {
                    let x = wrap(s.x + s.vx * t + 0.02 * sin(t * 0.8 + s.phase)) * w
                    let y = wrap(s.y + s.vy * t) * h
                    let f = 0.5 + 0.5 * sin(t * 2.2 + s.phase)
                    let a = 0.2 + 0.8 * f * f
                    let c = s.accent ? theme.a2 : theme.spark
                    let r = s.size
                    g.fill(Path(ellipseIn: CGRect(x: x - r * 3.4, y: y - r * 3.4, width: r * 6.8, height: r * 6.8)),
                           with: .color(c.opacity(a * (theme.light ? 0.16 : 0.24))))
                    g.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)),
                           with: .color(c.opacity(a * (theme.light ? 0.65 : 0.92))))
                }
            }
        }
        .ignoresSafeArea()
    }

    private func blob(_ g: inout GraphicsContext, _ c: CGPoint, _ r: CGFloat, _ color: Color, _ alpha: Double) {
        let rect = CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)
        g.fill(Path(ellipseIn: rect), with: .radialGradient(
            Gradient(colors: [color.opacity(alpha), color.opacity(0)]), center: c, startRadius: 0, endRadius: r))
    }

    private func wrap(_ v: Double) -> Double {
        let m = v.truncatingRemainder(dividingBy: 1)
        return m < 0 ? m + 1 : m
    }
}

/// Детерминированный генератор, чтобы искры не «прыгали» при перерисовке.
struct SeededRandom {
    private var state: UInt64
    init(seed: UInt64) { state = seed &* 6364136223846793005 &+ 1442695040888963407 }
    mutating func next() -> Double {
        state = state &* 6364136223846793005 &+ 1442695040888963407
        return Double((state >> 11) & 0xFFFFF) / Double(0xFFFFF)
    }
}
