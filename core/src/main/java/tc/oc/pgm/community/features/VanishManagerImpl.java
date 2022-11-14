package tc.oc.pgm.community.features;

import app.ashcon.intake.Command;
import app.ashcon.intake.CommandException;
import app.ashcon.intake.parametric.annotation.Switch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.kyori.text.Component;
import net.kyori.text.TextComponent;
import net.kyori.text.TranslatableComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import net.minecraft.server.v1_8_R3.DedicatedPlayerList;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.Permissions;
import tc.oc.pgm.api.community.vanish.VanishManager;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;
import tc.oc.pgm.community.events.PlayerVanishEvent;
import tc.oc.pgm.listeners.PGMListener;
import tc.oc.pgm.util.reflect.ReflectionUtils;
import xyz.destiall.java.reflection.Reflect;

public class VanishManagerImpl implements VanishManager, Listener {

  private static final String VANISH_KEY = "isVanished";
  private static final MetadataValue VANISH_VALUE = new FixedMetadataValue(PGM.get(), true);

  private final List<UUID> vanishedPlayers;
  private final MatchManager matchManager;
  private DedicatedPlayerList playerList;

  private final Future<?>
      hotbarTask; // Task is run every second to ensure vanished players retain hotbar message
  private boolean hotbarFlash;

  public VanishManagerImpl(MatchManager matchManager, ScheduledExecutorService tasks) {
    this.vanishedPlayers = Lists.newArrayList();
    this.matchManager = matchManager;
    this.hotbarFlash = false;
    this.hotbarTask =
        tasks.scheduleAtFixedRate(
            () -> {
              getOnlineVanished().forEach(p -> sendHotbarVanish(p, hotbarFlash));
              hotbarFlash = !hotbarFlash; // Toggle boolean so we get a nice flashing effect
            },
            0,
            1,
            TimeUnit.SECONDS);
    inject();
  }

  public void inject() {
    playerList =
        ReflectionUtils.readField(
            Bukkit.getServer().getClass(),
            Bukkit.getServer(),
            DedicatedPlayerList.class,
            "playerList");
    List<Player> playerView = new VanishedList(playerList.players, vanishedPlayers);
    Reflect.setDeclaredField(Bukkit.getServer(), "playerView", playerView);
  }

  public void release() {
    List<Player> playerView =
        Collections.unmodifiableList(
            Lists.transform(playerList.players, EntityPlayer::getBukkitEntity));
    Reflect.setDeclaredField(Bukkit.getServer(), "playerView", playerView);
  }

  @Override
  public void disable() {
    hotbarTask.cancel(true);
    release();
  }

  @Override
  public boolean isVanished(UUID uuid) {
    return vanishedPlayers.contains(uuid);
  }

  @Override
  public List<MatchPlayer> getOnlineVanished() {
    return vanishedPlayers.stream()
        .filter(u -> matchManager.getPlayer(u) != null)
        .map(matchManager::getPlayer)
        .collect(Collectors.toList());
  }

  @Override
  public boolean setVanished(MatchPlayer player, boolean vanish, boolean quiet) {
    // Keep track of the UUID and apply/remove META data, so we can detect vanish status from other
    // projects (i.e utils)
    if (vanish) {
      addVanished(player);
    } else {
      removeVanished(player);
    }

    final Match match = player.getMatch();

    // Ensure player is an observer
    match.setParty(player, match.getDefaultParty());

    // Set vanish status in match player
    player.setVanished(vanish);

    // Reset visibility to hide/show player
    player.resetVisibility();

    // Broadcast join/quit message
    if (!quiet) {
      PGMListener.announceJoinOrLeave(player, !vanish, false);
    }

    match.callEvent(new PlayerVanishEvent(player, vanish));

    return isVanished(player.getId());
  }

  private void addVanished(MatchPlayer player) {
    if (!isVanished(player.getId())) {
      this.vanishedPlayers.add(player.getId());
      player.getBukkit().setMetadata(VANISH_KEY, VANISH_VALUE);
    }
  }

  private void removeVanished(MatchPlayer player) {
    this.vanishedPlayers.remove(player.getId());
    player.getBukkit().removeMetadata(VANISH_KEY, VANISH_VALUE.getOwningPlugin());
  }

  /* Commands */
  @Command(
      aliases = {"vanish", "v"},
      desc = "Toggle vanish status",
      perms = Permissions.VANISH)
  public void vanish(MatchPlayer sender, @Switch('s') boolean silent) throws CommandException {
    if (setVanished(sender, !isVanished(sender.getId()), silent)) {
      sender.sendWarning(TranslatableComponent.of("vanish.activate").color(TextColor.GREEN));
    } else {
      sender.sendWarning(TranslatableComponent.of("vanish.deactivate").color(TextColor.RED));
    }
  }

