import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def response = 'https://im.qq.com/pcqq'.toURL().text
    def matcher = response =~ '<script>window.__INITIAL_STATE__=(\\{.*})</script>'
    if (!matcher.find()) {
        return null
    }
    def initialState = new JsonSlurper().parseText(matcher[0][1].toString().replaceAll('\\\\u002F', '/'))
    if (initialState.isError) {
        return null
    }

    return [
            version: initialState.rainbowConfig.banner.version,
            url    : initialState.rainbowConfig.banner.downloadUrl
    ]
}
