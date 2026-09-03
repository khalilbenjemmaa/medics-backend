package com.reemamiri.practice;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for tests that need a real database.
 *
 * A genuine PostgreSQL server, started from a bundled binary. That
 * matters here more than usual: the double-booking guarantee is a GiST
 * exclusion constraint, and Flyway, tstzrange and btree_gist all have
 * to behave exactly as they will in production. An in-memory database
 * would quietly skip the one thing most worth testing.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(refresh = AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD)
public abstract class AbstractIntegrationTest {
}
