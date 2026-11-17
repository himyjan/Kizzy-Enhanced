import os
import re

res_dir = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res'
default_file = os.path.join(res_dir, 'values/strings.xml')

# Read default strings.xml
with open(default_file, 'r', encoding='utf-8') as f:
    default_content = f.read()

# Extract all media-related string entries (full XML line)
media_pattern = re.compile(r'    <string name="[^"]*[Mm]edia[^"]*">.*?</string>', re.DOTALL)
media_entries = {}
for match in media_pattern.finditer(default_content):
    entry = match.group(0)
    name_match = re.search(r'name="([^"]*)"', entry)
    if name_match:
        media_entries[name_match.group(1)] = entry

print(f"Found {len(media_entries)} media-related strings in default")

# Find all language directories
lang_dirs = []
for d in sorted(os.listdir(res_dir)):
    if d.startswith('values-') and os.path.isdir(os.path.join(res_dir, d)):
        lang_dirs.append(d)

total_added = 0

for lang in lang_dirs:
    filepath = os.path.join(res_dir, lang, 'strings.xml')
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find which media entries are missing
    missing = []
    for name, entry in media_entries.items():
        if f'name="{name}"' not in content:
            missing.append((name, entry))

    if not missing:
        continue

    # Insert missing entries before </resources>
    # Sort by name for consistency
    missing.sort(key=lambda x: x[0])

    insert_text = '\n' + '\n'.join(entry for _, entry in missing) + '\n'

    # Insert before </resources>
    if '</resources>' in content:
        content = content.replace('</resources>', insert_text + '</resources>', 1)
    else:
        content = content.rstrip() + insert_text

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

    total_added += len(missing)
    names = [n for n, _ in missing]
    print(f"{lang}: Added {len(missing)} media strings: {', '.join(names)}")

print(f"\nTotal added: {total_added} media strings across {len(lang_dirs)} language files")
