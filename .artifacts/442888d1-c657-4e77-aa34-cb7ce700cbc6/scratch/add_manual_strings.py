path = '/Users/user/Downloads/Kizzy-Enhanced/common/resources/src/main/res/values/strings.xml'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add Manual RPC strings after enable_customRpc
old = '<string name="enable_customRpc">Enable Custom RPC</string>'
new = '''<string name="enable_customRpc">Enable Custom RPC</string>
    <string name="enable_manualRpc">Enable Manual RPC</string>
    <string name="manual_rpc_detect_apps">Allow Manual RPC to detect running apps</string>
    <string name="manual_rpc_detect_media">Allow Manual RPC to detect media playback</string>
    <string name="manual_rpc_title_toggle">Title</string>
    <string name="manual_rpc_title_toggle_desc">Show song info in the activity title instead of app name</string>'''

content = content.replace(old, new, 1)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Added Manual RPC strings")
