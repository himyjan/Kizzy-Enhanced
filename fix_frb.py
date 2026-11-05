with open('feature_rpc_base/build.gradle.kts', 'r') as f:
    content = f.read()

old = '''    implementation(projects.data)
implementation(projects.data)'''

new = '''    implementation(projects.data)'''

content = content.replace(old, new, 1)

with open('feature_rpc_base/build.gradle.kts', 'w') as f:
    f.write(content)

print('Done!')