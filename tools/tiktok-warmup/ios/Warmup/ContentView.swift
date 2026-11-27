import SwiftUI
import UIKit

struct ContentView: View {
    @AppStorage("theme") private var themeId = "dark"
    @AppStorage("steps") private var stepsRaw = ""
    @AppStorage("premiumCode") private var premiumCode = ""
    @AppStorage("remindOn") private var remindOn = false
    @AppStorage("remind1") private var remind1 = Reminders.defaultTime(hour: 10, minute: 30).timeIntervalSince1970
    @AppStorage("remind2") private var remind2 = Reminders.defaultTime(hour: 19, minute: 30).timeIntervalSince1970

    @State private var appeared = false
    @State private var celebrate = false
    @State private var toast: String?
    @Namespace private var ns

    private var theme: Theme { Theme.byId(themeId) }
    private var done: Set<Int> { Set(stepsRaw.split(separator: ",").compactMap { Int($0) }) }
    private var premium: Bool { !premiumCode.isEmpty }

    var body: some View {
        ZStack {
            FxBackground(theme: theme)
            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    ThemePicker(themeId: $themeId, theme: theme, ns: ns).appear(0, appeared)
                    HeroCard(theme: theme, steps: done.count, premium: premium, remindOn: remindOn).appear(1, appeared)
                    WhyCard(theme: theme).appear(2, appeared)
                    StepsCard(theme: theme, done: done, toggle: toggleStep, toast: showToast).appear(3, appeared)
                    PremiumCard(theme: theme, code: $premiumCode, onActivated: {
                        celebrateNow()
                        showToast("👑 Premium активирован!")
                    }, toast: showToast).appear(4, appeared)
                    RemindersCard(theme: theme, on: $remindOn, t1: $remind1, t2: $remind2, toast: showToast).appear(5, appeared)
                    TipsCard(theme: theme).appear(6, appeared)
                    Text("TikTok запрещает автоматизацию — не гоняйте прогрев часами подряд, чтобы не получить теневой бан.")
                        .font(.caption)
                        .foregroundColor(theme.hint)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 12)
                        .appear(7, appeared)
                }
                .padding(16)
                .padding(.bottom, 24)
            }
            if celebrate {
                Confetti(theme: theme).allowsHitTesting(false)
            }
            if let toast = toast {
                VStack {
                    Spacer()
                    Text(toast)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(LinearGradient(colors: [theme.a1, theme.a3],
                                                                  startPoint: .leading, endPoint: .trailing)))
                        .shadow(color: theme.a1.opacity(0.5), radius: 14, y: 6)
                        .padding(.bottom, 30)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .preferredColorScheme(theme.light ? .light : .dark)
        .animation(.easeInOut(duration: 0.35), value: themeId)
        .onAppear { appeared = true }
    }

    private func toggleStep(_ i: Int) {
        var s = done
        if s.contains(i) { s.remove(i) } else { s.insert(i) }
        stepsRaw = s.sorted().map(String.init).joined(separator: ",")
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if s.count == 5 && !done.contains(i) {
            celebrateNow()
            showToast("🔥 Всё готово — прогрев запущен!")
        }
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
            .animation(.spring(response: 0.6, dampingFraction: 0.85).delay(Double(index) * 0.07), value: appeared)
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
    let action: () -> Void
    @State private var phase: CGFloat = -1
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
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
    let steps: Int
    let premium: Bool
    let remindOn: Bool
    @State private var shift = false
    @State private var beat = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Text("❤")
                    .font(.system(size: 30))
                    .frame(width: 58, height: 58)
                    .background(Circle().fill(Color.white.opacity(0.2)).overlay(Circle().stroke(Color.white.opacity(0.25))))
                    .scaleEffect(beat ? 1.14 : 1)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Прогрев TikTok").font(.system(size: 24, weight: .bold))
                    Text("Версия для iPhone").font(.system(size: 14)).opacity(0.9)
                }
            }
            HStack(spacing: 8) {
                chip("🧭 \(steps)/5")
                chip(premium ? "👑 Premium" : "🆓 Free")
                chip(remindOn ? "⏰ вкл" : "⏰ выкл")
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
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Capsule().fill(Color.white.opacity(0.18)))
    }
}

// MARK: - Как это работает

struct WhyCard: View {
    let theme: Theme

    var body: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "🍏", title: "Как это работает на iPhone")
            Text("iOS не разрешает приложениям управлять TikTok. Поэтому прогрев идёт на облачном Android-телефоне — "
                 + "он работает на сервере 24/7, даже когда iPhone выключен. Это приложение проведёт по шагам, "
                 + "напомнит о сессиях и хранит ваш код Premium.")
                .font(.system(size: 14))
                .foregroundColor(theme.hint)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Мастер запуска

struct StepsCard: View {
    let theme: Theme
    let done: Set<Int>
    let toggle: (Int) -> Void
    let toast: (String) -> Void
    @Environment(\.openURL) private var openURL

    private struct Step {
        let title: String
        let detail: String
        let actions: [(String, String)]   // (кнопка, действие: url или copy:текст)
    }

