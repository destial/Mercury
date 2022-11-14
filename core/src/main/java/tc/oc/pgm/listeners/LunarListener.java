package tc.oc.pgm.listeners;

import com.lunarclient.bukkitapi.LunarClientAPI;
import com.lunarclient.bukkitapi.nethandler.client.LCPacketNotification;
import com.lunarclient.bukkitapi.nethandler.client.LCPacketTeammates;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.event.NameDecorationChangeEvent;
import tc.oc.pgm.api.event.ReloadEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;
import tc.oc.pgm.events.PlayerJoinPartyEvent;
import tc.oc.pgm.events.PlayerLeavePartyEvent;
import tc.oc.pgm.match.ObservingParty;
import tc.oc.pgm.teams.Team;

public class LunarListener implements Listener {
  private final LunarClientAPI api;

  public LunarListener() {
    log("Found LunarClient-API! Hooking into it!");
    PGM.get().getConfiguration().getGroups().add(new Verified());
    api = LunarClientAPI.getInstance();
  }

  @EventHandler
  public void onJoin(PlayerRegisterChannelEvent e) {
    if (!e.getChannel().equalsIgnoreCase(LunarClientAPI.MESSAGE_CHANNEL)) return;
    if (api.isRunningLunarClient(e.getPlayer())) {
      attach(e.getPlayer());
    }
  }

  @EventHandler
  public void onMatchPlayerAdd(MatchPlayerAddEvent e) {
    if (!api.isRunningLunarClient(e.getPlayer().getBukkit())) return;
    Bukkit.getScheduler()
        .runTaskLater(
            PGM.get(),
            () ->
                LunarClientAPI.getInstance()
                    .sendPacket(
                        e.getPlayer().getBukkit(),
                        new LCPacketNotification("You have been verified!", 5000, "info")),
            20L);
  }

  private BukkitTask task;

  private void task(Match match) {
    if (task != null) task.cancel();
    task =
        Bukkit.getScheduler()
            .runTaskTimer(
                PGM.get(),
                () -> {
                  for (Player player : api.getPlayersRunningLunarClient()) {
                    MatchPlayer matchPlayer = match.getPlayer(player);
                    if (matchPlayer == null) continue;
                    if (matchPlayer.isObserving()) {
                      sendNoTeammates(matchPlayer);
                      continue;
                    }
                    sendTeammates(matchPlayer);
                  }
                },
                0L,
                5L);
  }

  @EventHandler
  public void onPlayerJoinParty(PlayerJoinPartyEvent e) {
    if (e.getOldParty() instanceof Team) {
      for (MatchPlayer p : e.getOldParty().getPlayers()) {
        if (!api.isRunningLunarClient(p.getId()) || p == e.getPlayer()) continue;
        sendTeammates(p);
      }
    }
    if (e.getNewParty() instanceof Team) {
      for (MatchPlayer p : e.getNewParty().getPlayers()) {
        if (!api.isRunningLunarClient(p.getId())) continue;
        sendTeammates(p);
      }
    }
    if (!api.isRunningLunarClient(e.getPlayer().getBukkit())) return;
    if (e.getNewParty() instanceof ObservingParty) {
      sendNoTeammates(e.getPlayer());
    }
  }

  @EventHandler
  public void onMatchStart(MatchStartEvent e) {
    task(e.getMatch());
  }

  @EventHandler
  public void onMatchEnd(MatchFinishEvent e) {
    task.cancel();
    task = null;
    for (Player player : api.getPlayersRunningLunarClient()) {
      MatchPlayer p = e.getMatch().getPlayer(player);
      if (p == null) continue;
      sendNoTeammates(p);
    }
  }

