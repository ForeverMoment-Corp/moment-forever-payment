package com.forvmom.MomentForeverPayment.config;
import com.forvmom.MomentForeverPayment.scheduler.OutboxCleanupJob;
import com.forvmom.MomentForeverPayment.scheduler.OutboxOutgoingCleanupJob;
import com.forvmom.MomentForeverPayment.scheduler.PaymentIncomingRetryJob;
import com.forvmom.MomentForeverPayment.scheduler.PaymentOutgoingRetryJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// we are using fire now policy, so even if many missed fires, we fire only once when server
// heal back, as once fire only is actually trigger all missed enrich process. so good for me
@Configuration
public class QuartzConfig {

    @Value("${payment.cleanup.interval-hours:24}")
    private int cleanupIntervalHours;

    @Value("${payment.retry.incoming.interval-minutes:2}")
    private int incomingRetryIntervalMinutes;

    @Value("${payment.retry.outgoing.interval-seconds:30}")
    private int outgoingRetryIntervalSeconds;

    @Bean
    public JobDetail outboxCleanupJobDetail() {
        return JobBuilder.newJob(OutboxCleanupJob.class)
                .withIdentity("outboxCleanupJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger outboxCleanupTrigger() {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInHours(cleanupIntervalHours)
                .repeatForever()
                .withMisfireHandlingInstructionFireNow(); // Important: on misfire, run immediately

        return TriggerBuilder.newTrigger()
                .forJob(outboxCleanupJobDetail())
                .withIdentity("outboxCleanupTrigger")
                .withDescription("Runs every 24 hours to clean up old published records")
                .withSchedule(scheduleBuilder)
                .build();
    }

    @Bean
    public JobDetail outboxRetryJobDetail() {
        return JobBuilder.newJob(PaymentIncomingRetryJob.class)
                .withIdentity("outboxRetryJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger outboxRetryTrigger() {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(incomingRetryIntervalMinutes)
                .repeatForever()
                .withMisfireHandlingInstructionFireNow();

        return TriggerBuilder.newTrigger()
                .forJob(outboxRetryJobDetail())
                .withIdentity("outboxRetryTrigger")
                .withDescription("Retries failed/unprocessed outbox records older than 5 minutes")
                .withSchedule(scheduleBuilder)
                .build();
    }

    /// //////////Outgoing events Job & Triggers////////////////////////////

    @Bean
    public JobDetail outgoingOutboxPublisherJobDetail() {
        return JobBuilder.newJob(PaymentOutgoingRetryJob.class)
                .withIdentity("outgoingOutboxPublisherJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger outgoingOutboxPublisherTrigger() {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(outgoingRetryIntervalSeconds)
                .repeatForever()
                .withMisfireHandlingInstructionFireNow();

        return TriggerBuilder.newTrigger()
                .forJob(outgoingOutboxPublisherJobDetail())
                .withIdentity("outgoingOutboxPublisherTrigger")
                .withSchedule(scheduleBuilder)
                .build();
    }

    @Bean
    public JobDetail outgoingOutboxCleanupJobDetail() {
        return JobBuilder.newJob(OutboxOutgoingCleanupJob.class)
                .withIdentity("outgoingOutboxCleanupJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger outgoingOutboxCleanupTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(outgoingOutboxCleanupJobDetail())
                .withIdentity("outgoingOutboxCleanupTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(cleanupIntervalHours)
                        .repeatForever()
                        .withMisfireHandlingInstructionFireNow())
                .build();
    }
}