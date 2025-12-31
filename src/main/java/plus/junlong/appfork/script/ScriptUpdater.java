package plus.junlong.appfork.script;

import com.alibaba.fastjson2.JSONObject;

/**
 * @author Junlong
 */
public interface ScriptUpdater {

    Object checkUpdate(JSONObject manifest, JSONObject args);

}