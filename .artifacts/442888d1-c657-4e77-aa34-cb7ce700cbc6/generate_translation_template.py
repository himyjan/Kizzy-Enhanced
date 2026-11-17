import os
import csv

res_dir = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res'

# Manual RPC string names and their English values
manual_rpc_strings = [
    ('enable_manualRpc', 'Enable Manual RPC'),
    ('main_manualRpc', 'Manual RPC'),
    ('main_manualRpc_details', 'Manual RPC is a feature in Kizzy that constantly switches between Apps RPC and Media RPC.'),
    ('manual_rpc_detect_apps', 'Allow Manual RPC to detect running apps'),
    ('manual_rpc_detect_media', 'Allow Manual RPC to detect media playback'),
    ('manual_rpc_title_toggle', 'Title'),
    ('manual_rpc_title_toggle_desc', 'Show song info in the activity title instead of app name'),
    ('start_manualRpc', 'Start Manual RPC'),
    ('start_manualRpcToast', 'Start Manual RPC Toast'),
    ('start_manualRpc_toast', 'Manual RPC started'),
]

# Get all language directories sorted
lang_dirs = sorted([d for d in os.listdir(res_dir) if d.startswith('values-') and os.path.isdir(os.path.join(res_dir, d))])

# Extract language codes from directory names (values-XX -> XX)
lang_codes = [d.replace('values-', '') for d in lang_dirs]

# Write long-format CSV: string_key, english_value, language_code, translation
csv_path = '/Users/user/Downloads/Kizzy-Enhanced/.artifacts/442888d1-c657-4e77-aa34-cb7ce700cbc6/manual_rpc_translation_template.csv'

with open(csv_path, 'w', newline='', encoding='utf-8') as f:
    writer = csv.writer(f)
    writer.writerow(['string_key', 'english_value', 'language_code', 'translation'])

    for key, english_val in manual_rpc_strings:
        for lang_code in lang_codes:
            writer.writerow([key, english_val, lang_code, ''])

print(f'Translation template created: {csv_path}')
print(f'Total rows: {len(manual_rpc_strings) * len(lang_codes)}')
print(f'Strings: {len(manual_rpc_strings)}')
print(f'Languages: {len(lang_codes)}')
print()
print('Languages already translated (native script):')
print('  tr, th, bn, zh-rCN, zh-rTW, ja, ar, hi, ru, ko, uk')
print()
print('Languages still needing translation (English placeholders):')
for code in lang_codes:
    if code not in ['tr', 'th', 'bn', 'zh-rCN', 'zh-rTW', 'ja', 'ar', 'hi', 'ru', 'ko', 'uk']:
        print(f'  {code}', end=' ')
print()
