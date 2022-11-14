package tc.oc.pgm.elo;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import tc.oc.pgm.api.PGM;

public class EloConfig {
  private final Map<String, EloOptions> settings = new HashMap<>();
  private final int defaultElo;
  private final int maxPlayers;

  public EloConfig(FileConfiguration config) {
    defaultElo = config.getInt("default-elo", 1000);
    maxPlayers = config.getInt("max-players", 5);

    for (String key : config.getConfigurationSection("elo").getKeys(false)) {
      EloOptions options = new EloOptions();
      options.enabled = config.getBoolean("elo." + key + ".enabled", false);
      options.useScale = config.getBoolean("elo." + key + ".use-scale", false);
      options.scale = (float) config.getDouble("elo." + key + ".scale", 0f);
      options.points = config.getInt("elo." + key + ".points", 0);
      settings.put(key, options);
      PGM.get().getLogger().info("[ELO] Added elo option: " + key);
    }
  }

  public int getMaxPlayers() {
    return maxPlayers;
  }

  public Map<String, EloOptions> getSettings() {
    return settings;
  }

  public int getDefaultElo() {
    return defaultElo;
  }

  public static class EloOptions {
    public boolean enabled;
    public boolean useScale;
    public float scale;
    public int points;
  }
}
