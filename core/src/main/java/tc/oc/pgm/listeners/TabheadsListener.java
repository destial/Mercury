package tc.oc.pgm.listeners;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import java.util.HashMap;
import java.util.UUID;
import net.kyori.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

public class TabheadsListener implements PluginMessageListener, Listener {
  private Match match;

  public TabheadsListener() {
    Bukkit.getServer()
        .getMessenger()
        .registerIncomingPluginChannel(PGM.get(), "tabheads:premium", this);
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
    attach(player, Permissions.LEGIT);
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

  private void attach(Player p, String permission) {
    if (p.hasPermission(permission)) return;
    attachments.put(p.getUniqueId(), p.addAttachment(PGM.get()));
    PermissionAttachment a = attachments.get(p.getUniqueId());
    a.setPermission(permission, true);
    log(p.getName() + " has been given the " + permission);
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
    if (p.hasPermission(Permissions.LEGIT)) {
      a.unsetPermission(Permissions.LEGIT);
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
      permission = Permissions.register(new Permission(Permissions.LEGIT));
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
      @Override
      public String getPrefix() {
        return null;
      }

      @Override
      public String getSuffix() {
        return " &e™";
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
