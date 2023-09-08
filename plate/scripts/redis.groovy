import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/redis-windows/redis-windows/releases/latest'.toURL().text
    def result = new JsonSlurper().parseText(response)
    version = result.tag_name as String

    def url = [:]
    for (asset in result.assets) {
        url[asset.name as String] = asset.browser_download_url
    }

    return [
            'version': version,
            'url'    : url
    ]
}
