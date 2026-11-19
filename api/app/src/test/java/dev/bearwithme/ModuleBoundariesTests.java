package dev.bearwithme;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.repository.Repository;

/**
 * Module boundaries (spec, Learning roadmap): each module owns its tables and no module
 * touches another module's entities or repositories. Cross-module reads go through public services.
 *
 * <p>Limitation: native SQL / JPQL strings naming another module's table are not detected.
 */
@AnalyzeClasses(packages = ModuleBoundariesTests.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTests {

    static final String ROOT = "dev.bearwithme";

    private static final String[] FEATURE_MODULES = {
            ROOT + ".auth..", ROOT + ".card..", ROOT + ".review..", ROOT + ".dashboard..",
    };

    private static final DescribedPredicate<JavaClass> PERSISTENCE_TYPES = DescribedPredicate.describe(
            "JPA entities and Spring Data repositories",
            c -> c.isAnnotatedWith(Entity.class)
                    || c.isAnnotatedWith(Embeddable.class)
                    || c.isAnnotatedWith(MappedSuperclass.class)
                    || c.isAssignableTo(Repository.class));

    @ArchTest
    static final ArchRule persistence_types_are_owned_by_a_feature_module = classes()
            .that(PERSISTENCE_TYPES)
            .should().resideInAnyPackage(FEATURE_MODULES)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule persistence_types_are_private_to_their_module = classes()
            .that(PERSISTENCE_TYPES)
            .should(beAccessedOnlyFromOwnModule())
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule common_does_not_depend_on_feature_modules = noClasses()
            .that().resideInAPackage(ROOT + ".common..")
            .should().dependOnClassesThat().resideInAnyPackage(FEATURE_MODULES)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modules_are_free_of_cycles = slices()
            .matching(ROOT + ".(*)..")
            .should().beFreeOfCycles()
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> beAccessedOnlyFromOwnModule() {
        return new ArchCondition<>("be accessed only from their own module") {
            @Override
            public void check(JavaClass target, ConditionEvents events) {
                String owner = moduleOf(target);
                for (Dependency dependency : target.getDirectDependenciesToSelf()) {
                    JavaClass origin = dependency.getOriginClass();
                    if (!owner.equals(moduleOf(origin))) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** {@code dev.bearwithme.card.internal} → {@code card}; the root package → empty string. */
    static String moduleOf(JavaClass javaClass) {
        String pkg = javaClass.getPackageName();
        if (!pkg.startsWith(ROOT + ".")) {
            return "";
        }
        String rest = pkg.substring(ROOT.length() + 1);
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
