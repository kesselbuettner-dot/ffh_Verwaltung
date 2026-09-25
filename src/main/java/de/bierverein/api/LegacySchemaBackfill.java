package de.bierverein.api;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fills defaults after Hibernate has added nullable columns to an existing database. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacySchemaBackfill implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public LegacySchemaBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.update("update devices set operational_status = 'OK' where operational_status is null");
        jdbc.update("update devices set inspection_required = false where inspection_required is null");
        jdbc.update("update devices set placementx = 5 where placementx is null");
        jdbc.update("update devices set placementy = 8 where placementy is null");
        jdbc.update("update devices set placement_width = 38 where placement_width is null");
        jdbc.update("update devices set placement_height = 24 where placement_height is null");
        jdbc.update("update devices set placement_rotation = 0 where placement_rotation is null");
        jdbc.update("update devices set placement_layer = 10 where placement_layer is null");
        jdbc.update("update fire_vehicles set fire_relevant = true where fire_relevant is null");
        jdbc.update("update training_schedule_events set last_weekday_of_month = false where last_weekday_of_month is null");
        jdbc.update("update training_schedule_events set device_inspection = false where device_inspection is null");
    }
}
