import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/telegramdesktop/tdesktop/releases/latest'.toURL().text
    def result = new JsonSlurper().parseText(response)
    def tagName = result.tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = [:]
    for (asset in result.assets) {
        def label = asset.label as String
        if (label.toLowerCase().contains('windows')) {
            url[label] = asset.browser_download_url
        }
    }

    return [
            'version': version,
            'url'    : url
    ]
}
