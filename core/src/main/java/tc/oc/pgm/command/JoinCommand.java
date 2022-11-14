package tc.oc.pgm.command;

import app.ashcon.intake.Command;
import app.ashcon.intake.bukkit.parametric.Type;
import app.ashcon.intake.bukkit.parametric.annotation.Fallback;
import app.ashcon.intake.parametric.annotation.Switch;
import java.time.Duration;
import javax.annotation.Nullable;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.json.JSONObject;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.coins.Coins;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.PlayerData;
import tc.oc.pgm.join.JoinMatchModule;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.util.nms.NMSHacks;

public final class JoinCommand {

  @Command(
      aliases = {"join", "play"},
      desc = "Join the match",
      usage = "[team] - defaults to random",
      flags = "f",
      perms = Permissions.JOIN)
  public void join(
      Match match, MatchPlayer player, @Switch('f') boolean force, @Nullable Party team) {
    if (team != null && !(team instanceof Competitor)) {
      leave(player, match);
      return;
    }

    final JoinMatchModule join = match.needModule(JoinMatchModule.class);
    if (force && player.getBukkit().hasPermission(Permissions.JOIN_FORCE)) {
      join.forceJoin(player, (Competitor) team);
    } else {
      join.join(player, (Competitor) team);
    }
  }

  @Command(
      aliases = {"leave", "obs"},
      desc = "Leave the match",
      perms = Permissions.LEAVE)
  public void leave(MatchPlayer player, Match match) {
    Coins coins = player.getCoins();
    if (Bukkit.getPluginManager().getPlugin("Events") == null
        && match.isRunning()
        && coins.getCoins() < 1500) {
      player.sendWarning(
          TextComponent.of(
              "Insufficient balance to leave this match! You need 1,500 coins to leave!"));
      return;
    }
    if (match.needModule(JoinMatchModule.class).leave(player)) {
      if (match.isRunning()) {
        coins.removeCoins(1500);
        player.sendMessage(
            TextComponent.of("You have paid 1,500 coins to leave!", TextColor.GREEN));
      }
    }
  }

  @Command(
      aliases = {"ping", "latency"},
      desc = "Pong!")
  public void ping(MatchPlayer player, @Fallback(Type.NULL) Player p) {
    Player bukkit = p == null ? player.getBukkit() : p;
    int ping = NMSHacks.getPing(bukkit);
    player.sendMessage(
        TextComponent.of(bukkit.getName() + "'s ping: ", TextColor.GREEN)
            .append(
                TextComponent.of(
                    "" + ping + "ms",
                    ping < 100 ? TextColor.GREEN : ping < 200 ? TextColor.GOLD : TextColor.RED)));
  }

  @Command(
      aliases = {"denyjoin", "blacklist"},
      desc = "Deny a player from joining a match",
      perms = Permissions.STAFF)
  public void denyjoin(Match match, MatchPlayer sender, Player player) {
    if (player == null) {
      sender.sendWarning(TextComponent.of("Player not found!"));
      return;
    }
    final JoinMatchModule join = match.needModule(JoinMatchModule.class);
    join.addDenied(player.getUniqueId());
    sender.sendMessage(
        TextComponent.of(
            player.getName() + " has been denied to join all matches!", TextColor.GREEN));
  }

  @Command(
      aliases = {"allowjoin"},
      desc = "Allow a player to join a match",
      perms = Permissions.STAFF)
  public void allowjoin(Match match, MatchPlayer sender, Player player) {
    if (player == null) {
      sender.sendWarning(TextComponent.of("Player not found!"));
      return;
    }
    final JoinMatchModule join = match.needModule(JoinMatchModule.class);
    if (!join.isDenied(player.getUniqueId())) {
      sender.sendWarning(TextComponent.of("Player is not denied to join!"));
      return;
    }
    join.removeDenied(player.getUniqueId());
    sender.sendMessage(
        TextComponent.of(
            player.getName() + " has been allowed to join all matches!", TextColor.GREEN));
  }

  @Command(
      aliases = {"allowjoinall"},
      desc = "Allows all denied players to join",
      perms = Permissions.STAFF)
  public void allowjoinall(Match match, MatchPlayer sender) {
    final JoinMatchModule join = match.needModule(JoinMatchModule.class);
    join.removeAllDenied();
    sender.sendMessage(TextComponent.of("Allowed all to join the match", TextColor.GREEN));
  }

  @Command(
      aliases = {"addteamselections"},
      desc = "Add amount of choices for teams",
      perms = Permissions.DEV)
  public void addteamselections(Match match, Audience sender, Player player, int amount) {
    if (player == null) {
      sender.sendWarning(TextComponent.of("Player not found!"));
      return;
    }
    if (amount <= 0) return;
    PlayerData data = PGM.get().getDatastore().getPlayerData(player.getUniqueId());
    JSONObject object = data.getData();
    int prev = object.has("team_selections") ? object.getInt("team_selections") : 0;
    data.setData("team_selections", prev + amount);
    sender.sendMessage(
        TextComponent.of("Added " + amount + " choices to " + player.getName(), TextColor.YELLOW));
  }

  @Command(
      aliases = {"daily"},
      desc = "Daily claim rewards")
  public void claimdaily(Match match, MatchPlayer player) {
    JSONObject object = player.getPlayerData().getData();
    boolean claim = false;
    long now = System.currentTimeMillis();
    if (object.has("last_daily_claim")) {
      long before = object.getLong("last_daily_claim");
      if (now - before >= 86400000) {
        claim = true;
      }
    } else {
      claim = true;
    }

    if (claim) {
      player.getPlayerData().setData("last_daily_claim", now);

      int amount = player.getBukkit().hasPermission(Permissions.GROUP + ".vip") ? 3 : 1;
      int prev = object.has("team_selections") ? object.getInt("team_selections") : 0;
      player.getPlayerData().setData("team_selections", prev + amount);

      player.sendMessage(TextComponent.of("You have claimed your daily reward!", TextColor.GOLD));
    } else {
      long before = object.getLong("last_daily_claim");
      Duration d = Duration.ofMillis(86400000 - (now - before));
      String format =
          String.format("%sh %smin %ssec", d.toHours(), d.toMinutes() % 60, d.getSeconds() % 60);
      player.sendWarning(TextComponent.of("Wait " + format + " before claiming your next daily!"));
    }
  }

  @Command(
      aliases = {"teampasses"},
      desc = "Get the amount of team passes of players",
      perms = Permissions.STAFF)
  public void teampasses(MatchPlayer player, @Fallback(Type.NULL) Player target) {
    MatchPlayer check = player;
    if (target != null) {
      check = player.getMatch().getPlayer(target);
      if (check == null) {
        player.sendWarning(TextComponent.of("Player not found!"));
        return;
      }
    }

    JSONObject object = check.getPlayerData().getData();
    int amount = object.has("team_selections") ? object.getInt("team_selections") : 0;

    player.sendMessage(
        TextComponent.of(check.getName() + " has ", TextColor.GREEN)
            .append(
                TextComponent.of("" + amount, TextColor.AQUA)
                    .append(TextComponent.of(" team passes", TextColor.GREEN))));
  }
}
