package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import app.ashcon.intake.parametric.annotation.Switch;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.map.MapLibrary;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.community.command.ReportCommand;
import tc.oc.pgm.listeners.ChatDispatcher;

public final class AdminCommand {
  private final SimpleDateFormat format = new SimpleDateFormat("E dd MMM yyyy HH:mm:ss z");

  @Command(
      aliases = {"mercuryreload"},
      desc = "Reload the config",
      perms = Permissions.RELOAD)
  public void pgm() {
    PGM.get().reloadConfig();
  }

  @Command(
      aliases = {"loadnewmaps", "findnewmaps", "newmaps"},
      desc = "Load new maps",
      flags = "f",
      perms = Permissions.RELOAD)
  public void loadNewMaps(MapLibrary library, @Switch('f') boolean force) {
    library.loadNewMaps(force);
  }

  @Command(
      aliases = {"eloreload"},
      desc = "Reload the elo config",
      perms = Permissions.RELOAD)
  public void elo() {
    PGM.get().getConfiguration().reloadEloConfig();
  }

  @Command(
      aliases = {"broadcast", "bc"},
      perms = Permissions.STAFF,
      desc = "Broadcast")
  public void broadcast(Match match, String[] args) {
    String date = format.format(new Date());
    Component broadcast =
        TextComponent.of("\u26a0 BROADCAST \u26a0 ", TextColor.RED)
            .decoration(TextDecoration.BOLD, true)
            .append(TextComponent.of(String.join(" ", args), TextColor.YELLOW))
            .hoverEvent(HoverEvent.showText(TextComponent.of(date, TextColor.YELLOW)));
    match.sendMessage(broadcast);
    match.playSound(ReportCommand.REPORT_NOTIFY_SOUND);
  }

  @Command(
      aliases = {"freezechat", "fc"},
      perms = Permissions.STAFF,
      desc = "Freeze or unfreeze chat")
  public void freezechat(Match match, MatchPlayer player) {
    ChatDispatcher.get().FROZEN = !ChatDispatcher.get().FROZEN;
    TextComponent.Builder builder = TextComponent.builder();
    if (ChatDispatcher.get().FROZEN) {
      builder.append(
          TextComponent.of(
              "\u26a0 Chat has been frozen by " + player.getNameLegacy() + " \u26a0",
              TextColor.RED));
    } else {
      builder.append(
          TextComponent.of("Chat has been unfrozen by " + player.getNameLegacy(), TextColor.GREEN));
    }
    String date = format.format(new Date());
    match
        .getPlayers()
        .forEach(
            p -> {
              TextComponent hoverText = TextComponent.of(date, TextColor.YELLOW);
              if (p.getBukkit() != null && p.getBukkit().hasPermission(Permissions.STAFF)) {
                hoverText
                    .append(TextComponent.newline())
                    .append(TextComponent.newline())
                    .append(TextComponent.of("Click to toggle chat state", TextColor.AQUA))
                    .clickEvent(ClickEvent.runCommand("/freezechat"));
              }
              p.sendMessage(builder.hoverEvent(HoverEvent.showText(hoverText)).build());
              p.playSound(ReportCommand.REPORT_NOTIFY_SOUND);
            });
  }

  @Command(
      aliases = {"clearchat", "cc"},
      perms = Permissions.STAFF,
      desc = "Clear chat")
  public void clearchat(Match match, MatchPlayer player) {
    for (int i = 0; i < 250; i++) {
      match.sendMessage(i % 2 == 0 ? TextComponent.empty() : TextComponent.of("  "));
    }
    String date = format.format(new Date());
    match.sendWarning(
        TextComponent.of("Chat has been cleared by " + player.getNameLegacy())
            .hoverEvent(HoverEvent.showText(TextComponent.of(date, TextColor.YELLOW))));
  }
}
