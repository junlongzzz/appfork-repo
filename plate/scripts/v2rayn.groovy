import com.jayway.jsonpath.JsonPath
import groovy.json.JsonSlurper

static def checkUpdate(version, platform, args) {
    def preRelease = false

    if (args && args.prerelease) {
        preRelease = true
    }

    def response = (preRelease ? 'https://api.github.com/repos/2dust/v2rayN/releases' :
            'https://api.github.com/repos/2dust/v2rayN/releases/latest')
            .toURL().text
    def result = new JsonSlurper().parseText(response)

    if (preRelease && result instanceof List) {
        def read = JsonPath.read(result, '$.*[?(@.prerelease == true)]')
        if (read instanceof List && read.size() > 0) {
            result = read.first()
        } else {
            return null
        }
    }

    def tagName = result.tag_name as String
    version = tagName.replaceFirst('[vV]', '')

    def url = [:]
    for (asset in result.assets) {
        def name = asset.name as String
        if (name.endsWith('.zip') || name.endsWith('.7z')) {
            url[name] = asset.browser_download_url
        }
    }

    return [
            'version': version,
            'url'    : url
    ]
}
