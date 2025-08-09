package tc.oc.pgm.listeners.support;

import static tc.oc.pgm.util.text.TextParser.parseComponentLegacy;

import com.github.games647.craftapi.resolver.MojangResolver;
import com.github.games647.craftapi.resolver.RateLimitException;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import net.kyori.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.event.NameDecorationChangeEvent;
import tc.oc.pgm.api.event.ReloadEvent;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.player.MatchPlayer;

public class PremiumAuthListener implements PluginMessageListener, Listener {
  private final MojangResolver resolver;
  private Match match;

  public PremiumAuthListener() {
    Bukkit.getServer()
        .getMessenger()
        .registerIncomingPluginChannel(PGM.get(), "tabheads:premium", this);
    resolver = new MojangResolver();
    register();
  }

  @EventHandler
  public void onMatchLoad(MatchLoadEvent e) {
    match = e.getMatch();
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
    if (!channel.equalsIgnoreCase("tabheads:premium")) return;
    ByteArrayDataInput in = ByteStreams.newDataInput(bytes);
    String name = in.readUTF();
    MatchPlayer matchPlayer = match.getPlayer(player);
    if (matchPlayer == null) {
      player = Bukkit.getPlayer(name);
      matchPlayer = match.getPlayer(player);
    }
    if (matchPlayer == null) return;
    attach(player);
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    Bukkit.getScheduler()
        .runTaskLater(
            PGM.get(),
            () -> {
              try {
                resolver
                    .findProfile(e.getPlayer().getName())
                    .ifPresent(
                        p -> {
                          if (p.getId().equals(e.getPlayer().getUniqueId())
                              && p.getName().equals(e.getPlayer().getName())) attach(e.getPlayer());
                        });
              } catch (IOException | RateLimitException ex) {
                throw new RuntimeException(ex);
              }
            },
            5L);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    remove(e.getPlayer());
  }

  private final HashMap<UUID, PermissionAttachment> attachments = new HashMap<>();

  private void log(String s) {
    PGM.get().getLogger().info(s);
  }

  private void call(UUID uuid) {
    Bukkit.getPluginManager().callEvent(new NameDecorationChangeEvent(uuid));
  }

  private void attach(Player p) {
    if (p.hasPermission(Permissions.BOUGHT_ACCOUNT)) return;
    attachments.put(p.getUniqueId(), p.addAttachment(PGM.get()));
    PermissionAttachment a = attachments.get(p.getUniqueId());
    a.setPermission(Permissions.BOUGHT_ACCOUNT, true);
    log(p.getName() + " has been given the " + Permissions.BOUGHT_ACCOUNT);
    call(p.getUniqueId());
  }

  private void register() {
    PGM.get().getConfiguration().getGroups().add(new Premium());
  }

  @EventHandler
  public void onReload(ReloadEvent e) {
    register();
  }

  private void remove(Player p) {
    PermissionAttachment a = attachments.get(p.getUniqueId());
    if (a == null) return;
    if (p.hasPermission(Permissions.BOUGHT_ACCOUNT)) {
      a.unsetPermission(Permissions.BOUGHT_ACCOUNT);
    }
    a.remove();
    attachments.remove(p.getUniqueId());
    call(p.getUniqueId());
  }

  public static class Premium implements Config.Group {
    private final Flair flair;
    private final Permission permission;

    public Premium() {
      flair = new Flair();
      permission = Permissions.register(new Permission(Permissions.BOUGHT_ACCOUNT));
    }

    @Override
    public String getId() {
      return "premium";
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
        suffix = parseComponentLegacy(" &e™");
        displayName = parseComponentLegacy("&ePremium Player");
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
