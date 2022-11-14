package tc.oc.pgm.listeners;

import java.util.HashMap;
import java.util.UUID;
import me.frep.vulcan.api.data.IPlayerData;
import me.frep.vulcan.api.event.VulcanRegisterPlayerEvent;
import me.frep.vulcan.spigot.api.VulcanSpigotAPI;
import net.kyori.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.Nullable;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.event.NameDecorationChangeEvent;
import tc.oc.pgm.api.event.ReloadEvent;

public class VulcanListener implements Listener {
  public static class Forge implements Config.Group {
    private final Flair flair;
    private final Permission permission;

    public Forge() {
      flair = new Flair();
      permission = Permissions.register(new Permission(Permissions.FORGE));
    }

    @Override
    public String getId() {
      return "forge";
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
        return " &f∬";
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

  public static class Sus implements Config.Group {
    private final Flair flair;
    private final Permission permission;

    public Sus() {
      flair = new Flair();
      permission = Permissions.register(new Permission(Permissions.SUS));
    }

    @Override
    public String getId() {
      return "sus";
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
        return " &cඞ";
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

  private void register() {
    PGM.get().getConfiguration().getGroups().add(new Forge());
    PGM.get().getConfiguration().getGroups().add(new Sus());
  }

  public VulcanListener() {
    log("Vulcan detected! Hooking into it!");
    register();
  }

  private String $(String s) {
    return ChatColor.translateAlternateColorCodes('&', s);
  }

  private void log(String s) {
    PGM.get().getLogger().info(s);
  }

  private void call(UUID uuid) {
    Bukkit.getPluginManager().callEvent(new NameDecorationChangeEvent(uuid));
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onRegister(VulcanRegisterPlayerEvent e) {
    Bukkit.getScheduler()
        .runTaskLater(
            PGM.get(),
            () -> {
              final VulcanSpigotAPI vulcan = new VulcanSpigotAPI();
              Player p = e.getPlayer();
              IPlayerData data = vulcan.getPlayerData(p);
              if (data == null) return;
              String client = data.getClientBrand().toLowerCase().trim();
              log(p.getName() + " is using " + client);
              if (PGM.get().getConfiguration().getSussyClients().stream()
                  .anyMatch(s -> client.startsWith(s.toLowerCase()))) {
                attach(p, Permissions.SUS);
                p.sendMessage($("&7You are using a &csus &7client! Sussy little baka!"));
                return;
              }
              if (client.startsWith("fml") || client.startsWith("forge")) {
                attach(p, Permissions.FORGE);
                p.sendMessage($("&aYou are using &fForge&a! You have been given the forge sign!"));
              }
            },
            5L);
  }

  @EventHandler
  public void onReload(ReloadEvent e) {
    register();
    final VulcanSpigotAPI vulcan = new VulcanSpigotAPI();
    for (Player p : Bukkit.getOnlinePlayers()) {
      IPlayerData data = vulcan.getPlayerData(p);
      if (data == null) continue;
      String client = data.getClientBrand().toLowerCase().trim();
      if (PGM.get().getConfiguration().getSussyClients().stream()
          .anyMatch(s -> client.startsWith(s.toLowerCase()))) {
        attach(p, Permissions.SUS);
        continue;
      }
      if (client.startsWith("fml") || client.startsWith("forge")) {
        attach(p, Permissions.FORGE);
        continue;
      }
      remove(p);
    }
  }

  private final HashMap<UUID, PermissionAttachment> attachments = new HashMap<>();

  private void attach(Player p, String permission) {
    if (p.hasPermission(permission)) return;
    attachments.put(p.getUniqueId(), p.addAttachment(PGM.get()));
    PermissionAttachment a = attachments.get(p.getUniqueId());
    a.setPermission(permission, true);
    log(p.getName() + " has been given the " + permission);
    call(p.getUniqueId());
  }

  private void remove(Player p) {
    PermissionAttachment a = attachments.get(p.getUniqueId());
    if (a == null) return;
    if (p.hasPermission(Permissions.SUS)) {
      a.unsetPermission(Permissions.SUS);
    }
    if (p.hasPermission(Permissions.FORGE)) {
      a.unsetPermission(Permissions.FORGE);
    }
    a.remove();
    attachments.remove(p.getUniqueId());
    call(p.getUniqueId());
  }
}
