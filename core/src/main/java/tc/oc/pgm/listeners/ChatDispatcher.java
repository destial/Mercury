package tc.oc.pgm.listeners;

import app.ashcon.intake.Command;
import app.ashcon.intake.parametric.annotation.Text;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.community.vanish.VanishManager;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.PlayerData;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.community.command.ReportCommand;
import tc.oc.pgm.community.events.PlayerReportEvent;
import tc.oc.pgm.ffa.Tribute;
import tc.oc.pgm.namedecorations.NameDecorationRegistry;
import tc.oc.pgm.util.StringUtils;
import tc.oc.pgm.util.UsernameFormatUtils;
import tc.oc.pgm.util.bukkit.BukkitUtils;
import tc.oc.pgm.util.bukkit.OnlinePlayerMapAdapter;
import tc.oc.pgm.util.chat.Audience;
import tc.oc.pgm.util.chat.Sound;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.TextTranslations;

public class ChatDispatcher implements Listener {

  private static final ChatDispatcher INSTANCE = new ChatDispatcher();

  public static ChatDispatcher get() {
    return INSTANCE; // FIXME: no one should need to statically access ChatDispatcher, but community
    // does this a lot
  }

  private final MatchManager manager;
  private final VanishManager vanish;
  private final OnlinePlayerMapAdapter<UUID> lastMessagedBy;
  private final NameDecorationRegistry names;
  public boolean FROZEN = false;

  private final Map<UUID, String> muted;

  public static final TextComponent ADMIN_CHAT_PREFIX =
      TextComponent.builder()
          .append("[", TextColor.GOLD)
          .append("A", TextColor.RED)
          .append("] ", TextColor.GOLD)
          .build();

  private static final Sound DM_SOUND = new Sound("random.orb", 1f, 1.2f);
  private static final Sound AC_SOUND = new Sound("random.orb", 1f, 0.7f);

  private static final String GLOBAL_SYMBOL = "!";
  private static final String DM_SYMBOL = "$";
  private static final String ADMIN_CHAT_SYMBOL = "@";

  private static final String GLOBAL_FORMAT = "<%s>: %s";
  private static final String PREFIX_FORMAT = "%s: %s";
  private static final String AC_FORMAT =
      TextTranslations.translateLegacy(ADMIN_CHAT_PREFIX, null) + PREFIX_FORMAT;

  private static final Predicate<MatchPlayer> AC_FILTER =
      viewer -> viewer.getBukkit().hasPermission(Permissions.ADMINCHAT);

  public ChatDispatcher() {
    this.manager = PGM.get().getMatchManager();
    this.vanish = PGM.get().getVanishManager();
    this.lastMessagedBy = new OnlinePlayerMapAdapter<>(PGM.get());
    this.names = PGM.get().getNameDecorationRegistry();
    this.muted = Maps.newHashMap();
    PGM.get().getServer().getPluginManager().registerEvents(this, PGM.get());
  }

  public void addMuted(MatchPlayer player, String reason) {
    this.muted.put(player.getId(), reason);
  }

  public void removeMuted(MatchPlayer player) {
    this.muted.remove(player.getId());
  }

  public boolean isMuted(MatchPlayer player) {
    return player != null ? muted.containsKey(player.getId()) : false;
  }

  public Set<UUID> getMutedUUIDs() {
    return muted.keySet();
  }

  @Command(
      aliases = {"g", "all"},
      desc = "Send a message to everyone",
      usage = "[message]")
  public void sendGlobal(Match match, MatchPlayer sender, @Nullable @Text String message) {
    if (sender != null && sender.isVanished()) {
      sendAdmin(match, sender, message);
      return;
    }

    if (checkMute(sender)) {
      send(
          match,
          sender,
          message,
          GLOBAL_FORMAT,
          getChatFormat(null, sender, message, SettingValue.CHAT_GLOBAL),
          viewer -> true,
          SettingValue.CHAT_GLOBAL);
    }
  }

