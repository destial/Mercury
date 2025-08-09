package tc.oc.pgm.listeners.support;

import static tc.oc.pgm.util.text.TextParser.parseComponentLegacy;

import com.lunarclient.apollo.Apollo;
import com.lunarclient.apollo.common.location.ApolloBlockLocation;
import com.lunarclient.apollo.common.location.ApolloLocation;
import com.lunarclient.apollo.module.beam.Beam;
import com.lunarclient.apollo.module.beam.BeamModule;
import com.lunarclient.apollo.module.glow.GlowModule;
import com.lunarclient.apollo.module.team.TeamMember;
import com.lunarclient.apollo.module.team.TeamModule;
import com.lunarclient.apollo.player.ApolloPlayer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.kyori.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.event.NameDecorationChangeEvent;
import tc.oc.pgm.api.event.ReloadEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.match.event.MatchStartEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;
import tc.oc.pgm.controlpoint.ControlPoint;
import tc.oc.pgm.controlpoint.ControlPointMatchModule;
import tc.oc.pgm.core.Core;
import tc.oc.pgm.core.CoreMatchModule;
import tc.oc.pgm.destroyable.Destroyable;
import tc.oc.pgm.destroyable.DestroyableMatchModule;
import tc.oc.pgm.events.PlayerJoinPartyEvent;
import tc.oc.pgm.events.PlayerLeavePartyEvent;
import tc.oc.pgm.match.ObservingParty;
import tc.oc.pgm.teams.Team;

public class LunarListener implements Listener {
  private final HashMap<UUID, PermissionAttachment> attachments = new HashMap<>();
  private final TeamModule teamModule;
  private final GlowModule glowModule;
  private final BeamModule beamModule;

  public LunarListener() {
    log("Found Apollo-Bukkit! Hooking into it!");
    PGM.get().getConfiguration().getGroups().add(new LunarVerified());
    teamModule = Apollo.getModuleManager().getModule(TeamModule.class);
    glowModule = Apollo.getModuleManager().getModule(GlowModule.class);
    beamModule = Apollo.getModuleManager().getModule(BeamModule.class);
  }

