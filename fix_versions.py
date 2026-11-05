with open('gradle/libs.versions.toml', 'r') as f:
    content = f.read()

# Replace IMGUR_API_BASE_URL with imgur-api-base-url
old = 'IMGUR_API_BASE_URL = "https://api.imgur.com/3"'
new = 'imgur-api-base-url = "https://api.imgur.com/3"'

if old in content:
    content = content.replace(old, new, 1)
    with open('gradle/libs.versions.toml', 'w') as f:
        f.write(content)
    print('Successfully updated gradle/libs.versions.toml')
else:
    print('Pattern not found')