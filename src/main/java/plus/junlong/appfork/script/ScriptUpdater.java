package plus.junlong.appfork.script;

import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/**
 * @author Junlong
 */
public interface ScriptUpdater {

    Map<String, Object> checkUpdate(JSONObject manifest, JSONObject args);

}