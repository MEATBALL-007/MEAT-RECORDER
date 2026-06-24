#!/usr/bin/env python3
"""
Generate res/values-XX/strings.xml for MEAT REC's 20-language support.

Translations are AI-generated DRAFTS for the core UI string set. They should be
reviewed by native speakers before a public release. app_name stays "MEAT REC"
(brand) and the word "Pro" is kept as-is in all locales.

Run:  python3 docs/i18n/gen_translations.py
"""
import os
import html

BASE = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res")

KEYS = [
    "app_tagline", "app_tagline_pro", "tap_to_record", "tap_to_start",
    "settings", "language", "language_hint", "reset_factory", "reset_factory_detail",
    "reset_confirm_title", "reset_confirm_body", "reset", "cancel", "privacy_policy",
    "upgrade_to_pro", "restore_purchase", "pro_unlocked", "launch_promo",
    "upgrade_now", "lifetime",
]

# Format specifiers must be preserved exactly: %1$d , %1$s , %% .
T = {
 "zh": ["现场录音系统","Pro · 现场录音系统","点按录音","点按按钮开始录音","设置","语言",
        "选择你的语言，应用会立即更新。","恢复出厂设置","清除所有设置。录音、预设和 Pro 保持不变。",
        "重置所有设置？","录音设置将恢复默认。你的录音、自定义预设、主题、语言和 Pro 解锁不受影响。",
        "重置","取消","隐私政策","升级到 Pro","恢复购买","已解锁 Pro","限时 -%1$d%%","立即升级 — %1$s","永久"],
 "hi": ["फ़ील्ड रिकॉर्डिंग सिस्टम","Pro · फ़ील्ड रिकॉर्डिंग सिस्टम","रिकॉर्ड करने के लिए टैप करें","रिकॉर्डिंग शुरू करने के लिए बटन दबाएँ","सेटिंग्स","भाषा",
        "अपनी भाषा चुनें। ऐप तुरंत अपडेट हो जाता है।","फ़ैक्टरी डिफ़ॉल्ट पर रीसेट करें","सभी सेटिंग्स हटाता है। रिकॉर्डिंग, प्रीसेट और Pro सुरक्षित रहते हैं।",
        "सभी सेटिंग्स रीसेट करें?","रिकॉर्डिंग सेटिंग्स डिफ़ॉल्ट पर लौट जाती हैं। आपकी रिकॉर्डिंग, प्रीसेट, थीम, भाषा और Pro अनलॉक प्रभावित नहीं होते।",
        "रीसेट","रद्द करें","गोपनीयता नीति","Pro में अपग्रेड करें","खरीद पुनर्स्थापित करें","Pro अनलॉक हो गया","लॉन्च -%1$d%%","अभी अपग्रेड करें — %1$s","आजीवन"],
 "es": ["Sistema de grabación de campo","Pro · Sistema de grabación de campo","Toca para grabar","Toca el botón para empezar a grabar","Ajustes","Idioma",
        "Elige tu idioma. La app se actualiza al instante.","Restablecer valores de fábrica","Borra todos los ajustes. Grabaciones, ajustes y Pro se mantienen.",
        "¿Restablecer todos los ajustes?","Los ajustes de grabación vuelven a los valores predeterminados. Tus grabaciones, ajustes, tema, idioma y Pro NO se modifican.",
        "Restablecer","Cancelar","Política de privacidad","Mejorar a Pro","Restaurar compra","Pro desbloqueado","LANZAMIENTO -%1$d%%","Mejorar ahora — %1$s","de por vida"],
 "fr": ["Système d’enregistrement de terrain","Pro · Système d’enregistrement de terrain","Touchez pour enregistrer","Touchez le bouton pour démarrer l’enregistrement","Paramètres","Langue",
        "Choisissez votre langue. L’app se met à jour instantanément.","Réinitialiser aux réglages d’usine","Efface tous les réglages. Enregistrements, préréglages et Pro conservés.",
        "Réinitialiser tous les réglages ?","Les réglages d’enregistrement reviennent par défaut. Vos enregistrements, préréglages, thème, langue et Pro ne sont PAS touchés.",
        "Réinitialiser","Annuler","Politique de confidentialité","Passer à Pro","Restaurer l’achat","Pro débloqué","LANCEMENT -%1$d%%","Passer à Pro — %1$s","à vie"],
 "ar": ["نظام التسجيل الميداني","Pro · نظام التسجيل الميداني","اضغط للتسجيل","اضغط على الزر لبدء التسجيل","الإعدادات","اللغة",
        "اختر لغتك. يتحدّث التطبيق فوراً.","إعادة الضبط إلى إعدادات المصنع","يمسح كل الإعدادات. تبقى التسجيلات والإعدادات المسبقة و Pro آمنة.",
        "إعادة ضبط كل الإعدادات؟","تعود إعدادات التسجيل إلى الوضع الافتراضي. لا تتأثر تسجيلاتك وإعداداتك ومظهرك ولغتك وتفعيل Pro.",
        "إعادة ضبط","إلغاء","سياسة الخصوصية","الترقية إلى Pro","استعادة الشراء","تم تفعيل Pro","إطلاق -%1$d%%","الترقية الآن — %1$s","مدى الحياة"],
 "bn": ["ফিল্ড রেকর্ডিং সিস্টেম","Pro · ফিল্ড রেকর্ডিং সিস্টেম","রেকর্ড করতে ট্যাপ করুন","রেকর্ডিং শুরু করতে বোতামে ট্যাপ করুন","সেটিংস","ভাষা",
        "আপনার ভাষা বেছে নিন। অ্যাপ সঙ্গে সঙ্গে আপডেট হয়।","ফ্যাক্টরি ডিফল্টে রিসেট করুন","সব সেটিংস মুছে দেয়। রেকর্ডিং, প্রিসেট ও Pro নিরাপদ থাকে।",
        "সব সেটিংস রিসেট করবেন?","রেকর্ডিং সেটিংস ডিফল্টে ফিরে যায়। আপনার রেকর্ডিং, প্রিসেট, থিম, ভাষা ও Pro আনলক অপরিবর্তিত থাকে।",
        "রিসেট","বাতিল","গোপনীয়তা নীতি","Pro-তে আপগ্রেড করুন","ক্রয় পুনরুদ্ধার করুন","Pro আনলক হয়েছে","লঞ্চ -%1$d%%","এখনই আপগ্রেড করুন — %1$s","আজীবন"],
 "pt": ["Sistema de gravação de campo","Pro · Sistema de gravação de campo","Toque para gravar","Toque no botão para começar a gravar","Definições","Idioma",
        "Escolha o seu idioma. A app atualiza-se na hora.","Repor predefinições de fábrica","Apaga todas as definições. Gravações, predefinições e Pro ficam seguros.",
        "Repor todas as definições?","As definições de gravação voltam ao padrão. As suas gravações, predefinições, tema, idioma e Pro NÃO são alterados.",
        "Repor","Cancelar","Política de privacidade","Obter Pro","Restaurar compra","Pro desbloqueado","LANÇAMENTO -%1$d%%","Obter agora — %1$s","vitalício"],
 "ru": ["Система полевой записи","Pro · Система полевой записи","Нажмите, чтобы записать","Нажмите кнопку, чтобы начать запись","Настройки","Язык",
        "Выберите язык. Приложение обновится сразу.","Сбросить к заводским настройкам","Стирает все настройки. Записи, пресеты и Pro сохраняются.",
        "Сбросить все настройки?","Настройки записи вернутся к стандартным. Ваши записи, пресеты, тема, язык и Pro НЕ затрагиваются.",
        "Сбросить","Отмена","Политика конфиденциальности","Перейти на Pro","Восстановить покупку","Pro разблокирован","СТАРТ -%1$d%%","Перейти на Pro — %1$s","навсегда"],
 "id": ["Sistem Perekaman Lapangan","Pro · Sistem Perekaman Lapangan","Ketuk untuk Merekam","Ketuk tombol untuk mulai merekam","Pengaturan","Bahasa",
        "Pilih bahasa Anda. Aplikasi langsung diperbarui.","Setel ulang ke setelan pabrik","Menghapus semua pengaturan. Rekaman, preset & Pro tetap aman.",
        "Setel ulang semua pengaturan?","Pengaturan perekaman kembali ke default. Rekaman, preset, tema, bahasa, dan Pro Anda TIDAK terpengaruh.",
        "Setel ulang","Batal","Kebijakan Privasi","Tingkatkan ke Pro","Pulihkan pembelian","Pro terbuka","PELUNCURAN -%1$d%%","Tingkatkan sekarang — %1$s","seumur hidup"],
 "ja": ["フィールド録音システム","Pro · フィールド録音システム","タップして録音","ボタンをタップして録音を開始","設定","言語",
        "言語を選択してください。アプリは即座に更新されます。","工場出荷時設定にリセット","すべての設定を消去します。録音・プリセット・Pro は保持されます。",
        "すべての設定をリセットしますか？","録音設定が初期値に戻ります。録音・プリセット・テーマ・言語・Pro 解除は変更されません。",
        "リセット","キャンセル","プライバシーポリシー","Pro にアップグレード","購入を復元","Pro 解除済み","ローンチ -%1$d%%","今すぐアップグレード — %1$s","買い切り"],
 "de": ["Field-Recording-System","Pro · Field-Recording-System","Zum Aufnehmen tippen","Auf die Taste tippen, um die Aufnahme zu starten","Einstellungen","Sprache",
        "Wähle deine Sprache. Die App aktualisiert sich sofort.","Auf Werkseinstellungen zurücksetzen","Löscht alle Einstellungen. Aufnahmen, Presets & Pro bleiben erhalten.",
        "Alle Einstellungen zurücksetzen?","Aufnahme-Einstellungen werden zurückgesetzt. Deine Aufnahmen, Presets, Design, Sprache und Pro bleiben unberührt.",
        "Zurücksetzen","Abbrechen","Datenschutz","Auf Pro upgraden","Kauf wiederherstellen","Pro freigeschaltet","START -%1$d%%","Jetzt upgraden — %1$s","lebenslang"],
 "ko": ["필드 레코딩 시스템","Pro · 필드 레코딩 시스템","탭하여 녹음","버튼을 탭하여 녹음 시작","설정","언어",
        "언어를 선택하세요. 앱이 즉시 업데이트됩니다.","공장 초기화","모든 설정을 지웁니다. 녹음·프리셋·Pro는 유지됩니다.",
        "모든 설정을 초기화할까요?","녹음 설정이 기본값으로 돌아갑니다. 녹음·프리셋·테마·언어·Pro 잠금 해제는 영향을 받지 않습니다.",
        "초기화","취소","개인정보처리방침","Pro로 업그레이드","구매 복원","Pro 잠금 해제됨","출시 -%1$d%%","지금 업그레이드 — %1$s","평생"],
 "tr": ["Saha Kayıt Sistemi","Pro · Saha Kayıt Sistemi","Kaydetmek için dokunun","Kaydı başlatmak için düğmeye dokunun","Ayarlar","Dil",
        "Dilinizi seçin. Uygulama anında güncellenir.","Fabrika ayarlarına sıfırla","Tüm ayarları siler. Kayıtlar, ön ayarlar ve Pro korunur.",
        "Tüm ayarlar sıfırlansın mı?","Kayıt ayarları varsayılana döner. Kayıtlarınız, ön ayarlar, tema, dil ve Pro kilidi etkilenmez.",
        "Sıfırla","İptal","Gizlilik Politikası","Pro’ya yükselt","Satın alımı geri yükle","Pro açıldı","LANSMAN -%1$d%%","Şimdi yükselt — %1$s","ömür boyu"],
 "vi": ["Hệ thống ghi âm hiện trường","Pro · Hệ thống ghi âm hiện trường","Chạm để ghi âm","Chạm nút để bắt đầu ghi âm","Cài đặt","Ngôn ngữ",
        "Chọn ngôn ngữ của bạn. Ứng dụng cập nhật ngay.","Khôi phục cài đặt gốc","Xóa mọi cài đặt. Bản ghi, cài đặt sẵn và Pro vẫn an toàn.",
        "Đặt lại toàn bộ cài đặt?","Cài đặt ghi âm trở về mặc định. Bản ghi, cài đặt sẵn, giao diện, ngôn ngữ và Pro của bạn KHÔNG bị ảnh hưởng.",
        "Đặt lại","Hủy","Chính sách quyền riêng tư","Nâng cấp lên Pro","Khôi phục giao dịch","Đã mở khóa Pro","RA MẮT -%1$d%%","Nâng cấp ngay — %1$s","trọn đời"],
 "th": ["ระบบบันทึกเสียงภาคสนาม","Pro · ระบบบันทึกเสียงภาคสนาม","แตะเพื่ออัด","แตะปุ่มเพื่อเริ่มอัดเสียง","ตั้งค่า","ภาษา",
        "เลือกภาษาของคุณ แอปจะอัปเดตทันที","รีเซ็ตเป็นค่าจากโรงงาน","ล้างการตั้งค่าทั้งหมด ไฟล์อัด พรีเซ็ต และ Pro ยังอยู่ครบ",
        "รีเซ็ตการตั้งค่าทั้งหมด?","การตั้งค่าการอัดจะกลับเป็นค่าเริ่มต้น ไฟล์อัด พรีเซ็ต ธีม ภาษา และ Pro จะไม่ถูกแตะต้อง",
        "รีเซ็ต","ยกเลิก","นโยบายความเป็นส่วนตัว","อัปเกรดเป็น Pro","กู้คืนการซื้อ","ปลดล็อก Pro แล้ว","เปิดตัว -%1$d%%","อัปเกรดเลย — %1$s","ตลอดชีพ"],
 "it": ["Sistema di registrazione sul campo","Pro · Sistema di registrazione sul campo","Tocca per registrare","Tocca il pulsante per iniziare a registrare","Impostazioni","Lingua",
        "Scegli la tua lingua. L’app si aggiorna subito.","Ripristina impostazioni di fabbrica","Cancella tutte le impostazioni. Registrazioni, preset e Pro restano al sicuro.",
        "Ripristinare tutte le impostazioni?","Le impostazioni di registrazione tornano ai valori predefiniti. Registrazioni, preset, tema, lingua e Pro NON vengono toccati.",
        "Ripristina","Annulla","Informativa sulla privacy","Passa a Pro","Ripristina acquisto","Pro sbloccato","LANCIO -%1$d%%","Passa a Pro ora — %1$s","a vita"],
 "pl": ["System nagrań terenowych","Pro · System nagrań terenowych","Dotknij, aby nagrać","Dotknij przycisku, aby rozpocząć nagrywanie","Ustawienia","Język",
        "Wybierz język. Aplikacja zaktualizuje się od razu.","Przywróć ustawienia fabryczne","Usuwa wszystkie ustawienia. Nagrania, presety i Pro pozostają bezpieczne.",
        "Zresetować wszystkie ustawienia?","Ustawienia nagrywania wracają do domyślnych. Twoje nagrania, presety, motyw, język i Pro NIE zostaną zmienione.",
        "Resetuj","Anuluj","Polityka prywatności","Przejdź na Pro","Przywróć zakup","Pro odblokowane","PREMIERA -%1$d%%","Przejdź na Pro — %1$s","na zawsze"],
 "uk": ["Система польового запису","Pro · Система польового запису","Торкніться, щоб записати","Торкніться кнопки, щоб почати запис","Налаштування","Мова",
        "Виберіть мову. Застосунок оновиться миттєво.","Скинути до заводських налаштувань","Стирає всі налаштування. Записи, пресети та Pro лишаються в безпеці.",
        "Скинути всі налаштування?","Налаштування запису повернуться до типових. Ваші записи, пресети, тема, мова та Pro НЕ зачіпаються.",
        "Скинути","Скасувати","Політика конфіденційності","Перейти на Pro","Відновити покупку","Pro розблоковано","СТАРТ -%1$d%%","Перейти на Pro — %1$s","назавжди"],
 "fa": ["سیستم ضبط میدانی","Pro · سیستم ضبط میدانی","برای ضبط ضربه بزنید","برای شروع ضبط روی دکمه ضربه بزنید","تنظیمات","زبان",
        "زبان خود را انتخاب کنید. برنامه بلافاصله به‌روز می‌شود.","بازنشانی به تنظیمات کارخانه","همه تنظیمات را پاک می‌کند. ضبط‌ها، پیش‌تنظیم‌ها و Pro حفظ می‌شوند.",
        "همه تنظیمات بازنشانی شود؟","تنظیمات ضبط به حالت پیش‌فرض بازمی‌گردد. ضبط‌ها، پیش‌تنظیم‌ها، پوسته، زبان و Pro شما تغییر نمی‌کند.",
        "بازنشانی","لغو","سیاست حریم خصوصی","ارتقا به Pro","بازیابی خرید","Pro فعال شد","عرضه -%1$d%%","همین حالا ارتقا دهید — %1$s","مادام‌العمر"],
}

def esc(s):
    # Escape XML + Android apostrophe rules.
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"')
    return s

def write_locale(code, values):
    d = os.path.join(BASE, f"values-{code}")
    os.makedirs(d, exist_ok=True)
    lines = ['<resources>']
    for k, v in zip(KEYS, values):
        lines.append(f'    <string name="{k}">{esc(v)}</string>')
    lines.append('</resources>')
    with open(os.path.join(d, "strings.xml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"  values-{code}/strings.xml  ({len(values)} strings)")

print("Generating translated strings.xml files:")
for code, values in T.items():
    assert len(values) == len(KEYS), f"{code}: {len(values)} != {len(KEYS)}"
    write_locale(code, values)
print(f"Done — {len(T)} locales + English base = {len(T)+1} languages.")
