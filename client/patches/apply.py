#!/usr/bin/env python3
"""
Накладывает мод StarTegs на исходники Telegram для Android.

    python3 client/patches/apply.py <папка с исходниками Telegram>

Переменные окружения (секреты GitHub Actions):
    TG_API_ID, TG_API_HASH   — ваши ключи с https://my.telegram.org (обязательны для рабочего APK)

Каждая правка ищет точный фрагмент кода. Если Telegram поменял этот код, скрипт
остановится с понятной ошибкой, а не соберёт сломанный APK.
"""
import json
import os
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CLIENT = HERE.parent
TG = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else sys.exit(__doc__)
JAVA = TG / "TMessagesProj/src/main/java"
errors = []


def read_props(path):
    props = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        props[k.strip()] = v.strip()
    return props


P = read_props(CLIENT / "startegs.properties")
PACKAGE = P["package"]
assert re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", PACKAGE), "package: неверный формат"


def patch(rel, old, new, count=1):
    """Заменяет ровно count вхождений old на new в файле rel."""
    f = TG / rel
    s = f.read_text(encoding="utf-8")
    n = s.count(old)
    if n != count:
        errors.append(f"{rel}: ожидалось {count} вхождений, найдено {n}:\n    {old.strip()[:120]}")
        return
    f.write_text(s.replace(old, new), encoding="utf-8")
    print(f"  ✓ {rel}")


def java_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


print("== код мода")
dst = JAVA / "org/startegs"
dst.mkdir(parents=True, exist_ok=True)
for src in sorted((CLIENT / "src/org/startegs").glob("*.java")):
    shutil.copy(src, dst / src.name)

services = []
for i in range(1, 20):
    raw = P.get(f"service.{i}")
    if not raw:
        continue
    parts = [x.strip() for x in raw.split("|")]
    if len(parts) != 7:
        sys.exit(f"service.{i}: нужно 7 полей через |, а не {len(parts)}")
    if not re.fullmatch(r"[A-Za-z0-9_]{4,32}", parts[2]):
        print(f"  ⚠ service.{i} ({parts[0]}): username бота не указан — пункт не будет показан")
        parts[2] = ""
    services.append(parts)
(dst / "StarModConfig.java").write_text(
    "package org.startegs;\n\n// Создано client/patches/apply.py из startegs.properties — не редактируйте вручную.\n"
    "final class StarModConfig {\n"
    f"    static final String MOD_VERSION = {java_str(P.get('mod_version', '1.0'))};\n"
    "    /** название, подпись, username, цвет1, цвет2, иконка, режим */\n"
    "    static final String[][] SERVICES = {\n"
    + "".join("        {" + ", ".join(java_str(x) for x in s) + "},\n" for s in services)
    + "    };\n}\n",
    encoding="utf-8",
)
print(f"  ✓ org/startegs/*.java ({len(list(dst.glob('*.java')))} файлов), {len(services)} сервисов")

print("== сохранение удалённых сообщений")
MC = "TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java"
patch(MC,
      """                TL_update.TL_updateDeleteMessages update = (TL_update.TL_updateDeleteMessages) baseUpdate;
                if (deletedMessages == null) {
                    deletedMessages = new LongSparseArray<>();
                }
                ArrayList<Integer> arrayList = deletedMessages.get(0);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(0, arrayList);
                }
                arrayList.addAll(update.messages);
""",
      """                TL_update.TL_updateDeleteMessages update = (TL_update.TL_updateDeleteMessages) baseUpdate;
                if (!org.startegs.StarMod.interceptDelete(currentAccount, 0, update.messages)) {
                if (deletedMessages == null) {
                    deletedMessages = new LongSparseArray<>();
                }
                ArrayList<Integer> arrayList = deletedMessages.get(0);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(0, arrayList);
                }
                arrayList.addAll(update.messages);
                }
""")
patch(MC,
      """                if (deletedMessages == null) {
                    deletedMessages = new LongSparseArray<>();
                }
                long dialogId = -update.channel_id;
                ArrayList<Integer> arrayList = deletedMessages.get(dialogId);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(dialogId, arrayList);
                }
                arrayList.addAll(update.messages);
""",
      """                if (!org.startegs.StarMod.interceptDelete(currentAccount, -update.channel_id, update.messages)) {
                if (deletedMessages == null) {
                    deletedMessages = new LongSparseArray<>();
                }
                long dialogId = -update.channel_id;
                ArrayList<Integer> arrayList = deletedMessages.get(dialogId);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(dialogId, arrayList);
                }
                arrayList.addAll(update.messages);
                }
""")

print("== история изменений")
patch(MC,
      """                ArrayList<MessageObject> messageObjects = editingMessages.valueAt(b);
                for (int a = 0, size2 = messageObjects.size(); a < size2; a++) {
                    messagesRes.messages.add(messageObjects.get(a).messageOwner);
                }
                getMessagesStorage().putMessages(messagesRes, editingMessages.keyAt(b), -2, 0, false, 0, 0);""",
      """                ArrayList<MessageObject> messageObjects = editingMessages.valueAt(b);
                for (int a = 0, size2 = messageObjects.size(); a < size2; a++) {
                    messagesRes.messages.add(messageObjects.get(a).messageOwner);
                }
                org.startegs.StarMod.rememberEdits(currentAccount, editingMessages.keyAt(b), messageObjects);
                getMessagesStorage().putMessages(messagesRes, editingMessages.keyAt(b), -2, 0, false, 0, 0);""")

