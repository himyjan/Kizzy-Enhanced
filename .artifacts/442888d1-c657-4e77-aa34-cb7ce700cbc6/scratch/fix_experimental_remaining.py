import os
import re

res_dir = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res'

# Map of language directory -> list of (old_text, new_text) replacements
fixes = {
    'values-da': [
        ('Eksperimentel RPC', 'Manual RPC'),
        ('Start Eksperimentel RPC', 'Start Manual RPC'),
    ],
    'values-es': [
        ('RPC experimental', 'Manual RPC'),
    ],
    'values-fr': [
        ('RPC expérimental', 'Manual RPC'),
    ],
    'values-gl': [
        ('RPC experimental', 'Manual RPC'),
    ],
    'values-hr': [
        ('Eksperimentalan RPC', 'Manual RPC'),
    ],
    'values-id': [
        ('RPC Eksperimental', 'Manual RPC'),
    ],
    'values-in': [
        ('RPC Eksperimental', 'Manual RPC'),
    ],
    'values-ja': [
        ('実験的 RPC', 'Manual RPC'),
    ],
    'values-lt': [
        ('Eksperimentinis RPC', 'Manual RPC'),
    ],
    'values-lv': [
        ('Eksperimentālais RPC', 'Manual RPC'),
    ],
    'values-mk': [
        ('Експериментален RPC', 'Manual RPC'),
    ],
    'values-nb': [
        ('Eksperimentell RPC', 'Manual RPC'),
        ('Start Eksperimentell RPC', 'Start Manual RPC'),
    ],
    'values-pt': [
        ('RPC experimental', 'Manual RPC'),
    ],
    'values-sr': [
        ('Eksperimentalan RPC', 'Manual RPC'),
    ],
    'values-tr': [
        ('Deneysel RPC', 'Manual RPC'),
    ],
    'values-uk': [
        ('Експериментальний RPC', 'Manual RPC'),
    ],
    'values-zh-rTW': [
        ('實驗性 RPC', 'Manual RPC'),
    ],
}

# Also fix the default values/strings.xml line 403
default_fixes = [
    ('Show text only across all RPC types (Media, Custom, Console, Experimental).',
     'Show text only across all RPC types (Media, Custom, Console, Manual).'),
]

total_fixed = 0

for lang, replacements in fixes.items():
    filepath = os.path.join(res_dir, lang, 'strings.xml')
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    for old, new in replacements:
        count = content.count(old)
        if count > 0:
            content = content.replace(old, new)
            total_fixed += count
            print(f"{lang}: Fixed '{old}' -> '{new}' ({count} occurrence(s))")

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# Fix default values/strings.xml
default_file = os.path.join(res_dir, 'values/strings.xml')
with open(default_file, 'r', encoding='utf-8') as f:
    content = f.read()

for old, new in default_fixes:
    count = content.count(old)
    if count > 0:
        content = content.replace(old, new)
        total_fixed += count
        print(f"values: Fixed '{old[:50]}...' -> '{new[:50]}...' ({count} occurrence(s))")

with open(default_file, 'w', encoding='utf-8') as f:
    f.write(content)

print(f"\nTotal fixed: {total_fixed}")
