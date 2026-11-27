import Foundation
import UIKit
import UserNotifications

/// Сессия прогрева на iPhone: вы листаете TikTok, а приложение по вашим настройкам
/// присылает подсказки поверх TikTok — когда лайкнуть, добавить в избранное, прокомментировать,
/// сделать репост или перерыв. (iOS не разрешает приложениям самим нажимать в TikTok.)
struct SessionSettings {
    var videos: Int
    var likeMin: Int, likeMax: Int
    var watchMin: Int, watchMax: Int
    var skipChance: Int, longChance: Int
    var favChance: Int, repostChance: Int, commentChance: Int, phrasePercent: Int
    var skipLive: Bool, skipAds: Bool, capLoops: Bool
    var emoji: [String], phrases: [String], repostTexts: [String]
}

struct SessionPlan {
    var events: [(at: Double, title: String, body: String)] = []
    var duration: Double = 0
    var videos = 0, likes = 0, favorites = 0, comments = 0, reposts = 0
}

enum Session {
    static let prefix = "warmup-session-"
    static let maxNotifications = 60       // iOS хранит не больше 64 запланированных уведомлений
    static let maxDuration: Double = 90 * 60

    static func plan(_ s: SessionSettings) -> SessionPlan {
        var p = SessionPlan()
        var t: Double = 0
        var untilLike = Int.random(in: min(s.likeMin, s.likeMax)...max(s.likeMin, s.likeMax))
        var untilBreak = Int.random(in: 30...50)
        let limitVideos = s.videos > 0 ? s.videos : Int.max
        let limitTime = s.videos > 0 ? maxDuration : 40 * 60   // ∞ — сессия на 40 минут

        var tip = "Листай ленту и смотри ролики — я подскажу, когда лайкать."
        if s.skipLive || s.skipAds { tip += " Эфиры и рекламу сразу пролистывай." }
        if s.capLoops { tip += " Короткие ролики не пересматривай больше 1,5 раза." }
        p.events.append((3, "▶ Прогрев начался", tip))

        while p.videos < limitVideos && t < limitTime && p.events.count < maxNotifications - 1 {
            p.videos += 1
            t += watchSeconds(s) + Double.random(in: 1.0...1.8)
            untilLike -= 1
            if untilLike <= 0 {
                p.likes += 1
                p.events.append((t, "❤ Лайкни это видео", "Двойной тап по ролику"))
                untilLike = Int.random(in: min(s.likeMin, s.likeMax)...max(s.likeMin, s.likeMax))
                t += 2
            }
            if roll(s.favChance) && p.events.count < maxNotifications - 1 {
                p.favorites += 1
                p.events.append((t + 1, "★ В избранное", "Нажми закладку справа у этого ролика"))
                t += 2
            }
            if roll(s.commentChance) && p.events.count < maxNotifications - 1 {
                p.comments += 1
                p.events.append((t + 1, "💬 Оставь комментарий", "Напиши: «\(commentText(s))»"))
                t += 12
            }
            if roll(s.repostChance) && p.events.count < maxNotifications - 1 {
                p.reposts += 1
                let extra = s.repostTexts.randomElement().map { " Если спросит мысли: «\($0)»" } ?? ""
                p.events.append((t + 1, "↻ Сделай репост", "Поделиться → Репост." + extra))
                t += 8
            }
            untilBreak -= 1
            if untilBreak <= 0 && p.events.count < maxNotifications - 1 {
                let pause = Double(Int.random(in: 30...120))
                p.events.append((t, "☕ Перерыв \(Int(pause)) с", "Поставь видео на паузу и отдохни — потом листай дальше"))
                t += pause
                untilBreak = Int.random(in: 30...50)
            }
        }
        p.duration = t
        p.events.append((t + 2, "✅ Сессия окончена",
                         "Пролистано ~\(p.videos) роликов, ❤ \(p.likes). Следующая — по напоминанию."))
        return p
    }

    /// Время просмотра одного ролика — как в Android-версии.
    static func watchSeconds(_ s: SessionSettings) -> Double {
        let lo = min(s.watchMin, s.watchMax), hi = max(s.watchMin, s.watchMax)
        let r = Int.random(in: 0..<100)
        if r < s.skipChance { return Double.random(in: 1...3) }
        if r < s.skipChance + s.longChance { return Double.random(in: Double(hi)...Double(hi * 2)) }
        return Double.random(in: Double(lo)...Double(max(lo, hi)))
    }

    static func commentText(_ s: SessionSettings) -> String {
        if !s.phrases.isEmpty && (s.emoji.isEmpty || roll(s.phrasePercent)) {
            var t = s.phrases.randomElement()!
            if !s.emoji.isEmpty && roll(35) { t += " " + s.emoji.randomElement()! }
            return t
        }
        guard !s.emoji.isEmpty else { return "🔥" }
        return (0..<Int.random(in: 1...3)).map { _ in s.emoji.randomElement()! }.joined()
    }

    static func roll(_ percent: Int) -> Bool { percent > 0 && Int.random(in: 0..<100) < percent }

    // MARK: запуск / остановка

    static func start(_ plan: SessionPlan, completion: @escaping (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            DispatchQueue.main.async {
                guard granted else { completion(false); return }
                stop()
                for (i, e) in plan.events.enumerated() {
                    let c = UNMutableNotificationContent()
                    c.title = e.title
                    c.body = e.body
                    c.sound = .default
                    if #available(iOS 15.0, *) { c.interruptionLevel = .timeSensitive }
                    let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, e.at), repeats: false)
                    center.add(UNNotificationRequest(identifier: prefix + "\(i)", content: c, trigger: trigger))
                }
                openTikTok()
                completion(true)
            }
        }
    }

    static func stop() {
        let ids = (0..<(maxNotifications + 5)).map { prefix + "\($0)" }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
        UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: ids)
    }

    static func openTikTok() {
        for s in ["snssdk1233://", "snssdk1180://", "tiktok://"] {
            if let u = URL(string: s), UIApplication.shared.canOpenURL(u) {
                UIApplication.shared.open(u)
                return
            }
        }
        UIApplication.shared.open(URL(string: "https://www.tiktok.com/foryou")!)
    }
}
