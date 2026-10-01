package com.forvmom.MomentForeverPayment.scheduler;
import com.forvmom.MomentForeverPayment.service.OutboxCleanupService;
import com.forvmom.MomentForeverPayment.service.OutboxOutgoingCleanupService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class OutboxOutgoingCleanupJob implements Job {

    @Autowired
    private OutboxOutgoingCleanupService outboxOutgoingCleanupService;

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        outboxOutgoingCleanupService.cleanupPublishedRecords();
    }
}
