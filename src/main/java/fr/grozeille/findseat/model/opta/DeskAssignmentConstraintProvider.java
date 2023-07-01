package fr.grozeille.findseat.model.opta;

import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import org.optaplanner.core.api.score.stream.*;

import java.util.function.BiPredicate;
import java.util.function.Function;

@Slf4j
public class DeskAssignmentConstraintProvider implements ConstraintProvider {
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
                peopleConflictConstraint(constraintFactory),
                mandatoryPeopleConstraint(constraintFactory),
                teamNotSplitInOpenSpaceConstraint(constraintFactory),
                //peopleWithMonitoringScreenConstraint(constraintFactory),
                teamNotSplitInDeskGroupConstraint(constraintFactory),
                teamNotSplitInDeskRowConstraint(constraintFactory)
        };
    }

    private Constraint teamNotSplitInDeskRowConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(DeskAssignment.class)
                .join(DeskAssignment.class,
                        Joiners.filtering(new NotEqualBiPredicate<>(DeskAssignment::getId)),
                        Joiners.filtering(new NotEqualStringBiPredicate<>(d -> d.getPeople().getTeam())),
                        Joiners.filtering((a, b) -> {
                            // return only people next to each other
                            return a.getId().equals(b.getId()-1) ||
                                    a.getId().equals(b.getId()+1);
                        }))
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint("People in the same desk row of the same team should be next to each other");
    }

    private Constraint teamNotSplitInDeskGroupConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(DeskAssignment.class)
                .join(DeskAssignment.class,
                        Joiners.equal(d -> d.getPeople().getTeam()),
                        Joiners.equal(DeskAssignment::getDeskGroup),
                        Joiners.filtering(new NotEqualBiPredicate<>(DeskAssignment::getId)),
                        Joiners.filtering(new NotEqualStringBiPredicate<>(DeskAssignment::getRow)))
                .penalize(HardMediumSoftScore.ONE_MEDIUM)
                .asConstraint("People of the same team should not be split into 2 desk rows");
    }

    private Constraint teamNotSplitInOpenSpaceConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(DeskAssignment.class)
                .join(DeskAssignment.class,
                        Joiners.equal(d -> d.getPeople().getTeam()),
                        Joiners.filtering(new NotEqualBiPredicate<>(DeskAssignment::getId)),
                        Joiners.filtering(new NotEqualStringBiPredicate<>(DeskAssignment::getDeskGroup)))
                .penalize(HardMediumSoftScore.ofMedium(100))
                .asConstraint("People of the same team should not be split into 2 desk group");
    }

    private Constraint peopleWithMonitoringScreenConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(DeskAssignment.class)
                .filter(d -> d.getPeople().getWantMonitoringScreen() && d.getWithMonitoringScreens())
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("Some people need a monitoring screen");
    }

    private Constraint mandatoryPeopleConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(DeskAssignment.class)
                .filter(d -> d.getDoesNotExists() && d.getPeople().getIsMandatory())
                .penalize(HardMediumSoftScore.ofHard(10))
                .asConstraint("Need all mandatory people");
    }

    private Constraint peopleConflictConstraint(ConstraintFactory constraintFactory) {
        // a person can't be affected to 2 different desks

        return constraintFactory
                .forEach(DeskAssignment.class)
                .join(DeskAssignment.class,
                        Joiners.equal(DeskAssignment::getPeople),
                        Joiners.filtering(new NotEqualBiPredicate<>(DeskAssignment::getId)))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("People conflict");
    }
}
