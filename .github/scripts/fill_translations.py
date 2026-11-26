#!/usr/bin/env python3
"""
Fill all locale strings.xml files based on the English base (values/strings.xml).
Translates any missing strings and any English leftovers into the native language
of each locale using the Google Translate unofficial endpoint.

Protects:
- Format placeholders: %1$s, %2$d, %s, %d
- XML entities: &quot; &amp; &apos; &lt; &gt;
- Brand / technical terms that must stay identical across locales
"""
import re, os, sys, glob, json, time, urllib.parse, urllib.request, html

RES = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'common', 'resources', 'src', 'main', 'res')
RES = os.path.abspath(RES)
BASE = os.path.join(RES, 'values', 'strings.xml')

# Brand / technical terms kept identical in every locale
BRANDS = [
    'Kizzy Enhanced', 'Kizzy-Enhanced', 'Kizzy Quickie', 'Kizzy',
    'Discord', 'GitHub', 'Github', 'YouTube Music', 'YouTube', 'Spotify',
    'Last.fm', 'Libre.fm', 'ListenBrainz', 'Imgur', 'Twitch',
    'Nintendo Switch 2', 'Nintendo Switch', 'PlayStation', 'Nintendo',
    'Xbox', 'iOS', 'VR', 'Web', 'Desktop', 'Android', 'Windows', 'Linux',
    'OAuth2', 'OAuth', 'RPC', 'API', 'URL', 'ID', 'HTTP', 'HTTPS', 'JSON',
    'Webhook', 'Gateway', 'Bot Token', 'README', 'Kodi', 'VLC', 'Poweramp',
    'Musicolet', 'AIMP', 'MX Player', 'foobar2000', 'RetroMusic', 'Auxio',
    'Gramophone', 'NextPlayer', 'mpv-android', 'Morphe', 'AndDea', 'YMusic',
    'Revanced', 'DroidDeck', 'Eden', 'Samsung', 'Xiaomi', 'OnePlus',
    'Snapchat', 'Instagram', 'Facebook', 'Twitter', 'Telegram', 'WhatsApp',
]

# Locale code -> Google Translate target language code
def gtl_code(locale):
    m = re.match(r'values-(.+)', locale)
    code = m.group(1)
    # Google uses id for Indonesian, zh-CN / zh-TW for Chinese
    mapping = {
        'in': 'id', 'zh-rCN': 'zh-CN', 'zh-rTW': 'zh-TW',
        'fil': 'fil', 'nb': 'no', 'mi': 'mi', 'jv': 'jv',
    }
    return mapping.get(code, code)

def read_strings(path):
    """Return ordered list of (name, raw_value, attributes) and a name->value dict."""
    with open(path, encoding='utf-8') as f:
        content = f.read()
    entries = []
    for m in re.finditer(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', content, re.S):
        entries.append((m.group(1), m.group(3), m.group(2)))
    return entries, content

def decode_xml(s):
    return html.unescape(s)

def encode_xml(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '&quot;').replace("'", '&apos;')

# Sentinel markers for placeholders & brands (unlikely to be translated)
PLACEHOLDER_RE = re.compile(r'%\d?\$[sdf]|%[sdf]')
BRAND_RE = re.compile('|'.join(re.escape(b) for b in sorted(BRANDS, key=len, reverse=True)), re.I)

def protect(text):
    """Replace placeholders and brands with sentinel tokens."""
    tokens = {}
    idx = [0]
    def sub_placeholder(m):
        key = f' __KZPH{idx[0]}__ '.replace(' ', '')  # noqa
        tokens[key] = m.group(0)
        idx[0] += 1
        return key
    def sub_brand(m):
        key = f'__KZBR{idx[0]}__'
        tokens[key] = m.group(0)
        idx[0] += 1
        return key
    text = PLACEHOLDER_RE.sub(sub_placeholder, text)
    text = BRAND_RE.sub(sub_brand, text)
    return text, tokens

def restore(text, tokens):
    for k, v in tokens.items():
        text = text.replace(k, v)
    return text

_translate_cache = {}

def translate(text, target):
    """Translate text from English to target language via Google Translate."""
    if not text or text.strip() == '':
        return text
    key = (target, text)
    if key in _translate_cache:
        return _translate_cache[key]
    url = 'https://translate.googleapis.com/translate_a/single?' + urllib.parse.urlencode({
        'client': 'gtx', 'sl': 'en', 'tl': target, 'dt': 't', 'q': text
    })
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=20) as resp:
                data = json.loads(resp.read().decode('utf-8'))
            result = ''.join(seg[0] for seg in data[0] if seg and seg[0])
            _translate_cache[key] = result
            return result
        except Exception as e:
            if attempt == 3:
                print(f"  [WARN] translate failed ({target}): {e}", file=sys.stderr)
                _translate_cache[key] = text
                return text
            time.sleep(1 + attempt)
    return text

