package tc.oc.pgm.listeners.support;

import static tc.oc.pgm.util.text.TextParser.parseComponentLegacy;

import com.cheatbreaker.api.CheatBreakerAPI;
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
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.event.NameDecorationChangeEvent;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;

public class CheatBreakerListener implements Listener {
  private final HashMap<UUID, PermissionAttachment> attachments = new HashMap<>();

  public CheatBreakerListener() {
    log("Found CheatBreakerAPI! Hooking into it!");
    PGM.get().getConfiguration().getGroups().add(new CheatBreakerVerified());
  }

  @EventHandler
  public void onJoin(MatchPlayerAddEvent e) {
    Bukkit.getScheduler()
        .runTaskLater(
            PGM.get(),
            () -> {
              if (CheatBreakerAPI.getInstance().isRunningCheatBreaker(e.getPlayer().getId())) {
                attach(e.getPlayer().getBukkit());
              }
            },
            5L);
  }

  @EventHandler
  public void onLeave(PlayerQuitEvent e) {
    remove(e.getPlayer());
  }

  private void attach(Player p) {
    if (p.hasPermission(Permissions.CHEATBREAKER)) return;
    attachments.put(p.getUniqueId(), p.addAttachment(PGM.get()));
    PermissionAttachment a = attachments.get(p.getUniqueId());
    a.setPermission(Permissions.CHEATBREAKER, true);
    log(p.getName() + " has been given the " + Permissions.CHEATBREAKER);
    Bukkit.getScheduler().runTaskLater(PGM.get(), () -> call(p.getUniqueId()), 5L);
  }

  private void remove(Player p) {
    PermissionAttachment a = attachments.get(p.getUniqueId());
    if (a == null) return;
    if (p.hasPermission(Permissions.CHEATBREAKER)) {
      a.unsetPermission(Permissions.CHEATBREAKER);
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

  public static class CheatBreakerVerified implements Config.Group {
    private final Flair flair;
    private final Permission permission;

    public CheatBreakerVerified() {
      flair = new Flair();
      permission = Permissions.register(new Permission(Permissions.CHEATBREAKER));
    }

    @Override
    public String getId() {
      return "cheatbreaker";
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
        suffix = parseComponentLegacy(" &b✔");
        displayName = parseComponentLegacy("&bCheatBreaker Player");
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
