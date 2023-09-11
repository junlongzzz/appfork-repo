import cn.hutool.core.comparator.VersionComparator
import groovy.yaml.YamlSlurper

static def checkUpdate(version, platform, args) {
    def latest = new YamlSlurper().parseText('https://res.u-tools.cn/version2/latest.yml'.toURL().text)
    if (VersionComparator.INSTANCE.compare(latest.version as String, version as String) != 1) {
        return null
    }
    version = latest.version
    if (version == null) {
        return null
    }

    def url = null
    switch (platform) {
        case 'windows':
            url = [
                    '64位': "https://res.u-tools.cn/version2/uTools-${version}.exe".toString(),
                    '32位': "https://res.u-tools.cn/version2/uTools-${version}-ia32.exe".toString(),
            ]
            break
        case 'mac':
            url = [
                    'Intel芯片版本': "https://res.u-tools.cn/version2/uTools-${version}.dmg".toString(),
                    'Apple芯片版本': "https://res.u-tools.cn/version2/uTools-${version}-arm64.dmg".toString(),
            ]
            break
        case 'linux':
            url = [
                    'amd64_deb': "https://res.u-tools.cn/version2/utools_${version}_amd64.deb".toString()
            ]
            break
        default:
            return null
    }

    return [
            'version': version,
            'url'    : url
    ]
}

// win64 https://res.u-tools.cn/version2/uTools-2.6.3.exe
// win32 https://res.u-tools.cn/version2/uTools-2.6.3-ia32.exe
// mac intel https://res.u-tools.cn/version2/uTools-2.6.3.dmg
// mac apple https://res.u-tools.cn/version2/uTools-2.6.3-arm64.dmg
// linux https://res.u-tools.cn/version2/utools_2.6.3_amd64.deb
