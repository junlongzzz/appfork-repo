package plus.junlong.appfork.updater;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AppFork implements CommandLineRunner {

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

    private static final Map<String, Script> SCRIPT_CACHE = new LinkedHashMap<>();

    @Value("${config.repo-path}")
    private String repoPath;

    @Override
    public void run(String... args) {
        log.info("开始软件库同步[{}]...", repoPath);

        File[] manifests = new File(repoPath, "manifests").listFiles(pathname -> pathname.isFile() && pathname.getName().endsWith(".json"));
        if (manifests == null) {
            log.error("目录内无清单文件或不存在该目录:{}", repoPath);
            return;
        }

        long startTime = System.currentTimeMillis();

        Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {
            int count = 0;
            for (File manifest : manifests) {
                try {
                    sync(manifest);
                    count++;
                } catch (Exception e) {
                    log.error("sync [{}] error:{}", manifest.getName(), e.getMessage());
                }
            }
            return count;
        });

        try {
            int count = (int) future.get(4, TimeUnit.HOURS);
            log.info("软件库同步完成，同步结果：{}/{}，耗时：{}ms", count, manifests.length, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("软件库同步出错[{}]，耗时：{}ms", e.getClass().getSimpleName(), System.currentTimeMillis() - startTime);
            // 同步超时强行中断程序退出
            System.exit(0);
        }

    }

    private void sync(File manifest) throws Exception {
        if (manifest.length() <= 0 || !manifest.canRead() || !manifest.canWrite()) {
            log.error("清单文件为空或无读写权限");
            return;
        }
        JSONObject manifestJson = JSON.parseObject(FileUtil.readUtf8String(manifest));
        if (manifestJson == null || manifestJson.isEmpty()) {
            log.error("清单文件解析为空");
            return;
        }

        String code = FileUtil.mainName(manifest).toLowerCase();
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
            return;
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
        File script = new File(manifest.getParentFile().getParent(), "scripts" + File.separator + scriptName.toLowerCase() + ".groovy");
        if (script.exists() && script.isFile()) {
            // groovy脚本运行
            String scriptFilename = script.getName();
            Script updateScript = SCRIPT_CACHE.get(scriptFilename);
            if (updateScript == null) {
                updateScript = new GroovyShell().parse(script);
                SCRIPT_CACHE.put(scriptFilename, updateScript);
            }
            // 执行检测App更新的脚本指定方法
            Object checkUpdateObj = updateScript.invokeMethod("checkUpdate", new Object[]{version, platform.toLowerCase(), scriptArgs});
            if (checkUpdateObj instanceof Map<?, ?> checkUpdate) {
                String checkVersion = null;
                // 获取脚本返回的错误信息
                Object error = checkUpdate.get("error");
                if (error != null) {
                    log.error("exec [{}] script [{}] return error:{}", manifest.getName(), scriptFilename, error);
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
                        // 只有一个链接，直接检查是否是合法链接形式
                        if (isUrl((String) updateUrlObj)) {
                            updateUrl = updateUrlObj;
                        }
                    } else if (updateUrlObj instanceof Map<?, ?> updateUrlMap) {
                        // 有多个链接，循环检查是否是合法链接形式
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
                        log.info("{} 同步新版本 {}->{}", code, version, checkVersion);
                    }
                }
            }
        }
    }

    private boolean isUrl(String text) {
        return ReUtil.isMatch("(http[s]?:)?//(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*\\(\\),]|(?:%[0-9a-fA-F][0-9a-fA-F]))+", text);
    }

}