def is_brand_only(text):
    """True if the text consists only of brand/technical terms (should not be translated)."""
    cleaned = re.sub(r'[^\w\s]', ' ', decode_xml(text))
    words = [w for w in cleaned.split() if w]
    if not words:
        return True
    brand_words = set()
    for b in BRANDS:
        brand_words.update(w.lower() for w in b.split())
    return all(w.lower() in brand_words for w in words)

def needs_translation(en_value, loc_value):
    """True if a locale string is missing or is an untranslated English leftover."""
    if loc_value is None:
        return True
    e = re.sub(r'\s+', ' ', decode_xml(en_value)).strip().lower()
    l = re.sub(r'\s+', ' ', decode_xml(loc_value)).strip().lower()
    if e != l:
        return False
    return not is_brand_only(en_value)

def main():
    base_entries, _ = read_strings(BASE)
    base_names = [e[0] for e in base_entries]
    base_values = {e[0]: e[1] for e in base_entries}

    dirs = sorted(d for d in glob.glob(os.path.join(RES, 'values-*')) if os.path.isdir(d))
    print(f"Base strings: {len(base_entries)}, locales: {len(dirs)}")

    total_translated = 0
    for d in dirs:
        locale = os.path.basename(d)
        path = os.path.join(d, 'strings.xml')
        if not os.path.exists(path):
            print(f"[{locale}] MISSING FILE, skipping")
            continue
        entries, content = read_strings(path)
        loc_values = {e[0]: e[1] for e in entries}
        missing = [n for n in base_names if n not in loc_values]
        leftover = [n for n in base_names if n in loc_values and needs_translation(base_values[n], loc_values[n])]
        todo = missing + leftover
        if not todo:
            print(f"[{locale}] OK (all {len(base_names)} translated)")
            continue

        target = gtl_code(locale)
        print(f"[{locale}] translating {len(todo)} strings ({len(missing)} missing, {len(leftover)} leftovers) -> {target}")
        for i, name in enumerate(todo):
            en_raw = base_values[name]
            en_text = decode_xml(en_raw)
            if is_brand_only(en_text):
                translated_raw = en_raw
            else:
                protected, tokens = protect(en_text)
                result = translate(protected, target)
                translated_raw = encode_xml(restore(result, tokens))
                # Safety: if result still equals English and it's not brand-only, try once more without protection
                if re.sub(r'\s+', ' ', decode_xml(translated_raw)).strip().lower() == re.sub(r'\s+', ' ', en_text).strip().lower():
                    result2 = translate(en_text, target)
                    translated_raw = encode_xml(result2)
            loc_values[name] = translated_raw
            total_translated += 1
            if (i + 1) % 25 == 0:
                print(f"    {i+1}/{len(todo)}", flush=True)

        # Write complete file in base order (brand-only values kept as English)
        lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
        for name in base_names:
            val = loc_values.get(name, base_values[name])
            lines.append(f'    <string name="{name}">{val}</string>')
        lines.append('</resources>')
        with open(path, 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines) + '\n')

    print(f"\nTotal translations written: {total_translated}")

if __name__ == '__main__':
    main()
