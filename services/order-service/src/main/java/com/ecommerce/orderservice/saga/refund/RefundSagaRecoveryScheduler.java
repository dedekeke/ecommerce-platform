package com.ecommerce.orderservice.saga.refund;

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
 * Periodically picks up sagas that look stuck — IN_PROGRESS or COMPENSATING
 * with no recent update — and asks the orchestrator to resume them.
 * Step idempotency is what makes this safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundSagaRecoveryScheduler {

    static final Duration STUCK_THRESHOLD = Duration.ofMinutes(5);

    private final RefundSagaRepository sagaRepository;
    private final RefundOrchestrator orchestrator;
    private final Clock clock;

    // lockAtMostFor (10m) is deliberately 2x the 5-minute poll interval so the
    // lock always outlives a slow backlog run — if it expired mid-run another
    // instance would resume the same sagas, the double-compensation this lock
    // exists to prevent. If a holder crashes, a <=10m recovery gap is harmless
    // (recovery is itself a safety net). fixedDelay already spaces runs on one
    // instance, so no lockAtLeastFor refire guard is needed.
    @Scheduled(fixedDelayString = "${refund.saga.recovery-interval-ms:300000}")
    @SchedulerLock(name = "order-refundSagaRecovery", lockAtMostFor = "PT10M")
    public void recoverStuckSagas() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(STUCK_THRESHOLD);
        List<RefundSagaState> stuck = sagaRepository.findByStatusInAndUpdatedAtBefore(
            List.of(RefundSagaStatus.IN_PROGRESS, RefundSagaStatus.COMPENSATING),
            cutoff);

        if (stuck.isEmpty()) {
            log.debug("Refund recovery: no stuck sagas");
            return;
        }
        log.info("Refund recovery: resuming {} stuck saga(s)", stuck.size());
        for (RefundSagaState state : stuck) {
            try {
                orchestrator.resume(state);
            } catch (Exception e) {
                log.error("Recovery of saga {} failed", state.getId(), e);
            }
        }
    }
}
