import re
import os

res_dir = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res'

# Find all strings.xml files
strings_files = []
for root, dirs, files in os.walk(res_dir):
    for f in files:
        if f == 'strings.xml':
            strings_files.append(os.path.join(root, f))

strings_files.sort()

total_removed = 0

for filepath in strings_files:
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Remove all string entries where name contains "experimental_rpc"
    # Pattern matches: <string name="...experimental_rpc...">...</string>
    # Handles multiline content
    pattern = re.compile(
        r'\n    <string name="[^"]*experimental_rpc[^"]*">.*?</string>',
        re.DOTALL
    )

    new_content, count = pattern.subn('', content)

    if count > 0:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        total_removed += count
        rel_path = os.path.relpath(filepath, res_dir)
        print(f"Removed {count} experimental_rpc strings from {rel_path}")

print(f"\nTotal removed: {total_removed}")
