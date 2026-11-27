import Foundation
import UserNotifications

/// Ежедневные напоминания «пора запустить прогрев».
enum Reminders {
    static let ids = ["warmup-morning", "warmup-evening"]

    static func requestAndSchedule(times: [Date], completion: @escaping (Bool) -> Void) {
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            DispatchQueue.main.async {
                if granted { schedule(times: times) }
                completion(granted)
            }
        }
    }

    static func schedule(times: [Date]) {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: ids)
        let bodies = [
            "Пора прогреть аккаунт — открой и нажми «Начать прогрев» 🔥",
            "Вечерняя сессия прогрева — 30–40 минут, и аккаунт растёт ❤️"
        ]
        for (i, time) in times.prefix(ids.count).enumerated() {
            let content = UNMutableNotificationContent()
            content.title = "Прогрев TikTok"
            content.body = bodies[i]
            content.sound = .default
            let comps = Calendar.current.dateComponents([.hour, .minute], from: time)
            let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: true)
            center.add(UNNotificationRequest(identifier: ids[i], content: content, trigger: trigger))
        }
    }

    static func cancel() {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ids)
    }

    static func defaultTime(hour: Int, minute: Int) -> Date {
        Calendar.current.date(bySettingHour: hour, minute: minute, second: 0, of: Date()) ?? Date()
    }
}
