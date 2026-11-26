#!/usr/bin/env python3
"""
Merge LLM translations from /tmp/kizzy_i18n/out/*.json into the locale
strings.xml files, using values/strings.xml as the canonical key/order source.

IMPORTANT: The translation JSON files contain ONLY strings that were missing
or English leftovers. The merge PRESERVES every existing good native translation
in the locale file — it never touches keys absent from the translation JSON.
"""
import re, os, glob, json

RES = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..',
                                   'common', 'resources', 'src', 'main', 'res'))
BASE = os.path.join(RES, 'values', 'strings.xml')
TASK_DIR = '/tmp/kizzy_i18n/tasks'
OUT_DIR = '/tmp/kizzy_i18n/out'

def load(p):
    with open(p, encoding='utf-8') as f:
        return dict(re.findall(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', f.read(), re.S))

def encode(v):
    # v comes from LLM as plain text; encode for Android XML.
    # NOTE: Android aapt2 does NOT accept &apos; / &#39; for apostrophes.
    # Use \"...\" wrapping (with backslash-escaped inner quotes) instead.
    v = v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    if "'" in v:
        v = v.replace('"', '\\"')
        v = '"' + v + '"'
    else:
        v = v.replace('"', '&quot;')
    return v

base = load(BASE)
base_order = list(base.keys())

files = sorted(glob.glob(os.path.join(OUT_DIR, 'values-*.json')))
print(f"Found {len(files)} translation output files")

for fp in files:
    locale = os.path.basename(fp)[:-5]
    target = os.path.join(RES, locale, 'strings.xml')
    if not os.path.exists(os.path.dirname(target)):
        print(f"  SKIP {locale} (no dir)")
        continue
    loc = load(target) if os.path.exists(target) else {}

    # The task file lists exactly which keys were missing/English-leftover.
    task_path = os.path.join(TASK_DIR, locale + '.json')
    task_keys = set()
    if os.path.exists(task_path):
        with open(task_path, encoding='utf-8') as f:
            task_keys = set(json.load(f)['strings'].keys())

    with open(fp, encoding='utf-8') as f:
        trans = json.load(f)

    merged = dict(loc)  # preserve ALL existing translations
    applied = 0
    skipped_unexpected = 0
    for name, value in trans.items():
        # Only overwrite keys that were in the task (missing/leftover), and only
        # if the LLM actually returned a non-empty translation.
        if name in task_keys and name in base and value and str(value).strip():
            merged[name] = encode(str(value))
            applied += 1
        else:
            skipped_unexpected += 1

    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for name in base_order:
        val = merged.get(name, base[name])
        lines.append(f'    <string name="{name}">{val}</string>')
    lines.append('</resources>')
    with open(target, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    preserved = len(merged) - applied
    print(f"  {locale}: applied {applied}, preserved {preserved} existing, skipped {skipped_unexpected}")

print("Done merging")
