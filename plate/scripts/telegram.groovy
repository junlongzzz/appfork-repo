import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def url = [:]
    if (platform == 'windows') {
        def response = 'https://api.github.com/repos/telegramdesktop/tdesktop/releases/latest'.toURL().text
        def result = new JsonSlurper().parseText(response)
        def tagName = result.tag_name as String
        version = tagName.replaceFirst('[vV]', '')

        for (asset in result.assets) {
            def label = asset.label as String
            if (label.toLowerCase().contains('windows')) {
                url[label] = asset.browser_download_url
            }
        }
    } else if (platform == 'android') {
        def response = 'https://raw.githubusercontent.com/DrKLO/Telegram/master/TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java'.toURL().text
        def matcher = response =~ 'BUILD_VERSION_STRING = "([\\d.]+)"'
        if (!matcher.find()) {
            return null
        }
        version = matcher.group(1)
        url['Apk Installer'] = 'https://telegram.org/dl/android/apk'
        url['Download from Google Play'] = 'https://telegram.org/dl/android'
    }

    return [
            'version': version,
            'url'    : url
    ]
}
