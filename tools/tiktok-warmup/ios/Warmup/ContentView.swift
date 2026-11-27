import SwiftUI
import UIKit

// Экран повторяет Android-приложение «Прогрев TikTok»: те же карточки и настройки.
// Прогрев на iPhone идёт через облачный Android-телефон: «Начать прогрев» копирует
// все настройки кодом TWSET:..., который вставляется в Android-приложение одной кнопкой.

struct ContentView: View {
    @AppStorage("theme") private var themeId = "dark"
    @AppStorage("premiumCode") private var premiumCode = ""
    @AppStorage("launches") private var launches = 0

    // те же ключи и значения по умолчанию, что в Android-приложении
    @AppStorage("videos") private var videos = 120
    @AppStorage("likeMin") private var likeMin = 1
    @AppStorage("likeMax") private var likeMax = 18
    @AppStorage("watchMin") private var watchMin = 5
    @AppStorage("watchMax") private var watchMax = 25
    @AppStorage("skipChance") private var skipChance = 15
    @AppStorage("longChance") private var longChance = 10
    @AppStorage("favChance") private var favChance = 4
    @AppStorage("repostChance") private var repostChance = 2
    @AppStorage("commentChance") private var commentChance = 2
    @AppStorage("phrasePercent") private var phrasePercent = 60
    @AppStorage("photos") private var photos = true
    @AppStorage("skipLive") private var skipLive = true
    @AppStorage("skipAds") private var skipAds = true
    @AppStorage("capLoops") private var capLoops = true
    @AppStorage("emoji") private var emoji = Defaults.emoji
    @AppStorage("phrases") private var phrases = Defaults.phrases
    @AppStorage("repostTexts") private var repostTexts = Defaults.repost

    @AppStorage("remindOn") private var remindOn = false
    @AppStorage("remind1") private var remind1 = Reminders.defaultTime(hour: 10, minute: 30).timeIntervalSince1970
    @AppStorage("remind2") private var remind2 = Reminders.defaultTime(hour: 19, minute: 30).timeIntervalSince1970

    @State private var appeared = false
    @State private var celebrate = false
    @State private var toast: String?
    @State private var showLaunch = false
    @State private var lockBump = 0
    @Namespace private var ns

    private var theme: Theme { Theme.byId(themeId) }
    private var premium: Bool { !premiumCode.isEmpty }

    init() {
        UITextView.appearance().backgroundColor = .clear
    }

