package tc.oc.pgm.api.community.achievement;

import java.util.Arrays;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.tracker.info.DamageInfo;

public abstract class Achievement {
  public void onKill(MatchPlayer player, DamageInfo tracker) {}

  public void onDie(MatchPlayer player, DamageInfo tracker) {}

  public void onArrowHit(MatchPlayer player) {}

  public void onKillstreak(MatchPlayer player, int amount) {}

  public void onWin(MatchPlayer player) {}

  public void onLose(MatchPlayer player) {}

  public void onConsume(MatchPlayer player) {}

  public void onMonument(MatchPlayer player) {}

  public void onCore(MatchPlayer player) {}

  public void onFlag(MatchPlayer player) {}

  public void onWool(MatchPlayer player) {}

  public void onLeave(MatchPlayer player) {}

  public void onJoin(MatchPlayer player) {}

  protected boolean canAchieve(AchievementType type, int amount) {
    return Arrays.stream(type.getRanges()).anyMatch(i -> i == amount);
  }
}
