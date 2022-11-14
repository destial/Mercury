package tc.oc.pgm.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.json.JSONObject;
import tc.oc.pgm.api.Datastore;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.map.MapActivity;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.api.player.PlayerData;
import tc.oc.pgm.api.player.Username;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.api.setting.Settings;
import tc.oc.pgm.util.concurrent.ThreadSafeConnection;
import tc.oc.pgm.util.text.TextParser;

public class SQLDatastore extends ThreadSafeConnection implements Datastore {

  public SQLDatastore(String uri, int maxConnections) throws SQLException {
    super(() -> TextParser.parseSqlConnection(uri), maxConnections);

    submitQuery(
        () ->
            "CREATE TABLE IF NOT EXISTS usernames (id VARCHAR(36) PRIMARY KEY, name VARCHAR(16), expires LONG)");
    submitQuery(() -> "CREATE TABLE IF NOT EXISTS settings (id VARCHAR(36) PRIMARY KEY, bit LONG)");
    submitQuery(
        () ->
            "CREATE TABLE IF NOT EXISTS pools (name VARCHAR(255) PRIMARY KEY, next_map VARCHAR(255), last_active BOOLEAN)");
    submitQuery(() -> "CREATE TABLE IF NOT EXISTS coins (id VARCHAR(36) PRIMARY KEY, amount LONG)");
    submitQuery(
        () -> "CREATE TABLE IF NOT EXISTS stats (id VARCHAR(36) PRIMARY KEY, json VARCHAR(255))");
    submitQuery(
        () ->
            "CREATE TABLE IF NOT EXISTS playerdata (id VARCHAR(36) PRIMARY KEY, data VARCHAR(256))");
  }
  // -------------------------------------------------------------------------
  private class SQLUsername extends UsernameImpl {

    private volatile boolean queried;

    SQLUsername(@Nullable UUID id, @Nullable String name) {
      super(id, name);
    }

    @Override
    public String getNameLegacy() {
      String name = super.getNameLegacy();

      // Since there can be hundreds of names, only query when requested.
      if (!queried && name == null) {
        queried = true;
        submitQuery(new SelectQuery());
      }

      return name;
    }

    @Override
    public void setName(@Nullable String name) {
      super.setName(name);

      if (name != null) {
        submitQuery(new UpdateQuery());
      }
    }

    @Override
    public UUID getId() {
      UUID id = super.getId();
      if (id == null && super.getNameLegacy() != null) {
        try {
          submitQuery(new SelectName()).get();
        } catch (InterruptedException | ExecutionException e) {
          e.printStackTrace();
        }
      }
      return super.getId();
    }

    private class SelectName implements Query {
      @Override
      public String getFormat() {
        return "SELECT id, expires FROM usernames WHERE name = ? LIMIT 1";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getNameLegacy());

        try (final ResultSet result = statement.executeQuery()) {
          if (!result.next()) return;

          setId(UUID.fromString(result.getString(1)));
        }
      }
    }

    private class SelectQuery implements Query {
      @Override
      public String getFormat() {
        return "SELECT name, expires FROM usernames WHERE id = ? LIMIT 1";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());