    var body: some View {
        ZStack {
            FxBackground(theme: theme)
            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                  Group {
                    ThemePicker(themeId: $themeId, theme: theme, ns: ns).appear(0, appeared)
                    HeroCard(theme: theme, videos: videos, likeMin: likeMin, likeMax: likeMax,
                             watchMin: watchMin, watchMax: watchMax, fav: favChance, repost: repostChance,
                             comment: commentChance, photos: photos, skip: skipLive, launches: launches)
                        .appear(1, appeared)
                    ShimmerButton(colors: [theme.a1, theme.a2], title: "▶  Начать прогрев", height: 60) { start() }
                        .appear(2, appeared)
                    PremiumCard(theme: theme, code: $premiumCode, onActivated: {
                        celebrateNow()
                        showToast("👑 Premium активирован!")
                    }, toast: showToast)
                        .scaleEffect(lockBump % 2 == 1 ? 1.03 : 1)
                        .animation(.spring(response: 0.3, dampingFraction: 0.5), value: lockBump)
                        .appear(3, appeared)
                    CloudCard(theme: theme, toast: showToast).appear(4, appeared)
                    bestCard.appear(5, appeared)
                  }
                  Group {
                    likesCard.appear(6, appeared)
                    actionsCard.appear(7, appeared)
                    skipCard.appear(8, appeared)
                    commentsCard.appear(9, appeared)
                    watchCard.appear(10, appeared)
                    RemindersCard(theme: theme, on: $remindOn, t1: $remind1, t2: $remind2, toast: showToast)
                        .appear(11, appeared)
                    Text("TikTok запрещает автоматизацию — не гоняйте прогрев часами подряд, чтобы не получить теневой бан.")
                        .font(.caption)
                        .foregroundColor(theme.hint)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 12)
                        .appear(12, appeared)
                  }
                }
                .padding(16)
                .padding(.bottom, 24)
            }
            if showLaunch {
                LaunchSheet(theme: theme, close: { withAnimation(.spring()) { showLaunch = false } },
                            copy: { UIPasteboard.general.string = settingsCode() }, toast: showToast)
                    .transition(.opacity.combined(with: .scale(scale: 0.95)))
                    .zIndex(2)
            }
            if celebrate {
                Confetti(theme: theme).allowsHitTesting(false).zIndex(3)
            }
            if let toast = toast {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(LinearGradient(colors: [theme.a1, theme.a3],
                                                                  startPoint: .leading, endPoint: .trailing)))
                        .shadow(color: theme.a1.opacity(0.5), radius: 14, y: 6)
                        .padding(.bottom, 30)
                        .padding(.horizontal, 16)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(4)
            }
        }
        .preferredColorScheme(theme.light ? .light : .dark)
        .animation(.easeInOut(duration: 0.35), value: themeId)
        .onAppear { appeared = true }
    }

    // MARK: карточки настроек (как в Android)

    private var bestCard: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "🛡", title: "Лучшие настройки")
            Note(theme: theme, text: "Минимальный риск бана: спокойный темп, мало действий, перерывы и пропуск эфиров/рекламы. "
                 + "Запускайте 2–3 раза в день по 30–40 минут.")
            ShimmerButton(colors: [theme.a3, theme.a1], title: "✨  Применить лучшие настройки", height: 50) { applyBest() }
                .padding(.top, 6)
        }
    }

    private var likesCard: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "❤", title: "Лайки")
            StepperRow(theme: theme, label: "Лайк через: от", value: $likeMin, range: 1...100, step: 1, unit: " вид.")
            StepperRow(theme: theme, label: "Лайк через: до", value: $likeMax, range: 1...100, step: 1, unit: " вид.")
            StepperRow(theme: theme, label: "Постов за запуск", value: $videos, range: 0...2000, step: 10, unit: "",
                       infiniteZero: true)
            Note(theme: theme, text: "∞ — без ограничения, до нажатия «Стоп»")
        }
    }

    private var actionsCard: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "✦", title: "Как живой человек")
            StepperRow(theme: theme, label: "★ В избранное", value: $favChance, range: 0...50, step: 1, unit: "%")
            StepperRow(theme: theme, label: "↻ Репост", value: $repostChance, range: 0...50, step: 1, unit: "%",
                       locked: !premium, onLocked: locked)
            EditorRow(theme: theme, title: "Текст к репосту — если TikTok предложит «Добавить мысли» (по одному в строке)",
                      text: $repostTexts, multiline: true, locked: !premium, onLocked: locked)
            ToggleRow(theme: theme, label: "🖼 Листать фото-посты",
                      hint: "Смотрит каждое фото 1,5–4,5 с, иногда возвращается назад", isOn: $photos)
            Note(theme: theme, text: "Проценты — шанс действия на каждом посте. Репосты видят ваши подписчики — не ставьте много.")
        }
    }

    private var skipCard: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "⏭", title: "Пропускать")
            ToggleRow(theme: theme, label: "📡 Прямые эфиры (LIVE)",
                      hint: "Эфир в ленте сразу пролистывается, внутрь не заходит", isOn: $skipLive)
            ToggleRow(theme: theme, label: "📢 Авто-пропуск рекламы",
                      hint: "Посты с пометкой «Реклама» / «Sponsored» — пролист за 1–2 с, без лайков",
                      isOn: $skipAds, locked: !premium, onLocked: locked)
        }
    }

    private var commentsCard: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "💬", title: "Комментарии")
            StepperRow(theme: theme, label: "Комментировать", value: $commentChance, range: 0...30, step: 1, unit: "%",
                       locked: !premium, onLocked: locked)
            StepperRow(theme: theme, label: "Из них фразами", value: $phrasePercent, range: 0...100, step: 10, unit: "%",
                       locked: !premium, onLocked: locked)
            EditorRow(theme: theme, title: "Эмодзи (через пробел)", text: $emoji, multiline: false,
                      locked: !premium, onLocked: locked)
            EditorRow(theme: theme, title: "Фразы (по одной в строке)", text: $phrases, multiline: true,
                      locked: !premium, onLocked: locked)
            PillButton(theme: theme, title: "Вернуть наборы по умолчанию") {
                if !premium { locked(); return }
                emoji = Defaults.emoji
                phrases = Defaults.phrases
                repostTexts = Defaults.repost
                showToast("Наборы восстановлены")
            }
            .padding(.top, 4)
            Note(theme: theme, text: "Пишет фразу (иногда с эмодзи) или 1–3 эмодзи и закрывает комментарии. "
                 + "Комментарии видят все — держите процент маленьким.")
        }
    }

    private var watchCard: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "⏱", title: "Время просмотра")
            StepperRow(theme: theme, label: "Смотреть от", value: $watchMin, range: 1...300, step: 1, unit: " с")
            StepperRow(theme: theme, label: "Смотреть до", value: $watchMax, range: 1...300, step: 1, unit: " с")
            StepperRow(theme: theme, label: "Быстро пролистать (1–3 с)", value: $skipChance, range: 0...100, step: 5, unit: "%")
            StepperRow(theme: theme, label: "Досмотреть подольше", value: $longChance, range: 0...100, step: 5, unit: "%")
            ToggleRow(theme: theme, label: "🔁 Не дольше 1,5 длины ролика",
                      hint: "Короткий ролик не крутится по кругу: 10-секундный — максимум 15 с. На перерывах видео на паузе",
                      isOn: $capLoops)
            Note(theme: theme, text: "Время каждого ролика случайное. «Подольше» — до двух «до».")
        }
    }

    // MARK: действия

    private func applyBest() {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
            videos = 80; likeMin = 4; likeMax = 15; watchMin = 7; watchMax = 30
            skipChance = 15; longChance = 15; favChance = 3
            photos = true; skipLive = true; capLoops = true
            if premium { repostChance = 1; commentChance = 1; phrasePercent = 70; skipAds = true }
        }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        showToast("✨ Готово! Безопасные настройки применены")
    }

    /// Все настройки одним кодом — вставляется в Android-приложение кнопкой «📥 Вставить настройки с iPhone».
    private func settingsCode() -> String {
        let dict: [String: Any] = [
            "videos": videos, "likeMin": min(likeMin, likeMax), "likeMax": max(likeMin, likeMax),
            "watchMin": min(watchMin, watchMax), "watchMax": max(watchMin, watchMax),
            "skipChance": skipChance, "longChance": longChance, "favChance": favChance,
            "repostChance": repostChance, "commentChance": commentChance, "phrasePercent": phrasePercent,
            "photos": photos, "skipLive": skipLive, "skipAds": skipAds, "capLoops": capLoops,
            "emoji": emoji, "phrases": phrases, "repostTexts": repostTexts
        ]
        let data = (try? JSONSerialization.data(withJSONObject: dict, options: [.sortedKeys])) ?? Data()
        return "TWSET:" + data.base64EncodedString()
    }

    private func start() {
        UIPasteboard.general.string = settingsCode()
        launches += 1
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        withAnimation(.spring(response: 0.45, dampingFraction: 0.85)) { showLaunch = true }
    }

    private func locked() {
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
        lockBump += 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { lockBump += 1 }
        showToast("🔒 Доступно в Premium")
    }

    private func celebrateNow() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        celebrate = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) { celebrate = false }
    }

    private func showToast(_ text: String) {
        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) { toast = text }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) {
            if toast == text { withAnimation(.easeOut(duration: 0.3)) { toast = nil } }
        }
    }
}

