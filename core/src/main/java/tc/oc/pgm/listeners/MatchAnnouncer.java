package tc.oc.pgm.listeners;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.io.FileUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.Contributor;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.events.PlayerJoinMatchEvent;
import tc.oc.pgm.match.Observers;
import tc.oc.pgm.teams.Team;
import tc.oc.pgm.util.LegacyFormatUtils;
import tc.oc.pgm.util.chat.Sound;
import tc.oc.pgm.util.named.MapNameStyle;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextFormatter;

public class MatchAnnouncer implements Listener {
  private static final Sound SOUND_MATCH_START = new Sound("note.pling", 1f, 1.59f);
  private static final Sound SOUND_MATCH_WIN = new Sound("mob.wither.death", 1f, 1f);
  private static final Sound SOUND_MATCH_LOSE = new Sound("mob.wither.spawn", 1f, 1f);

  private static MatchAnnouncer instance;
  private int index = 0;
  private int broadcastInterval;
  private boolean broadcastEnabled;
  private boolean broadcastRandom;
  private List<String> broadcasts;

  public static MatchAnnouncer getInstance() {
    return instance;
  }

  public MatchAnnouncer() {
    instance = this;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMatchLoad(final MatchLoadEvent event) {
    final Match match = event.getMatch();
    this.match = match;
    loadBroadcastFile();
    match
        .getExecutor(MatchScope.LOADED)
        .scheduleWithFixedDelay(
            () -> {
              if (ChatDispatcher.get().FROZEN) return;
              match.getPlayers().forEach(this::sendCurrentlyPlaying);
            },
            0,
            1,
            TimeUnit.MINUTES);

    startBroadcastTask(event);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMatchBegin(final MatchStartEvent event) {
    Match match = event.getMatch();
    match.sendMessage(TranslatableComponent.of("broadcast.matchStart", TextColor.GREEN));

    Component go = TranslatableComponent.of("broadcast.go", TextColor.GREEN);
    for (MatchPlayer player : match.getParticipants()) {
      player.showTitle(go, TextComponent.empty(), 0, 5, 15);
    }

    match.playSound(SOUND_MATCH_START);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onMatchEnd(final MatchFinishEvent event) {
    Match match = event.getMatch();

    // broadcast match finish message
    for (MatchPlayer viewer : match.getPlayers()) {
      Component title, subtitle = TextComponent.empty();
      if (event.getWinner() == null) {
        title = TranslatableComponent.of("broadcast.gameOver");
      } else {
        title =
            TranslatableComponent.of(
                event.getWinner().isNamePlural()
                    ? "broadcast.gameOver.teamWinners"
                    : "broadcast.gameOver.teamWinner",
                event.getWinner().getName());

        if (event.getWinner() == viewer.getParty()) {
          // Winner
          viewer.playSound(SOUND_MATCH_WIN);
          if (viewer.getParty() instanceof Team) {
            subtitle = TranslatableComponent.of("broadcast.gameOver.teamWon", TextColor.GREEN);
          }
        } else if (viewer.getParty() instanceof Competitor) {
          // Loser
          viewer.playSound(SOUND_MATCH_LOSE);
          if (viewer.getParty() instanceof Team) {
            subtitle = TranslatableComponent.of("broadcast.gameOver.teamLost", TextColor.RED);
          }
        } else {
          // Observer
          viewer.playSound(SOUND_MATCH_WIN);
        }
      }

      if (event.getWinner() == null || event.getWinners() == null || event.getWinners().isEmpty()) {
        viewer.showTitle(title, subtitle, 0, 40, 40);
        viewer.sendMessage(title);
      } else {
        viewer.showTitle(title, subtitle, 0, 40, 40);
        viewer.sendMessage(
            LegacyFormatUtils.horizontalDivider(event.getWinner().getColor().asBungee(), 100));
        viewer.sendMessage(TextComponent.builder().append("   ").append(title).build());
        viewer.sendMessage(
            LegacyFormatUtils.horizontalDivider(event.getWinner().getColor().asBungee(), 100));
        if (!(viewer.getParty() instanceof Observers)) viewer.sendMessage(subtitle);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void clearTitle(PlayerJoinMatchEvent event) {
    final Player player = event.getPlayer().getBukkit();

    player.hideTitle();

    // Bukkit assumes a player's locale is "en_US" before it receives a player's setting packet.
    // Thus, we delay sending this prominent message, so it is more likely its in the right locale.
    event
        .getPlayer()
        .getMatch()
        .getExecutor(MatchScope.LOADED)
        .schedule(() -> sendWelcomeMessage(event.getPlayer()), 500, TimeUnit.MILLISECONDS);
  }

  private void sendWelcomeMessage(MatchPlayer viewer) {
    MapInfo mapInfo = viewer.getMatch().getMap();

    String title = ChatColor.AQUA.toString() + ChatColor.BOLD + mapInfo.getName();
    viewer.sendMessage(
        TextFormatter.horizontalLineHeading(
            viewer.getBukkit(), TextComponent.of(title), TextColor.GRAY, 200));

    String objective = " " + ChatColor.BLUE + ChatColor.ITALIC + mapInfo.getDescription();
    LegacyFormatUtils.wordWrap(objective, 200).forEach(viewer::sendMessage);

    Collection<Contributor> authors = mapInfo.getAuthors();
    if (!authors.isEmpty()) {
      viewer.sendMessage(
          TextComponent.space()
              .append(
                  TranslatableComponent.of(
                      "misc.createdBy",
                      TextColor.GRAY,
                      TextFormatter.nameList(authors, NameStyle.FANCY, TextColor.GRAY))));
    }

    viewer.sendMessage(LegacyFormatUtils.horizontalLine(ChatColor.GRAY, 200));
  }

  private void sendCurrentlyPlaying(MatchPlayer viewer) {
    viewer.sendMessage(
        TranslatableComponent.of(
            "misc.playing",
            TextColor.RED,
            viewer.getMatch().getMap().getNameWithVersion(MapNameStyle.COLOR_WITH_AUTHORS)));
  }

  public void loadBroadcastFile() {
    Config config = PGM.get().getConfiguration();
    String broadcastFilePath = config.getBroadcastFile();
    if (broadcastFilePath == null || broadcastFilePath.isEmpty()) return;
    File broadcastFile = new File(broadcastFilePath);
    if (!broadcastFile.exists()) {
      try {
        FileUtils.copyInputStreamToFile(PGM.get().getResource("broadcasts.yml"), broadcastFile);
      } catch (IOException e) {
        return;
      }
    }
    FileConfiguration broadcastsFile =
        YamlConfiguration.loadConfiguration(new File(broadcastFilePath));
    broadcastInterval = broadcastsFile.getInt("interval", 30);
    broadcastEnabled = broadcastsFile.getBoolean("enabled", false);
    broadcastRandom = broadcastsFile.getBoolean("random", false);
    broadcasts =
        broadcastsFile.contains("broadcasts")
            ? broadcastsFile.getStringList("broadcasts")
            : new ArrayList<>();
    index = 0;
  }

  private ScheduledFuture<?> a;

  private void startBroadcastTask(MatchLoadEvent e) {
    this.match = e.getMatch();
    start(match);
  }

  private Match match;

  private void start(Match match) {
    if (a != null) {
      a.cancel(true);
    }
    a =
        match
            .getExecutor(MatchScope.LOADED)
            .scheduleWithFixedDelay(
                () -> {
                  if (ChatDispatcher.get().FROZEN) return;
                  match.getPlayers().forEach(this::sendBroadcast);
                },
                0,
                broadcastInterval,
                TimeUnit.SECONDS);
  }

  private void sendBroadcast(MatchPlayer player) {
    if (!broadcastEnabled || broadcasts.size() == 0) return;
    String rawBroadcast =
        ChatColor.translateAlternateColorCodes(
            '&',
            broadcastRandom
                ? broadcasts.get((int) Math.floor((Math.random() * broadcasts.size())))
                : broadcasts.get(index));
    ChatColor chatColor = ChatColor.RESET;
    StringBuilder broadcast = new StringBuilder();
    for (int i = 0; i < rawBroadcast.length(); ++i) {
      char c = rawBroadcast.charAt(i);
      if (c == ChatColor.COLOR_CHAR) {
        if (i + 1 >= rawBroadcast.length()) break;
        char color = rawBroadcast.charAt(i + 1);
        chatColor = ChatColor.getByChar(color);
      } else {
        if (i == 0) continue;
        char prev = rawBroadcast.charAt(i - 1);
        if (prev == ChatColor.COLOR_CHAR) continue;
        broadcast.append(chatColor.toString()).append(c);
      }
    }
    player.sendMessage(TextComponent.of(broadcast.toString()));
    if (++index >= broadcasts.size()) index = 0;
  }
}
