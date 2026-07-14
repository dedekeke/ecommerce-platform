package com.ecommerce.orderservice.saga.rma;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Re-drives RMA sagas stuck in transient states. Runs every 5 minutes
 * by default — same cadence as the refund recovery scheduler.
 *
 * <p>Note we exclude {@link ReturnStatus#AWAITING_SHIPMENT} on purpose:
 * customers may take days to actually ship the parcel. Those sagas are
 * not "stuck", they are waiting for an external event (warehouse scan).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RmaRecoveryScheduler {

    static final Duration STUCK_THRESHOLD = Duration.ofMinutes(5);

    static final List<ReturnStatus> RECOVERABLE_STATUSES = List.of(
        ReturnStatus.REQUESTED,
        ReturnStatus.NOTIFIED,
        ReturnStatus.INSPECTING
    );

    private final ReturnRepository returnRepository;
    private final RmaOrchestrator orchestrator;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${rma.saga.recovery-interval-ms:300000}")
    @SchedulerLock(name = "order-rmaRecovery",
        lockAtMostFor = "PT5M", lockAtLeastFor = "PT0S")
    public void recoverStuckRmas() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(STUCK_THRESHOLD);
        // JOIN FETCH the lines: this read tx commits before resume() reaches
        // handleApproved(), which touches rma.getLines() on a detached entity.
        List<Return> stuck = returnRepository.findByStatusInAndUpdatedAtBeforeWithLines(
            RECOVERABLE_STATUSES, cutoff);
        if (stuck.isEmpty()) {
            log.debug("RMA recovery: no stuck returns");
            return;
        }
        log.info("RMA recovery: resuming {} stuck return(s)", stuck.size());
        for (Return rma : stuck) {
            try {
                orchestrator.resume(rma);
            } catch (Exception e) {
                log.error("Recovery of RMA {} failed", rma.getRmaNumber(), e);
            }
        }
    }
}
