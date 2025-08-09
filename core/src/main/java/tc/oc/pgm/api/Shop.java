package tc.oc.pgm.api;

public interface Shop {
  int getBuyMapCost();

  int getBuyStartCost();

  int getBuySkipCost();

  int getKillMax();

  int getObjectiveMax();

  int getKillMin();

  int getObjectiveMin();

  float getPremiumMultiplier();
}
