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

  default double getCombatScore() {
    float kdScore =
        (getKills() * PER_KILL_DEATH_SCORE)
            - (getDeaths() * PER_KILL_DEATH_SCORE * NEGATIVE_MODIFIER);
    float wlScore =
        (getWins() * PER_WIN_LOSS_SCORE) - (getLosses() * PER_WIN_LOSS_SCORE * NEGATIVE_MODIFIER);
    int obScore =
        (getWoolsCaptured() * PER_OBJECTIVE_SCORE)
            + (getMonumentsBroken() * PER_OBJECTIVE_SCORE)
            + (getFlagsCaptured() * PER_OBJECTIVE_SCORE)
            + (getCoresLeaked() * PER_OBJECTIVE_SCORE);
    int mvpScore = (getMVP() * PER_MVP_SCORE);

    return kdScore + wlScore + obScore + mvpScore + getLongestShot();
  }

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
    SCORE
  }

  float PER_KILL_DEATH_SCORE = 0.5f;
  int PER_WIN_LOSS_SCORE = 10;
  float NEGATIVE_MODIFIER = 2.5f;
  int PER_OBJECTIVE_SCORE = 5;
  int PER_MVP_SCORE = 2;
}
