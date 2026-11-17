import os

res_dir = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res'

# Translations for languages I can confidently handle
# Format: {lang_dir: {string_name: native_translation}}
translations = {
    'values-tr': {
        'enable_manualRpc': 'Manuel RPC\'yi Etkinleştir',
        'manual_rpc_detect_apps': 'Manuel RPC\'nin çalışan uygulamaları algılamasına izin ver',
        'manual_rpc_detect_media': 'Manuel RPC\'nin medya oynatmasını algılamasına izin ver',
        'manual_rpc_title_toggle': 'Başlık',
        'manual_rpc_title_toggle_desc': 'Şarkı bilgisini etkinlik başlığında uygulama adı yerine göster',
        'start_manualRpc': 'Manuel RPC\'yi Başlat',
        'start_manualRpcToast': 'Manuel RPC başlatıldı',
        'start_manualRpc_toast': 'Manuel RPC başlatıldı',
    },
    'values-th': {
        'enable_manualRpc': 'เปิดใช้งาน Manual RPC',
        'manual_rpc_detect_apps': 'อนุญาตให้ Manual RPC ตรวจจับแอปที่กำลังทำงานอยู่',
        'manual_rpc_detect_media': 'อนุญาตให้ Manual RPC ตรวจจับสื่อที่กำลังเล่นอยู่',
        'manual_rpc_title_toggle': 'ชื่อ',
        'manual_rpc_title_toggle_desc': 'แสดงข้อมูลเพลงในหัวข้อกิจกรรมแทนชื่อแอป',
        'start_manualRpc': 'เริ่ม Manual RPC',
        'start_manualRpcToast': 'Manual RPC เริ่มใช้งานแล้ว',
        'start_manualRpc_toast': 'Manual RPC เริ่มใช้งานแล้ว',
    },
    'values-bn': {
        'enable_manualRpc': 'ম্যানুয়াল আরপিসি সক্ষম করুন',
        'manual_rpc_detect_apps': 'ম্যানুয়াল আরপিসিকে চলমান অ্যাপ্লিকেশন শনাক্ত করার অনুমতি দিন',
        'manual_rpc_detect_media': 'ম্যানুয়াল আরপিসিকে মিডিয়া প্লেব্যাক শনাক্ত করতে দিন',
        'manual_rpc_title_toggle': 'শিরোনাম',
        'manual_rpc_title_toggle_desc': 'গান টাইটেলে বাঁজন গান টাইটেল দিবে',
        'start_manualRpc': 'ম্যানুয়াল আরপিসি শুরু করুন',
        'start_manualRpcToast': 'ম্যানুয়াল আরপিসি শুরু হয়েছে',
        'start_manualRpc_toast': 'ম্যানুয়াল আরপিসি শুরু হয়েছে',
    },
    'values-zh-rCN': {
        'enable_manualRpc': '启用手动 RPC',
        'manual_rpc_detect_apps': '允许手动 RPC 检测运行中的应用程序',
        'manual_rpc_detect_media': '允许手动 RPC 检测媒体播放',
        'manual_rpc_title_toggle': '标题',
        'manual_rpc_title_toggle_desc': '在活动标题中显示歌曲信息而不是应用程序名称',
        'start_manualRpc': '启动手动 RPC',
        'start_manualRpcToast': '手动 RPC 已启动',
        'start_manualRpc_toast': '手动 RPC 已启动',
    },
    'values-zh-rTW': {
        'enable_manualRpc': '啟用手動 RPC',
        'manual_rpc_detect_apps': '允許手動 RPC 檢測正在運行的應用程式',
        'manual_rpc_detect_media': '允許手動 RPC 檢測媒體播放',
        'manual_rpc_title_toggle': '標題',
        'manual_rpc_title_toggle_desc': '在活動標題中顯示歌曲資訊，而非應用程式名稱',
        'start_manualRpc': '啟動手動 RPC',
        'start_manualRpcToast': '手動 RPC 已啟動',
        'start_manualRpc_toast': '手動 RPC 已啟動',
    },
    'values-ja': {
        'enable_manualRpc': '手動 RPC を有効にする',
        'manual_rpc_detect_apps': '手動 RPC に実行中のアプリの検出を許可',
        'manual_rpc_detect_media': '手動 RPC にメディア再生の検出を許可',
        'manual_rpc_title_toggle': 'タイトル',
        'manual_rpc_title_toggle_desc': 'アプリ名の代わりにアクティビティタイトルに曲情報を表示',
        'start_manualRpc': '手動 RPC を開始',
        'start_manualRpcToast': '手動 RPC を開始しました',
        'start_manualRpc_toast': '手動 RPC を開始しました',
    },
    'values-ar': {
        'enable_manualRpc': 'تفعيل RPC يدوي',
        'manual_rpc_detect_apps': 'السماح لـ RPC يدوي باكتشاف التطبيقات قيد التشغيل',
        'manual_rpc_detect_media': 'السماح لـ RPC يدوي باكتشاف تشغيل الوسائط',
        'manual_rpc_title_toggle': 'العنوان',
        'manual_rpc_title_toggle_desc': 'إظهار معلومات الأغنية في عنوان النشاط بدلاً من اسم التطبيق',
        'start_manualRpc': 'ابدأ RPC يدوي',
        'start_manualRpcToast': 'تم بدء RPC يدوي',
        'start_manualRpc_toast': 'تم بدء RPC يدوي',
    },
    'values-hi': {
        'enable_manualRpc': 'मैनुअल RPC सक्षम करें',
        'manual_rpc_detect_apps': 'मैनुअल RPC को चल रहे ऐप्स का पता लगाने की अनुमति दें',
        'manual_rpc_detect_media': 'मैनुअल RPC को मीडिया प्लेबैक का पता लगाने की अनुमति दें',
        'manual_rpc_title_toggle': 'शीर्षक',
        'manual_rpc_title_toggle_desc': 'ऐप नाम के बजाय गतिविधि शीर्षक में गीत जानकारी दिखाएं',
        'start_manualRpc': 'मैनुअल RPC शुरू करें',
        'start_manualRpcToast': 'मैनुअल RPC शुरू किया गया',
        'start_manualRpc_toast': 'मैनुअल RPC शुरू किया गया',
    },
    'values-ru': {
        'enable_manualRpc': 'Включить ручной RPC',
        'manual_rpc_detect_apps': 'Разрешить ручному RPC обнаруживать запущенные приложения',
        'manual_rpc_detect_media': 'Разрешить ручному RPC обнаруживать воспроизведение медиа',
        'manual_rpc_title_toggle': 'Заголовок',
        'manual_rpc_title_toggle_desc': 'Показывать информацию о песне в заголовке активности вместо названия приложения',
        'start_manualRpc': 'Запустить ручной RPC',
        'start_manualRpcToast': 'Ручной RPC запущен',
        'start_manualRpc_toast': 'Ручной RPC запущен',
    },
    'values-ko': {
        'enable_manualRpc': '수동 RPC 활성화',
        'manual_rpc_detect_apps': '수동 RPC가 실행 중인 앱을 감지하도록 허용',
        'manual_rpc_detect_media': '수동 RPC가 미디어 재생을 감지하도록 허용',
        'manual_rpc_title_toggle': '제목',
        'manual_rpc_title_toggle_desc': '앱 이름 대신 활동 제목에 노래 정보 표시',
        'start_manualRpc': '수동 RPC 시작',
        'start_manualRpcToast': '수동 RPC 시작됨',
        'start_manualRpc_toast': '수동 RPC 시작됨',
    },
    'values-uk': {
        'enable_manualRpc': 'Увімкнути ручний RPC',
        'manual_rpc_detect_apps': 'Дозволити ручному RPC виявляти запущені програми',
        'manual_rpc_detect_media': 'Дозволити ручному RPC виявляти відтворення медіа',
        'manual_rpc_title_toggle': 'Заголовок',
        'manual_rpc_title_toggle_desc': 'Показувати інформацію про пісню в заголовку активності замість імені програми',
        'start_manualRpc': 'Запустити ручний RPC',
        'start_manualRpcToast': 'Ручний RPC запущено',
        'start_manualRpc_toast': 'Ручний RPC запущено',
    },
}

total_fixed = 0
files_modified = 0

for lang_dir, lang_translations in translations.items():
    filepath = os.path.join(res_dir, lang_dir, 'strings.xml')
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    for name, translation in lang_translations.items():
        # Replace the English placeholder with native translation
        old_pattern = f'<string name="{name}">'
        # Find the line and replace the content between tags
        import re
        pattern = rf'<string name="{name}">.*?</string>'
        replacement = f'<string name="{name}">{translation}</string>'
        new_content, count = re.subn(pattern, replacement, content)
        if count > 0:
            content = new_content
            total_fixed += 1

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

    files_modified += 1
    print(f"{lang_dir}: Fixed {len(lang_translations)} Manual RPC translations")

print(f"\nTotal: {total_fixed} strings across {files_modified} language files")
