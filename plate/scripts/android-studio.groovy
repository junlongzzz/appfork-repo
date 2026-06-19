import com.alibaba.fastjson2.JSONObject
import plus.junlong.appfork.script.ScriptUpdater
import plus.junlong.appfork.script.ScriptVars

import java.net.http.HttpResponse

class UpdateScript implements ScriptUpdater {

    // 官方下载页，页面内包含各平台的真实下载链接
    static final String DOWNLOAD_PAGE = 'https://developer.android.com/studio'

    // 匹配页面内的下载链接，从路径中取版本号，从文件名中取实际文件名（文件名使用代号而非版本号，如 quail1-patch2）
    // 示例：https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.1.1.10/android-studio-quail1-patch2-windows.zip
    static final String LINK_REGEX =
            'https://[a-z0-9.]*gvt1\\.com/android/studio/(?:install|ide-zips)/' +
                    '(?<version>[\\d.]+)/(?<filename>android-studio-[^"\'\\s]+?-' +
                    '(?<suffix>windows\\.exe|windows\\.zip|mac\\.dmg|mac_arm\\.dmg|linux\\.tar\\.gz))'

    // 各平台需要收集的文件后缀
    static final Map<String, List<String>> PLATFORM_SUFFIXES = [
            'windows': ['windows.exe', 'windows.zip'],
            'mac'    : ['mac.dmg', 'mac_arm.dmg'],
            'linux'  : ['linux.tar.gz']
    ]

    // 各文件后缀追加在「软件名 + 版本号」之后的标识，主下载项留空（只显示软件名 + 版本号）
    static final Map<String, String> SUFFIX_LABELS = [
            'windows.exe' : '',
            'windows.zip' : 'zip',
            'mac.dmg'     : '',
            'mac_arm.dmg' : 'arm',
            'linux.tar.gz': ''
    ]

    @Override
    Object checkUpdate(JSONObject manifest, JSONObject args) {
        def platform = manifest.platform as String
        def wantSuffixes = PLATFORM_SUFFIXES[platform]
        if (!wantSuffixes) {
            return null
        }

        def request = ScriptVars.newRequestBuilder()
                .setHeader('User-Agent', ScriptVars.USER_AGENT_NONE)
                .uri(DOWNLOAD_PAGE.toURI())
                .GET()
                .build()
        def response = ScriptVars.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return [error: "Request to ${DOWNLOAD_PAGE} returned HTTP ${response.statusCode()}" as String]
        }

        def appName = manifest.name as String
        def version = null
        def url = [:]
        def matcher = response.body() =~ LINK_REGEX
        while (matcher.find()) {
            def suffix = matcher.group('suffix')
            if (!wantSuffixes.contains(suffix)) {
                continue
            }
            version = matcher.group('version')
            // 下载项名称使用「软件名 + 版本号 + 架构」，仅在需要区分时追加架构/类型标识
            def label = SUFFIX_LABELS[suffix]
            def name = label ? "${appName} ${version} (${label})" : "${appName} ${version}"
            url[name as String] = matcher.group(0)
        }

        if (!version || url.isEmpty()) {
            return null
        }

        return [
                version: version,
                url    : url
        ]
    }

}
