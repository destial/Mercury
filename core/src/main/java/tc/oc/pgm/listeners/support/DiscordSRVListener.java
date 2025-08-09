package tc.oc.pgm.listeners.support;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.ListenerPriority;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.DiscordGuildMessagePreProcessEvent;
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder;
import github.scarsz.discordsrv.dependencies.jda.api.entities.TextChannel;
import java.awt.Color;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.kyori.text.Component;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.event.KillstreakEvent;
import tc.oc.pgm.api.map.MapTag;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerDeathBroadcastEvent;
import tc.oc.pgm.core.CoreLeakEvent;
import tc.oc.pgm.destroyable.DestroyableDestroyedEvent;
import tc.oc.pgm.events.CycleStartEvent;
import tc.oc.pgm.events.VoteCompleteEvent;
import tc.oc.pgm.flag.event.FlagCaptureEvent;
import tc.oc.pgm.restart.CancelRestartEvent;
import tc.oc.pgm.restart.RequestRestartEvent;
import tc.oc.pgm.util.TimeUtils;
import tc.oc.pgm.util.text.TextTranslations;
import tc.oc.pgm.wool.PlayerWoolPlaceEvent;

public class DiscordSRVListener implements Listener {
  private TextChannel channel;

  public DiscordSRVListener() {
    PGM.get().getLogger().info("Found DiscordSRV! Hooking into it!");
    getChannel();
    DiscordSRV.api.subscribe(this);
  }

  private String convert(Component message) {
    return clean(
        TextComponent.toLegacyText(
            ComponentSerializer.parse(GsonComponentSerializer.INSTANCE.serialize(message))));
  }

  private String clean(String message) {
    return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', message));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMatchDeath(MatchPlayerDeathBroadcastEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setAuthor(convert(e.getMessage()));
    if (e.getParent().getVictim().getBukkit() != null) {
      String url = DiscordSRV.getAvatarUrl(e.getParent().getVictim().getBukkit());
      builder.setAuthor(convert(e.getMessage()), null, url.substring(0, url.indexOf("#")));
    }
    builder.setColor(e.getParent().getVictim().getParty().getFullColor().asRGB());
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMatchStart(MatchStartEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(e.getMatch().getMap().getName() + " has started!");
    builder.setColor(Color.GREEN);
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMatchEnd(MatchFinishEvent e) {
    if (getChannel() == null) return;
    Component title;
    if (e.getWinner() == null) {
      title = TranslatableComponent.of("broadcast.gameOver");
    } else {
      title =
          TranslatableComponent.of(
              e.getWinner().isNamePlural()
                  ? "broadcast.gameOver.teamWinners"
                  : "broadcast.gameOver.teamWinner",
              e.getWinner().getName());
    }
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(convert(TextTranslations.translate(title, Locale.ENGLISH)));
    builder.setColor(
        e.getWinner() != null ? e.getWinner().getFullColor().asRGB() : Color.DARK_GRAY.getRGB());
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMonumentBreak(DestroyableDestroyedEvent e) {
    if (getChannel() == null) return;
    if (!e.getDestroyable().isVisible()) return;
    if (!e.getDestroyable().isCompleted()) return;
    String message =
        e.getDestroyable().getOwner().getNameLegacy()
            + "'s "
            + e.getDestroyable().getName()
            + " has been destroyed!";
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(clean(message));
    builder.setColor(e.getDestroyable().getOwner().getFullColor().asRGB());
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWoolPlace(PlayerWoolPlaceEvent e) {
    if (getChannel() == null) return;
    MatchPlayer placer = e.getPlayer().getPlayer().orElse(null);
    EmbedBuilder builder = new EmbedBuilder();
    String message =
        placer == null
            ? e.getWool().getName() + " has been placed!"
            : placer.getNameLegacy() + " placed " + e.getWool().getName() + "!";
    builder.setTitle(clean(message));
    builder.setColor(e.getWool().getColor().asRGB());
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onCoreLeak(CoreLeakEvent e) {
    if (getChannel() == null) return;
    String message =
        e.getCore().getOwner().getNameLegacy()
            + "'s "
            + e.getCore().getName()
            + " has been leaked!";
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(clean(message));
    builder.setColor(e.getCore().getOwner().getFullColor().asRGB());
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onFlagCapture(FlagCaptureEvent e) {
    if (getChannel() == null) return;
    String message =
        e.getCarrier().getNameLegacy()
            + " captured "
            + e.getCarrier().getParty().getNameLegacy()
            + "'s "
            + e.getGoal().getName()
            + " flag!";
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(clean(message));
    builder.setColor(e.getCarrier().getParty().getFullColor().asRGB());
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onKillStreak(KillstreakEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(
        clean(e.getPlayer().getNameLegacy() + " is on a " + e.getAmount() + " killstreak!"));
    builder.setColor(e.getPlayer().getParty().getFullColor().asRGB());
    channel.sendMessage(builder.build()).queue();
  }

  @Subscribe(priority = ListenerPriority.MONITOR)
  public void onDiscordCommand(DiscordGuildMessagePreProcessEvent e) {
    if (getChannel() == null) return;
    if (!e.getChannel().getId().equals(channel.getId())) return;
    if (Stream.of("mapinfo", "matchinfo")
        .anyMatch(s -> s.equalsIgnoreCase(e.getMessage().getContentRaw()))) {
      e.setCancelled(true);
      MatchManager matchManager = PGM.get().getMatchManager();
      EmbedBuilder builder = new EmbedBuilder();
      matchManager
          .getMatches()
          .forEachRemaining(
              (match -> {
                StringBuilder scores = new StringBuilder();
                for (Party party : match.getParties()) {
                  if (!party.isParticipating()) continue;
                  scores
                      .append(party.getNameLegacy())
                      .append(" ")
                      .append(party.getPlayers().size())
                      .append("/")
                      .append(match.getMaxPlayers() / 2)
                      .append("\n");
                }
                builder.addField(
                    "Match #" + match.getId(),
                    "**"
                        + match.getMap().getName()
                        + "** ("
                        + match.getMap().getTags().stream()
                            .map(MapTag::getName)
                            .collect(Collectors.joining(" "))
                        + ")\nTime: "
                        + TimeUtils.formatDuration(match.getDuration())
                        + "\n"
                        + clean(scores.toString()),
                    false);
              }));
      builder.setColor(Color.CYAN);
      channel
          .sendMessage(builder.build())
          .queue(
              message -> {
                message.delete().queueAfter(10, TimeUnit.SECONDS);
                e.getMessage().delete().queueAfter(10, TimeUnit.SECONDS);
              });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onVotedMap(VoteCompleteEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(e.getVotedMap().getName() + " was chosen with " + e.getVotes() + " votes");
    builder.setColor(Color.GREEN);
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onCycle(CycleStartEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle("Cycling in " + e.getDuration().getSeconds() + " seconds");
    builder.setColor(Color.CYAN);
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onMatchLoad(MatchLoadEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle(
        "Loaded map " + e.getMatch().getMap().getName() + " for match #" + e.getMatch().getId());
    builder.setColor(Color.GREEN);
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRequestRestart(RequestRestartEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle("Server restarting!");
    builder.setColor(Color.CYAN);
    channel.sendMessage(builder.build()).queue();
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onCancelRestart(CancelRestartEvent e) {
    if (getChannel() == null) return;
    EmbedBuilder builder = new EmbedBuilder();
    builder.setTitle("Cancelled server restart!");
    builder.setColor(Color.CYAN);
    channel.sendMessage(builder.build()).queue();
  }

  private TextChannel getChannel() {
    channel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName("global");
    return channel;
  }
}
