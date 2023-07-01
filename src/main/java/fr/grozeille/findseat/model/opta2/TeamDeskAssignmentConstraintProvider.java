package fr.grozeille.findseat.model.opta2;

import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import org.optaplanner.core.api.score.stream.Constraint;
import org.optaplanner.core.api.score.stream.ConstraintFactory;
import org.optaplanner.core.api.score.stream.ConstraintProvider;
import org.optaplanner.core.api.score.stream.Joiners;

import java.util.function.BiPredicate;
import java.util.function.Function;

@Slf4j
public class TeamDeskAssignmentConstraintProvider implements ConstraintProvider {
    public static class NotEqualBiPredicate<A, R> implements BiPredicate<A, A> {

        private final Function<A, R> mapping;

        public NotEqualBiPredicate(Function<A, R> mapping) {
            this.mapping = mapping;
        }

        @Override
        public boolean test(A a, A b) {
            if(a == null) {
                return false;
            }
            return !this.mapping.apply(a).equals(this.mapping.apply(b));
        }
    }

    public static class NotEqualStringBiPredicate<A> implements BiPredicate<A, A> {

        private final Function<A, String> mapping;

        public NotEqualStringBiPredicate(Function<A, String> mapping) {
            this.mapping = mapping;
        }

        @Override
        public boolean test(A a, A b) {
            if(a == null) {
                return false;
            }
            return !this.mapping.apply(a).equalsIgnoreCase(this.mapping.apply(b));
        }
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                teamsMustNotOverlapConstraint(constraintFactory),
                mandatoryPeopleMustBeAffectedConstraint(constraintFactory),
                deskShouldNotBeEmptyConstraint(constraintFactory),
                teamShouldNotBeSplitInOpenSpaceConstraint(constraintFactory),
                teamShouldNotBeSplitInDeskGroupConstraint(constraintFactory),
        };
    }

    private Constraint deskShouldNotBeEmptyConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingNullVars(Team.class)
                .filter(t -> t.getDesk() == null)
                .map(Team::getSize)
                .penalize(HardMediumSoftScore.ONE_MEDIUM, s -> s)
                .asConstraint("Should try to have the maximum of people on the floor");
    }

    private Constraint teamShouldNotBeSplitInDeskGroupConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Team.class)
                .filter(t -> t.getIsMandatory() && t.getDesk() == null ||
                        t.getIsMandatory() && t.getSize() + t.getDesk().getId() > t.getDesk().getEndOfRow())
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint("Team should not be split in 2 desk rows");
    }

    private Constraint teamShouldNotBeSplitInOpenSpaceConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingNullVars(Team.class)
                .filter(t -> t.getIsMandatory() && t.getDesk() == null ||
                        t.getIsMandatory() && t.getSize() + t.getDesk().getId() > t.getDesk().getEndOfDeskGroup())
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("Team should not be split in 2 desk groups");
    }

    private Constraint mandatoryPeopleMustBeAffectedConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingNullVars(Team.class)
                .filter(t -> t.getIsMandatory() && t.getDesk() == null ||
                        t.getIsMandatory() && t.getSize() + t.getDesk().getId() > t.getDesk().getEndOfOpenSpace())
                .penalize(HardMediumSoftScore.ONE_HARD, Team::getSize)
                .asConstraint("All mandatory people must have a desk");
    }

    private Constraint teamsMustNotOverlapConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(Team.class)
                .join(Team.class,
                        Joiners.filtering(new NotEqualBiPredicate<>(Team::getId)),
                        Joiners.filtering((a, b) -> {
                            long startIndexA = a.getDesk().getId();
                            long endIndexA = startIndexA + a.getSize()-1;

                            long startIndexB = b.getDesk().getId();
                            long endIndexB = startIndexB + b.getSize()-1;

                            return startIndexA >= startIndexB && startIndexA <= endIndexB ||
                                    endIndexA >= startIndexB && endIndexA <= endIndexB ||
                                    startIndexB >= startIndexA && startIndexB <= endIndexA ||
                                    endIndexB >= startIndexA && endIndexB <= endIndexA;
                        }))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Teams must not overlap");

    }

}
