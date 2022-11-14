package tc.oc.pgm.db;

import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.text.Component;
import org.bukkit.Bukkit;
import tc.oc.pgm.api.player.Username;
import tc.oc.pgm.util.UsernameResolver;
import tc.oc.pgm.util.named.NameStyle;
import tc.oc.pgm.util.text.types.PlayerComponent;

class UsernameImpl implements Username {

  private UUID id;
  private String name;

  UsernameImpl(@Nullable UUID id, @Nullable String name) {
    this.id = id;
    setName(name);
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Nullable
  @Override
  public String getNameLegacy() {
    return name;
  }

  @Override
  public Component getName(NameStyle style) {
    if (name != null) {
      return PlayerComponent.of(Bukkit.getPlayer(id), name, style);
    }
    return PlayerComponent.UNKNOWN;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  @Override
  public void setName(@Nullable String name) {
    if (name == null) {
      UsernameResolver.resolve(id, this::setName);
    } else {
      this.name = name;
    }
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Username)) return false;
    return getId().equals(((Username) o).getId());
  }

  @Override
  public String toString() {
    return name == null ? id.toString() : name;
  }
}