enum Defaults {
    static let emoji = "🔥 😂 ❤️ 😍 👍 💯 🤣 👏 😮 🙌 ✨ 😁"
    static let phrases = [
        "Это просто огонь", "Пересмотрел уже раз пять", "Как же жизненно", "Обожаю такой контент",
        "Вот это поворот", "Сохранил, чтобы не потерять", "Ну это шедевр", "Кто тоже залип на этом видео?",
        "Лучшее, что я видел сегодня", "Аж настроение поднялось", "Ахаха, это же я", "Очень красиво получилось",
        "Хочу ещё такого", "Это гениально", "Скинул другу, он в восторге", "Почему это так смешно",
        "Вот это талант", "Идеально под вечер", "Не ожидал такой концовки", "Автор, ты лучший"
    ].joined(separator: "\n")
    static let repost = [
        "Это стоит посмотреть каждому", "Не мог не поделиться, это шедевр", "Сохраняю к себе, слишком хорошо",
        "Пересматриваю уже который раз", "Обязательно досмотрите до конца", "Очень жизненно, согласен на все сто",
        "Вот такое у меня сегодня настроение", "Лучшее, что попалось мне за день", "Пусть будет у меня на странице",
        "Покажу это всем друзьям", "Вот это действительно круто", "Не могу перестать смеяться с этого"
    ].joined(separator: "\n")
}

