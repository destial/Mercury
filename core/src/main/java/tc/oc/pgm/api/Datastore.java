package tc.oc.pgm.api;

import java.util.Map;
import java.util.UUID;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.map.MapActivity;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.api.player.PlayerData;
import tc.oc.pgm.api.player.Username;
import tc.oc.pgm.api.setting.Settings;

/** A fast, persistent datastore that provides synchronous responses. */
public interface Datastore {

  /**
   * Get the username for a given player {@link UUID}.
   *
   * @param uuid The {@link UUID} of a player.
   * @return A {@link Username}.
   */
  Username getUsername(UUID uuid);

  Username getUsername(String name);

  /**
   * Get the settings for a given player {@link UUID}.
   *
   * @param uuid The {@link UUID} of a player.
   * @return A {@link Settings}.
   */
  Settings getSettings(UUID uuid);

  /**
   * Get the activity related to a defined map pool.
   *
   * @param poolName The name of a defined map pool.
   * @return A {@link MapActivity}.
   */
  MapActivity getMapActivity(String poolName);

  /**
   * Get the coins for a given player {@link UUID}.
   *
   * @param uuid The {@link UUID} of a player.
   * @return A {@link Coins}.
   */
  Coins getCoins(UUID uuid);

  GlobalStats getStats(UUID uuid);

  void clearStats(UUID uuid);

  Map<UUID, GlobalStats> getAllStats();

  PlayerData getPlayerData(UUID uuid);

  /** Cleans up any resources or connections. */
  void close();
}
