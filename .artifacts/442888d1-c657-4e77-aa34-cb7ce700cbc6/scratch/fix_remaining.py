import re

# Fix Turkish: "Deneysel RPC\'yi başlat" -> "Manual RPC'yi başlat"
path = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res/values-tr/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace(
    '<string name="start_manualRpc">Deneysel RPC\'yi başlat</string>',
    '<string name="start_manualRpc">Manual RPC\'yi başlat</string>'
)
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Turkish main fixed")

# Fix Saudi Arabic: "ابدا تشغيل آر بي سي手动" -> proper Arabic
path = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res/values-sa/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace(
    '<string name="start_manualRpc">ابدا تشغيل آر بي سي手动</string>',
    '<string name="start_manualRpc">ابدأ تشغيل RPC يدويًا</string>'
)
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Saudi Arabic fixed")

print("Done!")
