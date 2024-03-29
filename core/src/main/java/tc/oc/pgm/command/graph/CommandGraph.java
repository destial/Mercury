package tc.oc.pgm.command.graph;

import app.ashcon.intake.argument.CommandArgs;
import app.ashcon.intake.bukkit.graph.BasicBukkitCommandGraph;
import app.ashcon.intake.fluent.DispatcherNode;
import app.ashcon.intake.parametric.AbstractModule;
import app.ashcon.intake.parametric.Module;
import app.ashcon.intake.parametric.Provider;
import app.ashcon.intake.parametric.provider.EnumProvider;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import tc.oc.pgm.api.Config;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.map.MapInfo;
import tc.oc.pgm.api.map.MapLibrary;
import tc.oc.pgm.api.map.MapOrder;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchManager;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.party.Party;
import tc.oc.pgm.api.party.VictoryCondition;
import tc.oc.pgm.api.player.GlobalStats;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.setting.SettingKey;
import tc.oc.pgm.api.setting.SettingValue;
import tc.oc.pgm.command.*;
import tc.oc.pgm.teams.TeamMatchModule;
import tc.oc.pgm.util.chat.Audience;

public class CommandGraph extends BasicBukkitCommandGraph {

  public CommandGraph(Module... modules) {
    super(
        ImmutableList.builder()
            .add(modules)
            .add(new CommandModule())
            .build()
            .toArray(new Module[modules.length + 1]));
    this.registerAll();
  }

  public void registerAll() {
    register(new AdminCommand());
    register(new CancelCommand());
    register(new ClassCommand());
    register(new CycleCommand());
    register(new FinishCommand());
    register(new FreeForAllCommand(), "ffa", "players");
    register(new InventoryCommand());
    register(new JoinCommand());
    register(new ListCommand());
    register(new LobbyCommand());
    register(new MapCommand());
    register(new MapOrderCommand());
    register(new MapPoolCommand());
    register(new MatchCommand());
    register(new ModeCommand(), "mode", "modes");
    register(new ProximityCommand());
    register(new RestartCommand());
    register(new SettingCommand());
    register(new StartCommand());
    register(new StatsCommand());
    register(new TeamCommand(), "team");
    register(new TimeLimitCommand());
    register(new ScoreLimitCommand());
    register(new VotingCommand(), "vote", "votes");

    if (Bukkit.getPluginManager().getPlugin("Events") == null) {
      register(new CoinCommand());
      register(new EloCommand());
      register(new SudoCommand());
      register(new SuffixCommand());
      register(new EditKitCommand());
      register(new GlobalStatsCommand());
      // register(new PredictionCommand());
    }
  }

  public void register(Object command, String... aliases) {
    DispatcherNode node = getRootDispatcherNode();
    if (aliases.length > 0) {
      node = node.registerNode(aliases);
    }
    node.registerCommands(command);
  }

  static class CommandModule extends AbstractModule {

    @Override
    protected void configure() {
      bind(Duration.class, new DurationParser());
      bind(PGM.class, PGM::get);
      bind(Config.class, PGM::getConfiguration);
      bind(MatchManager.class, PGM::getMatchManager);
      bind(MapLibrary.class, PGM::getMapLibrary);
      bind(MapOrder.class, PGM::getMapOrder);
      bind(Audience.class, new AudienceProvider());
      bind(Match.class, new MatchProvider());
      bind(MatchPlayer.class, new MatchPlayerProvider());
      bind(MapInfo.class, new MapInfoParser());
      bind(Party.class, new PartyProvider());
      bind(TeamMatchModule.class, new TeamsProvider());
      bind(VictoryCondition.class, new VictoryConditionProvider());
      bind(SettingKey.class, new SettingKeyParser());
      bind(GlobalStats.Leaderboard.class, new LeaderboardProvider());
      bind(Competitor.class, new CompetitorProvider());
      bind(SettingValue.class, new EnumProvider<>(SettingValue.class));
      bind(String[].class, new StringsProvider());
    }

    private <T> void bind(Class<T> type, Provider<T> provider) {
      bind(type).toProvider(provider);
    }

    private <T> void bind(Class<T> type, Singleton<T> supplier) {
      bind(type, (Provider<T>) supplier);
    }

    private <T> void bind(Class<T> type, Function<PGM, T> supplier) {
      bind(type, () -> supplier.apply(PGM.get()));
    }

    @FunctionalInterface
    private interface Singleton<T> extends Provider<T>, Supplier<T> {
      @Override
      default T get(CommandArgs args, List<? extends Annotation> list) {
        return get();
      }

      @Override
      default boolean isProvided() {
        return true;
      }
    }
  }
}