// MARK: - Строки настроек

struct Note: View {
    let theme: Theme
    let text: String
    var body: some View {
        Text(text)
            .font(.system(size: 13))
            .foregroundColor(theme.hint)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// «Название   −  значение  +» — как в Android. Удержание «+/−» листает быстро.
struct StepperRow: View {
    let theme: Theme
    let label: String
    @Binding var value: Int
    let range: ClosedRange<Int>
    let step: Int
    let unit: String
    var infiniteZero = false
    var locked = false
    var onLocked: () -> Void = {}
    @State private var bump = false
    @State private var shake: CGFloat = 0

    private var text: String { infiniteZero && value == 0 ? "∞" : "\(value)\(unit)" }

    var body: some View {
        HStack(spacing: 0) {
            Text((locked ? "🔒 " : "") + label)
                .font(.system(size: 15))
                .foregroundColor(theme.text)
                .frame(maxWidth: .infinity, alignment: .leading)
            RoundButton(theme: theme, symbol: "−") { change(-1) }
            Text(text)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(theme.text)
                .frame(width: 68)
                .scaleEffect(bump ? 1.14 : 1)
                .modifier(Shake(animatableData: shake))
            RoundButton(theme: theme, symbol: "+") { change(1) }
        }
        .padding(.vertical, 6)
    }

    private func change(_ dir: Int) {
        if locked { onLocked(); return }
        let nv = min(range.upperBound, max(range.lowerBound, value + dir * step))
        if nv == value {
            withAnimation(.linear(duration: 0.4)) { shake += 1 }
            return
        }
        value = nv
        UISelectionFeedbackGenerator().selectionChanged()
        bump = true
        withAnimation(.spring(response: 0.3, dampingFraction: 0.4)) { bump = false }
    }
}

struct RoundButton: View {
    let theme: Theme
    let symbol: String
    let action: () -> Void
    @State private var timer: Timer?

    var body: some View {
        Text(symbol)
            .font(.system(size: 20, weight: .semibold))
            .foregroundColor(theme.text)
            .frame(width: 36, height: 36)
            .background(Circle().fill(theme.soft).overlay(Circle().stroke(theme.line, lineWidth: 1)))
            .contentShape(Circle())
            .onTapGesture { action() }
            .onLongPressGesture(minimumDuration: 0.4, pressing: { pressing in
                if !pressing { timer?.invalidate(); timer = nil }
            }, perform: {
                timer?.invalidate()
                timer = Timer.scheduledTimer(withTimeInterval: 0.09, repeats: true) { _ in action() }
            })
    }
}

/// Строка с переключателем, как в Android.
struct ToggleRow: View {
    let theme: Theme
    let label: String
    let hint: String
    @Binding var isOn: Bool
    var locked = false
    var onLocked: () -> Void = {}

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text((locked ? "🔒 " : "") + label).font(.system(size: 15)).foregroundColor(theme.text)
                Text(hint).font(.system(size: 12)).foregroundColor(theme.hint)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Toggle("", isOn: Binding(get: { isOn && !locked }, set: { v in
                if locked { onLocked() } else { withAnimation(.spring()) { isOn = v } }
            }))
            .labelsHidden()
            .toggleStyle(SwitchToggleStyle(tint: theme.a1))
        }
        .padding(.vertical, 6)
    }
}

/// Поле для текста (эмодзи, фразы, тексты репостов).
struct EditorRow: View {
    let theme: Theme
    let title: String
    @Binding var text: String
    let multiline: Bool
    var locked = false
    var onLocked: () -> Void = {}
    @FocusState private var focused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text((locked ? "🔒 " : "") + title).font(.system(size: 13)).foregroundColor(theme.hint)
            Group {
                if multiline {
                    TextEditor(text: $text)
                        .font(.system(size: 15))
                        .frame(minHeight: 90, maxHeight: 160)
                } else {
                    TextField("🔥 😂 ❤️", text: $text)
                        .font(.system(size: 20))
                }
            }
            .focused($focused)
            .foregroundColor(theme.text)
            .disabled(locked)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 14).fill(theme.soft)
                    .overlay(RoundedRectangle(cornerRadius: 14)
                        .stroke(focused ? theme.a1 : theme.line, lineWidth: focused ? 2 : 1))
            )
            .opacity(locked ? 0.55 : 1)
            .overlay(Group {
                if locked {
                    Color.clear.contentShape(Rectangle()).onTapGesture { onLocked() }
                }
            })
        }
        .padding(.top, 4)
    }
}