CELL = "TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java"
patch(CELL,
      """        timeTextWidth = timeWidth = (int) Math.ceil(Theme.chat_timePaint.measureText(currentTimeString, 0, currentTimeString == null ? 0 : currentTimeString.length()));""",
      """        currentTimeString = org.startegs.StarMod.decorateTime(currentAccount, currentMessageObject, currentTimeString);
        timeTextWidth = timeWidth = (int) Math.ceil(Theme.chat_timePaint.measureText(currentTimeString, 0, currentTimeString == null ? 0 : currentTimeString.length()));""")

CA = "TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java"
patch(CA,
      """        if (showWelcomeMessageRevertOption(primaryMessage)) {
            items.add(getString(R.string.WelcomeMessageRevert));
            options.add(OPTION_WELCOME_REVERT);
            icons.add(R.drawable.outline_revert_24);
        }
""",
      """        if (showWelcomeMessageRevertOption(primaryMessage)) {
            items.add(getString(R.string.WelcomeMessageRevert));
            options.add(OPTION_WELCOME_REVERT);
            icons.add(R.drawable.outline_revert_24);
        }
        if (selectedObject != null && org.startegs.StarMod.hasEdits(currentAccount, selectedObject)) {
            items.add("История изменений");
            options.add(org.startegs.StarMod.OPTION_EDIT_HISTORY);
            icons.add(R.drawable.msg_edit);
        }
""")
patch(CA,
      """            case OPTION_COPY: {
                final TL_iv.RichMessage copyRichMessage""",
      """            case org.startegs.StarMod.OPTION_EDIT_HISTORY: {
                org.startegs.StarMod.showEditHistory(this, selectedObject);
                break;
            }
            case OPTION_COPY: {
                final TL_iv.RichMessage copyRichMessage""")

print("== раздел StarTegs в настройках")
SA = "TMessagesProj/src/main/java/org/telegram/ui/SettingsActivity.java"
patch(SA,
      """        items.add(SettingCell.Factory.of(1, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.settings_account, getString(R.string.SettingsAccount), getString(R.string.SettingsAccountInfo)));""",
      """        org.startegs.StarMod.fillSettings(items);
        items.add(SettingCell.Factory.of(1, IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom, R.drawable.settings_account, getString(R.string.SettingsAccount), getString(R.string.SettingsAccountInfo)));""")
patch(SA,
      """        switch (item.id) {
            case 1:
                presentSettingFragment(new UserInfoActivity());""",
      """        if (org.startegs.StarMod.onSettingsClick(this, item.id, () -> listView.adapter.update(true))) {
            return;
        }
        switch (item.id) {
            case 1:
                presentSettingFragment(new UserInfoActivity());""")

print("== приватность")
patch(MC,
      """    public boolean sendTyping(long dialogId, long threadMsgId, int action, String emojicon, int classGuid) {
        if (action < 0 || action >= sendingTypings.length || dialogId == 0) {""",
      """    public boolean sendTyping(long dialogId, long threadMsgId, int action, String emojicon, int classGuid) {
        if (org.startegs.StarMod.ghostTyping()) {
            return false;
        }
        if (action < 0 || action >= sendingTypings.length || dialogId == 0) {""")
patch(MC,
      """    private void completeReadTask(ReadTask task) {
        if (task.replyId != 0 && task.monoForumPeerId == 0) {""",
      """    private void completeReadTask(ReadTask task) {
        if (org.startegs.StarMod.ghostRead() && task.replyId == 0 && DialogObject.isUserDialog(task.dialogId)) {
            return; // «прочитано» в личных чатах не отправляем
        }
        if (task.replyId != 0 && task.monoForumPeerId == 0) {""")

print("== интерфейс")
patch("TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java",
      """            newVisibility = !onlySelfStories && getStoriesController().hasStories();
            onlySelfStories = getStoriesController().hasOnlySelfStories();""",
      """            newVisibility = !org.startegs.StarMod.hideStories() && !onlySelfStories && getStoriesController().hasStories();
            onlySelfStories = !org.startegs.StarMod.hideStories() && getStoriesController().hasOnlySelfStories();""")
patch(SA,
      """        if (!getMessagesController().premiumFeaturesBlocked()) {
            items.add(SettingCell.Factory.of(11,""",
      """        if (!org.startegs.StarMod.hidePromo() && !getMessagesController().premiumFeaturesBlocked()) {
            items.add(SettingCell.Factory.of(11,""")