  public void sendTeammates(MatchPlayer p) {
    if (p == null || p.getBukkit() == null || !p.getBukkit().isOnline()) return;
    Map<UUID, Map<String, Double>> map = new HashMap<>();
    for (MatchPlayer teammate : p.getParty().getPlayers()) {
      if (p == teammate) continue;
      Map<String, Double> pos = new HashMap<>();
      Location loc = teammate.getBukkit().getLocation();
      pos.put("x", loc.getX());
      pos.put("y", loc.getY());
      pos.put("z", loc.getZ());
      map.put(teammate.getId(), pos);
    }
    LunarClientAPI.getInstance()
        .sendTeammates(p.getBukkit(), new LCPacketTeammates(null, 1000, map));
  }

  public void sendNoTeammates(MatchPlayer p) {
    if (p.getBukkit() == null) return;
    if (!p.getBukkit().isOnline()) return;
    LunarClientAPI.getInstance()
        .sendTeammates(p.getBukkit(), new LCPacketTeammates(null, 1000, new HashMap<>()));
  }

  @EventHandler
  public void onPlayerLeaveParty(PlayerLeavePartyEvent e) {
    if (!api.isRunningLunarClient(e.getPlayer().getBukkit())) return;
    sendNoTeammates(e.getPlayer());
  }

  @EventHandler
  public void onReload(ReloadEvent e) {
    PGM.get().getConfiguration().getGroups().add(new Verified());
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (api.isRunningLunarClient(p)) {
        attach(p);
        continue;
      }
      remove(p);
    }
  }

  @EventHandler
  public void onLeave(PlayerQuitEvent e) {
    remove(e.getPlayer());
  }

  private final HashMap<UUID, PermissionAttachment> attachments = new HashMap<>();

  private void attach(Player p) {
    if (p.hasPermission(Permissions.VERIFIED)) return;
    attachments.put(p.getUniqueId(), p.addAttachment(PGM.get()));
    PermissionAttachment a = attachments.get(p.getUniqueId());
    a.setPermission(Permissions.VERIFIED, true);
    log(p.getName() + " has been given the " + Permissions.VERIFIED);
    Bukkit.getScheduler().runTaskLater(PGM.get(), () -> call(p.getUniqueId()), 20L);
  }

  private void remove(Player p) {
    PermissionAttachment a = attachments.get(p.getUniqueId());
    if (a == null) return;
    if (p.hasPermission(Permissions.VERIFIED)) {
      a.unsetPermission(Permissions.VERIFIED);
    }
    a.remove();
    attachments.remove(p.getUniqueId());
  }

  private void log(String s) {
    PGM.get().getLogger().info(s);
  }

  private void call(UUID uuid) {
    Bukkit.getPluginManager().callEvent(new NameDecorationChangeEvent(uuid));
  }

  public static class Verified implements Config.Group {
    private final Flair flair;
    private final Permission permission;

    public Verified() {
      flair = new Flair();
      permission = Permissions.register(new Permission(Permissions.VERIFIED));
    }

    @Override
    public String getId() {
      return "verified";
    }

    @Override
    public Config.Flair getFlair() {
      return flair;
    }

    @Nullable
    @Override
    public String getPrefix() {
      return flair.getPrefix();
    }

    @Nullable
    @Override
    public String getSuffix() {
      return flair.getSuffix();
    }

    @Nullable
    @Override
    public String getMessageColor() {
      return flair.getMessageColor();
    }

    @Nullable
    @Override
    public ChatColor getMessageChatColor() {
      return null;
    }

    @Override
    public Permission getPermission() {
      return permission;
    }

    @Override
    public Permission getObserverPermission() {
      return Permissions.DEFAULT;
    }

    @Override
    public Permission getParticipantPermission() {
      return Permissions.DEFAULT;
    }

    private static class Flair implements Config.Flair {

      @Override
      public String getPrefix() {
        return null;
      }

      @Override
      public String getSuffix() {
        return " &a\u2714";
      }

      @Override
      public String getDescription() {
        return null;
      }

      @Override
      public String getDisplayName() {
        return "";
      }

      @Override
      public String getClickLink() {
        return null;
      }

      @Override
      public String getMessageColor() {
        return null;
      }

      @Override
      public Component getPrefixOverride() {
        return null;
      }

      @Override
      public Component getSuffixOverride() {
        return null;
      }
    }
  }
}
