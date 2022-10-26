package plus.junlong.appfork.updater;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class AppFork implements CommandLineRunner {

    private static final String REPO_DIR = "plate-test";

    private static final Map<String, String> platforms = new LinkedHashMap<>();
    private static final Map<String, String> categories = new LinkedHashMap<>();

    static {
        // 平台列表
        platforms.put("windows", "Windows");
        platforms.put("linux", "Linux");
        platforms.put("mac", "macOS");
        platforms.put("android", "Android");
        platforms.put("extensions", "浏览器扩展");
        platforms.put("other", "Other");
        // 分类列表
        categories.put("network", "网络应用");
        categories.put("chat", "社交沟通");
        categories.put("music", "音乐欣赏");
        categories.put("video", "视频播放");
        categories.put("graphics", "图形图像");
        categories.put("games", "游戏娱乐");
        categories.put("office", "办公学习");
        categories.put("reading", "阅读翻译");
        categories.put("development", "编程开发");
        categories.put("tools", "系统工具");
        categories.put("beautify", "主题美化");
        categories.put("others", "其他应用");
        categories.put("image", "系统镜像");
    }

    @Override
    public void run(String... args) {
        log.info("开始软件库同步...");

        File[] manifests = new File(REPO_DIR, "manifests").listFiles(pathname -> pathname.isFile() && pathname.getName().endsWith(".json"));
        if (manifests == null) {
            log.error("manifests 为空或不存在该目录");
            return;
        }

        long startTime = System.currentTimeMillis();

        GroovyShell groovyShell = new GroovyShell();
        for (File manifest : manifests) {
            JSONObject manifestJson;
            try {
                if (manifest.length() <= 0 || !manifest.canRead() || !manifest.canWrite()) {
                    throw new RuntimeException("清单文件为空或无读写权限");
                }
                manifestJson = JSON.parseObject(FileUtil.readUtf8String(manifest));
                if (manifestJson == null || manifestJson.isEmpty()) {
                    throw new RuntimeException("清单文件解析为空");
                }
            } catch (Exception e) {
                log.error("manifest [{}] read error:{}", manifest.getName(), e.getMessage());
                continue;
            }

            String code = FileNameUtil.mainName(manifest).toLowerCase();
            String name = manifestJson.getString("name");
            String homepage = manifestJson.getString("homepage");
            // String logo = manifestJson.getString("logo");
            String author = manifestJson.getString("author");
            String description = manifestJson.getString("description");
            String category = manifestJson.getString("category");
            String platform = manifestJson.getString("platform");
            String version = manifestJson.getString("version");
            // String url = manifestJson.getString("url");
            Object scriptValue = manifestJson.get("script");

            if (StrUtil.isBlank(name) ||
                    StrUtil.isBlank(homepage) ||
                    StrUtil.isBlank(platform) || !platforms.containsKey(platform.toLowerCase()) ||
                    StrUtil.isBlank(category) || !categories.containsKey(category.toLowerCase())) {
                log.error("manifest [{}] has illegal attribute", manifest.getName());
                continue;
            }

            // 清单文件对应的script脚本文件名 默认为清单文件名一致
            String scriptName = code;
            // 脚本文件执行时的额外参数
            Map<?, ?> scriptArgs = null;
            if (scriptValue instanceof String) {
                scriptName = (String) scriptValue;
            } else if (scriptValue instanceof Map<?, ?> scriptValueMap) {
                Object nameObj = scriptValueMap.get("name");
                if (nameObj instanceof String) {
                    scriptName = (String) nameObj;
                }
                Object argsObj = scriptValueMap.get("args");
                if (argsObj instanceof Map) {
                    scriptArgs = (Map<?, ?>) argsObj;
                }
            }

            // 检查更新脚本
            File script = new File(REPO_DIR, "scripts" + File.separator + scriptName.toLowerCase() + ".groovy");
            if (script.exists() && script.isFile()) {
                try {
                    // groovy脚本运行
                    Script updateScript = groovyShell.parse(script);
                    // 执行检测App更新的脚本指定方法
                    Object checkUpdateObj = updateScript.invokeMethod("checkUpdate", new Object[]{version, platform.toLowerCase(), scriptArgs});
                    if (checkUpdateObj instanceof Map<?, ?> checkUpdate) {
                        String checkVersion = null;
                        // 获取脚本返回的错误信息
                        Object error = checkUpdate.get("error");
                        if (error != null) {
                            log.error("exec [{}] script [{}] return error:{}", manifest.getName(), script.getName(), error);
                        } else {
                            // 获取脚本返回的版本号
                            Object checkVersionObj = checkUpdate.get("version");
                            if (checkVersionObj instanceof String) {
                                checkVersion = (String) checkVersionObj;
                            }
                        }
                        // 发现新版本
                        if (!StrUtil.isBlank(checkVersion) && !checkVersion.equalsIgnoreCase(version)) {
                            // 获取脚本返回的下载链接
                            Object updateUrlObj = checkUpdate.get("url");
                            Object updateUrl = null;
                            if (updateUrlObj instanceof String) {
                                // 只有一个链接，直接检查是否时合法链接形式
                                if (isUrl((String) updateUrlObj)) {
                                    updateUrl = updateUrlObj;
                                }
                            } else if (updateUrlObj instanceof Map<?, ?> updateUrlMap) {
                                // 有多个链接，循环检查是否时合法链接形式
                                if (!updateUrlMap.isEmpty()) {
                                    updateUrl = updateUrlMap;
                                    for (Object value : updateUrlMap.values()) {
                                        if (!(value instanceof String) || !isUrl((String) value)) {
                                            updateUrl = null;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (updateUrl != null) {
                                log.info("{} 发现新版本 {}->{}", code, version, checkVersion);
                                // 更新版本信息
                                manifestJson.put("version", checkVersion);
                                manifestJson.put("url", updateUrl);
                                if (StrUtil.isBlank(author)) {
                                    manifestJson.put("author", "[Unknown]");
                                }
                                if (StrUtil.isBlank(description)) {
                                    manifestJson.put("description", "[暂无描述]");
                                }
                                // 将新版清单内容写入文件
                                FileUtil.writeUtf8String(JSON.toJSONString(manifestJson, JSONWriter.Feature.PrettyFormat), manifest);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("exec [{}] script [{}] error:{}", manifest.getName(), script.getName(), e.getMessage());
                }
            }
        }

        log.info("软件库同步完成，耗时：{}ms", System.currentTimeMillis() - startTime);
    }

    private boolean isUrl(String text) {
        return !StrUtil.isBlank(text) && ReUtil.isMatch("(http[s]?:)?//(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*\\(\\),]|(?:%[0-9a-fA-F][0-9a-fA-F]))+", text);
    }

}
