import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/Eugeny/tabby/releases/latest'.toURL().text
    def result = new JsonSlurper().parseText(response)

    def tagName = result.tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = [:]
    for (asset in result.assets) {
        def name = asset.name as String
        if (name.endsWith('.exe')) {
            url[name] = asset.browser_download_url
        }
    }

    return [
            'version': version,
            'url'    : url
    ]
}
