package tc.oc.pgm.api.player;

import org.json.JSONObject;

public interface PlayerData {
  JSONObject getData();

  void setData(String key, Object value);

  void removeData(String key);
}