// MARK: - Запуск: настройки → облачный телефон

struct LaunchSheet: View {
    let theme: Theme
    let close: () -> Void
    let copy: () -> Void
    let toast: (String) -> Void
    @Environment(\.openURL) private var openURL

    var body: some View {
        ZStack {
            Color.black.opacity(0.55).ignoresSafeArea().onTapGesture(perform: close)
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Text("▶")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(LinearGradient(colors: [theme.a1, theme.a2],
                                                                 startPoint: .topLeading, endPoint: .bottomTrailing)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Настройки скопированы").font(.system(size: 18, weight: .bold)).foregroundColor(theme.text)
                        Text("Прогрев запускается в облачном телефоне").font(.system(size: 13)).foregroundColor(theme.hint)
                    }
                }
                step(1, "Откройте облачный телефон (Redfinger, VMOS Cloud, GeeLark…)")
                step(2, "Там откройте «Прогрев TikTok» → «📥 Вставить настройки с iPhone»")
                step(3, "Нажмите «▶ Начать прогрев» — дальше всё идёт на сервере, iPhone можно убрать")
                Note(theme: theme, text: "iOS не разрешает приложениям управлять TikTok, поэтому прогрев идёт на облачном Android — "
                     + "это работает 24/7, даже когда iPhone выключен.")
                HStack(spacing: 8) {
                    PillButton(theme: theme, title: "📋 Скопировать ещё раз") {
                        copy()
                        toast("📋 Код настроек скопирован")
                    }
                    PillButton(theme: theme, title: "Готово", action: close)
                }
            }
            .padding(20)
            .background(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(theme.light ? Color.white : theme.bg)
                .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).stroke(theme.line)))
            .shadow(color: theme.a1.opacity(0.35), radius: 24, y: 10)
            .padding(20)
        }
    }

    private func step(_ n: Int, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Text("\(n)")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 24, height: 24)
                .background(Circle().fill(theme.a1))
            Text(text).font(.system(size: 15)).foregroundColor(theme.text)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Облачный телефон (вместо «Подключить доступ»)

struct CloudCard: View {
    let theme: Theme
    let toast: (String) -> Void
    @Environment(\.openURL) private var openURL
    @State private var ring = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Card(theme: theme) {
            VStack(spacing: 8) {
                ZStack {
                    Circle().stroke(theme.a1, lineWidth: 2)
                        .frame(width: 72, height: 72)
                        .scaleEffect(ring ? 1.35 : 0.9)
                        .opacity(ring ? 0 : 1)
                    Text("☁️")
                        .font(.system(size: 28))
                        .frame(width: 64, height: 64)
                        .background(Circle().fill(LinearGradient(colors: [theme.a1, theme.a3],
                                                                 startPoint: .topLeading, endPoint: .bottomTrailing)))
                }
                .frame(width: 96, height: 96)
                .onAppear {
                    guard !reduceMotion else { return }
                    withAnimation(.easeOut(duration: 1.8).repeatForever(autoreverses: false)) { ring = true }
                }
                Text("Облачный телефон").font(.system(size: 18, weight: .semibold)).foregroundColor(theme.text)
                Text("Прогрев на iPhone работает через облачный Android: поставьте любой сервис, внутри него — "
                     + "«Прогрев TikTok» и TikTok.")
                    .font(.system(size: 13))
                    .foregroundColor(theme.hint)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    PillButton(theme: theme, title: "Redfinger") { openURL(search("redfinger")) }
                    PillButton(theme: theme, title: "VMOS Cloud") { openURL(search("vmos cloud")) }
                    PillButton(theme: theme, title: "GeeLark") { openURL(search("geelark")) }
                }
            }
            HStack(spacing: 8) {
                PillButton(theme: theme, title: "📋 Ссылка на APK") {
                    UIPasteboard.general.string = Premium.apkURL
                    toast("📋 Ссылка скопирована — вставьте в браузер облачного телефона")
                }
                PillButton(theme: theme, title: "🤖 Бот") { openURL(Premium.botURL) }
            }
        }
    }

    private func search(_ term: String) -> URL {
        URL(string: "itms-apps://search.itunes.apple.com/WebObjects/MZSearch.woa/wa/search?media=software&term="
            + (term.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? term))!
    }
}

