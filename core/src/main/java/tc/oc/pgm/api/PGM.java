package tc.oc.pgm.api;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.bukkit.plugin.Plugin;
import tc.oc.pgm.api.community.achievement.AchievementManager;
import tc.oc.pgm.api.community.vanish.VanishManager;
import tc.oc.pgm.api.map.MapLibrary;
import tc.oc.pgm.api.map.MapOrder;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.namedecorations.NameDecorationRegistry;
import tc.oc.pgm.tablist.MatchTabManager;

/** PvP Game Manager (aka. PGM), the global {@link Plugin} to manage PvP games. */
public interface PGM extends Plugin {

  Config getConfiguration();

  Logger getGameLogger();

  Datastore getDatastore();

  MatchManager getMatchManager();

  @Nullable
  MatchTabManager getMatchTabManager();

  MapLibrary getMapLibrary();

  MapOrder getMapOrder();

  NameDecorationRegistry getNameDecorationRegistry();

  ScheduledExecutorService getExecutor();

  ScheduledExecutorService getAsyncExecutor();

  VanishManager getVanishManager();

  AchievementManager getAchievementManager();

  boolean isPAPIEnabled();

  AtomicReference<PGM> GLOBAL = new AtomicReference<>(null);

  static PGM set(PGM pgm) {
    try {
      get();
      throw new IllegalArgumentException("Mercury was already initialized!");
    } catch (IllegalStateException e) {
      GLOBAL.set(checkNotNull(pgm, "Mercury cannot be null!"));
    }
    return get();
  }

  static PGM get() {
    final PGM pgm = GLOBAL.get();
    if (pgm == null) {
      throw new IllegalStateException("Mercury is not yet enabled!");
    }
    return pgm;
  }
}
