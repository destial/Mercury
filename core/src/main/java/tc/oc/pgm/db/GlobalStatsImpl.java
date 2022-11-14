package tc.oc.pgm.db;

import java.util.UUID;
import org.json.JSONObject;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.player.GlobalStats;

public class GlobalStatsImpl implements GlobalStats {
  private int kills;
  private int arrows;
  private int deaths;
  private int longestShot;
  private int highestKs;
  private int monumentsBroken;
  private int woolCaptured;
  private int coreLeaked;
  private int flagsCaptured;
  private int wins;
  private int losses;
  private int mvp;
  private int elo;
  private final UUID uuid;

  public GlobalStatsImpl(UUID uuid) {
    this.uuid = uuid;
    elo = PGM.get().getConfiguration().getEloConfig().getDefaultElo();
  }

  @Override
  public int getKills() {
    return kills;
  }

  @Override
  public int getDeaths() {
    return deaths;
  }

  @Override
  public int getLongestShot() {
    return longestShot;
  }

  @Override
  public int getHighestKS() {
    return highestKs;
  }

  @Override
  public float getKDR() {
    int deaths = getDeaths();
    if (deaths == 0) deaths = 1;
    return getKills() / (float) deaths;
  }

  @Override
  public int getArrowsHit() {
    return arrows;
  }

  @Override
  public int getMonumentsBroken() {
    return monumentsBroken;
  }

  @Override
  public int getCoresLeaked() {
    return coreLeaked;
  }

  @Override
  public int getFlagsCaptured() {
    return flagsCaptured;
  }

  @Override
  public int getWoolsCaptured() {
    return woolCaptured;
  }

  @Override
  public int getWins() {
    return wins;
  }

  @Override
  public int getLosses() {
    return losses;
  }

  @Override
  public int getMVP() {
    return mvp;
  }

  @Override
  public int getElo() {
    return elo;
  }

  @Override
  public void setMonumentsBroken(int m) {
    monumentsBroken = m;
  }

  @Override
  public void setCoresLeaked(int m) {
    coreLeaked = m;
  }

  @Override
  public void setFlagsCaptured(int m) {
    flagsCaptured = m;
  }

  @Override
  public void setWoolsCaptured(int m) {
    woolCaptured = m;
  }

  @Override
  public void setWins(int m) {
    wins = m;
  }

  @Override
  public void setLosses(int m) {
    losses = m;
  }

  @Override
  public void setMVP(int m) {
    mvp = m;
  }

  @Override
  public void breakMonument() {
    monumentsBroken++;
  }

  @Override
  public void leakCore() {
    coreLeaked++;
  }

  @Override
  public void captureFlag() {
    flagsCaptured++;
  }

  @Override
  public void captureWool() {
    woolCaptured++;
  }

  @Override
  public void win() {
    wins++;
  }

  @Override
  public void lose() {
    losses++;
  }

  @Override
  public void setKills(int kills) {
    if (kills > this.kills) this.kills = kills;
  }

  @Override
  public void setDeaths(int deaths) {
    if (deaths > this.deaths) this.deaths = deaths;
  }

  @Override
  public void setLongestShot(int shot) {
    if (shot > longestShot) this.longestShot = shot;
  }

  @Override
  public void setHighestKS(int ks) {
    if (ks > highestKs) this.highestKs = ks;
  }

  @Override
  public void setStats(JSONObject object) {
    setDeaths(object.getInt("deaths"));
    setKills(object.getInt("kills"));
    setHighestKS(object.getInt("ks"));
    setLongestShot(object.getInt("shot"));
    setWins(object.getInt("wins"));
    setLosses(object.getInt("losses"));
    setMonumentsBroken(object.getInt("monuments"));
    setCoresLeaked(object.getInt("cores"));
    setWoolsCaptured(object.getInt("wools"));
    setFlagsCaptured(object.getInt("flags"));
    setMVP(object.has("mvp") ? object.getInt("mvp") : 0);
    elo = object.has("elo") ? object.getInt("elo") : elo;
    arrows = object.has("arrows") ? object.getInt("arrows") : 0;
  }

  @Override
  public void addKill() {
    ++kills;
  }

  @Override
  public void addDeath() {
    ++deaths;
  }

  @Override
  public void arrowHit() {
    ++arrows;
  }

  @Override
  public void addElo(int elo) {
    this.elo += elo;
    if (this.elo < 10) {
      this.elo = 10;
    }
  }

  @Override
  public void mvp() {
    ++mvp;
  }

  @Override
  public UUID getId() {
    return uuid;
  }

  @Override
  public String toString() {
    JSONObject object = new JSONObject();
    object.put("kills", getKills());
    object.put("deaths", getDeaths());
    object.put("shot", getLongestShot());
    object.put("ks", getHighestKS());
    object.put("mvp", getMVP());
    object.put("wins", getWins());
    object.put("losses", getLosses());
    object.put("monuments", getMonumentsBroken());
    object.put("cores", getCoresLeaked());
    object.put("wools", getWoolsCaptured());
    object.put("flags", getFlagsCaptured());
    object.put("elo", getElo());
    object.put("arrows", getArrowsHit());
    return object.toString();
  }
}
