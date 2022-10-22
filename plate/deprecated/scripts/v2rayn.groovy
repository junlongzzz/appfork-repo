import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/2dust/v2rayN/releases/latest'.toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    return [
            'version': version,
            'url'    : [
                    'v2rayN-Core.zip': "https://github.com/2dust/v2rayN/releases/download/${version}/v2rayN-Core.zip".toString(),
                    'v2rayN.zip'     : "https://github.com/2dust/v2rayN/releases/download/${version}/v2rayN.zip".toString()
            ]
    ]
}