        try (final ResultSet result = statement.executeQuery()) {
          if (!result.next()) return;

          setName(result.getString(1));
        }
      }
    }

    private class UpdateQuery implements Query {
      @Override
      public String getFormat() {
        return "REPLACE INTO usernames VALUES (?, ?, ?)";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());
        statement.setString(2, getNameLegacy());

        // Pick a random expiration time between 1 and 2 weeks
        statement.setLong(
            3,
            System.currentTimeMillis() + Duration.ofDays(7 + (int) (Math.random() * 7)).toMillis());

        statement.executeUpdate();
      }
    }
  }

  @Override
  public Username getUsername(UUID id) {
    return new SQLUsername(id, null);
  }

  @Override
  public Username getUsername(String name) {
    return new SQLUsername(null, name);
  }

  // -------------------------------------------------------------------------
  private class SQLSettings extends SettingsImpl {

    SQLSettings(UUID id, long bit) {
      super(id, bit);
      submitQuery(new SelectQuery());
    }

    @Override
    public void setValue(SettingKey key, SettingValue value) {
      final long oldBit = getBit();
      super.setValue(key, value);

      if (oldBit == getBit()) return;
      submitQuery(oldBit <= 0 ? new InsertQuery(value) : new UpdateQuery(value));
    }

    private class SelectQuery implements Query {
      @Override
      public String getFormat() {
        return "SELECT bit FROM settings WHERE id = ? LIMIT 1";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());

        try (final ResultSet result = statement.executeQuery()) {
          if (result.next()) {
            setBit(result.getLong(1));
          }
        }
      }
    }

    private class InsertQuery implements Query {

      private final SettingValue value;

      private InsertQuery(SettingValue value) {
        this.value = value;
      }

      @Override
      public String getFormat() {
        return "REPLACE INTO settings VALUES (?,?)";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());
        statement.setLong(2, bitSettings(value));

        statement.executeUpdate();
      }
    }

    private class UpdateQuery implements Query {

      private final SettingValue value;

      private UpdateQuery(SettingValue value) {
        this.value = value;
      }

      @Override
      public String getFormat() {
        return "UPDATE settings SET bit = ((bit & ~?) | ?) WHERE id = ?";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setLong(2, bitSettings(value));
        statement.setString(3, getId().toString());

        for (SettingValue unset : value.getKey().getPossibleValues()) {
          if (unset == value) continue;
          statement.setLong(1, bitSettings(unset));
          statement.addBatch();
        }

        statement.executeBatch();
      }
    }
  }

  @Override
  public Settings getSettings(UUID id) {
    return new SQLSettings(id, 0);
  }
  // -------------------------------------------------------------------------
  public class SQLCoins extends CoinsImpl {
    SQLCoins(UUID id, long amount) {
      super(id, amount);
      submitQuery(new SelectQuery());
    }

    @Override
    public void setCoins(long amount) {
      final long oldCoins = super.getCoins();
      super.setCoins(amount);

      if (oldCoins != super.getCoins()) {
        submitQuery(new InsertQuery(amount));
      }
    }

    private class SelectQuery implements Query {
      @Override
      public String getFormat() {
        return "SELECT amount FROM coins WHERE id = ?";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());

        try (final ResultSet result = statement.executeQuery()) {
          if (!result.next()) return;

          setCoins(result.getLong(1));
        }
      }
    }

    private class InsertQuery implements Query {
      private final long amount;

      private InsertQuery(long amount) {
        this.amount = amount;
      }

      @Override
      public String getFormat() {
        return "REPLACE INTO coins (id,amount) VALUES (?,?)";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());
        statement.setLong(2, amount);
        statement.executeUpdate();
      }
    }
  }

  @Override
  public Coins getCoins(UUID uuid) {
    return new SQLCoins(uuid, 0);
  }

  public class SQLStats extends GlobalStatsImpl {
    SQLStats(UUID uuid) {
      super(uuid);
      submitQuery(new SQLStats.SelectQuery());
    }

    @Override
    public void arrowHit() {
      final int a = super.getArrowsHit();
      super.arrowHit();
      if (a != super.getArrowsHit()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void addKill() {
      final int k = super.getKills();
      super.addKill();
      if (k != super.getKills()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void addDeath() {
      final int k = super.getDeaths();
      super.addDeath();
      if (k != super.getDeaths()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void mvp() {
      final int k = super.getMVP();
      super.mvp();
      if (k != super.getMVP()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setMonumentsBroken(int m) {
      final int k = super.getMonumentsBroken();
      super.setMonumentsBroken(m);
      if (k != super.getMonumentsBroken()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setCoresLeaked(int m) {
      final int k = super.getCoresLeaked();
      super.setCoresLeaked(m);
      if (k != super.getCoresLeaked()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setFlagsCaptured(int m) {
      final int k = super.getFlagsCaptured();
      super.setFlagsCaptured(m);
      if (k != super.getFlagsCaptured()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setWoolsCaptured(int m) {
      final int k = super.getWoolsCaptured();
      super.setWoolsCaptured(m);
      if (k != super.getWoolsCaptured()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setWins(int m) {
      final int k = super.getWins();
      super.setWins(m);
      if (k != super.getWins()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setLosses(int m) {
      final int k = super.getLosses();
      super.setLosses(m);
      if (k != super.getLosses()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setMVP(int m) {
      final int k = super.getMVP();
      super.setMVP(m);
      if (k != super.getMVP()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void breakMonument() {
      final int k = super.getMonumentsBroken();
      super.breakMonument();
      if (k != super.getMonumentsBroken()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void leakCore() {
      final int k = super.getCoresLeaked();
      super.leakCore();
      if (k != super.getCoresLeaked()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void captureFlag() {
      final int k = super.getFlagsCaptured();
      super.captureFlag();
      if (k != super.getFlagsCaptured()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void captureWool() {
      final int k = super.getWoolsCaptured();
      super.captureWool();
      if (k != super.getWoolsCaptured()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void win() {
      final int k = super.getWins();
      super.win();
      if (k != super.getWins()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void lose() {
      final int k = super.getLosses();
      super.lose();
      if (k != super.getLosses()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setKills(int kills) {
      final int k = super.getKills();
      super.setKills(kills);
      if (k != kills) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setDeaths(int deaths) {
      final int d = super.getDeaths();
      super.setDeaths(deaths);
      if (d != deaths) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setLongestShot(int shot) {
      final int l = super.getLongestShot();
      super.setLongestShot(shot);
      if (l != super.getLongestShot()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setHighestKS(int ks) {
      final int k = super.getHighestKS();
      super.setHighestKS(ks);
      if (k != super.getHighestKS()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void addElo(int elo) {
      final int e = super.getElo();
      super.addElo(elo);
      if (e != super.getElo()) {
        submitQuery(new SQLStats.InsertQuery(super.toString()));
      }
    }

    @Override
    public void setStats(JSONObject object) {
      final String stats = super.toString();
      super.setStats(object);
      if (!stats.equalsIgnoreCase(super.toString())) {
        submitQuery(new SQLStats.InsertQuery(object.toString()));
      }
    }

    private class SelectQuery implements Query {
      @Override
      public String getFormat() {
        return "SELECT json FROM stats WHERE id = ?";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());

        try (final ResultSet result = statement.executeQuery()) {
          if (!result.next()) return;
          String s = result.getString(1);
          JSONObject object = new JSONObject(s);
          setStats(object);
        }
      }
    }

    private class InsertQuery implements Query {
      private final String json;

      private InsertQuery(String json) {
        this.json = json;
      }

      @Override
      public String getFormat() {
        return "REPLACE INTO stats (id,json) VALUES (?,?)";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getId().toString());
        statement.setString(2, json);
        statement.executeUpdate();
      }
    }
  }

  private static class RemoveQuery implements Query {
    private final UUID uuid;

    private RemoveQuery(UUID uuid) {
      this.uuid = uuid;
    }

    @Override
    public String getFormat() {
      return "DELETE FROM stats WHERE id = ?";
    }

    @Override
    public void query(PreparedStatement statement) throws SQLException {
      statement.setString(1, uuid.toString());
      statement.executeUpdate();
    }
  }

  private class AllQuery implements Query {
    public final Map<UUID, GlobalStats> all_stats;

    private AllQuery() {
      all_stats = new HashMap<>();
    }

    @Override
    public String getFormat() {
      return "SELECT * FROM stats";
    }

    @Override
    public void query(PreparedStatement statement) throws SQLException {
      try (final ResultSet result = statement.executeQuery()) {
        all_stats.clear();
        while (result.next()) {
          UUID id = UUID.fromString(result.getString(1));
          JSONObject object = new JSONObject(result.getString(2));
          GlobalStats stats = new GlobalStatsImpl(id);
          stats.setStats(object);
          all_stats.put(id, stats);
        }
      }
    }
  }

  @Override
  public GlobalStats getStats(UUID uuid) {
    return new SQLStats(uuid);
  }

  @Override
  public void clearStats(UUID uuid) {
    submitQuery(new RemoveQuery(uuid));
  }

  @Override
  public Map<UUID, GlobalStats> getAllStats() {
    AllQuery all = new AllQuery();
    try {
      submitQuery(all).get();
      return all.all_stats;
    } catch (Exception e) {
      e.printStackTrace();
      return new HashMap<>();
    }
  }

  private class SQLPlayerData implements PlayerData {
    private final UUID uuid;
    private JSONObject object = new JSONObject();

    SQLPlayerData(UUID uuid) {
      this.uuid = uuid;
      submitQuery(new SelectQuery());
    }

    @Override
    public JSONObject getData() {
      return object;
    }

    @Override
    public void setData(String key, Object value) {
      object.put(key, value);
      submitQuery(new InsertQuery(object.toString()));
    }

    @Override
    public void removeData(String key) {
      object.remove(key);
      submitQuery(new InsertQuery(object.toString()));
    }

    private class SelectQuery implements Query {
      @Override
      public String getFormat() {
        return "SELECT data FROM playerdata WHERE id = ?";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, uuid.toString());

        try (final ResultSet result = statement.executeQuery()) {
          if (!result.next()) return;
          String s = result.getString(1);
          object = new JSONObject(s);
        }
      }
    }

    private class InsertQuery implements Query {
      private final String json;

      private InsertQuery(String json) {
        this.json = json;
      }

      @Override
      public String getFormat() {
        return "REPLACE INTO playerdata (id,data) VALUES (?,?)";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, uuid.toString());
        statement.setString(2, json);
        statement.executeUpdate();
      }
    }
  }

  @Override
  public PlayerData getPlayerData(UUID uuid) {
    return new SQLPlayerData(uuid);
  }

  // -------------------------------------------------------------------------
  private class SQLMapActivity extends MapActivityImpl {

    SQLMapActivity(String poolName, @Nullable String mapName, boolean active) {
      super(poolName, mapName, active);
      try {
        submitQuery(new SelectQuery()).get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    }

    @Override
    public void update(@Nullable String nextMap, boolean active) {
      super.update(nextMap, active);
      submitQuery(new UpdateQuery());
    }

    private class SelectQuery implements Query {

      @Override
      public String getFormat() {
        return "SELECT * FROM pools WHERE name = ? LIMIT 1";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getPoolName());

        try (final ResultSet result = statement.executeQuery()) {
          if (result.next()) {
            update(result.getString(2), result.getBoolean(3));
          } else {
            update(null, false);
          }
        }
      }
    }

    private class UpdateQuery implements Query {

      @Override
      public String getFormat() {
        return "REPLACE INTO pools VALUES (?, ?, ?)";
      }

      @Override
      public void query(PreparedStatement statement) throws SQLException {
        statement.setString(1, getPoolName());
        statement.setString(2, getMapName());
        statement.setBoolean(3, isActive());

        statement.executeUpdate();
      }
    }
  }

  @Override
  public MapActivity getMapActivity(String name) {
    return new SQLMapActivity(name, null, false);
  }
  // -------------------------------------------------------------------------
}