  @Command(
      aliases = {"t"},
      desc = "Send a message to your team",
      usage = "[message]")
  public void sendTeam(Match match, MatchPlayer sender, @Nullable @Text String message) {
    if (sender != null && sender.isVanished()) {
      sendAdmin(match, sender, message);
      return;
    }

    final Party party = sender == null ? match.getDefaultParty() : sender.getParty();

    // No team chat when playing free-for-all or match end, default to global chat
    if (party instanceof Tribute || match.isFinished()) {
      sendGlobal(match, sender, message);
      return;
    }

    if (checkMute(sender)) {
      send(
          match,
          sender,
          message,
          TextTranslations.translateLegacy(party.getChatPrefix(), null) + PREFIX_FORMAT,
          getChatFormat(party.getChatPrefix(), sender, message, SettingValue.CHAT_TEAM),
          viewer ->
              party.equals(viewer.getParty())
                  || (viewer.isObserving()
                      && viewer.getBukkit().hasPermission(Permissions.ADMINCHAT)),
          SettingValue.CHAT_TEAM);
    }
  }

  @Command(
      aliases = {"a"},
      desc = "Send a message to operators",
      usage = "[message]",
      perms = Permissions.ADMINCHAT)
  public void sendAdmin(Match match, MatchPlayer sender, @Nullable @Text String message) {
    // If a player managed to send a default message without permissions, reset their chat channel
    if (sender != null && !sender.getBukkit().hasPermission(Permissions.ADMINCHAT)) {
      sender.getSettings().resetValue(SettingKey.CHAT);
      SettingKey.CHAT.update(sender);
      sender.sendWarning(TranslatableComponent.of("misc.noPermission"));
      return;
    }

    send(
        match,
        sender,
        message != null ? BukkitUtils.colorize(message) : null,
        AC_FORMAT,
        getChatFormat(ADMIN_CHAT_PREFIX, sender, message, SettingValue.CHAT_ADMIN),
        AC_FILTER,
        SettingValue.CHAT_ADMIN);

    // Play sounds for admin chat
    if (message != null) {
      match.getPlayers().stream()
          .filter(AC_FILTER) // Initial filter
          .filter(viewer -> !viewer.equals(sender)) // Don't play sound for sender
          .forEach(pl -> playSound(pl, AC_SOUND));
    }
  }

  @Command(
      aliases = {"msg", "tell", "pm", "dm"},
      desc = "Send a direct message to a player",
      usage = "[player] [message]")
  public void sendDirect(Match match, MatchPlayer sender, Player receiver, @Text String message) {
    if (sender == null) return;

    if (vanish.isVanished(sender.getId())) {
      sender.sendWarning(TranslatableComponent.of("vanish.chat.deny"));
      return;
    }

    if (isMuted(sender) && !receiver.hasPermission(Permissions.STAFF)) {
      sendMutedMessage(sender);
      return; // Muted players may only message staff
    }

    MatchPlayer matchReceiver = manager.getPlayer(receiver);
    if (matchReceiver != null) {

      // Vanish Check - Don't allow messages to vanished
      if (vanish.isVanished(matchReceiver.getId())) {
        sender.sendWarning(TranslatableComponent.of("command.playerNotFound"));
        return;
      }

      SettingValue option = matchReceiver.getSettings().getValue(SettingKey.MESSAGE);

      if (option.equals(SettingValue.MESSAGE_OFF)
          && !sender.getBukkit().hasPermission(Permissions.STAFF)) {
        Component blocked =
            TranslatableComponent.of(
                "command.message.blocked", matchReceiver.getName(NameStyle.FANCY));
        sender.sendWarning(blocked);
        return;
      }

      if (isMuted(matchReceiver) && !sender.getBukkit().hasPermission(Permissions.STAFF)) {
        Component muted =
            TranslatableComponent.of(
                "moderation.mute.target", matchReceiver.getName(NameStyle.CONCISE));
        sender.sendWarning(muted);
        return; // Only staff can message muted players
      } else {
        playSound(matchReceiver, DM_SOUND);
      }
    }

    lastMessagedBy.put(receiver, sender.getId());

    // Send message to receiver
    send(
        match,
        sender,
        message,
        formatPrivateMessage("misc.from", matchReceiver.getBukkit()),
        getChatFormat(
            TextComponent.builder()
                .append(
                    TranslatableComponent.of("misc.from", TextColor.GRAY, TextDecoration.ITALIC))
                .append(" ")
                .build(),
            sender,
            message,
            SettingValue.CHAT_GLOBAL),
        viewer -> viewer.getBukkit().equals(receiver),
        null);

    // Send message to the sender
    send(
        match,
        manager.getPlayer(receiver), // Allow for cross-match messages
        message,
        formatPrivateMessage("misc.to", sender.getBukkit()),
        getChatFormat(
            TextComponent.builder()
                .append(TranslatableComponent.of("misc.to", TextColor.GRAY, TextDecoration.ITALIC))
                .append(" ")
                .build(),
            manager.getPlayer(receiver),
            message,
            SettingValue.CHAT_ADMIN),
        viewer -> viewer.getBukkit().equals(sender.getBukkit()),
        null);
  }