    private var steps: [Step] {
        [
            Step(title: "Установите облачный телефон",
                 detail: "Любой сервис облачного Android с приложением для iPhone. Хватит самого простого тарифа.",
                 actions: [("Redfinger", search("redfinger")), ("VMOS Cloud", search("vmos cloud")),
                           ("GeeLark", search("geelark"))]),
            Step(title: "Скачайте «Прогрев TikTok» в облачном телефоне",
                 detail: "Внутри облачного телефона откройте браузер и вставьте ссылку на APK — или откройте там нашего бота.",
                 actions: [("📋 Ссылка на APK", "copy:" + Premium.apkURL), ("🤖 Бот", Premium.botURL.absoluteString)]),
            Step(title: "Установите TikTok и войдите в аккаунт",
                 detail: "Там же, в облачном телефоне, через Google Play.",
                 actions: []),
            Step(title: "Подключите доступ и примените лучшие настройки",
                 detail: "В приложении: «Подключить» → включить в Спецвозможностях, затем «✨ Применить лучшие настройки».",
                 actions: []),
            Step(title: "Нажмите «▶ Начать прогрев»",
                 detail: "Можно закрыть облачный телефон — прогрев продолжится на сервере.",
                 actions: [])
        ]
    }

    private func search(_ term: String) -> String {
        "itms-apps://search.itunes.apple.com/WebObjects/MZSearch.woa/wa/search?media=software&term="
            + (term.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? term)
    }

    var body: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "🧭", title: "Запуск за 5 шагов")
            progress
            ForEach(0..<steps.count, id: \.self) { i in
                row(i, steps[i])
                if i < steps.count - 1 { Divider().background(theme.line) }
            }
        }
    }

    private var progress: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(theme.soft)
                Capsule()
                    .fill(LinearGradient(colors: [theme.a1, theme.a2], startPoint: .leading, endPoint: .trailing))
                    .frame(width: max(8, geo.size.width * CGFloat(done.count) / 5))
                    .animation(.spring(response: 0.5, dampingFraction: 0.8), value: done.count)
            }
        }
        .frame(height: 8)
        .padding(.bottom, 4)
    }

    private func row(_ i: Int, _ s: Step) -> some View {
        let isDone = done.contains(i)
        return HStack(alignment: .top, spacing: 12) {
            Button { withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) { toggle(i) } } label: {
                ZStack {
                    Circle().stroke(isDone ? Color.clear : theme.hint.opacity(0.6), lineWidth: 2)
                    if isDone {
                        Circle().fill(LinearGradient(colors: [theme.a1, theme.a2], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .transition(.scale)
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                            .transition(.scale)
                    } else {
                        Text("\(i + 1)").font(.system(size: 13, weight: .bold)).foregroundColor(theme.hint)
                    }
                }
                .frame(width: 30, height: 30)
            }
            .buttonStyle(PressStyle())

            VStack(alignment: .leading, spacing: 6) {
                Text(s.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(theme.text)
                    .opacity(isDone ? 0.6 : 1)
                Text(s.detail)
                    .font(.system(size: 13))
                    .foregroundColor(theme.hint)
                    .fixedSize(horizontal: false, vertical: true)
                if !s.actions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(0..<s.actions.count, id: \.self) { k in
                                PillButton(theme: theme, title: s.actions[k].0) { run(s.actions[k].1) }
                            }
                        }
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }

    private func run(_ action: String) {
        if action.hasPrefix("copy:") {
            UIPasteboard.general.string = String(action.dropFirst(5))
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            toast("📋 Скопировано")
        } else if let url = URL(string: action) {
            openURL(url)
        }
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
            HStack {
                Text("👑").font(.system(size: 26))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Premium активен").font(.system(size: 18, weight: .bold))
                    Text("Вставьте этот код в приложение в облачном телефоне").font(.system(size: 13)).opacity(0.9)
                }
            }
            Text(code)
                .font(.system(size: 20, weight: .bold, design: .monospaced))
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity)
                .background(RoundedRectangle(cornerRadius: 14).fill(Color.black.opacity(0.18)))
            Button {
                UIPasteboard.general.string = code
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                toast("📋 Код скопирован")
            } label: {
                Text("📋 Скопировать код")
                    .font(.system(size: 15, weight: .semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Capsule().fill(Color.white.opacity(0.22)))
            }
            .buttonStyle(PressStyle())
        }
        .foregroundColor(.white)
        .padding(18)
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
            ShimmerButton(colors: [theme.a1, Theme.gold], title: "👑  Купить за 50 ⭐ в боте") {
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

// MARK: - Советы

struct TipsCard: View {
    let theme: Theme

    private let tips = [
        ("🛡", "Нажимайте «Лучшие настройки» — это самый безопасный режим."),
        ("⏱", "2–3 сессии в день по 30–40 минут лучше, чем сутки без перерыва."),
        ("📡", "Пропуск эфиров и рекламы держите включённым."),
        ("💬", "Комментарии и репосты — не больше 1–2 %: их видят все."),
        ("🎯", "Первые дни листайте ленту по своей нише — так алгоритм быстрее поймёт аккаунт.")
    ]

    var body: some View {
        Card(theme: theme) {
            CardTitle(theme: theme, icon: "💡", title: "Советы против бана")
            ForEach(0..<tips.count, id: \.self) { i in
                HStack(alignment: .top, spacing: 10) {
                    Text(tips[i].0)
                    Text(tips[i].1)
                        .font(.system(size: 14))
                        .foregroundColor(theme.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
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
                    let x0 = rnd.next() * size.width
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
