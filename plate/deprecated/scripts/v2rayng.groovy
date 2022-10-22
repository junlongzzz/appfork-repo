import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://api.github.com/repos/2dust/v2rayNG/releases/latest'.toURL().text
    def tagName = new JsonSlurper().parseText(response).tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    return [
            'version': version,
            'url'    : [
                    'universal'  : "https://github.com/2dust/v2rayNG/releases/download/${version}/v2rayNG_${version}.apk".toString(),
                    'arm64-v8a'  : "https://github.com/2dust/v2rayNG/releases/download/${version}/v2rayNG_${version}_arm64-v8a.apk".toString(),
                    'armeabi-v7a': "https://github.com/2dust/v2rayNG/releases/download/${version}/v2rayNG_${version}_armeabi-v7a.apk".toString(),
                    'x86'        : "https://github.com/2dust/v2rayNG/releases/download/${version}/v2rayNG_${version}_x86.apk".toString(),
                    'x86_64'     : "https://github.com/2dust/v2rayNG/releases/download/${version}/v2rayNG_${version}_x86_64.apk".toString(),
            ]
    ]
}
