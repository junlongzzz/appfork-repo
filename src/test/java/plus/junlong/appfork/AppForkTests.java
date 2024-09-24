package plus.junlong.appfork;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class AppForkTests {

    @Test
    public void convertJsonOrYaml() {
        Path path = Path.of("plate/manifests/b/bing-wallpaper.json");
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            log.error("文件不存在: {}", path);
            return;
        }
        File file = path.toFile();
        String format = FileUtil.extName(file).toLowerCase();
        String read = FileUtil.readUtf8String(file);
        if (StrUtil.isBlank(read)) {
            log.error("文件内容为空");
            return;
        }
        log.info("文件内容：\n{}", read);
        String write;
        if ("json".equals(format)) {
            // convert to yaml
            format = "yaml";
            JSONObject manifestJson = JSON.parseObject(read);
            write = new Yaml().dumpAsMap(manifestJson);
        } else if ("yaml".equals(format)) {
            // convert to json
            format = "json";
            JSONObject manifestJson = new Yaml().loadAs(read, JSONObject.class);
            write = JSON.toJSONString(manifestJson, JSONWriter.Feature.PrettyFormat);
        } else {
            log.error("不支持的文件类型");
            return;
        }
        String fromFilePath = file.getAbsolutePath();
        String toFilePath = fromFilePath.substring(0, fromFilePath.lastIndexOf(".") + 1) + format;
        log.info("转换后文件：{}", toFilePath);
        FileUtil.writeUtf8String(write, toFilePath);
        log.info("转换后：\n{}", write);
    }

}
