package tc.oc.pgm.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.controlpoint.events.ControllerChangeEvent;
import tc.oc.pgm.core.Core;
import tc.oc.pgm.core.CoreLeakEvent;
import tc.oc.pgm.destroyable.Destroyable;
import tc.oc.pgm.destroyable.DestroyableDestroyedEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.flag.event.FlagCaptureEvent;
import tc.oc.pgm.goals.Contribution;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.wool.PlayerWoolPlaceEvent;

@ListenerScope(MatchScope.RUNNING)
public class CoinsMatchModule implements MatchModule, Listener {

  private final Match match;

  public CoinsMatchModule(Match match) {
    this.match = match;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerDeath(MatchPlayerDeathEvent event) {
    if (event.getMatch() != match) {
      return;
    }
    if (event.getKiller() == event.getVictim().getParticipantState() || event.getKiller() == null) {
      return;
    }
    Optional<MatchPlayer> player = event.getKiller().getPlayer();
    if (!player.isPresent()) {
      return;
    }
    MatchPlayer killer = player.get();
    if (killer == event.getVictim()) {
      return;
    }
    int random = (int) Math.floor(Math.random() * 10) + 1;
    TextComponent objective =
        TextComponent.builder()
            .append("killed ", TextColor.YELLOW)
            .append(event.getVictim().getName(NameStyle.COLOR))
            .build();
    addCoins(killer, random, objective);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDestroy(DestroyableDestroyedEvent event) {
    if (event.getMatch() != match) {
      return;
    }
    Destroyable destroyable = event.getDestroyable();
    List<? extends Contribution> sort = new ArrayList<>(destroyable.getContributions());
    for (Contribution entry : sort) {
      Optional<MatchPlayer> player = entry.getPlayerState().getPlayer();
      if (!player.isPresent()) {
        continue;
      }
      MatchPlayer destroyer = player.get();
      int random = (int) Math.floor(Math.random() * 10) + 20;
      TextComponent objective =
          TextComponent.builder()
              .append("destroyed ", TextColor.YELLOW)
              .append(
                  event
                      .getDestroyable()
                      .getComponentName()
                      .color(event.getDestroyable().getOwner().getName().color()))
              .build();
      addCoins(destroyer, random, objective);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlace(PlayerWoolPlaceEvent event) {
    if (event.getMatch() != match) {
      return;
    }
    Optional<MatchPlayer> player = event.getPlayer().getPlayer();
    if (!player.isPresent()) {
      return;
    }
    MatchPlayer placer = player.get();
    int random = (int) Math.floor(Math.random() * 10) + 20;
    TextComponent objective =
        TextComponent.builder()
            .append("placed ", TextColor.YELLOW)
            .append(event.getWool().getComponentName())
            .build();
    addCoins(placer, random, objective);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onLeak(CoreLeakEvent event) {
    if (event.getMatch() != match) {
      return;
    }
    Core core = event.getCore();
    List<? extends Contribution> sort = new ArrayList<>(core.getContributions());
    for (Contribution entry : sort) {
      Optional<MatchPlayer> player = entry.getPlayerState().getPlayer();
      if (!player.isPresent()) {
        return;
      }
      MatchPlayer leaker = player.get();
      int random = (int) Math.floor(Math.random() * 10) + 20;
      TextComponent objective =
          TextComponent.builder()
              .append("leaked ", TextColor.YELLOW)
              .append(
                  event
                      .getCore()
                      .getComponentName()
                      .color(event.getCore().getOwner().getChatPrefix().color()))
              .build();
      addCoins(leaker, random, objective);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCapturePoint(ControllerChangeEvent event) {
    if (event.getMatch() != match) {
      return;
    }
    Competitor competitor = event.getNewController();
    if (competitor == null) {
      return;
    }
    Collection<MatchPlayer> players = competitor.getPlayers();
    for (MatchPlayer player : players) {
      if (player == null) {
        continue;
      }
      int random = (int) Math.floor(Math.random() * 10) + 20;
      TextComponent objective =
          TextComponent.builder()
              .append("captured ", TextColor.YELLOW)
              .append(
                  event
                      .getControlPoint()
                      .getComponentName()
                      .color(event.getNewController().getChatPrefix().color()))
              .build();
      addCoins(player, random, objective);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onFlagCapture(FlagCaptureEvent event) {
    if (event.getMatch() != match) {
      return;
    }
    MatchPlayer capturer = event.getCarrier();
    if (capturer == null) {
      return;
    }
    int random = (int) Math.floor(Math.random() * 10) + 20;
    TextComponent objective =
        TextComponent.builder()
            .append("captured ", TextColor.WHITE)
            .append(event.getGoal().getComponentName().color(event.getGoal().getChatColor()))
            .build();
    addCoins(capturer, random, objective);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onWin(MatchFinishEvent event) {
    if (event.getMatch() != match) {
      return;
    }
    Collection<Competitor> winners = event.getWinners();
    for (Competitor winner : winners) {
      Collection<MatchPlayer> winnerPlayers = winner.getPlayers();
      for (MatchPlayer winnerPlayer : winnerPlayers) {
        if (winnerPlayer == null) {
          continue;
        }
        int random = (int) Math.floor(Math.random() * 10) + 20;
        TextComponent objective =
            TextComponent.builder().append("won the match", TextColor.YELLOW).build();
        addCoins(winnerPlayer, random, objective);
      }
    }
  }

  public void addCoins(MatchPlayer player, int amount, Component objective) {
    boolean d = false;
    if (player.getBukkit().hasPermission(Permissions.PREM)) {
      amount *= 2;
      d = true;
    }
    player.getCoins().addCoins(amount);
    TextComponent message =
        TextComponent.builder()
            .append(
                TranslatableComponent.of(
                    "death.getcoins",
                    TextColor.YELLOW,
                    TextComponent.of(amount + (d ? " x2 (Boost)" : ""), TextColor.GOLD),
                    objective))
            .build();
    player.showHotbar(message);
  }
}
