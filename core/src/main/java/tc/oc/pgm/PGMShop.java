package tc.oc.pgm;

import org.bukkit.configuration.file.FileConfiguration;
import tc.oc.pgm.api.Shop;

public class PGMShop implements Shop {
  private int buyMap = 10000;
  private int buyStart = 5000;
  private int buySkip = 5000;
  private int killMax = 10;
  private int killMin = 1;
  private int objectiveMax = 50;
  private int objectiveMin = 20;
  private float premiumMultiplier = 2;

  public PGMShop(FileConfiguration config) {
    buyMap = config.getInt("buy-map", buyMap);
    buyStart = config.getInt("buy-start", buyStart);
    buySkip = config.getInt("buy-skip", buySkip);
    killMax = config.getInt("kill-coins-max", killMax);
    objectiveMax = config.getInt("objective-coins-max", objectiveMax);
    killMin = config.getInt("kill-coins-min", killMin);
    objectiveMin = config.getInt("objective-coins-min", objectiveMin);
    premiumMultiplier = config.getFloat("premium-multiplier", premiumMultiplier);
  }

  @Override
  public int getBuyMapCost() {
    return buyMap;
  }

  @Override
  public int getBuyStartCost() {
    return buyStart;
  }

  @Override
  public int getBuySkipCost() {
    return buySkip;
  }

  @Override
  public int getKillMax() {
    return killMax;
  }

  @Override
  public int getObjectiveMax() {
    return objectiveMax;
  }

  @Override
  public int getKillMin() {
    return killMin;
  }

  @Override
  public int getObjectiveMin() {
    return objectiveMin;
  }

  @Override
  public float getPremiumMultiplier() {
    return premiumMultiplier;
  }
}