  private String formatPrivateMessage(String key, CommandSender viewer) {
    Component action =
        TranslatableComponent.of(key, TextColor.GRAY).decoration(TextDecoration.ITALIC, true);
    return TextTranslations.translateLegacy(action, viewer) + " " + PREFIX_FORMAT;
  }

  @Command(
      aliases = {"reply", "r"},
      desc = "Reply to a direct message",
      usage = "[message]")
  public void sendReply(Match match, Audience audience, MatchPlayer sender, @Text String message) {
    if (sender == null) return;
    final MatchPlayer receiver = manager.getPlayer(lastMessagedBy.get(sender.getBukkit()));
    if (receiver == null) {
      audience.sendWarning(
          TranslatableComponent.of("command.message.noReply", TextComponent.of("/msg")));
      return;
    }

    sendDirect(match, sender, receiver.getBukkit(), message);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void onChat(AsyncPlayerChatEvent event) {
    if (CHAT_EVENT_CACHE.getIfPresent(event) == null) {
      event.setCancelled(true);
    } else {
      CHAT_EVENT_CACHE.invalidate(event);
      return;
    }

    final MatchPlayer player = manager.getPlayer(event.getPlayer());
    if (player != null) {
      if (FROZEN && !player.getBukkit().hasPermission(Permissions.STAFF)) {
        player.sendWarning(TextComponent.of("Chat has been frozen!"));
        return;
      }
      final String message = event.getMessage();

      if (message.startsWith(GLOBAL_SYMBOL)) {
        sendGlobal(player.getMatch(), player, message.substring(1));
      } else if (message.startsWith(DM_SYMBOL)) {
        final String target = message.substring(1, message.indexOf(" "));
        final MatchPlayer receiver =
            getApproximatePlayer(player.getMatch(), target, player.getBukkit());
        if (receiver == null) {
          player.sendWarning(
              TranslatableComponent.of("chat.message.unknownTarget", TextComponent.of(target)));
        } else {
          sendDirect(
              player.getMatch(),
              player,
              receiver.getBukkit(),
              message.replace(target, "").substring(1));
        }
      } else if (message.startsWith(ADMIN_CHAT_SYMBOL)
          && player.getBukkit().hasPermission(Permissions.ADMINCHAT)) {
        sendAdmin(player.getMatch(), player, event.getMessage().substring(1));
      } else {
        sendDefault(player.getMatch(), player, event.getMessage());
      }
    }
  }

  public void sendDefault(Match match, MatchPlayer sender, String message) {
    switch (sender == null
        ? SettingValue.CHAT_GLOBAL
        : sender.getSettings().getValue(SettingKey.CHAT)) {
      case CHAT_TEAM:
        sendTeam(match, sender, message);
        return;
      case CHAT_ADMIN:
        sendAdmin(match, sender, message);
        return;
      default:
        sendGlobal(match, sender, message);
    }
  }

  public void playSound(MatchPlayer player, Sound sound) {
    SettingValue value = player.getSettings().getValue(SettingKey.SOUNDS);
    if ((sound.equals(AC_SOUND) && value.equals(SettingValue.SOUNDS_ALL))
        || (sound.equals(DM_SOUND) && !value.equals(SettingValue.SOUNDS_NONE))) {
      player.playSound(sound);
    }
  }

  private static final Cache<AsyncPlayerChatEvent, Boolean> CHAT_EVENT_CACHE =
      CacheBuilder.newBuilder().weakKeys().expireAfterWrite(15, TimeUnit.SECONDS).build();

  public String lastMessage;
  public String lastMessageName;

  public void send(
      Match match,
      MatchPlayer sender,
      @Nullable String text,
      String format,
      Component componentMsg,
      Predicate<MatchPlayer> filter,
      @Nullable SettingValue type) {
    // When a message is empty, this indicates the player wants to change their default chat channel
    if (text == null && sender != null) {
      // FIXME: there should be a better way to do this
      sender.getBukkit().performCommand("set " + SettingKey.CHAT + " " + type.getName());
      return;
    }

    final String message = text.trim();

    if (sender != null) {
      PGM.get()
          .getAsyncExecutor()
          .execute(
              () -> {
                final AsyncPlayerChatEvent event =
                    new AsyncPlayerChatEvent(
                        false,
                        sender.getBukkit(),
                        message,
                        match.getPlayers().stream()
                            .filter(filter)
                            .map(MatchPlayer::getBukkit)
                            .collect(Collectors.toSet()));
                event.setFormat(format);
                CHAT_EVENT_CACHE.put(event, true);
                match.callEvent(event);

                if (event.isCancelled()) {
                  return;
                }

                if (type != SettingValue.CHAT_GLOBAL) {
                  event.setCancelled(true);
                }

                event.getRecipients().stream()
                    .map(Audience::get)
                    .forEach(player -> player.sendMessage(componentMsg));
              });
      if (type == SettingValue.CHAT_GLOBAL)
        Audience.get(Bukkit.getConsoleSender()).sendMessage(componentMsg);
      if (type == SettingValue.CHAT_ADMIN) {
        lastStaffMessage = System.currentTimeMillis();
        lastMessage = message;
        lastMessageName = sender.getNameLegacy();

        ByteArrayDataOutput data = ByteStreams.newDataOutput();
        data.writeUTF(lastMessageName);
        data.writeUTF(lastMessage);
        data.writeLong(lastStaffMessage);
        byte[] bytes = data.toByteArray();

        servers.stream()
            .distinct()
            .forEach(
                server -> {
                  ByteArrayDataOutput out = ByteStreams.newDataOutput();
                  out.writeUTF("Forward");
                  out.writeUTF(server);
                  out.writeUTF("CMIStaffSubChannel");
                  out.writeShort(bytes.length);
                  out.write(bytes);
                  sender.getBukkit().sendPluginMessage(PGM.get(), "BungeeCord", out.toByteArray());
                });
      }
      return;
    }
    match.getPlayers().stream()
        .filter(filter)
        .forEach(
            player ->
                player.sendMessage(
                    String.format(
                        format,
                        TextTranslations.translate(
                            UsernameFormatUtils.CONSOLE_NAME, player.getBukkit().getLocale()),
                        message)));
  }

  private MatchPlayer getApproximatePlayer(Match match, String query, CommandSender sender) {
    return StringUtils.bestFuzzyMatch(
        query,
        match.getPlayers().stream()
            .collect(
                Collectors.toMap(
                    player -> player.getBukkit().getName(sender), Function.identity())),
        0.75);
  }

  private void sendMutedMessage(MatchPlayer player) {
    Component warning =
        TranslatableComponent.of(
            "moderation.mute.message",
            TextComponent.of(muted.getOrDefault(player.getId(), ""), TextColor.AQUA));
    player.sendWarning(warning);
  }

  private boolean checkMute(MatchPlayer player) {
    if (isMuted(player)) {
      sendMutedMessage(player);
      return false;
    }

    return true;
  }

  @EventHandler
  public void onReport(PlayerReportEvent e) {
    final Component component =
        TranslatableComponent.of(
            "moderation.report.notify",
            TextColor.YELLOW,
            e.getSender() == null
                ? UsernameFormatUtils.CONSOLE_NAME
                : e.getSender().getName(NameStyle.FANCY),
            e.getPlayer().getName(NameStyle.FANCY),
            TextComponent.of(e.getReason().trim(), TextColor.WHITE));

    broadcastAdminChatMessage(
        component, e.getPlayer().getMatch(), Optional.of(ReportCommand.REPORT_NOTIFY_SOUND));
  }

  public static void broadcastAdminChatMessage(Component message, Match match) {
    broadcastAdminChatMessage(message, match, Optional.of(AC_SOUND));
  }

  public static void broadcastAdminChatMessage(
      Component message, Match match, Optional<Sound> sound) {
    TextComponent formatted = ADMIN_CHAT_PREFIX.append(message);
    match.getPlayers().stream()
        .filter(AC_FILTER)
        .forEach(
            mp -> {
              // If provided a sound, play if setting allows
              sound.ifPresent(
                  s -> {
                    if (canPlaySound(mp)) {
                      mp.playSound(s);
                    }
                  });
              mp.sendMessage(formatted);
            });
    Audience.get(Bukkit.getConsoleSender()).sendMessage(formatted);
  }

  private static boolean canPlaySound(MatchPlayer viewer) {
    return viewer.getSettings().getValue(SettingKey.SOUNDS).equals(SettingValue.SOUNDS_ALL);
  }

  private Component getChatFormat(
      @Nullable Component prefix, MatchPlayer player, String message, SettingValue chatType) {
    String msg = message != null ? ChatColor.stripColor(message) : "";
    if (prefix == null) {
      String format = PGM.get().getConfiguration().getGlobalFormat();
      return TextComponent.builder().append(replaceFormat(player, msg, format, chatType)).build();
    }
    String format = PGM.get().getConfiguration().getTeamFormat();
    return TextComponent.builder()
        .append(prefix)
        .append(replaceFormat(player, msg, format, chatType))
        .build();
  }

  private Component replaceFormat(
      MatchPlayer player, String message, String format, SettingValue chatType) {
    String messageCode = names.getMessageColor(player.getBukkit());
    ChatColor color = ChatColor.RESET;
    if (!messageCode.isEmpty()) {
      color = ChatColor.getByChar(messageCode.length() >= 2 ? messageCode.charAt(1) : 'r');
      if (color == null) color = ChatColor.RESET;
    }
    if (chatType == SettingValue.CHAT_ADMIN) {
      color = ChatColor.YELLOW;
    }
    Player p = player.getBukkit();
    format = format.replace("<player>", names.getDecoratedName(p, player.getParty()));

    if (PGM.get().isPAPIEnabled()) format = PlaceholderAPI.setPlaceholders(p, format);

    format = ChatColor.translateAlternateColorCodes('&', format);

    StringBuilder coloredMessage = new StringBuilder();
    for (int i = 0; i < message.length(); i++)
      coloredMessage.append(color).append(message.charAt(i));

    format = format.replace("<message>", coloredMessage);

    TextComponent.Builder builder =
        TextComponent.builder()
            .append(TextComponent.of("Kills: " + player.getStats().getKills(), TextColor.GREEN))
            .append(TextComponent.of(" | ", TextColor.GRAY))
            .append(TextComponent.of("Deaths: " + player.getStats().getDeaths(), TextColor.RED))
            .append(TextComponent.newline())
            .append(TextComponent.of("Wins: " + player.getStats().getWins(), TextColor.GREEN))
            .append(TextComponent.of(" | ", TextColor.GRAY))
            .append(TextComponent.of("Losses: " + player.getStats().getLosses(), TextColor.RED))
            .append(TextComponent.newline())
            .append(TextComponent.of("Coins: " + player.getCoins().getCoins(), TextColor.YELLOW))
            .append(TextComponent.newline())
            .append(TextComponent.of("Elo: " + player.getStats().getElo(), TextColor.DARK_AQUA));

    PlayerData data = player.getPlayerData();
    JSONObject json = data.getData();
    builder.append(TextComponent.newline());
    builder.append(TextComponent.of("Tags: ", TextColor.DARK_PURPLE));
    if (json.has("tags")) {
      int i = 1;
      JSONArray array = json.getJSONArray("tags");
      for (Object o : array) {
        String tag = (String) o;
        builder.append(TextComponent.of(tag, TextColor.LIGHT_PURPLE));
        if (i++ != array.length()) {
          builder.append(TextComponent.of(" | ", TextColor.GRAY));
        }
      }
    }

    TextComponent hover = builder.build();

    return TextComponent.of(format)
        .hoverEvent(HoverEvent.showText(hover))
        .clickEvent(ClickEvent.suggestCommand("/msg " + player.getNameLegacy()));
  }

  private final List<String> servers = new ArrayList<>();

  public List<String> getServers() {
    return servers;
  }

  public long lastStaffMessage;

  @EventHandler
  private void onJoin(PlayerJoinEvent e) {
    if (servers.isEmpty()) {
      ByteArrayDataOutput data = ByteStreams.newDataOutput();
      data.writeUTF("GetServers");
      e.getPlayer().sendPluginMessage(PGM.get(), "BungeeCord", data.toByteArray());
    }
  }
}
