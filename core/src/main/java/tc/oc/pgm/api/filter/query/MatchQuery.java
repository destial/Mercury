package tc.oc.pgm.api.filter.query;

import java.util.Optional;
import tc.oc.pgm.api.match.Match;
import tc.oc.pgm.api.match.MatchModule;

public interface MatchQuery extends Query {
  Match getMatch();

  default <T extends MatchModule> T moduleRequire(Class<T> cls) {
    return getMatch().needModule(cls);
  }

  default <T extends MatchModule> Optional<T> moduleOptional(Class<T> cls) {
    return Optional.ofNullable(getMatch().getModule(cls));
  }
}
