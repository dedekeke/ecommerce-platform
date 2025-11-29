package com.ecommerce.orderservice.grpc;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.ecommerce.orderservice.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.grpc.proto.*;
import com.ecommerce.orderservice.service.OrderService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * gRPC service implementation for Order Service
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class OrderGrpcServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    private final OrderService orderService;
    private final OrderEventPublisher eventPublisher;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public void createOrder(CreateOrderRequest request, StreamObserver<CreateOrderResponse> responseObserver) {
        log.info("gRPC: CreateOrder request for user: {}", request.getUserId());

        try {
            // Convert request items to domain entities
            List<OrderItem> items = request.getItemsList().stream()
                .map(this::convertToOrderItem)
                .collect(Collectors.toList());

            // Convert address
            Address shippingAddress = convertToAddress(request.getShippingAddress());

            // Create order
            Order order = orderService.createOrder(
                request.getUserId(),
                items,
                shippingAddress,
                request.getPromotionCode().isEmpty() ? null : request.getPromotionCode()
            );

            // Publish event
            eventPublisher.publishOrderCreatedEvent(order);

            // Build response
            CreateOrderResponse response = CreateOrderResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Order created successfully")
                .setOrder(convertToOrderResponse(order))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC: Order created successfully: {}", order.getOrderNumber());

        } catch (Exception e) {
            log.error("gRPC: Error creating order", e);
            CreateOrderResponse response = CreateOrderResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to create order: " + e.getMessage())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getOrder(GetOrderRequest request, StreamObserver<GetOrderResponse> responseObserver) {
        log.info("gRPC: GetOrder request - orderId: {}, userId: {}", request.getOrderId(), request.getUserId());

        try {
            Order order = orderService.getOrder(request.getOrderId(), request.getUserId());

            GetOrderResponse response = GetOrderResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Order retrieved successfully")
                .setOrder(convertToOrderResponse(order))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (OrderNotFoundException e) {
            log.warn("gRPC: Order not found - {}", e.getMessage());
            GetOrderResponse response = GetOrderResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error retrieving order", e);
            GetOrderResponse response = GetOrderResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to retrieve order: " + e.getMessage())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateOrderStatus(UpdateOrderStatusRequest request, StreamObserver<UpdateOrderStatusResponse> responseObserver) {
        log.info("gRPC: UpdateOrderStatus request - orderId: {}, status: {}",
            request.getOrderId(), request.getStatus());

        try {
            OrderStatus newStatus = convertToOrderStatus(request.getStatus());
            Order order = orderService.updateOrderStatus(request.getOrderId(), newStatus);

            // Publish event
            eventPublisher.publishOrderUpdatedEvent(order);

            // Check if order is completed
            if (newStatus == OrderStatus.DELIVERED) {
                eventPublisher.publishOrderCompletedEvent(order);
            }

            UpdateOrderStatusResponse response = UpdateOrderStatusResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Order status updated successfully")
                .setOrder(convertToOrderResponse(order))
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InvalidOrderStatusTransitionException e) {
            log.warn("gRPC: Invalid status transition - {}", e.getMessage());
            UpdateOrderStatusResponse response = UpdateOrderStatusResponse.newBuilder()
                .setSuccess(false)
                .setMessage(e.getMessage())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error updating order status", e);
            UpdateOrderStatusResponse response = UpdateOrderStatusResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to update order status: " + e.getMessage())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void cancelOrder(CancelOrderRequest request, StreamObserver<CancelOrderResponse> responseObserver) {
        log.info("gRPC: CancelOrder request - orderId: {}, userId: {}",
            request.getOrderId(), request.getUserId());

        try {
            Order order = orderService.cancelOrder(
                request.getOrderId(),
                request.getUserId(),
                request.getReason()
            );

            // Publish event
            eventPublisher.publishOrderCancelledEvent(order);

            CancelOrderResponse response = CancelOrderResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Order cancelled successfully")
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error cancelling order", e);
            CancelOrderResponse response = CancelOrderResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to cancel order: " + e.getMessage())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getUserOrders(GetUserOrdersRequest request, StreamObserver<GetUserOrdersResponse> responseObserver) {
        log.info("gRPC: GetUserOrders request - userId: {}, page: {}, size: {}",
            request.getUserId(), request.getPage(), request.getSize());

        try {
            Page<Order> ordersPage = orderService.getUserOrders(
                request.getUserId(),
                PageRequest.of(request.getPage(), request.getSize())
            );

            List<OrderResponse> orderResponses = ordersPage.getContent().stream()
                .map(this::convertToOrderResponse)
                .collect(Collectors.toList());

            GetUserOrdersResponse response = GetUserOrdersResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Orders retrieved successfully")
                .addAllOrders(orderResponses)
                .setTotalElements(ordersPage.getTotalElements())
                .setTotalPages(ordersPage.getTotalPages())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("gRPC: Error retrieving user orders", e);
            GetUserOrdersResponse response = GetUserOrdersResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Failed to retrieve orders: " + e.getMessage())
                .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    // Conversion methods

    private OrderItem convertToOrderItem(OrderItemRequest request) {
        return OrderItem.builder()
            .productId(request.getProductId())
            .productName(request.getProductName())
            .price(BigDecimal.valueOf(request.getPrice()))
            .quantity(request.getQuantity())
            .build();
    }

    private Address convertToAddress(com.ecommerce.orderservice.grpc.proto.Address addressProto) {
        return Address.builder()
            .street(addressProto.getStreet())
            .city(addressProto.getCity())
            .state(addressProto.getState())
            .postalCode(addressProto.getPostalCode())
            .country(addressProto.getCountry())
            .build();
    }

    private OrderResponse convertToOrderResponse(Order order) {
        OrderResponse.Builder builder = OrderResponse.newBuilder()
            .setId(order.getId())
            .setOrderNumber(order.getOrderNumber())
            .setUserId(order.getUserId())
            .setSubtotal(order.getSubtotal().doubleValue())
            .setTax(order.getTax().doubleValue())
            .setShippingCost(order.getShippingCost().doubleValue())
            .setTotal(order.getTotal().doubleValue())
            .setStatus(convertToOrderStatusProto(order.getStatus()))
            .setShippingAddress(convertToAddressProto(order.getShippingAddress()))
            .setCreatedAt(order.getCreatedAt().format(DATE_FORMATTER))
            .setUpdatedAt(order.getUpdatedAt().format(DATE_FORMATTER));

        // Add items
        order.getItems().forEach(item ->
            builder.addItems(convertToOrderItemResponse(item))
        );

        // Add payment intent ID if present
        if (order.getPaymentIntentId() != null) {
            builder.setPaymentIntentId(order.getPaymentIntentId());
        }

        return builder.build();
    }

    private OrderItemResponse convertToOrderItemResponse(OrderItem item) {
        return OrderItemResponse.newBuilder()
            .setId(item.getId())
            .setProductId(item.getProductId())
            .setProductName(item.getProductName())
            .setPrice(item.getPrice().doubleValue())
            .setQuantity(item.getQuantity())
            .setSubtotal(item.getSubtotal().doubleValue())
            .build();
    }

    private com.ecommerce.orderservice.grpc.proto.Address convertToAddressProto(Address address) {
        return com.ecommerce.orderservice.grpc.proto.Address.newBuilder()
            .setStreet(address.getStreet())
            .setCity(address.getCity())
            .setState(address.getState())
            .setPostalCode(address.getPostalCode())
            .setCountry(address.getCountry())
            .build();
    }

    private com.ecommerce.orderservice.grpc.proto.OrderStatus convertToOrderStatusProto(OrderStatus status) {
        return com.ecommerce.orderservice.grpc.proto.OrderStatus.valueOf(status.name());
    }

    private OrderStatus convertToOrderStatus(com.ecommerce.orderservice.grpc.proto.OrderStatus statusProto) {
        return OrderStatus.valueOf(statusProto.name());
    }
}
