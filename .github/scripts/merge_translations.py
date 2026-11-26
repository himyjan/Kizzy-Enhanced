#!/usr/bin/env python3
"""
Merge LLM translations from /tmp/kizzy_i18n/out/*.json into the locale
strings.xml files, using values/strings.xml as the canonical key/order source.
"""
import re, os, glob, json, html

RES = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..',
                                   'common', 'resources', 'src', 'main', 'res'))
BASE = os.path.join(RES, 'values', 'strings.xml')

def load(p):
    with open(p, encoding='utf-8') as f:
        return dict(re.findall(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', f.read(), re.S))

def encode(v):
    # v comes from LLM as plain text; encode for Android XML
    return (v.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
             .replace('"', '&quot;').replace("'", '&apos;'))

base = load(BASE)
base_order = list(base.keys())
out_dir = '/tmp/kizzy_i18n/out'

files = sorted(glob.glob(os.path.join(out_dir, 'values-*.json')))
print(f"Found {len(files)} translation output files")

for fp in files:
    locale = os.path.basename(fp)[:-5]
    target = os.path.join(RES, locale, 'strings.xml')
    if not os.path.exists(os.path.dirname(target)):
        print(f"  SKIP {locale} (no dir)")
        continue
    loc = load(target) if os.path.exists(target) else {}
    with open(fp, encoding='utf-8') as f:
        trans = json.load(f)
    merged = dict(loc)
    applied = 0
    for name, value in trans.items():
        if name in base and value and str(value).strip():
            merged[name] = encode(str(value))
            applied += 1

    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for name in base_order:
        val = merged.get(name, base[name])
        lines.append(f'    <string name="{name}">{val}</string>')
    lines.append('</resources>')
    with open(target, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    print(f"  {locale}: applied {applied} translations -> {len(base_order)} total strings")

print("Done merging")
