import os
import re

res = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res'
out_path = '/Users/user/Downloads/Kizzy-Enhanced/.artifacts/442888d1-c657-4e77-aa34-cb7ce700cbc6/scratch/audit_result.txt'

pat = re.compile(
    r'<string name="(enable_manualRpc|main_manualRpc|main_manualRpc_details|main_manualRpcDetails|manual_rpc_detect_apps|manual_rpc_detect_media|manual_rpc_title_toggle|manual_rpc_title_toggle_desc|start_manualRpc|start_manualRpcToast|start_manualRpc_toast)">(.*?)</string>',
    re.S,
)

lines = ['=== Still-English Manual RPC strings per language ===']
total = 0
for d in sorted(os.listdir(res)):
    if not d.startswith('values-'):
        continue
    p = os.path.join(res, d, 'strings.xml')
    if not os.path.exists(p):
        continue
    c = open(p, encoding='utf-8').read()
    en = []
    for m in pat.finditer(c):
        val = m.group(2).strip()
        if re.match(r'^(Enable|Start|Allow|Show|Title$|Manual RPC is|Manual RPC$)', val):
            en.append(f'{m.group(1)}={val[:50]}')
    if en:
        total += len(en)
        lines.append(f'{d}: {len(en)}')
        for e in en:
            lines.append(f'   {e}')

lines.append(f'Total English placeholders: {total}')

with open(out_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')

print(f'Audit written to {out_path}')
