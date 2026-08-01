package com.ecommerce.orderservice.saga.refund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Scheduled(fixedDelayString = "${refund.saga.recovery-interval-ms:300000}")
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