// MARK: - Общие элементы

/// Карточки по очереди «всплывают» снизу при открытии.
struct AppearModifier: ViewModifier {
    let index: Int
    let appeared: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(appeared || reduceMotion ? 1 : 0)
            .offset(y: appeared || reduceMotion ? 0 : 36)
            .scaleEffect(appeared || reduceMotion ? 1 : 0.97)
            .animation(.spring(response: 0.6, dampingFraction: 0.85).delay(Double(index) * 0.06), value: appeared)
    }
}

extension View {
    func appear(_ index: Int, _ appeared: Bool) -> some View {
        modifier(AppearModifier(index: index, appeared: appeared))
    }
}

struct Card<Content: View>: View {
    let theme: Theme
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10, content: content)
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(theme.card)
                    .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(theme.line, lineWidth: 1))
            )
            .shadow(color: theme.light ? Color.black.opacity(0.06) : .clear, radius: 8, y: 3)
    }
}

struct CardTitle: View {
    let theme: Theme
    let icon: String
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Text(icon)
                .font(.system(size: 15))
                .foregroundColor(.white)
                .frame(width: 32, height: 32)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(LinearGradient(colors: [theme.a1, theme.a3], startPoint: .topLeading, endPoint: .bottomTrailing)))
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(theme.text)
        }
        .padding(.bottom, 2)
    }
}

/// Кнопка, которая слегка «утапливается» при нажатии.
struct PressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

struct PillButton: View {
    let theme: Theme
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(theme.light ? theme.a1 : .white)
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .background(
                    Capsule().fill(LinearGradient(colors: [theme.a1.opacity(theme.light ? 0.12 : 0.35),
                                                           theme.a3.opacity(theme.light ? 0.08 : 0.35)],
                                                  startPoint: .leading, endPoint: .trailing))
                        .overlay(Capsule().stroke(theme.a1.opacity(0.35), lineWidth: 1))
                )
        }
        .buttonStyle(PressStyle())
    }
}

/// Большая градиентная кнопка с бегущим бликом.
struct ShimmerButton: View {
    let colors: [Color]
    let title: String
    var height: CGFloat = 52
    let action: () -> Void
    @State private var phase: CGFloat = -1
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: height > 55 ? 17 : 16, weight: .semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .background(
                    ZStack {
                        LinearGradient(colors: colors, startPoint: .leading, endPoint: .trailing)
                        GeometryReader { geo in
                            LinearGradient(colors: [.white.opacity(0), .white.opacity(0.4), .white.opacity(0)],
                                           startPoint: .leading, endPoint: .trailing)
                                .frame(width: geo.size.width * 0.35)
                                .rotationEffect(.degrees(20))
                                .offset(x: phase * geo.size.width * 1.4)
                        }
                    }
                    .clipShape(Capsule())
                )
                .shadow(color: (colors.first ?? .red).opacity(0.45), radius: 12, y: 6)
        }
        .buttonStyle(PressStyle())
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.linear(duration: 2.6).repeatForever(autoreverses: false)) { phase = 1 }
        }
    }
}

struct Shake: GeometryEffect {
    var animatableData: CGFloat
    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: 8 * sin(animatableData * .pi * 6), y: 0))
    }
}

// MARK: - Переключатель темы

struct ThemePicker: View {
    @Binding var themeId: String
    let theme: Theme
    let ns: Namespace.ID

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Theme.all, id: \.id) { t in
                Button {
                    withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) { themeId = t.id }
                    UISelectionFeedbackGenerator().selectionChanged()
                } label: {
                    Text(t.title)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .foregroundColor(themeId == t.id ? .white : theme.text)
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(
                            ZStack {
                                if themeId == t.id {
                                    Capsule()
                                        .fill(LinearGradient(colors: [theme.a1, theme.a2], startPoint: .leading, endPoint: .trailing))
                                        .matchedGeometryEffect(id: "theme", in: ns)
                                }
                            }
                        )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .background(Capsule().fill(theme.card).overlay(Capsule().stroke(theme.line, lineWidth: 1)))
    }
}

