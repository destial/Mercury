package tc.oc.pgm.util.nms;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.server.v1_8_R3.*;
import net.minecraft.server.v1_8_R3.WorldBorder;
import org.bukkit.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.*;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_8_R3.inventory.CraftMetaBook;
import org.bukkit.craftbukkit.v1_8_R3.scoreboard.CraftTeam;
import org.bukkit.craftbukkit.v1_8_R3.util.CraftMagicNumbers;
import org.bukkit.craftbukkit.v1_8_R3.util.Skins;
import org.bukkit.entity.*;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.NameTagVisibility;
import org.bukkit.util.Vector;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.util.reflect.ReflectionUtils;

public interface NMSHacks {

  AtomicInteger ENTITY_IDS = new AtomicInteger(Integer.MAX_VALUE);

  static EntityTrackerEntry getTrackerEntry(net.minecraft.server.v1_8_R3.Entity nms) {
    return ((WorldServer) nms.getWorld()).getTracker().trackedEntities.get(nms.getId());
  }

  static EntityTrackerEntry getTrackerEntry(Entity entity) {
    return getTrackerEntry(((CraftEntity) entity).getHandle());
  }

  static void sendPacket(Object packet) {
    for (Player pl : Bukkit.getOnlinePlayers()) {
      sendPacket(pl, packet);
    }
  }

  static void sendPacket(Object packet, Match match) {
    for (MatchPlayer pl : match.getPlayers()) {
      sendPacket(pl.getBukkit(), packet);
    }
  }

  static void sendPacket(Player bukkitPlayer, Object packet) {
    if (bukkitPlayer.isOnline()) {
      EntityPlayer nmsPlayer = ((CraftPlayer) bukkitPlayer).getHandle();
      nmsPlayer.playerConnection.sendPacket((Packet<?>) packet);
    }
  }

  static void sendPacketToViewers(Entity entity, Object packet) {
    EntityTrackerEntry entry = getTrackerEntry(entity);
    for (EntityPlayer viewer : entry.trackedPlayers) {
      viewer.playerConnection.sendPacket((Packet<?>) packet);
    }
  }

  static PacketPlayOutPlayerInfo.PlayerInfoData playerListPacketData(
      PacketPlayOutPlayerInfo packet,
      UUID uuid,
      String name,
      GameMode gamemode,
      int ping,
      @Nullable Skin skin,
      @Nullable BaseComponent... displayName) {
    GameProfile profile = new GameProfile(uuid, name);
    if (skin != null) {
      for (Map.Entry<String, Collection<Property>> entry :
          Skins.toProperties(skin).asMap().entrySet()) {
        profile.getProperties().putAll(entry.getKey(), entry.getValue());
      }
    }
    PacketPlayOutPlayerInfo.PlayerInfoData data =
        packet.constructData(
            profile,
            ping,
            gamemode == null ? null : WorldSettings.EnumGamemode.getById(gamemode.getValue()),
            null); // ELECTROID
    data.displayName = displayName == null || displayName.length == 0 ? null : displayName;
    return data;
  }

  static PacketPlayOutPlayerInfo.PlayerInfoData playerListPacketData(
      PacketPlayOutPlayerInfo packet, UUID uuid, BaseComponent... displayName) {
    return playerListPacketData(
        packet, uuid, "|" + uuid.toString().substring(0, 15), null, 0, null, displayName);
  }

  static PacketPlayOutPlayerInfo.PlayerInfoData playerListPacketData(
      PacketPlayOutPlayerInfo packet, UUID uuid) {
    return playerListPacketData(packet, uuid, null, null, 0, null);
  }

  static PacketPlayOutPlayerInfo.PlayerInfoData playerListPacketData(
      PacketPlayOutPlayerInfo packet, UUID uuid, int ping) {
    return playerListPacketData(packet, uuid, uuid.toString().substring(0, 16), null, ping, null);
  }

  /**
   * Removes all players from the tab for the viewer and re-adds them
   *
   * @param viewer The viewer to send the packets to
   */
  static void removeAndAddAllTabPlayers(Player viewer) {
    List<EntityPlayer> players = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (viewer.canSee(player) || player == viewer)
        players.add(((CraftPlayer) player).getHandle());
    }

