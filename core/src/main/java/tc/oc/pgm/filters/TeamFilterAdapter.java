package tc.oc.pgm.filters;

import java.util.Optional;
import tc.oc.pgm.api.filter.query.MatchQuery;
import tc.oc.pgm.features.XMLFeatureReference;
import tc.oc.pgm.teams.TeamFactory;
import tc.oc.pgm.teams.TeamMatchModule;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class TeamFilterAdapter extends TypedFilter<MatchQuery> {

  private final Optional<XMLFeatureReference<TeamFactory>> team;
  private final GoalFilter filter;

  public TeamFilterAdapter(Optional<XMLFeatureReference<TeamFactory>> team, GoalFilter filter) {
    this.team = team;
    this.filter = filter;
  }

  @Override
  public Class<? extends MatchQuery> getQueryType() {
    return MatchQuery.class;
  }

  @Override
  protected QueryResponse queryTyped(MatchQuery query) {
    return QueryResponse.fromBoolean(
        team.map(
                teamFactoryFeatureReference ->
                    query.moduleOptional(TeamMatchModule.class).map(tmm -> filter.query(query)))
            .isPresent());
  }
}
