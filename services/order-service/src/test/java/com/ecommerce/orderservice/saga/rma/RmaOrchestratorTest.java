package com.ecommerce.orderservice.saga.rma;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.saga.refund.RefundOrchestrator;
import com.ecommerce.orderservice.saga.refund.RefundSagaState;
import com.ecommerce.orderservice.saga.refund.RefundSagaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RmaOrchestrator — Returns/RMA saga")
class RmaOrchestratorTest {

    @Mock private ReturnRepository returnRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RefundOrchestrator refundOrchestrator;
    @Mock private MockShippingClient shippingClient;
    @Mock private RmaEventPublisher eventPublisher;

    private Clock clock;
    private RmaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);
        orchestrator = new RmaOrchestrator(returnRepository, orderRepository,
            refundOrchestrator, shippingClient, eventPublisher, clock);

        lenient().when(returnRepository.save(any(Return.class)))
            .thenAnswer(inv -> {
                Return r = inv.getArgument(0);
                if (r.getId() == null) {
                    r.setId("rma-id-1");
                }
                return r;
            });
        lenient().when(returnRepository.findByOrderIdAndStatusIn(anyString(), anyList()))
            .thenReturn(List.of());
    }

    private Order deliveredOrder() {
        Order o = new Order();
        o.setId("order-1");
        o.setOrderNumber("ORD-1");
        o.setUserId("user-1");
        o.setStatus(OrderStatus.DELIVERED);
        o.setCreatedAt(LocalDateTime.now(clock).minusDays(5));
        return o;
    }

    // ---------- requestReturn ----------

    @Test
    @DisplayName("should_createReturnAndAdvanceToAwaitingShipment_when_orderEligible")
    void should_createReturnAndAdvanceToAwaitingShipment_when_orderEligible() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        when(shippingClient.generateReturnLabel(anyString(), eq("order-1")))
            .thenReturn("https://shipping.mock/labels/abc");

        Return rma = orchestrator.requestReturn("order-1", "user-1", "size", "u@x.com");

        assertThat(rma.getStatus()).isEqualTo(ReturnStatus.AWAITING_SHIPMENT);
        assertThat(rma.getRmaNumber()).startsWith("RMA-");
        assertThat(rma.getReturnLabelUrl()).isEqualTo("https://shipping.mock/labels/abc");
        assertThat(rma.getReason()).isEqualTo("size");
        verify(eventPublisher).publishRequested(any(RmaEvent.class));
    }

    @Test
    @DisplayName("should_rejectRequest_when_orderMissing")
    void should_rejectRequest_when_orderMissing() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orchestrator.requestReturn("order-1", "u", "r", "e"))
            .isInstanceOf(RmaException.class)
            .hasMessageContaining("Order not found");
        verify(returnRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_rejectRequest_when_orderNotDelivered")
    void should_rejectRequest_when_orderNotDelivered() {
        Order o = deliveredOrder();
        o.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> orchestrator.requestReturn("order-1", "u", "r", "e"))
            .isInstanceOf(RmaException.class)
            .hasMessageContaining("DELIVERED");
    }

    @Test
    @DisplayName("should_rejectRequest_when_returnWindowExpired")
    void should_rejectRequest_when_returnWindowExpired() {
        Order o = deliveredOrder();
        o.setCreatedAt(LocalDateTime.now(clock).minusDays(45));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> orchestrator.requestReturn("order-1", "u", "r", "e"))
            .isInstanceOf(RmaException.class)
            .hasMessageContaining("window");
    }

    @Test
    @DisplayName("should_rejectRequest_when_activeReturnExists")
    void should_rejectRequest_when_activeReturnExists() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        Return existing = Return.builder()
            .id("rma-9").orderId("order-1").status(ReturnStatus.AWAITING_SHIPMENT).build();
        when(returnRepository.findByOrderIdAndStatusIn(eq("order-1"), anyList()))
            .thenReturn(List.of(existing));

        assertThatThrownBy(() -> orchestrator.requestReturn("order-1", "u", "r", "e"))
            .isInstanceOf(RmaException.class)
            .hasMessageContaining("active return already exists");
    }

    // ---------- markReceived ----------

    @Test
    @DisplayName("should_markReceived_when_returnIsAwaitingShipment")
    void should_markReceived_when_returnIsAwaitingShipment() {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").orderId("order-1")
            .status(ReturnStatus.AWAITING_SHIPMENT).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));

        Return result = orchestrator.markReceived("rma-1");

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.RECEIVED);
        assertThat(result.getReceivedAt()).isNotNull();
        verify(eventPublisher).publishReceived(any(RmaEvent.class));
    }

    @Test
    @DisplayName("should_rejectReceive_when_returnNotInAwaitingShipment")
    void should_rejectReceive_when_returnNotInAwaitingShipment() {
        Return rma = Return.builder().id("rma-1").status(ReturnStatus.COMPLETED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));

        assertThatThrownBy(() -> orchestrator.markReceived("rma-1"))
            .isInstanceOf(RmaException.class);
    }

    // ---------- inspect APPROVED ----------

    @Test
    @DisplayName("should_completeRma_when_inspectApprovedAndRefundCompletes")
    void should_completeRma_when_inspectApprovedAndRefundCompletes() {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").orderId("order-1")
            .status(ReturnStatus.RECEIVED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        RefundSagaState refundState = RefundSagaState.builder()
            .id("refund-1").status(RefundSagaStatus.COMPLETED).build();
        when(refundOrchestrator.startRefund(eq("order-1"), anyString(), anyString()))
            .thenReturn(refundState);

        Return result = orchestrator.inspect("rma-1", "APPROVED", "OPENED", "ok", "u@x.com");

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.COMPLETED);
        assertThat(result.getOutcome()).isEqualTo("APPROVED");
        assertThat(result.getRefundSagaId()).isEqualTo("refund-1");
        assertThat(result.getInspectedAt()).isNotNull();
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(refundOrchestrator).startRefund(eq("order-1"), reasonCaptor.capture(), anyString());
        assertThat(reasonCaptor.getValue()).startsWith("RMA-");
        verify(eventPublisher).publishCompleted(any(RmaEvent.class));
    }

    @Test
    @DisplayName("should_markRmaFailed_when_refundSagaEndsFailed")
    void should_markRmaFailed_when_refundSagaEndsFailed() {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").orderId("order-1")
            .status(ReturnStatus.RECEIVED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        RefundSagaState refundState = RefundSagaState.builder()
            .id("refund-1")
            .status(RefundSagaStatus.FAILED)
            .failureReason("payment down")
            .build();
        when(refundOrchestrator.startRefund(eq("order-1"), anyString(), any()))
            .thenReturn(refundState);

        Return result = orchestrator.inspect("rma-1", "APPROVED", null, null, null);

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.FAILED);
        assertThat(result.getFailureReason()).contains("FAILED");
        assertThat(result.getRefundSagaId()).isEqualTo("refund-1");
        verify(eventPublisher, never()).publishCompleted(any());
    }

    @Test
    @DisplayName("should_markRmaFailed_when_refundOrchestratorThrows")
    void should_markRmaFailed_when_refundOrchestratorThrows() {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").orderId("order-1")
            .status(ReturnStatus.RECEIVED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        when(refundOrchestrator.startRefund(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        Return result = orchestrator.inspect("rma-1", "APPROVED", null, null, null);

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.FAILED);
        assertThat(result.getFailureReason()).contains("boom");
    }

    // ---------- inspect REJECTED ----------

    @Test
    @DisplayName("should_shipBackAndPublishRejected_when_inspectRejected")
    void should_shipBackAndPublishRejected_when_inspectRejected() {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").orderId("order-1")
            .status(ReturnStatus.RECEIVED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        when(shippingClient.shipBackToCustomer(anyString(), anyString())).thenReturn("MOCK-1");

        Return result = orchestrator.inspect("rma-1", "rejected", "DAMAGED", "scratched", "u@x.com");

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(result.getOutcome()).isEqualTo("REJECTED");
        assertThat(result.getCondition()).isEqualTo("DAMAGED");
        assertThat(result.getNotes()).isEqualTo("scratched");
        verify(shippingClient).shipBackToCustomer(eq("RMA-1"), eq("order-1"));
        verify(eventPublisher).publishRejected(any(RmaEvent.class));
        verify(refundOrchestrator, never()).startRefund(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should_rejectInspect_when_outcomeIsInvalid")
    void should_rejectInspect_when_outcomeIsInvalid() {
        Return rma = Return.builder().id("rma-1").status(ReturnStatus.RECEIVED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));

        assertThatThrownBy(() -> orchestrator.inspect("rma-1", "MAYBE", null, null, null))
            .isInstanceOf(RmaException.class)
            .hasMessageContaining("Invalid outcome");
    }

    @Test
    @DisplayName("should_rejectInspect_when_outcomeIsNull")
    void should_rejectInspect_when_outcomeIsNull() {
        Return rma = Return.builder().id("rma-1").status(ReturnStatus.RECEIVED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));

        assertThatThrownBy(() -> orchestrator.inspect("rma-1", null, null, null, null))
            .isInstanceOf(RmaException.class);
    }

    @Test
    @DisplayName("should_rejectInspect_when_returnInTerminalState")
    void should_rejectInspect_when_returnInTerminalState() {
        Return rma = Return.builder().id("rma-1").status(ReturnStatus.COMPLETED).build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));

        assertThatThrownBy(() -> orchestrator.inspect("rma-1", "APPROVED", null, null, null))
            .isInstanceOf(RmaException.class);
    }

    // ---------- resume ----------

    @Test
    @DisplayName("resume_should_replayLabelAndAdvance_when_statusIsRequested")
    void resume_should_replayLabelAndAdvance_when_statusIsRequested() {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").orderId("order-1")
            .status(ReturnStatus.REQUESTED).build();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        when(shippingClient.generateReturnLabel(anyString(), anyString())).thenReturn("https://x");

        Return result = orchestrator.resume(rma);

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.AWAITING_SHIPMENT);
        assertThat(result.getReturnLabelUrl()).isEqualTo("https://x");
        verify(eventPublisher).publishRequested(any());
    }

    @Test
    @DisplayName("resume_should_replayInspectionDispatch_when_statusIsInspectingApproved")
    void resume_should_replayInspectionDispatch_when_statusIsInspectingApproved() {
        Return rma = Return.builder()
            .id("rma-1").rmaNumber("RMA-1").orderId("order-1")
            .status(ReturnStatus.INSPECTING).outcome("APPROVED").build();
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(deliveredOrder()));
        RefundSagaState refundState = RefundSagaState.builder()
            .id("refund-1").status(RefundSagaStatus.COMPLETED).build();
        when(refundOrchestrator.startRefund(anyString(), anyString(), any()))
            .thenReturn(refundState);

        Return result = orchestrator.resume(rma);

        assertThat(result.getStatus()).isEqualTo(ReturnStatus.COMPLETED);
        verify(refundOrchestrator, times(1)).startRefund(anyString(), anyString(), any());
    }

    // ---------- look-ups ----------

    @Test
    @DisplayName("findById_should_delegateToRepository")
    void findById_should_delegateToRepository() {
        Return rma = Return.builder().id("rma-1").build();
        when(returnRepository.findById("rma-1")).thenReturn(Optional.of(rma));

        assertThat(orchestrator.findById("rma-1")).contains(rma);
    }

    @Test
    @DisplayName("findByUser_should_delegateToRepository")
    void findByUser_should_delegateToRepository() {
        when(returnRepository.findByUserId("user-1"))
            .thenReturn(List.of(Return.builder().id("a").build()));

        assertThat(orchestrator.findByUser("user-1")).hasSize(1);
    }
}