  @EventHandler
  public void onJoin(MatchPlayerAddEvent e) {
    Bukkit.getScheduler()
        .runTaskLater(
            PGM.get(),
            () -> {
              if (Apollo.getPlayerManager().hasSupport(e.getPlayer().getId())) {
                attach(e.getPlayer().getBukkit());
              }
            },
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
                  for (ApolloPlayer player : Apollo.getPlayerManager().getPlayers()) {
                    MatchPlayer matchPlayer = match.getPlayer(player.getUniqueId());
                    if (matchPlayer == null) continue;
                    if (matchPlayer.isObserving()) {
                      sendNoTeammates(player, matchPlayer);
                      continue;
                    }
                    sendTeammates(player, matchPlayer);
                  }
                },
                0L,
                5L);
  }

  @EventHandler
  public void onPlayerJoinParty(PlayerJoinPartyEvent e) {
    if (e.getOldParty() instanceof Team) {
      for (MatchPlayer p : e.getOldParty().getPlayers()) {
        if (p == e.getPlayer()) continue;

        Apollo.getPlayerManager()
            .getPlayer(e.getPlayer().getId())
            .ifPresent(ap -> sendTeammates(ap, p));
      }
    }
    if (e.getNewParty() instanceof Team) {
      for (MatchPlayer p : e.getNewParty().getPlayers()) {
        Apollo.getPlayerManager()
            .getPlayer(e.getPlayer().getId())
            .ifPresent(ap -> sendTeammates(ap, p));
      }
    }
    Apollo.getPlayerManager()
        .getPlayer(e.getPlayer().getId())
        .ifPresent(
            ap -> {
              if (e.getNewParty() instanceof ObservingParty) {
                sendNoTeammates(ap, e.getPlayer());
              }
            });
  }

  @EventHandler
  public void onMatchStart(MatchStartEvent e) {
    task(e.getMatch());
  }

  @EventHandler
  public void onMatchEnd(MatchFinishEvent e) {
    task.cancel();
    task = null;
    for (ApolloPlayer player : Apollo.getPlayerManager().getPlayers()) {
      MatchPlayer p = e.getMatch().getPlayer(player.getUniqueId());
      if (p == null) continue;
      sendNoTeammates(player, p);
    }
  }

  public void sendTeammates(ApolloPlayer ap, MatchPlayer p) {
    if (p == null || p.getBukkit() == null || !p.getBukkit().isOnline()) return;
    List<TeamMember> map = new ArrayList<>();
    Color partyColor = p.getParty().getFullColor();
    java.awt.Color color =
        new java.awt.Color(partyColor.getRed(), partyColor.getGreen(), partyColor.getBlue());
    for (MatchPlayer teammate : p.getParty().getPlayers()) {
      if (p == teammate) continue;
      Location loc = teammate.getBukkit().getLocation();
      map.add(
          TeamMember.builder()
              .displayName(net.kyori.adventure.text.Component.text(teammate.getNameLegacy()))
              .playerUuid(teammate.getId())
              .location(
                  ApolloLocation.builder()
                      .x(loc.getX())
                      .y(loc.getY())
                      .z(loc.getZ())
                      .world(loc.getWorld().getName())
                      .build())
              .markerColor(color)
              .build());
    }
    teamModule.updateTeamMembers(ap, map);
    glowModule.resetGlow(ap);
    for (TeamMember tm : map) {
      glowModule.overrideGlow(ap, tm.getPlayerUuid(), color);
    }
    beamModule.resetBeams(ap);
    DestroyableMatchModule dmm = p.getMatch().getModule(DestroyableMatchModule.class);
    if (dmm != null) {
      for (Destroyable destroyable : dmm.getDestroyables()) {
        if (destroyable.getOwner() != p.getParty() && !destroyable.isCompleted()) {
          java.awt.Color beamColor =
              new java.awt.Color(
                  destroyable.getOwner().getFullColor().getRed(),
                  destroyable.getOwner().getFullColor().getGreen(),
                  destroyable.getOwner().getFullColor().getBlue());
          Vector center = destroyable.getBlockRegion().getBounds().getCenterPoint();
          Beam beam =
              Beam.builder()
                  .id(destroyable.getId())
                  .location(
                      ApolloBlockLocation.builder()
                          .x(center.getBlockX())
                          .y(center.getBlockY())
                          .z(center.getBlockZ())
                          .world(p.getWorld().getName())
                          .build())
                  .color(beamColor)
                  .build();
          beamModule.displayBeam(ap, beam);
        }
      }
    }

    CoreMatchModule cmm = p.getMatch().getModule(CoreMatchModule.class);
    if (cmm != null) {
      for (Core core : cmm.getCores()) {
        if (core.getOwner() != p.getParty() && !core.isCompleted()) {
          java.awt.Color beamColor =
              new java.awt.Color(
                  core.getOwner().getFullColor().getRed(),
                  core.getOwner().getFullColor().getGreen(),
                  core.getOwner().getFullColor().getBlue());
          Vector center = core.getCasingRegion().getBounds().getCenterPoint();
          Beam beam =
              Beam.builder()
                  .id(core.getId())
                  .location(
                      ApolloBlockLocation.builder()
                          .x(center.getBlockX())
                          .y(center.getBlockY())
                          .z(center.getBlockZ())
                          .world(p.getWorld().getName())
                          .build())
                  .color(beamColor)
                  .build();
          beamModule.displayBeam(ap, beam);
        }
      }
    }

    ControlPointMatchModule cpmm = p.getMatch().getModule(ControlPointMatchModule.class);
    if (cpmm != null) {
      for (ControlPoint cp : cpmm.getControlPoints()) {
        if (cp.getPartialOwner() != p.getParty()) {
          Vector center = cp.getCenterPoint();
          Competitor partialOwner = cp.getPartialOwner();
          java.awt.Color beamColor =
              new java.awt.Color(
                  partialOwner == null ? 255 : partialOwner.getFullColor().getRed(),
                  partialOwner == null ? 255 : partialOwner.getFullColor().getGreen(),
                  partialOwner == null ? 255 : partialOwner.getFullColor().getBlue());
          Beam beam =
              Beam.builder()
                  .id(cp.getId())
                  .location(
                      ApolloBlockLocation.builder()
                          .x(center.getBlockX())
                          .y(center.getBlockY())
                          .z(center.getBlockZ())
                          .world(p.getWorld().getName())
                          .build())
                  .color(beamColor)
                  .build();
          beamModule.displayBeam(ap, beam);
        }
      }
    }
  }

  public void sendNoTeammates(ApolloPlayer ap, MatchPlayer p) {
    if (p.getBukkit() == null) return;
    if (!p.getBukkit().isOnline()) return;
    glowModule.resetGlow(ap);
    teamModule.resetTeamMembers(ap);
    beamModule.resetBeams(ap);
  }

  @EventHandler
  public void onPlayerLeaveParty(PlayerLeavePartyEvent e) {
    Apollo.getPlayerManager()
        .getPlayer(e.getPlayer().getId())
        .ifPresent(ap -> sendNoTeammates(ap, e.getPlayer()));
  }

  @EventHandler
  public void onReload(ReloadEvent e) {
    PGM.get().getConfiguration().getGroups().add(new LunarVerified());
    for (Player p : Bukkit.getOnlinePlayers()) {
      if (Apollo.getPlayerManager().hasSupport(p.getUniqueId())) {
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

  private void attach(Player p) {
    if (p.hasPermission(Permissions.LUNAR)) return;
    attachments.put(p.getUniqueId(), p.addAttachment(PGM.get()));
    PermissionAttachment a = attachments.get(p.getUniqueId());
    a.setPermission(Permissions.LUNAR, true);
    log(p.getName() + " has been given the " + Permissions.LUNAR);
    Bukkit.getScheduler().runTaskLater(PGM.get(), () -> call(p.getUniqueId()), 5L);
  }

  private void remove(Player p) {
    PermissionAttachment a = attachments.get(p.getUniqueId());
    if (a == null) return;
    if (p.hasPermission(Permissions.LUNAR)) {
      a.unsetPermission(Permissions.LUNAR);
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

  public static class LunarVerified implements Config.Group {
    private final Flair flair;
    private final Permission permission;

    public LunarVerified() {
      flair = new Flair();
      permission = Permissions.register(new Permission(Permissions.LUNAR));
    }

    @Override
    public String getId() {
      return "lunar";
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
      private final String suffix;
      private final String displayName;

      public Flair() {
        suffix = parseComponentLegacy(" &a✔");
        displayName = parseComponentLegacy("&aLunar Player");
      }

      @Override
      public String getPrefix() {
        return null;
      }

      @Override
      public String getSuffix() {
        return suffix;
      }

      @Override
      public String getDescription() {
        return null;
      }

      @Override
      public String getDisplayName() {
        return displayName;
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
