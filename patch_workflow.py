with open('.github/workflows/auto-release.yml', 'r') as f:
    text = f.read()

target = '''      - name: Generate Version Name
        id: version
        run: |
          VERSION="v1.$(date +'%Y%m%d%H%M%S')"
          echo "version=$VERSION" >> $GITHUB_OUTPUT
          echo "Generated version: $VERSION"
      - name: Build Debug APK'''

replacement = '''      - name: Generate Version Name
        id: version
        run: |
          VERSION="v1.$(date +'%Y%m%d%H%M%S')"
          echo "version=$VERSION" >> $GITHUB_OUTPUT
          echo "Generated version: $VERSION"
      - name: Inject Version into Gradle
        run: |
          sed -i "s/versionName = \\\".*\\\"/versionName = \\\"${{ steps.version.outputs.version }}\\\"/g" app/build.gradle.kts
      - name: Build Debug APK'''

text = text.replace(target, replacement)
with open('.github/workflows/auto-release.yml', 'w') as f:
    f.write(text)
