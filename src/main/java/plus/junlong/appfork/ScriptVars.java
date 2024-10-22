package plus.junlong.appfork;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * 脚本文件内可使用的变量
 *
 * @author Junlong
 */
public final class ScriptVars {

    /**
     * HTTP请求 User-Agent
     */
    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0";

    /**
     * 默认的 HttpClient
     */
    public static final HttpClient HTTP_CLIENT = newHttpClientBuilder().build();

    /**
     * 创建一个 HttpClient.Builder
     * * 自动跟随重定向
     * * 连接超时 120s
     * * 使用虚拟线程
     */
    public static HttpClient.Builder newHttpClientBuilder() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofMillis(120000))
                // 虚拟线程
                .executor(Executors.newVirtualThreadPerTaskExecutor());
    }

}
