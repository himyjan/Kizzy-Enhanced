import os

base = os.path.dirname(os.path.abspath(__file__))

for name in ('fix_english_placeholders.py', 'audit_en.py'):
    path = os.path.join(base, name)
    with open(path, encoding='utf-8') as f:
        code = f.read()
    print(f'=== Running {name} ===')
    exec(compile(code, path, 'exec'))

print('=== All scripts completed ===')
