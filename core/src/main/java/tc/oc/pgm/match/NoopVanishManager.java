package tc.oc.pgm.match;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.UUID;
import tc.oc.pgm.api.community.vanish.VanishManager;
import tc.oc.pgm.api.player.MatchPlayer;

public class NoopVanishManager implements VanishManager {
  private final Collection<MatchPlayer> vanished = Lists.newArrayList();

  @Override
  public boolean isVanished(UUID uuid) {
    return false;
  }

  @Override
  public Collection<MatchPlayer> getOnlineVanished() {
    return vanished;
  }

  @Override
  public boolean setVanished(MatchPlayer player, boolean vanish, boolean quiet) {
    return false;
  }

  @Override
  public void disable() {}
}
