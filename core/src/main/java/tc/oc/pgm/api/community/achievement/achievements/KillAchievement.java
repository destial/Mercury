package tc.oc.pgm.api.community.achievement.achievements;

import tc.oc.pgm.api.community.achievement.Achievement;
import tc.oc.pgm.api.community.achievement.AchievementType;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.tracker.info.DamageInfo;

public class KillAchievement extends Achievement {
  @Override
  public void onKill(MatchPlayer player, DamageInfo tracker) {
    super.onKill(player, tracker);
    if (canAchieve(AchievementType.KILLS, player.getStats().getKills())) {
      // add rewards
    }
  }

  @Override
  public void onArrowHit(MatchPlayer player) {
    super.onArrowHit(player);
    player.getStats().arrowHit();
    if (canAchieve(AchievementType.ARROWS, player.getStats().getArrowsHit())) {
      // add rewards
    }
  }
}
