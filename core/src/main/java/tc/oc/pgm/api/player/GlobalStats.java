package tc.oc.pgm.api.player;

import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;
import tc.oc.pgm.api.PGM;

public interface GlobalStats {
  int getKills();

  int getDeaths();

  int getLongestShot();

  int getHighestKS();

  float getKDR();

  int getArrowsHit();

  int getMonumentsBroken();

  int getCoresLeaked();

  int getFlagsCaptured();

  int getWoolsCaptured();

  int getWins();

  int getLosses();

  int getMVP();

  int getElo();

  void setMonumentsBroken(int m);

  void setCoresLeaked(int m);

  void setFlagsCaptured(int m);

  void setWoolsCaptured(int m);

  void setWins(int m);

  void setLosses(int m);

  void setMVP(int m);

  void breakMonument();

  void leakCore();

  void captureFlag();

  void captureWool();

  void win();

  void lose();

  void setKills(int kills);

  void setDeaths(int deaths);

  void setLongestShot(int shot);

  void setHighestKS(int ks);

  void setStats(JSONObject jsonObject);

  void addKill();

  void addDeath();

  void arrowHit();

  void addElo(int elo);

  void mvp();

  UUID getId();

  static Map<UUID, GlobalStats> allStats() {
    return PGM.get().getDatastore().getAllStats();
  }

  enum Leaderboard {
    KILLS,
    DEATHS,
    ARROWS,
    WINS,
    LOSSES,
    KILLSTREAK,
    KDR,
    BOW,
    MONUMENTS,
    CORES,
    FLAGS,
    WOOLS,
    MVP,
    WLR,
    ELO,
  }
}
