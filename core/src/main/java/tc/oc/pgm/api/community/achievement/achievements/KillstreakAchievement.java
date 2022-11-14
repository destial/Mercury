package tc.oc.pgm.api.community.achievement.achievements;

import tc.oc.pgm.api.community.achievement.Achievement;
import tc.oc.pgm.api.community.achievement.AchievementType;
import tc.oc.pgm.api.player.MatchPlayer;

public class KillstreakAchievement extends Achievement {

  @Override
  public void onKillstreak(MatchPlayer player, int amount) {
    if (canAchieve(AchievementType.KILLSTREAK, amount)) {
      // add rewards
    }
  }
}
