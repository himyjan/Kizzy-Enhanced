import os

ROOT = '/Users/user/Downloads/Kizzy-Enhanced'
RES = os.path.join(ROOT, 'common/resources/src/main/res')


def sub_file(path, pairs):
    if not os.path.exists(path):
        print(f'MISSING FILE {path}')
        return
    with open(path, encoding='utf-8') as f:
        c = f.read()
    changed = False
    label = os.path.basename(os.path.dirname(path)) if path.endswith('strings.xml') else os.path.basename(path)
    for old, new in pairs:
        if old in c:
            n = c.count(old)
            c = c.replace(old, new)
            print(f'{label}: {n}x "{old[:60]}" -> "{new[:60]}"')
            changed = True
        else:
            print(f'{label}: not found "{old[:60]}"')
    if changed:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(c)


# Leftover "experimental" wording in language files -> native "manual" wording
lang_pairs = {
    'values-ar': [('RPC تجريبي', 'RPC يدوي')],
    'values-cs': [('Experimentální RPC', 'Ruční RPC'), ('experimentální RPC', 'ruční RPC')],
    'values-de': [('Experimentelles RPC', 'Manuelle RPC'), ('experimentelles RPC', 'manuelle RPC')],
    'values-ko': [('실험적 RPC', '수동 RPC')],
    'values-kr': [('실험적 RPC', '수동 RPC')],
    'values-nl': [('Experimentele RPC', 'Handmatige RPC'), ('experimentele RPC', 'handmatige RPC')],
    'values-ru': [
        ('Экспериментальный RPC', 'Ручной RPC'),
        ('экспериментальный RPC', 'ручной RPC'),
        ('Экспериментального RPC', 'Ручного RPC'),
        ('экспериментального RPC', 'ручного RPC'),
        ('Экспериментальному RPC', 'Ручному RPC'),
        ('экспериментальному RPC', 'ручному RPC'),
    ],
    'values-sv': [('Experimentell RPC', 'Manuell RPC'), ('experimentell RPC', 'manuell RPC')],
    'values-zh-rCN': [('实验性 RPC', '手动 RPC')],
    'values-zh-rTW': [('實驗性 RPC', '手動 RPC')],
}

for lang, pairs in lang_pairs.items():
    sub_file(os.path.join(RES, lang, 'strings.xml'), pairs)

# Docs / READMEs mentioning the old experimental name
sub_file(os.path.join(ROOT, 'README_RU.md'), [
    ('Экспериментального RPC', 'Ручного RPC'),
    ('Экспериментальный RPC', 'Ручной RPC'),
])
sub_file(os.path.join(ROOT, 'README_ZH-HANS.md'), [
    ('實驗性 RPC', '手動 RPC'),
    ('实验性 RPC', '手动 RPC'),
])
sub_file(os.path.join(ROOT, 'README_ZH-HANT.md'), [
    ('實驗性 RPC', '手動 RPC'),
    ('实验性 RPC', '手动 RPC'),
])
sub_file(os.path.join(ROOT, 'docs/upgrade-reports/index.md'), [
    ('(Custom, Media, Apps, Experimental)', '(Custom, Media, Apps, Manual)'),
])

print('=== Experimental cleanup done ===')
