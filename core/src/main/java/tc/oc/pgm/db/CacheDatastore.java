package tc.oc.pgm.db;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import tc.oc.pgm.api.Datastore;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.map.MapActivity;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.api.player.PlayerData;
import tc.oc.pgm.api.player.Username;
import tc.oc.pgm.api.setting.Settings;

@SuppressWarnings({"UnstableApiUsage"})
public class CacheDatastore implements Datastore {

  private final Datastore datastore;
  private final LoadingCache<UUID, Username> usernames;
  private final LoadingCache<UUID, Settings> settings;
  private final LoadingCache<String, MapActivity> activities;
  private final LoadingCache<UUID, Coins> coins;
  private final LoadingCache<UUID, GlobalStats> stats;
  private final LoadingCache<UUID, PlayerData> playerData;

  public CacheDatastore(Datastore datastore) {
    this.datastore = datastore;
    this.usernames =
        CacheBuilder.newBuilder()
            .softValues()
            .build(
                new CacheLoader<UUID, Username>() {
                  @Override
                  public Username load(@NotNull UUID id) {
                    return datastore.getUsername(id);
                  }
                });
    this.settings =
        CacheBuilder.newBuilder()
            .maximumSize(Math.min(100, Bukkit.getMaxPlayers()))
            .build(
                new CacheLoader<UUID, Settings>() {
                  @Override
                  public Settings load(@NotNull UUID id) {
                    return datastore.getSettings(id);
                  }
                });
    this.activities =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, MapActivity>() {
                  @Override
                  public MapActivity load(@NotNull String name) {
                    return datastore.getMapActivity(name);
                  }
                });
    this.coins =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<UUID, Coins>() {
                  @Override
                  public Coins load(@NotNull UUID id) {
                    return datastore.getCoins(id);
                  }
                });
    this.stats =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<UUID, GlobalStats>() {
                  @Override
                  public GlobalStats load(@NotNull UUID id) {
                    return datastore.getStats(id);
                  }
                });
    this.playerData =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<UUID, PlayerData>() {
                  @Override
                  public PlayerData load(@NotNull UUID uuid) {
                    return datastore.getPlayerData(uuid);
                  }
                });
  }

  @Override
  public Username getUsername(UUID id) {
    return usernames.getUnchecked(id);
  }

  @Override
  public Username getUsername(String name) {
    return datastore.getUsername(name);
  }

  @Override
  public Settings getSettings(UUID id) {
    return settings.getUnchecked(id);
  }

  @Override
  public MapActivity getMapActivity(String poolName) {
    return activities.getUnchecked(poolName);
  }

  @Override
  public Coins getCoins(UUID id) {
    return coins.getUnchecked(id);
  }

  @Override
  public GlobalStats getStats(UUID uuid) {
    return stats.getUnchecked(uuid);
  }

  @Override
  public void clearStats(UUID uuid) {
    stats.invalidate(uuid);
    datastore.clearStats(uuid);
  }

  @Override
  public Map<UUID, GlobalStats> getAllStats() {
    return datastore.getAllStats();
  }

  @Override
  public PlayerData getPlayerData(UUID uuid) {
    return playerData.getUnchecked(uuid);
  }

  @Override
  public void close() {
    datastore.close();

    usernames.invalidateAll();
    settings.invalidateAll();
    coins.invalidateAll();
    stats.invalidateAll();
    activities.invalidateAll();
    playerData.invalidateAll();
  }
}
