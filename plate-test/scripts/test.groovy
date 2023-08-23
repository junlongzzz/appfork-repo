static def checkUpdate(version, platform, args) {
    println('test script...')

    println(checkUpdateTest(version, platform, args))

    return [
            version: 'beta',
            url    : 'https://junlong.plus/ztool/appfork'
    ]
}

static def checkUpdateTest(version, platform, args) {
    // test script here
}