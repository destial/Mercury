package tc.oc.pgm.listeners;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.community.events.PlayerVanishEvent;
import tc.oc.pgm.events.PlayerParticipationStartEvent;
import tc.oc.pgm.util.chat.Audience;

public class AFKListener implements Listener {
  private final Map<UUID, Long> afkPlayers;
  private final Map<UUID, Long> notMoved;
  private static final long AFK_TIMER = 30 * 1000;
  private static final long AFK_KICK = 5 * 60 * 1000;
  private final String[] afkMessages = {
    "The numbers Mason! What do they mean?",
    "007",
    "1337",
    "Look behind you!",
    "Save electricity, go off bozo",
    ":floosh:",
    "stop afk",
    "imagine afk, haha",
    "what r u doing",
    "whats 2 + 1",
    "eeeeeeeeeeeeeeeeeee"
  };

  public AFKListener() {
    afkPlayers = new ConcurrentHashMap<>();
    notMoved = new ConcurrentHashMap<>();
    Bukkit.getScheduler()
        .runTaskTimer(
            PGM.get(),
            () -> {
              Collection<? extends Player> online = Bukkit.getOnlinePlayers();
              for (Player player : online) {
                if (!afkPlayers.containsKey(player.getUniqueId())) {
                  notMoved.putIfAbsent(
                      player.getUniqueId(), System.currentTimeMillis() + AFK_TIMER);
                }
              }

              Set<Map.Entry<UUID, Long>> noMoveSet =
                  Collections.unmodifiableSet(notMoved.entrySet());
              for (Map.Entry<UUID, Long> noMove : noMoveSet) {
                if (noMove.getValue() <= System.currentTimeMillis()) {
                  afkPlayers.put(noMove.getKey(), System.currentTimeMillis() + AFK_KICK);
                  Player player = Bukkit.getPlayer(noMove.getKey());
                  if (player != null) {
                    player.sendMessage(ChatColor.GOLD + "You are now AFK!");
                  }
                }
              }

              Set<Map.Entry<UUID, Long>> afkSet =
                  Collections.unmodifiableSet(afkPlayers.entrySet());
              for (Map.Entry<UUID, Long> afk : afkSet) {
                notMoved.remove(afk.getKey());
                Player player = Bukkit.getPlayer(afk.getKey());
                long current = System.currentTimeMillis();
                if (afk.getValue() <= current) {
                  if (player != null) {
                    player.kickPlayer(
                        ChatColor.GOLD + "You have been kicked for being AFK too long!");
                  }
                } else {
                  if (player != null) {
                    Audience audience = Audience.get(player);
                    long millis = afk.getValue() - current;
                    int s = (int) (millis / 1000);
                    String randomMessage = afkMessages[(int) (Math.random() * afkMessages.length)];
                    audience.showTitle(
                        TextComponent.of("You are now AFK (" + s + "s)", TextColor.GOLD),
                        TextComponent.of(randomMessage, TextColor.GREEN),
                        0,
                        30,
                        0);
                  }
                }
              }
            },
            0L,
            20L);
  }

  @EventHandler
  public void onMove(PlayerMoveEvent e) {
    if (e instanceof PlayerTeleportEvent) return;
    if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
      afkPlayers.remove(e.getPlayer().getUniqueId());
      e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
    }
    notMoved.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onInteract(PlayerInteractEvent e) {
    if (e.getAction() != Action.PHYSICAL) {
      if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
        afkPlayers.remove(e.getPlayer().getUniqueId());
        e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
      }
      notMoved.remove(e.getPlayer().getUniqueId());
    }
  }

  @EventHandler
  public void onShift(PlayerToggleSneakEvent e) {
    if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
      afkPlayers.remove(e.getPlayer().getUniqueId());
      e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
    }
    notMoved.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onJoinParty(PlayerParticipationStartEvent e) {
    if (afkPlayers.containsKey(e.getPlayer().getId())) {
      e.setCancelled(true);
    }
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent e) {
    afkPlayers.remove(e.getPlayer().getUniqueId());
    notMoved.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onJoin(PlayerJoinEvent e) {
    afkPlayers.remove(e.getPlayer().getUniqueId());
    notMoved.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onInventory(InventoryInteractEvent e) {
    if (e.getWhoClicked() instanceof Player) {
      if (afkPlayers.containsKey(e.getWhoClicked().getUniqueId())) {
        afkPlayers.remove(e.getWhoClicked().getUniqueId());
        e.getWhoClicked().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
      }
      notMoved.remove(e.getWhoClicked().getUniqueId());
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent e) {
    if (e.getPlayer() instanceof Player) {
      if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
        afkPlayers.remove(e.getPlayer().getUniqueId());
        e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
      }
      notMoved.remove(e.getPlayer().getUniqueId());
    }
  }

  @EventHandler
  public void onVanish(PlayerVanishEvent e) {
    if (afkPlayers.containsKey(e.getPlayer().getId())) {
      afkPlayers.remove(e.getPlayer().getId());
      e.getPlayer().sendMessage(TextComponent.of("You are no longer AFK!", TextColor.GREEN));
    }
    notMoved.remove(e.getPlayer().getId());
  }

  @EventHandler
  public void onCommand(PlayerCommandPreprocessEvent e) {
    if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
      afkPlayers.remove(e.getPlayer().getUniqueId());
      e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
    }
    notMoved.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onDrop(PlayerDropItemEvent e) {
    if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
      afkPlayers.remove(e.getPlayer().getUniqueId());
      e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
    }
    notMoved.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onChat(AsyncPlayerChatEvent e) {
    if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
      afkPlayers.remove(e.getPlayer().getUniqueId());
      e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
    }
    notMoved.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void onHotbar(PlayerItemHeldEvent e) {
    if (afkPlayers.containsKey(e.getPlayer().getUniqueId())) {
      afkPlayers.remove(e.getPlayer().getUniqueId());
      e.getPlayer().sendMessage(ChatColor.GREEN + "You are no longer AFK!");
    }
    notMoved.remove(e.getPlayer().getUniqueId());
  }
}
