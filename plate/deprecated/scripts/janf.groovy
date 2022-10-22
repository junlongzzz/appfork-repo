import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/ja-netfilter/ja-netfilter/releases/latest'.toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    return [
            'version': version,
            'url'    : "https://github.com/ja-netfilter/ja-netfilter/releases/download/${version}/ja-netfilter-${version}.zip".toString()
    ]
}
