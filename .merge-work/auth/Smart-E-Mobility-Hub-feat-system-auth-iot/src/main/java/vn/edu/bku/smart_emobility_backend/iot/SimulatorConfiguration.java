package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorConfiguration implements SchedulingConfigurer {
    private final IoTSimulator simulator;
    private final SimulatorProperties properties;
    private final Clock clock;

    public SimulatorConfiguration(IoTSimulator simulator, SimulatorProperties properties, Clock clock) {
        this.simulator = simulator;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        if (!properties.devices().isEmpty() || !properties.stations().isEmpty()) {
            registrar.addFixedDelayTask(new FixedDelayTask(() -> simulator.tick(clock.instant()),
                    properties.tickInterval(), Duration.ZERO));
        }
    }
}