// MARK: - Шапка

struct HeroCard: View {
    let theme: Theme
    let videos: Int
    let likeMin: Int
    let likeMax: Int
    let watchMin: Int
    let watchMax: Int
    let fav: Int
    let repost: Int
    let comment: Int
    let photos: Bool
    let skip: Bool
    let launches: Int
    @State private var shift = false
    @State private var beat = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 14) {
                Text("❤")
                    .font(.system(size: 30))
                    .frame(width: 58, height: 58)
                    .background(Circle().fill(Color.white.opacity(0.2)).overlay(Circle().stroke(Color.white.opacity(0.25))))
                    .scaleEffect(beat ? 1.14 : 1)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Прогрев TikTok").font(.system(size: 24, weight: .bold))
                    Text(launches > 0 ? "Запусков: \(launches)" : "Готов к запуску").font(.system(size: 14)).opacity(0.9)
                }
            }
            .padding(.bottom, 8)
            HStack(spacing: 8) {
                chip("🎬 " + (videos == 0 ? "∞" : "\(videos)"))
                chip("❤ \(min(likeMin, likeMax))–\(max(likeMin, likeMax))")
                chip("⏱ \(min(watchMin, watchMax))–\(max(watchMin, watchMax)) с")
            }
            HStack(spacing: 8) {
                chip("★ \(fav)%")
                chip("↻ \(repost)%")
                chip("💬 \(comment)%")
            }
            HStack(spacing: 8) {
                chip(photos ? "🖼 вкл" : "🖼 выкл")
                chip(skip ? "⏭ эфиры" : "⏭ выкл")
            }
        }
        .foregroundColor(.white)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(colors: [theme.a1, theme.a3, theme.a2],
                           startPoint: shift ? .topLeading : .bottomLeading,
                           endPoint: shift ? .bottomTrailing : .topTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .shadow(color: theme.a1.opacity(0.45), radius: 18, y: 8)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 6).repeatForever(autoreverses: true)) { shift = true }
            withAnimation(.easeInOut(duration: 0.55).repeatForever(autoreverses: true)) { beat = true }
        }
    }

    private func chip(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 13, weight: .semibold))
            .lineLimit(1)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(Color.white.opacity(0.18)))
    }
}

// MARK: - Premium

struct PremiumCard: View {
    let theme: Theme
    @Binding var code: String
    let onActivated: () -> Void
    let toast: (String) -> Void
    @State private var input = ""
    @State private var showInput = false
    @State private var attempts: CGFloat = 0
    @Environment(\.openURL) private var openURL

    var body: some View {
        if code.isEmpty { buyCard } else { activeCard }
    }

    private var activeCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("👑  Premium активен · все функции открыты")
                .font(.system(size: 15, weight: .semibold))
                .frame(maxWidth: .infinity)
            HStack {
                Text(code).font(.system(size: 15, weight: .bold, design: .monospaced))
                Spacer()
                Button {
                    UIPasteboard.general.string = code
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    toast("📋 Код скопирован — вставьте его в облачном телефоне")
                } label: {
                    Text("📋").font(.system(size: 18))
                        .frame(width: 40, height: 34)
                        .background(Capsule().fill(Color.white.opacity(0.22)))
                }
                .buttonStyle(PressStyle())
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.black.opacity(0.18)))
        }
        .foregroundColor(.white)
        .padding(16)
        .background(LinearGradient(colors: [theme.a1, Theme.gold], startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
        .shadow(color: Theme.gold.opacity(0.4), radius: 16, y: 6)
    }

    private var buyCard: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "👑", title: "Premium")
            Text("✓ Без дневного лимита постов\n✓ Авто-пропуск рекламы\n✓ Комментарии: эмодзи и фразы\n✓ Репосты с текстом")
                .font(.system(size: 14))
                .foregroundColor(theme.text)
                .fixedSize(horizontal: false, vertical: true)
            ShimmerButton(colors: [theme.a1, Theme.gold], title: "👑  Купить Premium") {
                openURL(Premium.buyURL)
            }
            .padding(.top, 6)
            if showInput {
                HStack(spacing: 8) {
                    TextField("STAR-XXXX-XXXX-XXXX", text: $input)
                        .textInputAutocapitalization(.characters)
                        .disableAutocorrection(true)
                        .font(.system(size: 16, design: .monospaced))
                        .foregroundColor(theme.text)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 11)
                        .background(RoundedRectangle(cornerRadius: 14).fill(theme.soft))
                        .modifier(Shake(animatableData: attempts))
                    PillButton(theme: theme, title: "Активировать") { activate() }
                }
                .transition(.move(edge: .top).combined(with: .opacity))
            }
            PillButton(theme: theme, title: showInput ? "Скрыть" : "У меня есть код") {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.85)) { showInput.toggle() }
            }
        }
    }

    private func activate() {
        if let ok = Premium.validate(input) {
            withAnimation(.spring()) { code = ok }
            onActivated()
        } else {
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            withAnimation(.linear(duration: 0.4)) { attempts += 1 }
            toast("Неверный код")
        }
    }
}

