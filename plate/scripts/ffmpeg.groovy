import java.util.regex.Matcher

static def checkUpdate(version, platform, args) {
    def response = 'https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-full.7z.ver'.toURL().text
    Matcher matcher = response =~ '([\\d.]+)'
    if (!matcher.find()) {
        return null
    }
    version = matcher[0][1] as String
    return [
            'version': version,
            'url'    : [
                    'essentials.7z'      : "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-${version}-essentials_build.7z".toString(),
                    'essentials.zip'     : "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-${version}-essentials_build.zip".toString(),
                    'full.7z [recommend]': "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-${version}-full_build.7z".toString(),
                    'full-shared.7z'     : "https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-${version}-full_build-shared.7z".toString(),
            ]
    ]
}