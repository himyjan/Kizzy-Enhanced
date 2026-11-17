import os
import re

res_dir = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res'
default_file = os.path.join(res_dir, 'values/strings.xml')

# Read default strings.xml
with open(default_file, 'r', encoding='utf-8') as f:
    default_content = f.read()

# Extract Manual RPC related strings from default
manual_rpc_entries = {}
for match in re.finditer(r'    <string name="([^"]*[Mm]anual[^"]*[Rr]pc[^"]*)">(.*?)</string>', default_content, re.DOTALL):
    manual_rpc_entries[match.group(1)] = match.group(0)

print(f"Found {len(manual_rpc_entries)} Manual RPC related strings in default:")
for name in sorted(manual_rpc_entries.keys()):
    print(f"  {name}")

print()

# Check all language files
lang_dirs = []
for d in sorted(os.listdir(res_dir)):
    if d.startswith('values-') and os.path.isdir(os.path.join(res_dir, d)):
        lang_dirs.append(d)

total_added = 0
files_modified = 0

for lang in lang_dirs:
    filepath = os.path.join(res_dir, lang, 'strings.xml')
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    missing = [(name, entry) for name, entry in manual_rpc_entries.items()
               if f'name="{name}"' not in content]

    if not missing:
        continue

    missing.sort(key=lambda x: x[0])
    insert_text = '\n' + '\n'.join(entry for _, entry in missing) + '\n'

    if '</resources>' in content:
        content = content.replace('</resources>', insert_text + '</resources>', 1)
    else:
        content = content.rstrip() + insert_text

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

    total_added += len(missing)
    files_modified += 1
    names = [n for n, _ in missing]
    print(f"{lang}: Added {len(missing)} Manual RPC strings: {', '.join(names)}")

print(f"\nTotal added: {total_added} Manual RPC strings across {files_modified} language files")