    sendPacket(
        viewer,
        new PacketPlayOutPlayerInfo(
            PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, players));
    sendPacket(
        viewer,
        new PacketPlayOutPlayerInfo(
            PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, players));
  }

  static Packet teamPacket(
      int operation,
      String name,
      String displayName,
      String prefix,
      String suffix,
      boolean friendlyFire,
      boolean seeFriendlyInvisibles,
      NameTagVisibility nameTagVisibility,
      Collection<String> players) {

    PacketPlayOutScoreboardTeam packet = new PacketPlayOutScoreboardTeam();
    packet.a = name;
    packet.b = displayName;
    packet.c = prefix;
    packet.d = suffix;
    packet.e = nameTagVisibility == null ? null : CraftTeam.bukkitToNotch(nameTagVisibility).e;
    // packet.f = color
    packet.g = players;
    packet.h = operation;
    if (friendlyFire) {
      packet.i |= 1;
    }
    if (seeFriendlyInvisibles) {
      packet.i |= 2;
    }
    return packet;
  }

  static Packet teamCreatePacket(
      String name,
      String displayName,
      String prefix,
      String suffix,
      boolean friendlyFire,
      boolean seeFriendlyInvisibles,
      Collection<String> players) {
    return teamPacket(
        0,
        name,
        displayName,
        prefix,
        suffix,
        friendlyFire,
        seeFriendlyInvisibles,
        NameTagVisibility.ALWAYS,
        players);
  }

  static Packet teamRemovePacket(String name) {
    return teamPacket(1, name, null, null, null, false, false, null, Lists.<String>newArrayList());
  }

  static Packet teamUpdatePacket(
      String name,
      String displayName,
      String prefix,
      String suffix,
      boolean friendlyFire,
      boolean seeFriendlyInvisibles) {
    return teamPacket(
        2,
        name,
        displayName,
        prefix,
        suffix,
        friendlyFire,
        seeFriendlyInvisibles,
        NameTagVisibility.ALWAYS,
        Lists.newArrayList());
  }

  static Packet teamJoinPacket(String name, Collection<String> players) {
    return teamPacket(3, name, null, null, null, false, false, null, players);
  }

  static Packet teamLeavePacket(String name, Collection<String> players) {
    return teamPacket(4, name, null, null, null, false, false, null, players);
  }

  static int allocateEntityId() {
    return ENTITY_IDS.decrementAndGet();
  }

  static class EntityMetadata {
    public final DataWatcher dataWatcher;

    public EntityMetadata(DataWatcher watcher) {
      dataWatcher = watcher;
    }

    static EntityMetadata clone(DataWatcher original) {
      List<DataWatcher.WatchableObject> values = original.c();
      DataWatcher copy = new DataWatcher(null);
      for (DataWatcher.WatchableObject value : values) {
        copy.a(value.a(), value.b());
      }
      return new EntityMetadata(copy);
    }

    static EntityMetadata clone(Entity entity) {
      return clone(((CraftEntity) entity).getHandle().getDataWatcher());
    }

    @Override
    public EntityMetadata clone() {
      return clone(this.dataWatcher);
    }
  }

  static EntityMetadata createBossMetadata(String name, float health) {
    EntityMetadata data = createEntityMetadata();
    setEntityMetadata(data, (byte) 0x20, (short) 300);
    setLivingEntityMetadata(data, health, Color.BLACK, false, (byte) 0, name, true, true);
    return data;
  }

  static EntityMetadata createWitherMetadata(String name, float health) {
    EntityMetadata data = createBossMetadata(name, health);
    DataWatcher watcher = data.dataWatcher;
    watcher.a(20, 890); // Invulnerability countdown
    return data;
  }

  static void spawnWither(
      Player player, int entityId, Location location, String name, float health) {
    EntityMetadata data = createWitherMetadata(name, health);
    spawnLivingEntity(player, EntityType.WITHER, entityId, location, data);
  }

  static Packet destroyEntitiesPacket(int... entityIds) {
    return new PacketPlayOutEntityDestroy(entityIds);
  }

  static void destroyEntities(Player player, int... entityIds) {
    sendPacket(player, destroyEntitiesPacket(entityIds));
  }

  static void updateBoss(Player player, int entityId, String name, float health) {
    EntityMetadata data = createBossMetadata(name, health);
    sendPacket(player, new PacketPlayOutEntityMetadata(entityId, data.dataWatcher, true));
  }

  static Packet spawnPlayerPacket(int entityId, UUID uuid, Location location, Player player) {
    return spawnPlayerPacket(entityId, uuid, location, null, EntityMetadata.clone(player));
  }

  static Packet spawnPlayerPacket(
      int entityId,
      UUID uuid,
      Location location,
      org.bukkit.inventory.ItemStack heldItem,
      EntityMetadata metadata) {
    return new PacketPlayOutNamedEntitySpawn(
        entityId,
        uuid,
        location.getX(),
        location.getY(),
        location.getZ(),
        (byte) location.getYaw(),
        (byte) location.getPitch(),
        CraftItemStack.asNMSCopy(heldItem),
        metadata.dataWatcher);
  }

  static void spawnLivingEntity(
      Player player, EntityType type, int entityId, Location location, EntityMetadata metadata) {
    sendPacket(player, spawnLivingEntityPacket(type, entityId, location, metadata));
  }

  @SuppressWarnings("deprecation")
  static Packet spawnLivingEntityPacket(
      EntityType type, int entityId, Location location, EntityMetadata metadata) {
    return new PacketPlayOutSpawnEntityLiving(
        entityId,
        (byte) type.getTypeId(),
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch(),
        location.getPitch(),
        0,
        0,
        0,
        metadata.dataWatcher);
  }

  static void entityAttach(Player player, int entityID, int vehicleID, boolean leash) {
    sendPacket(player, new PacketPlayOutAttachEntity(entityID, vehicleID, leash));
  }

  static Packet teleportEntityPacket(int entityId, Location location) {
    return new PacketPlayOutEntityTeleport(
        entityId, // Entity ID
        (int) (location.getX() * 32), // World X * 32
        (int) (location.getY() * 32), // World Y * 32
        (int) (location.getZ() * 32), // World Z * 32
        (byte) (location.getYaw() * 256 / 360), // Yaw
        (byte) (location.getPitch() * 256 / 360), // Pitch
        true); // On Ground + Height Correction
  }

  static void teleportEntity(Player player, int entityId, Location location) {
    sendPacket(player, teleportEntityPacket(entityId, location));
  }

  static Packet entityMetadataPacket(int entityId, Entity entity, boolean complete) {
    return new PacketPlayOutEntityMetadata(
        entityId,
        ((CraftEntity) entity).getHandle().getDataWatcher(),
        complete); // true = all values, false = only dirty values
  }

  static EntityMetadata createEntityMetadata() {
    return new EntityMetadata(new DataWatcher(null));
  }

  static void setEntityMetadata(EntityMetadata metadata, byte flags, short air) {
    DataWatcher dataWatcher = metadata.dataWatcher;
    dataWatcher.a(0, flags);
    dataWatcher.a(1, air);
  }

  static void setEntityMetadata(
      EntityMetadata metadata,
      boolean onFire,
      boolean crouched,
      boolean sprinting,
      boolean eatingOrBlocking,
      boolean invisible,
      short air) {
    int flags = 0;
    if (onFire) flags |= 0x01;
    if (crouched) flags |= 0x02;
    if (sprinting) flags |= 0x08;
    if (eatingOrBlocking) flags |= 0x10;
    if (invisible) flags |= 0x20;
    setEntityMetadata(metadata, (byte) flags, air);
  }

  static void setLivingEntityMetadata(
      EntityMetadata metadata,
      float health,
      Color potionEffectColor,
      boolean potionEffectAmbient,
      byte arrowCount,
      String name,
      boolean showName,
      boolean noAI) {
    DataWatcher dataWatcher = metadata.dataWatcher;
    dataWatcher.a(6, health);
    dataWatcher.a(7, potionEffectColor.asRGB());
    dataWatcher.a(8, (byte) (potionEffectAmbient ? 1 : 0));
    dataWatcher.a(9, arrowCount);
    dataWatcher.a(2, name);
    dataWatcher.a(3, (byte) (showName ? 1 : 0));
    dataWatcher.a(15, (byte) (noAI ? 1 : 0));
  }

  static void setArmorStandFlags(
      EntityMetadata metadata, boolean small, boolean gravity, boolean arms, boolean baseplate) {
    int flags = 0;
    if (small) flags |= 0x01;
    if (gravity) flags |= 0x02;
    if (arms) flags |= 0x04;
    if (baseplate) flags |= 0x08;
    metadata.dataWatcher.a(10, (byte) flags);
  }

  /**
   * Test if the given tool is capable of "efficiently" mining the given block.
   *
   * <p>Derived from CraftBlock.itemCausesDrops()
   */
  static boolean canMineBlock(MaterialData blockMaterial, org.bukkit.inventory.ItemStack tool) {
    if (!blockMaterial.getItemType().isBlock()) {
      throw new IllegalArgumentException("Material '" + blockMaterial + "' is not a block");
    }

    net.minecraft.server.v1_8_R3.Block nmsBlock =
        CraftMagicNumbers.getBlock(blockMaterial.getItemType());
    net.minecraft.server.v1_8_R3.Item nmsTool =
        tool == null ? null : CraftMagicNumbers.getItem(tool.getType());

    return nmsBlock != null
        && (nmsBlock.getMaterial().isAlwaysDestroyable()
            || (nmsTool != null && nmsTool.canDestroySpecialBlock(nmsBlock)));
  }

  static long getMonotonicTime(World world) {
    return ((CraftWorld) world).getHandle().getTime();
  }

  static void sendMessage(Player player, BaseComponent[] message, int position) {
    PacketPlayOutChat packet = new PacketPlayOutChat(null, (byte) position);
    packet.components = message;
    sendPacket(player, packet);
  }

  static void showBorderWarning(Player player, boolean show) {
    WorldBorder border = new WorldBorder();
    border.setWarningDistance(show ? Integer.MAX_VALUE : 0);
    sendPacket(
        player,
        new PacketPlayOutWorldBorder(
            border, PacketPlayOutWorldBorder.EnumWorldBorderAction.SET_WARNING_BLOCKS));
  }

  static void playDeathAnimation(Player player) {
    EntityPlayer handle = ((CraftPlayer) player).getHandle();
    PacketPlayOutEntityMetadata packet =
        new PacketPlayOutEntityMetadata(handle.getId(), handle.getDataWatcher(), false);

    // Add/replace health to zero
    boolean replaced = false;
    DataWatcher.WatchableObject zeroHealth =
        new DataWatcher.WatchableObject(3, 6, 0f); // type 3 (float), index 6 (health)

    if (packet.b != null) {
      for (int i = 0; i < packet.b.size(); i++) {
        DataWatcher.WatchableObject wo = packet.b.get(i);
        if (wo.a() == 6) {
          packet.b.set(i, zeroHealth);
          replaced = true;
        }
      }
    }

    if (!replaced) {
      if (packet.b == null) {
        packet.b = Collections.singletonList(zeroHealth);
      } else {
        packet.b.add(zeroHealth);
      }
    }

    sendPacketToViewers(player, packet);
  }

  static org.bukkit.enchantments.Enchantment getEnchantment(String key) {
    Enchantment enchantment = Enchantment.getByName(key);
    return enchantment == null ? null : org.bukkit.enchantments.Enchantment.getById(enchantment.id);
  }

  static PotionEffectType getPotionEffectType(String key) {
    MobEffectList nms = MobEffectList.b(key);
    return nms == null ? null : PotionEffectType.getById(nms.id);
  }

  static Set<MaterialData> getBlockStates(Material material) {
    ImmutableSet.Builder<MaterialData> materials = ImmutableSet.builder();
    Block nmsBlock = CraftMagicNumbers.getBlock(material);
    List<IBlockData> states =
        ReflectionUtils.readField(BlockStateList.class, nmsBlock.P(), List.class, "e");
    if (states != null) {
      for (IBlockData state : states) {
        int data = nmsBlock.toLegacyData(state);
        materials.add(material.getNewData((byte) data));
      }
    }
    return materials.build();
  }

  static void setAbsorption(LivingEntity entity, double health) {
    ((CraftLivingEntity) entity).getHandle().setAbsorptionHearts((float) health);
  }

  static double getAbsorption(LivingEntity entity) {
    return ((CraftLivingEntity) entity).getHandle().getAbsorptionHearts();
  }

  static void setBookPages(BookMeta book, BaseComponent... pages) {
    for (BaseComponent page : pages) {
      ((CraftMetaBook) book)
          .pages.add(IChatBaseComponent.ChatSerializer.a(ComponentSerializer.toString(page)));
    }
  }

  static void openBook(org.bukkit.inventory.ItemStack book, Player player) {
    ((CraftPlayer) player).getHandle().openBook(CraftItemStack.asNMSCopy(book));
  }

  static int getProtocolVersion(Player player) {
    return ((CraftPlayer) player).getHandle().playerConnection.networkManager.protocolVersion;
  }

  static int getPing(Player player) {
    return ((CraftPlayer) player).getHandle().ping;
  }

  static Packet setPassengerPacket(int riderId, int vehicleId) {
    return new PacketPlayOutAttachEntity(riderId, vehicleId, false);
  }

  static Packet entityEquipmentPacket(
      int entityId, int slot, org.bukkit.inventory.ItemStack armor) {
    return new PacketPlayOutEntityEquipment(entityId, slot, CraftItemStack.asNMSCopy(armor));
  }

  interface FakeEntity {
    int entityId();

    Entity entity();

    void spawn(Player viewer, Location location, org.bukkit.util.Vector velocity);

    default void spawn(Player viewer, Location location) {
      spawn(viewer, location, new org.bukkit.util.Vector(0, 0, 0));
    }

    default void destroy(Player viewer) {
      sendPacket(viewer, destroyEntitiesPacket(entityId()));
    }

    default void teleport(Player viewer, Location location) {
      sendPacket(viewer, teleportEntityPacket(entityId(), location));
    }

    default void ride(Player viewer, Entity rider) {
      sendPacket(viewer, setPassengerPacket(rider.getEntityId(), entityId()));
    }

    default void mount(Player viewer, Entity vehicle) {
      sendPacket(viewer, setPassengerPacket(entityId(), vehicle.getEntityId()));
    }

    default void wear(Player viewer, int slot, org.bukkit.inventory.ItemStack item) {
      sendPacket(viewer, entityEquipmentPacket(entityId(), slot, item));
    }
  }

  abstract class FakeEntityImpl<T extends net.minecraft.server.v1_8_R3.Entity>
      implements FakeEntity {
    protected final T entity;

    protected FakeEntityImpl(T entity) {
      this.entity = entity;
    }

    @Override
    public Entity entity() {
      return entity.getBukkitEntity();
    }

    @Override
    public int entityId() {
      return entity.getId();
    }
  }

  class FakeLivingEntity<T extends EntityLiving> extends FakeEntityImpl<T> {

    protected FakeLivingEntity(T entity) {
      super(entity);
    }

    @Override
    public void spawn(Player viewer, Location location, Vector velocity) {
      entity.setPositionRotation(
          location.getX(),
          location.getY(),
          location.getZ(),
          location.getYaw(),
          location.getPitch());
      entity.motX = velocity.getX();
      entity.motY = velocity.getY();
      entity.motZ = velocity.getZ();
      sendPacket(viewer, spawnPacket());
    }

    protected Packet<?> spawnPacket() {
      return new PacketPlayOutSpawnEntityLiving(entity);
    }
  }

  class FakeZombie extends FakeLivingEntity<EntityZombie> {

    public FakeZombie(World world, boolean invisible) {
      this(world, invisible, false);
    }

    public FakeZombie(World world, boolean invisible, boolean baby) {
      super(new EntityZombie(((CraftWorld) world).getHandle()));

      entity.setInvisible(invisible);
      entity.setBaby(baby);
      NBTTagCompound tag = entity.getNBTTag();
      if (tag == null) {
        tag = new NBTTagCompound();
      }
      entity.c(tag);
      tag.setBoolean("Silent", true);
      tag.setBoolean("Invulnerable", true);
      tag.setBoolean("NoGravity", true);
      tag.setBoolean("NoAI", true);
      entity.f(tag);
    }
  }
}
