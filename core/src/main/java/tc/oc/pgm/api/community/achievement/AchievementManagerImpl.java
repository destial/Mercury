package tc.oc.pgm.api.community.achievement;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.community.achievement.achievements.KillAchievement;
import tc.oc.pgm.api.community.achievement.achievements.KillstreakAchievement;
import tc.oc.pgm.api.event.KillstreakEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.core.CoreLeakEvent;
import tc.oc.pgm.destroyable.DestroyableDestroyedEvent;
import tc.oc.pgm.events.PlayerJoinMatchEvent;
import tc.oc.pgm.events.PlayerLeaveMatchEvent;
import tc.oc.pgm.flag.event.FlagCaptureEvent;
import tc.oc.pgm.goals.Contribution;
import tc.oc.pgm.wool.PlayerWoolPlaceEvent;

public class AchievementManagerImpl implements AchievementManager {
  private final List<Achievement> achievementList;

  public AchievementManagerImpl() {
    this.achievementList = new ArrayList<>();
    achievementList.add(new KillAchievement());
    achievementList.add(new KillstreakAchievement());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onKill(MatchPlayerDeathEvent e) {
    if (e.getKiller() != null) {
      MatchPlayer killer = e.getKiller().getPlayer().orElse(null);
      if (killer != null) {
        for (Achievement achievement : achievementList) {
          achievement.onKill(killer, e.getDamageInfo());
        }
      }
    }
    if (e.getVictim() != null) {
      for (Achievement achievement : achievementList) {
        achievement.onDie(e.getVictim(), e.getDamageInfo());
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void entityDamage(EntityDamageByEntityEvent e) {
    if (e.getEntity() instanceof Player) {
      if (e.getDamager() instanceof Arrow) {
        Arrow arrow = (Arrow) e.getDamager();
        if (arrow.getShooter() instanceof Player) {
          Player player = (Player) arrow.getShooter();
          MatchPlayer damager = PGM.get().getMatchManager().getPlayer(player);
          if (damager != null) {
            for (Achievement achievement : achievementList) {
              achievement.onArrowHit(damager);
            }
          }
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onWin(MatchFinishEvent event) {
    Match match = event.getMatch();
    for (MatchPlayer viewer : match.getPlayers()) {
      if (event.getWinner() != null) {
        if (event.getWinner() == viewer.getParty()) {
          for (Achievement achievement : achievementList) {
            achievement.onWin(viewer);
          }
        } else if (viewer.getParty() instanceof Competitor) {
          for (Achievement achievement : achievementList) {
            achievement.onLose(viewer);
          }
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onKillstreak(KillstreakEvent e) {
    for (Achievement achievement : achievementList) {
      achievement.onKillstreak(e.getPlayer(), e.getAmount());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onJoin(PlayerJoinMatchEvent e) {
    for (Achievement achievement : achievementList) {
      achievement.onJoin(e.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onLeave(PlayerLeaveMatchEvent e) {
    for (Achievement achievement : achievementList) {
      achievement.onLeave(e.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onMonument(DestroyableDestroyedEvent e) {
    for (Contribution c : e.getDestroyable().getContributions()) {
      MatchPlayer player = c.getPlayerState().getPlayer().orElse(null);
      if (player != null) {
        for (Achievement achievement : achievementList) {
          achievement.onMonument(player);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onCore(CoreLeakEvent e) {
    for (Contribution c : e.getCore().getContributions()) {
      MatchPlayer player = c.getPlayerState().getPlayer().orElse(null);
      if (player != null) {
        for (Achievement achievement : achievementList) {
          achievement.onCore(player);
        }
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onFlag(FlagCaptureEvent e) {
    for (Achievement achievement : achievementList) {
      achievement.onFlag(e.getCarrier());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onWool(PlayerWoolPlaceEvent e) {
    MatchPlayer player = e.getPlayer().getPlayer().orElse(null);
    if (player != null) {
      for (Achievement achievement : achievementList) {
        achievement.onWool(player);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onConsume(PlayerItemConsumeEvent e) {
    MatchPlayer player = PGM.get().getMatchManager().getPlayer(e.getPlayer());
    if (player != null) {
      for (Achievement achievement : achievementList) {
        achievement.onConsume(player);
      }
    }
  }
}