// MARK: - Напоминания

struct RemindersCard: View {
    let theme: Theme
    @Binding var on: Bool
    @Binding var t1: Double
    @Binding var t2: Double
    let toast: (String) -> Void

    private var times: [Date] { [Date(timeIntervalSince1970: t1), Date(timeIntervalSince1970: t2)] }

    var body: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "⏰", title: "Напоминания о прогреве")
            Toggle(isOn: Binding(get: { on }, set: { setOn($0) })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Напоминать 2 раза в день").font(.system(size: 15)).foregroundColor(theme.text)
                    Text("Лучший режим против бана — 2 сессии по 30–40 минут")
                        .font(.system(size: 12)).foregroundColor(theme.hint)
                }
            }
            .toggleStyle(SwitchToggleStyle(tint: theme.a1))
            if on {
                timeRow("Утро", value: $t1)
                timeRow("Вечер", value: $t2)
            }
        }
        .animation(.spring(response: 0.4, dampingFraction: 0.85), value: on)
    }

    private func timeRow(_ title: String, value: Binding<Double>) -> some View {
        HStack {
            Text(title).font(.system(size: 15)).foregroundColor(theme.text)
            Spacer()
            DatePicker("", selection: Binding(
                get: { Date(timeIntervalSince1970: value.wrappedValue) },
                set: {
                    value.wrappedValue = $0.timeIntervalSince1970
                    Reminders.schedule(times: times)
                }), displayedComponents: .hourAndMinute)
                .labelsHidden()
                .accentColor(theme.a1)
        }
    }

    private func setOn(_ v: Bool) {
        if v {
            Reminders.requestAndSchedule(times: times) { granted in
                on = granted
                toast(granted ? "⏰ Напоминания включены" : "Разрешите уведомления в Настройках iPhone")
            }
        } else {
            on = false
            Reminders.cancel()
        }
    }
}

// MARK: - Конфетти

struct Confetti: View {
    let theme: Theme
    @State private var start = Date()

    var body: some View {
        TimelineView(.animation) { ctx in
            Canvas { g, size in
                let t = ctx.date.timeIntervalSince(start)
                var rnd = SeededRandom(seed: 42)
                let colors = [theme.a1, theme.a2, Theme.gold, Color.white, theme.spark]
                for i in 0..<90 {
                    let x0 = rnd.next() * Double(size.width)
                    let speed = 260 + rnd.next() * 320
                    let drift = (rnd.next() - 0.5) * 160
                    let delay = rnd.next() * 0.5
                    let tt = max(0, t - delay)
                    let y = -20 + tt * speed + 0.5 * 180 * tt * tt
                    let x = x0 + drift * tt + sin(tt * 6 + Double(i)) * 14
                    let w = 6 + rnd.next() * 6
                    var p = g
                    p.translateBy(x: x, y: y)
                    p.rotate(by: .radians(tt * (3 + rnd.next() * 5)))
                    p.opacity = max(0, 1 - t / 3)
                    p.fill(Path(roundedRect: CGRect(x: -w / 2, y: -w / 4, width: w, height: w / 2), cornerRadius: 1),
                           with: .color(colors[i % colors.count]))
                }
            }
        }
        .ignoresSafeArea()
        .onAppear { start = Date() }
    }
}