patch(SA,
      """        if (!getMessagesController().premiumFeaturesBlocked()) {
            items.add(SettingCell.Factory.of(15,""",
      """        if (!org.startegs.StarMod.hidePromo() && !getMessagesController().premiumFeaturesBlocked()) {
            items.add(SettingCell.Factory.of(15,""")
patch(SA,
      """        if (!getMessagesController().premiumPurchaseBlocked()) {
            items.add(SettingCell.Factory.of(16,""",
      """        if (!org.startegs.StarMod.hidePromo() && !getMessagesController().premiumPurchaseBlocked()) {
            items.add(SettingCell.Factory.of(16,""")

print("== фирменная тема")
sys.path.insert(0, str(HERE))
import brand_theme
brand_theme.main(TG / "TMessagesProj/src/main/assets", TG / "TMessagesProj/src/main/assets")

print("== ребрендинг")
patch("gradle.properties", "APP_PACKAGE=org.telegram.messenger", f"APP_PACKAGE={PACKAGE}")
patch("TMessagesProj/src/main/res/values/strings.xml",
      '<string name="AppName">Telegram</string>', f'<string name="AppName">{P.get("app_name", "StarTegs")}</string>')
patch("TMessagesProj/src/main/res/values/strings.xml",
      '<string name="AppNameBeta">Telegram Beta</string>', f'<string name="AppNameBeta">{P.get("app_name", "StarTegs")} Beta</string>')
# Тип системного аккаунта должен отличаться от официального Telegram, иначе на телефоне
# с обоими приложениями синхронизация контактов сломается у одного из них.
patch("TMessagesProj/src/main/res/xml/auth.xml", 'android:accountType="org.telegram.messenger"', f'android:accountType="{PACKAGE}"')
patch("TMessagesProj/src/main/res/xml/sync_contacts.xml", 'android:accountType="org.telegram.messenger"', f'android:accountType="{PACKAGE}"')
patch("TMessagesProj/src/main/res/xml/auth_menu.xml", 'android:targetPackage="org.telegram.messenger"', f'android:targetPackage="{PACKAGE}.web"')
patch("TMessagesProj/src/main/java/org/telegram/messenger/ContactsController.java", '"org.telegram.messenger"', java_str(PACKAGE), count=5)

for gs in ["TMessagesProj_AppStandalone/google-services.json", "TMessagesProj_App/google-services.json"]:
    f = TG / gs
    if f.exists():
        data = json.loads(f.read_text(encoding="utf-8"))
        for c in data.get("client", []):
            info = c.get("client_info", {}).get("android_client_info", {})
            if "package_name" in info:
                info["package_name"] = info["package_name"].replace("org.telegram.messenger", PACKAGE)
        f.write_text(json.dumps(data, indent=2), encoding="utf-8")
        print(f"  ✓ {gs}")

print("== BuildVars")
BV = "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
api_id, api_hash = os.environ.get("TG_API_ID", "").strip(), os.environ.get("TG_API_HASH", "").strip()
if api_id and api_hash:
    if not api_id.isdigit() or not re.fullmatch(r"[0-9a-f]{32}", api_hash):
        sys.exit("TG_API_ID должен быть числом, TG_API_HASH — 32 символа 0-9a-f (с my.telegram.org)")
    patch(BV, "public static int APP_ID = 4;", f"public static int APP_ID = {api_id};")
    patch(BV, 'public static String APP_HASH = "014b35b6184100b085b0d0572f9b5103";', f'public static String APP_HASH = "{api_hash}";')
else:
    print("  ⚠ TG_API_ID / TG_API_HASH не заданы — APK соберётся, но войти в аккаунт в нём не получится")
    patch(BV, "public static int APP_ID = 4;", "public static int APP_ID = 0;")
    patch(BV, 'public static String APP_HASH = "014b35b6184100b085b0d0572f9b5103";', 'public static String APP_HASH = "";')
patch(BV, "public static boolean CHECK_UPDATES = true;", "public static boolean CHECK_UPDATES = false;")  # не предлагать официальные обновления
patch(BV, "public static boolean SUPPORTS_PASSKEYS = true;", "public static boolean SUPPORTS_PASSKEYS = false;")
patch(BV, "public static boolean IS_BILLING_UNAVAILABLE = false;", "public static boolean IS_BILLING_UNAVAILABLE = true;")

print("== иконка")
res_src = CLIENT / "res"
res_dst = TG / "TMessagesProj_AppStandalone/src/main/res"
for f in res_src.rglob("*"):
    if f.is_file():
        target = res_dst / f.relative_to(res_src)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, target)
print(f"  ✓ {sum(1 for f in res_src.rglob('*') if f.is_file())} файлов")

print("== архитектуры")
abis = [a.strip() for a in P.get("abi", "arm64-v8a").split(",") if a.strip()]
patch("TMessagesProj_AppStandalone/build.gradle",
      'abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"',
      "abiFilters " + ", ".join(f'"{a}"' for a in abis))

if errors:
    print("\n✘ Не удалось наложить мод — код Telegram изменился:\n")
    for e in errors:
        print(" - " + e)
    sys.exit(1)
print("\n✔ Мод StarTegs наложен")
