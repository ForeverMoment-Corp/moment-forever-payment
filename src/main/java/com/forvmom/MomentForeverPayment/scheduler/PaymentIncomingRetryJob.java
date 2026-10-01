package com.forvmom.MomentForeverPayment.scheduler;

import com.forvmom.MomentForeverPayment.service.PaymentIncomingRetryService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.DisallowConcurrentExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
public class PaymentIncomingRetryJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(PaymentIncomingRetryJob.class);

    @Autowired
    private PaymentIncomingRetryService retryService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("Starting payment incoming retry job");
            retryService.retryStuckAndFailedRecords();
            log.info("Completed payment incoming retry job");
        } catch (Exception e) {
            log.error("Payment incoming retry job failed", e);
            throw new JobExecutionException("Retry job failed", e);
        }
    }
}