import re

files = {
    # Turkish: "Deneysel" (Experimental) -> "Manual"
    'values-tr/strings.xml': [
        ('Deneysel RPC\'yi başlat', 'Manual RPC\'yi başlat'),
        ('Deneysel RPC başlatıldı', 'Manual RPC başlatıldı'),
    ],
    # Thai: remove "แล้ว" (already)
    'values-th/strings.xml': [
        ('เริ่มการใช้ Manual RPC แล้ว', 'เริ่ม Manual RPC'),
        ('Manual RPC เริ่มใช้งานไปแล้ว', 'Manual RPC เริ่มใช้งาน'),
    ],
    # Bengali: "এক্সপেরিমেন্টাল" (Experimental) -> "ম্যানুয়াল" (Manual)
    'values-bn/strings.xml': [
        ('এক্সপেরিমেন্টাল আরপিসি শুরু করুন', 'ম্যানুয়াল আরপিসি শুরু করুন'),
        ('এক্সপেরিমেন্টাল আরপিসি শুরু হয়েছে', 'ম্যানুয়াল আরপিসি শুরু হয়েছে'),
    ],
    # Gujarati: "કસ્ટમ" (Custom) -> "મૅન્યુઅલ" (Manual)
    'values-gu/strings.xml': [
        ('કસ્ટમ RPC કાર્યારંભ થયું', 'મૅન્યુઅલ RPC કાર્યારંભ થયું'),
    ],
    # Hebrew: "ניסיוני" (Experimental) -> "ידני" (Manual)
    'values-he/strings.xml': [
        ('RPC ניסיוני התחיל', 'ידני RPC התחיל'),
    ],
    # Hindi: "एक्सपेरिमेंटल" (Experimental) -> "मैनुअल" (Manual)
    'values-hi/strings.xml': [
        ('एक्सपेरिमेंटल आरपीसी शुरू किया गया', 'मैनुअल आरपीसी शुरू किया गया'),
    ],
    # Hungarian: "Kísérleti" (Experimental) -> "Kézi" (Manual)
    'values-hu/strings.xml': [
        ('Kísérleti RPC elindítva', 'Kézi RPC elindítva'),
    ],
    # Korean: "실험적" (Experimental) -> "수동" (Manual)
    'values-ko/strings.xml': [
        ('실험적 RPC 시작됨', '수동 RPC 시작됨'),
    ],
    'values-kr/strings.xml': [
        ('실험적 RPC가 시작되었습니다', '수동 RPC가 시작되었습니다'),
    ],
    # Lao: remove "แล้ว" (already)
    'values-lo/strings.xml': [
        ('Manual RPC ເລີ່ມແລ້ວ', 'Manual RPC ເລີ່ມແລ້ວ'),
    ],
    # Macedonian: "Експериментални" (Experimental) -> "Рачни" (Manual)
    'values-mk/strings.xml': [
        ('Експериментални РПЦ покренут', 'Рачни РПЦ покренути'),
    ],
    # Malayalam: "എക്സ്പിരിമെന്റൽ" (Experimental) -> "മాన్యുവൽ" (Manual)
    'values-ml/strings.xml': [
        ('ആക്സ്പിരിമെന്റൽ RPC തുടങ്ങി', 'മాన్యുവൽ RPC തുടങ്ങി'),
    ],
    # Marathi: "एक्सपेरिमेंटल" (Experimental) -> "मॅन्युअल" (Manual)
    'values-mr/strings.xml': [
        ('एक्सपेरिमेंटल RPC सुरू झाले', 'मॅन्युअल RPC सुरू झाले'),
    ],
    # Nepali: "एक्सपेरिमेंटल" (Experimental) -> "म्यानुअल" (Manual)
    'values-ne/strings.xml': [
        ('एक्सपेरिमेंटल RPC सुरु गरियो', 'म्यानुअल RPC सुरु गरियो'),
    ],
    # Saudi Arabic: fix both main and toast (currently using Devanagari/Nepali script)
    'values-sa/strings.xml': [
        ('Manual RPC सुरु गर्नुहोस्', 'ابدأ تشغيل RPC يدويًا'),
        ('Manual RPC सुरु भयو', 'تم بدء RPC يدويًا'),
    ],
    # Tamil: "எக்ஸ்பெரிமெண்டல்" (Experimental) -> "கೈமுறை" (Manual)
    'values-ta/strings.xml': [
        ('எக்ஸ்பெரிமெண்டல் RPC தொடங்கியது', 'கೈமுறை RPC தொடங்கியது'),
    ],
    # Telugu: "ఎక్స్పెరిమెంటల్" (Experimental) -> "మాన్యువల్" (Manual)
    'values-te/strings.xml': [
        ('ఎక్స్పెరిమెంటల్ RPC ప్రారంభించింది', 'మాన్యువల్ RPC ప్రారంభించింది'),
    ],
    # Ukrainian: "Експериментальний" (Experimental) -> "Ручний" (Manual)
    'values-uk/strings.xml': [
        ('Експериментальний RPC запущено', 'Ручний RPC запущено'),
    ],
    # Urdu: "ایکسپیرمنٹل" (Experimental) -> "مانيوال" (Manual)
    'values-ur/strings.xml': [
        ('ایکسپیرمنٹل RPC شروع ہو گیا', 'مانيوال RPC شروع ہو گیا'),
    ],
}

for rel_path, replacements in files.items():
    abs_path = f'/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res/{rel_path}'
    with open(abs_path, 'r', encoding='utf-8') as f:
        content = f.read()

    for old, new in replacements:
        count = content.count(old)
        if count > 0:
            content = content.replace(old, new)
            print(f"Fixed {rel_path}: replaced '{old[:40]}...' ({count} occurrence(s))")
        else:
            print(f"SKIPPED {rel_path}: '{old[:40]}...' not found")

    with open(abs_path, 'w', encoding='utf-8') as f:
        f.write(content)

print("\nDone!")
