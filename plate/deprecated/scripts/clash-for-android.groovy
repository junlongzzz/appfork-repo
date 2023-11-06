import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def result = new JsonSlurper().parse('https://api.github.com/repos/Kr328/ClashForAndroid/releases/latest'.toURL())

    def tagName = result.tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = [:]
    url['Get it on Google Play'] = 'https://play.google.com/store/apps/details?id=com.github.kr328.clash'
    for (asset in result.assets) {
        def name = asset.name as String
        if (name.containsIgnoreCase('-premium')) {
            url[name] = asset.browser_download_url
        }
    }

    return [
            'version': version,
            'url'    : url
    ]
}
