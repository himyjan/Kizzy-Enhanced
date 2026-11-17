path = '/Users/user/Downloads/Kizzy-Enhanced/feature_manual_rpc/src/main/java/xyz/dead8309/feature_manual_rpc/ManualRpcScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'title = stringResource(R.string.experimental_rpc_title_toggle),\n\n                        description = stringResource(R.string.experimental_rpc_title_toggle_desc),',
    'title = stringResource(R.string.manual_rpc_title_toggle),\n\n                        description = stringResource(R.string.manual_rpc_title_toggle_desc),'
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed remaining experimental_rpc references")
