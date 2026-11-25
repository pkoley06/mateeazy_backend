package com.matcher.matchmate_backend.repository;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks every repository query against the entity model without a database
 * (the Testcontainers tests need Docker). Spring checks these only when the app
 * starts, so a typo here would otherwise first show up as a failed deploy.
 *
 * - @Query JPQL (and count queries) are parsed and type-checked by Hibernate.
 * - Derived methods (findByXAndY...) are parsed against the entity's properties.
 */
class RepositoryQueryValidationTest {

    private static final String BASE = "com.matcher.matchmate_backend";

    private static StandardServiceRegistry registry;
    private static SessionFactory sessionFactory;

    @BeforeAll
    static void bootHibernate() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        for (Class<?> entity : scan(BASE + ".model.entity", new AnnotationTypeFilter(Entity.class), false)) {
            sources.addAnnotatedClass(entity);
        }
        sessionFactory = sources.buildMetadata().buildSessionFactory();
    }

    @AfterAll
    static void close() {
        if (sessionFactory != null) sessionFactory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @TestFactory
    Stream<DynamicTest> everyRepositoryQueryIsValid() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> repo : scan(BASE + ".repository", new AssignableTypeFilter(JpaRepository.class), true)) {
            Class<?> domain = domainType(repo);
            for (Method m : repo.getDeclaredMethods()) {
                if (m.isDefault() || m.isSynthetic()) continue;
                String name = repo.getSimpleName() + "." + m.getName();
                Query q = m.getAnnotation(Query.class);
                if (q != null) {
                    if (q.nativeQuery()) continue;
                    tests.add(DynamicTest.dynamicTest(name, () -> checkJpql(q.value())));
                    if (!q.countQuery().isBlank()) {
                        tests.add(DynamicTest.dynamicTest(name + " (count)", () -> checkJpql(q.countQuery())));
                    }
                } else if (domain != null) {
                    tests.add(DynamicTest.dynamicTest(name, () -> new PartTree(m.getName(), domain)));
                }
            }
        }
        assertThat(tests).isNotEmpty();
        return tests.stream();
    }

    private static void checkJpql(String jpql) {
        try (EntityManager em = sessionFactory.createEntityManager()) {
            em.createQuery(jpql);
        }
    }

    private static Class<?> domainType(Class<?> repo) {
        for (Type t : repo.getGenericInterfaces()) {
            if (t instanceof ParameterizedType p && p.getRawType() == JpaRepository.class) {
                return (Class<?>) p.getActualTypeArguments()[0];
            }
        }
        return null;
    }

    private static List<Class<?>> scan(String pkg, org.springframework.core.type.filter.TypeFilter filter, boolean interfaces) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition def) {
                return interfaces ? def.getMetadata().isInterface() : def.getMetadata().isConcrete();
            }
        };
        scanner.addIncludeFilter(filter);
        List<Class<?>> out = new ArrayList<>();
        Set<BeanDefinition> found = scanner.findCandidateComponents(pkg);
        for (BeanDefinition def : found) {
            out.add(ClassUtils.resolveClassName(def.getBeanClassName(), RepositoryQueryValidationTest.class.getClassLoader()));
        }
        return out;
    }
}
