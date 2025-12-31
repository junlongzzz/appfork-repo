import com.alibaba.fastjson2.JSONObject
import plus.junlong.appfork.script.ScriptUpdater

class UpdateScript implements ScriptUpdater {

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def platform = manifest.platform as String

        def url = switch (platform) {
            case 'windows' -> 'https://update.todesk.com/windows/uplog.html'
            case 'mac' -> 'https://dl.todesk.com/macos/uplog.html'
            case 'linux' -> 'https://update.todesk.com/linux/uplog.html'
            case 'android' -> 'https://update.todesk.com/android/uplog.html'
            default -> null
        }

        if (url == null) {
            return null
        }

        def response = url.toURL().text
        def matcher = response =~ '>([\\d.]+)</div>'
        if (!matcher.find()) {
            return null
        }
        def version = matcher.group(1)

        url = switch (platform) {
            case 'windows' -> [
                    '在线安装包': 'https://dl.todesk.com/windows/ToDesk.exe',
                    '离线安装包': 'https://dl.todesk.com/windows/ToDesk_Setup.exe',
                    '精简版'    : 'https://dl.todesk.com/windows/ToDesk_Lite.exe'
            ]
            case 'mac' -> "https://dl.todesk.com/macos/ToDesk_${version}.pkg".toString()
            case 'android' -> "https://dl.todesk.com/android/ToDesk_${version}.apk".toString()
            case 'linux' -> {
                def paramsMatcher = 'https://www.todesk.com/linux.html'.toURL().text =~ 'window.__NUXT__.*(?<funcParams>\\(.*\\))\\);'
                if (paramsMatcher.find()) {
                    def linuxUrls = [:]
                    paramsMatcher.group('funcParams')
                            .replaceAll('[ "]', '') // 去除引号和空格
                            .replaceAll('\\\\u002F', '/') // unicode \u002F 转义
                            .split(',').each { item ->
                        if (item.startsWith('https://')) {
                            linuxUrls[item.substring(item.lastIndexOf('/') + 1)] = item
                        }
                    }
                    linuxUrls.isEmpty() ? null : linuxUrls
                } else {
                    null
                }
            }
            default -> null
        }

        if (url == null) {
            return null
        }

        return [
                'version': version,
                'url'    : url
        ]
    }

}