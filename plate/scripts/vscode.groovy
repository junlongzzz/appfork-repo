import com.jayway.jsonpath.JsonPath

static def checkUpdate(version, platform, args) {
    def jsonpath = switch (platform) {
        case 'windows' -> '$.products[?(@.platform.os =~ /win32(.*)/i)]'
        case 'linux' -> '$.products[?(@.platform.os =~ /linux(.*)/i)]'
        case 'mac' -> '$.products[?(@.platform.os =~ /darwin(.*)/i)]'
        default -> null
    }

    if (jsonpath == null) {
        return null
    }

    def response = new URL('https://code.visualstudio.com/sha?build=stable').text
    def read = JsonPath.read(response, jsonpath)
    def url = [:]
    if (read instanceof List) {
        read.forEach(product -> {
            version = product.name
            url[product.platform.prettyname as String] = "https://update.code.visualstudio.com/${version}/${product.platform.os}/stable" as String
        })
    } else {
        return null
    }

    return [
            'version': version,
            'url'    : url
    ]
}
