package com.ecommerce.inventoryservice.grpc;

import com.ecommerce.common.grpc.inventory.*;
import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.InventoryReservation;
import com.ecommerce.inventoryservice.exception.InsufficientStockException;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
import com.ecommerce.inventoryservice.exception.InvalidReservationException;
import com.ecommerce.inventoryservice.exception.ReservationNotFoundException;
import com.ecommerce.inventoryservice.mapper.InventoryGrpcMapper;
import com.ecommerce.inventoryservice.service.InventoryService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class InventoryGrpcServiceImpl extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final InventoryService inventoryService;
    private final InventoryGrpcMapper grpcMapper;

    @Override
    public void checkAvailability(CheckAvailabilityRequest request,
                                   StreamObserver<AvailabilityResponse> responseObserver) {
        log.info("gRPC CheckAvailability called for product: {}, quantity: {}",
                request.getProductId(), request.getQuantity());

        try {
            Inventory inventory = inventoryService.getInventoryByProductId(request.getProductId());
            boolean available = inventory.isAvailable(request.getQuantity());

            AvailabilityResponse response = AvailabilityResponse.newBuilder()
                    .setProductId(request.getProductId())
                    .setAvailable(available)
                    .setAvailableQuantity(inventory.getAvailableQuantity())
                    .setSuccess(true)
                    .setMessage(available ? "Stock available" : "Insufficient stock")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InventoryNotFoundException e) {
            log.error("Inventory not found: {}", e.getMessage());
            AvailabilityResponse response = AvailabilityResponse.newBuilder()
                    .setProductId(request.getProductId())
                    .setAvailable(false)
                    .setAvailableQuantity(0)
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error checking availability: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void bulkCheckAvailability(BulkCheckAvailabilityRequest request,
                                       StreamObserver<BulkAvailabilityResponse> responseObserver) {
        log.info("gRPC BulkCheckAvailability called for {} items", request.getItemsCount());

        try {
            Map<String, Integer> productQuantities = request.getItemsList().stream()
                    .collect(Collectors.toMap(
                            CheckAvailabilityRequest::getProductId,
                            CheckAvailabilityRequest::getQuantity
                    ));

            Map<String, Boolean> availabilityMap = inventoryService.bulkCheckAvailability(productQuantities);

            List<AvailabilityResponse> responses = request.getItemsList().stream()
                    .map(item -> {
                        boolean available = availabilityMap.getOrDefault(item.getProductId(), false);
                        try {
                            Inventory inventory = inventoryService.getInventoryByProductId(item.getProductId());
                            return AvailabilityResponse.newBuilder()
                                    .setProductId(item.getProductId())
                                    .setAvailable(available)
                                    .setAvailableQuantity(inventory.getAvailableQuantity())
                                    .setSuccess(true)
                                    .setMessage(available ? "Stock available" : "Insufficient stock")
                                    .build();
                        } catch (Exception e) {
                            return AvailabilityResponse.newBuilder()
                                    .setProductId(item.getProductId())
                                    .setAvailable(false)
                                    .setAvailableQuantity(0)
                                    .setSuccess(false)
                                    .setMessage(e.getMessage())
                                    .build();
                        }
                    })
                    .collect(Collectors.toList());

            boolean allAvailable = responses.stream().allMatch(AvailabilityResponse::getAvailable);

            BulkAvailabilityResponse response = BulkAvailabilityResponse.newBuilder()
                    .addAllItems(responses)
                    .setAllAvailable(allAvailable)
                    .setSuccess(true)
                    .setMessage(allAvailable ? "All items available" : "Some items unavailable")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in bulk check availability: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void reserveStock(ReserveStockRequest request,
                              StreamObserver<ReservationResponse> responseObserver) {
        log.info("gRPC ReserveStock called for order: {} with {} items",
                request.getOrderId(), request.getItemsCount());

        try {
            Map<String, Integer> productQuantities = request.getItemsList().stream()
                    .collect(Collectors.toMap(
                            StockItem::getProductId,
                            StockItem::getQuantity
                    ));

            int expirationMinutes = request.getExpirationMinutes() > 0 ? request.getExpirationMinutes() : null;

            List<InventoryReservation> reservations = inventoryService.bulkReserveStock(
                    request.getOrderId(),
                    productQuantities,
                    expirationMinutes
            );

            // For simplicity, we'll return the first reservation ID as the primary one
            InventoryReservation primaryReservation = reservations.get(0);

            List<StockItem> reservedItems = reservations.stream()
                    .map(r -> StockItem.newBuilder()
                            .setProductId(r.getProductId())
                            .setQuantity(r.getQuantity())
                            .build())
                    .collect(Collectors.toList());

            ReservationResponse response = ReservationResponse.newBuilder()
                    .setReservationId(primaryReservation.getId())
                    .setOrderId(request.getOrderId())
                    .setStatus(grpcMapper.mapReservationStatus(primaryReservation.getStatus()))
                    .addAllItems(reservedItems)
                    .setExpiresAt(primaryReservation.getExpiresAt().toEpochSecond(ZoneOffset.UTC))
                    .setSuccess(true)
                    .setMessage("Stock reserved successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InsufficientStockException e) {
            log.error("Insufficient stock: {}", e.getMessage());
            ReservationResponse response = ReservationResponse.newBuilder()
                    .setOrderId(request.getOrderId())
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error reserving stock: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void commitReservation(CommitReservationRequest request,
                                   StreamObserver<ReservationResponse> responseObserver) {
        log.info("gRPC CommitReservation called for order: {}", request.getOrderId());

        try {
            inventoryService.commitReservationsByOrderId(request.getOrderId());

            ReservationResponse response = ReservationResponse.newBuilder()
                    .setOrderId(request.getOrderId())
                    .setStatus(com.ecommerce.common.grpc.inventory.ReservationStatus.COMMITTED)
                    .setSuccess(true)
                    .setMessage("Reservation committed successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (ReservationNotFoundException | InvalidReservationException e) {
            log.error("Error committing reservation: {}", e.getMessage());
            ReservationResponse response = ReservationResponse.newBuilder()
                    .setOrderId(request.getOrderId())
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error committing reservation: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void releaseReservation(ReleaseReservationRequest request,
                                    StreamObserver<ReservationResponse> responseObserver) {
        log.info("gRPC ReleaseReservation called for order: {}, reason: {}",
                request.getOrderId(), request.getReason());

        try {
            inventoryService.releaseReservationsByOrderId(request.getOrderId());

            ReservationResponse response = ReservationResponse.newBuilder()
                    .setOrderId(request.getOrderId())
                    .setStatus(com.ecommerce.common.grpc.inventory.ReservationStatus.RELEASED)
                    .setSuccess(true)
                    .setMessage("Reservation released successfully")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error releasing reservation: {}", e.getMessage(), e);
            ReservationResponse response = ReservationResponse.newBuilder()
                    .setOrderId(request.getOrderId())
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void updateStock(UpdateStockRequest request,
                             StreamObserver<InventoryResponse> responseObserver) {
        log.info("gRPC UpdateStock called for product: {}, change: {}, type: {}",
                request.getProductId(), request.getQuantityChange(), request.getUpdateType());

        try {
            Inventory inventory = inventoryService.updateStock(
                    request.getProductId(),
                    request.getQuantityChange(),
                    request.getUpdateType().name(),
                    request.getNotes()
            );

            InventoryResponse response = grpcMapper.buildInventoryResponse(inventory, true, "Stock updated successfully");

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InventoryNotFoundException e) {
            log.error("Inventory not found: {}", e.getMessage());
            InventoryResponse response = InventoryResponse.newBuilder()
                    .setProductId(request.getProductId())
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error updating stock: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getInventory(GetInventoryRequest request,
                              StreamObserver<InventoryResponse> responseObserver) {
        log.info("gRPC GetInventory called for product: {}", request.getProductId());

        try {
            Inventory inventory = inventoryService.getInventoryByProductId(request.getProductId());
            InventoryResponse response = grpcMapper.buildInventoryResponse(inventory, true, "Inventory retrieved successfully");

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InventoryNotFoundException e) {
            log.error("Inventory not found: {}", e.getMessage());
            InventoryResponse response = InventoryResponse.newBuilder()
                    .setProductId(request.getProductId())
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting inventory: {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }
}