  /* Events */
  private final Cache<UUID, String> loginSubdomains =
      CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.SECONDS).build();
  private final List<UUID> tempVanish =
      Lists.newArrayList(); // List of online UUIDs who joined via "vanish" subdomain

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPreJoin(PlayerLoginEvent event) {
    Player player = event.getPlayer();
    loginSubdomains.invalidate(player.getUniqueId());
    if (player.hasPermission(Permissions.VANISH)
        && !isVanished(player.getUniqueId())
        && isVanishSubdomain(event.getHostname())) {
      loginSubdomains.put(player.getUniqueId(), event.getHostname());
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onJoin(PlayerJoinEvent event) {
    MatchPlayer player = matchManager.getPlayer(event.getPlayer());
    if (isVanished(player.getId())) { // Player is already vanished
      player.setVanished(true);
    } else if (player
        .getBukkit()
        .hasPermission(Permissions.VANISH)) { // Player is not vanished, but has permission to

      // Automatic vanish if player logs in via a "vanish" subdomain
      String domain = loginSubdomains.getIfPresent(player.getId());
      if (domain != null) {
        loginSubdomains.invalidate(player.getId());
        tempVanish.add(player.getId());
        setVanished(player, true, true);
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onQuit(PlayerQuitEvent event) {
    MatchPlayer player = matchManager.getPlayer(event.getPlayer());
    // If player is vanished & joined via "vanish" subdomain. Remove vanish status on quit
    if (isVanished(player.getId()) && tempVanish.contains(player.getId())) {
      setVanished(player, false, true);
      // Temporary vanish status is removed before quit,
      // so prevent regular quit msg and forces a staff only broadcast
      event.setQuitMessage(null);
      PGMListener.announceJoinOrLeave(player, false, true, true);
    }
  }

  @EventHandler
  public void onUnvanish(PlayerVanishEvent event) {
    // If player joined via "vanish" subdomain, but unvanishes while online
    // stop tracking them for auto-vanish removal
    if (!event.isVanished()) {
      tempVanish.remove(event.getPlayer().getId());
    }
  }

  @EventHandler
  public void checkMatchPlayer(MatchPlayerAddEvent event) {
    event.getPlayer().setVanished(isVanished(event.getPlayer().getId()));
  }

  private boolean isVanishSubdomain(String address) {
    return address.startsWith("staff.");
  }

  private void sendHotbarVanish(MatchPlayer player, boolean flashColor) {
    Component warning =
        TextComponent.of(" \u26a0 ", flashColor ? TextColor.YELLOW : TextColor.GOLD);
    Component vanish =
        TranslatableComponent.of("vanish.hotbar", TextColor.RED, TextDecoration.BOLD);
    Component message =
        TextComponent.builder().append(warning).append(vanish).append(warning).build();
    player.showHotbar(message);
  }

  public static class VanishedList implements List<Player> {
    private final List<EntityPlayer> reference;
    private final java.util.function.Predicate<? super EntityPlayer> filter;
    private final Function<? super EntityPlayer, ? extends Player> map;

    public VanishedList(List<EntityPlayer> reference, List<UUID> vanished) {
      this.reference = reference;
      this.filter = (e) -> !vanished.contains(e.getUniqueID());
      this.map = EntityPlayer::getBukkitEntity;
    }

    public List<Player> getOnlinePlayers() {
      return reference.stream().filter(filter).map(map).collect(Collectors.toList());
    }

    @Override
    public int size() {
      return getOnlinePlayers().size();
    }

    @Override
    public boolean isEmpty() {
      return getOnlinePlayers().isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return getOnlinePlayers().contains(o);
    }

    @NotNull
    @Override
    public Iterator<Player> iterator() {
      return getOnlinePlayers().iterator();
    }

    @NotNull
    @Override
    public Object[] toArray() {
      return getOnlinePlayers().toArray();
    }

    @NotNull
    @Override
    public <T> T[] toArray(@NotNull T[] a) {
      return getOnlinePlayers().toArray(a);
    }

    @Override
    public boolean add(Player player) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> c) {
      return getOnlinePlayers().containsAll(c);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Player> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, @NotNull Collection<? extends Player> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Player get(int index) {
      return getOnlinePlayers().get(index);
    }

    @Override
    public Player set(int index, Player element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, Player element) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Player remove(int index) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
      return getOnlinePlayers().indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
      return getOnlinePlayers().lastIndexOf(o);
    }

    @NotNull
    @Override
    public ListIterator<Player> listIterator() {
      return getOnlinePlayers().listIterator();
    }

    @NotNull
    @Override
    public ListIterator<Player> listIterator(int index) {
      return getOnlinePlayers().listIterator(index);
    }

    @NotNull
    @Override
    public List<Player> subList(int fromIndex, int toIndex) {
      return getOnlinePlayers().subList(fromIndex, toIndex);
    }
  }
}
