package tc.oc.pgm.filters;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import tc.oc.pgm.api.filter.Filter;
import tc.oc.pgm.api.filter.FilterListener;
import tc.oc.pgm.api.filter.query.MatchQuery;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;
import tc.oc.pgm.api.match.MatchScope;
import tc.oc.pgm.api.match.event.MatchLoadEvent;
import tc.oc.pgm.api.player.event.MatchPlayerDeathEvent;
import tc.oc.pgm.events.ListenerScope;
import tc.oc.pgm.events.PlayerPartyChangeEvent;
import tc.oc.pgm.flag.event.FlagStateChangeEvent;
import tc.oc.pgm.goals.events.GoalCompleteEvent;
import tc.oc.pgm.util.TimeUtils;

@ListenerScope(MatchScope.LOADED)
public class FilterMatchModule implements MatchModule, Listener {

  private final Match match;
  private final SetMultimap<Filter, FilterListener> listeners = HashMultimap.create();
  private final Map<Filter, Filter.QueryResponse> responses = new HashMap<>();
  private final PriorityQueue<TimeFilter> timeFilterQueue = new PriorityQueue<>();

  public FilterMatchModule(Match match) {
    this.match = match;
  }

  /**
   * Listen for changes in the response by the given filter to a global {@link MatchQuery}. See
   * {@link FilterListener} for more details. Currently, only global queries are supported, and only
   * {@link TimeFilter}s, {@link GoalFilter}s, {@link FlagStateFilter}s, and {@link
   * CarryingFlagFilter}s are guaranteed to notify for all changes.
   */
  public void listen(Filter filter, FilterListener listener) {
    listeners.put(filter, listener);

    Filter.QueryResponse oldResponse = responses.get(filter);
    Filter.QueryResponse newResponse = filter.query(match.getQuery());
    responses.put(filter, newResponse);
    listener.filterQueryChanged(filter, match.getQuery(), oldResponse, newResponse);
  }

  @EventHandler
  public void onMatchLoad(MatchLoadEvent event) {
    checkAll(event);
  }

  @Override
  public void load() {}

  @Override
  public void enable() {
    checkTimeFilters();
  }

  void check(Filter filter, Event event) {
    if (event == null
        || !filter.isDynamic()
        || filter.getRelevantEvents().contains(event.getClass())) {
      Filter.QueryResponse newResponse = filter.query(match.getQuery());
      Filter.QueryResponse oldResponse = responses.put(filter, newResponse);

      if (oldResponse != newResponse) {
        for (FilterListener listener : listeners.get(filter)) {
          listener.filterQueryChanged(filter, match.getQuery(), oldResponse, newResponse);
        }
      }
    }
  }

  void checkAll(Event event) {
    for (Filter filter : listeners.keySet()) {
      check(filter, event);
    }
  }

  void checkTimeFilters() {
    if (!match.isRunning()) return;

    Duration now = match.getDuration();
    boolean removed = false;
    TimeFilter next;
    for (; ; ) {
      next = timeFilterQueue.peek();
      if (next == null || TimeUtils.isLongerThan(next.getTime(), now)) break;
      timeFilterQueue.remove();
      removed = true;
    }

    if (removed) checkAll(null);

    if (next != null) {
      match
          .getExecutor(MatchScope.RUNNING)
          .schedule(
              this::checkTimeFilters, next.getTime().minus(now).toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onGoalComplete(GoalCompleteEvent event) {
    checkAll(event);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerDeath(MatchPlayerDeathEvent event) {
    checkAll(event);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onPlayerPartyChange(PlayerPartyChangeEvent event) {
    checkAll(event);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onFlagChange(FlagStateChangeEvent event) {
    checkAll(event);
  }
}
