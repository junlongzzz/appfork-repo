import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def result = new JsonSlurper().parse('https://api.github.com/repos/Fndroid/clash_for_windows_pkg/releases/latest'.toURL())

    def tagName = result.tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = [:]
    for (asset in result.assets) {
        def name = asset.name as String
        if (name.endsWithIgnoreCase('.exe') || name.containsIgnoreCase('-win')) {
            url[name] = asset.browser_download_url
        }
    }

    return [
            'version': version,
            'url'    : url
    ]
}
