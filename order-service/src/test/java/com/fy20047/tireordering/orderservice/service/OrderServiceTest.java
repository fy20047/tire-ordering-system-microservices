package com.fy20047.tireordering.orderservice.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fy20047.tireordering.orderservice.client.TireServiceClient;
import com.fy20047.tireordering.orderservice.entity.Order;
import com.fy20047.tireordering.orderservice.enums.InstallationOption;
import com.fy20047.tireordering.orderservice.enums.OrderStatus;
import com.fy20047.tireordering.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TireServiceClient tireServiceClient;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_whenDeliveryWithoutAddress_shouldThrowValidationError() {
        OrderService.CreateOrderCommand command = buildCommand(InstallationOption.DELIVERY, " ");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(command)
        );

        assertEquals("deliveryAddress is required for delivery", ex.getMessage());
        verifyNoInteractions(tireServiceClient);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void createOrder_whenTireNotFound_shouldReturnBadRequestError() {
        OrderService.CreateOrderCommand command = buildCommand(InstallationOption.INSTALL, null);
        when(tireServiceClient.getTireById(100L)).thenThrow(new IllegalArgumentException("Tire not found"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> orderService.createOrder(command)
        );

        assertEquals("Tire not found", ex.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_whenTireInactive_shouldThrowConflictError() {
        when(tireServiceClient.getTireById(100L)).thenReturn(
                new TireServiceClient.TireProduct(
                        100L,
                        "MICHELIN",
                        "Pilot Sport 5",
                        "FR",
                        "225/45R17",
                        4800,
                        false
                )
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> orderService.createOrder(buildCommand(InstallationOption.INSTALL, null))
        );

        assertEquals("Tire is not available", ex.getMessage());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_whenValid_shouldPersistSnapshotAndPendingStatus() {
        when(tireServiceClient.getTireById(100L)).thenReturn(
                new TireServiceClient.TireProduct(
                        100L,
                        "MICHELIN",
                        "Pilot Sport 5",
                        "FR",
                        "225/45R17",
                        4800,
                        true
                )
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = orderService.createOrder(buildCommand(InstallationOption.INSTALL, null));

        assertAll(
                () -> assertEquals(100L, created.getTireId()),
                () -> assertEquals("MICHELIN", created.getTireSnapshotBrand()),
                () -> assertEquals("Pilot Sport 5", created.getTireSnapshotSeries()),
                () -> assertEquals("225/45R17", created.getTireSnapshotSize()),
                () -> assertEquals(4800, created.getTireSnapshotPrice()),
                () -> assertEquals(OrderStatus.PENDING, created.getStatus()),
                () -> assertEquals("Test User", created.getCustomerName()),
                () -> assertEquals("0912000111", created.getPhone()),
                () -> assertEquals("Model 3", created.getCarModel())
        );
        verify(orderRepository).save(any(Order.class));
    }

    private OrderService.CreateOrderCommand buildCommand(
            InstallationOption installationOption,
            String deliveryAddress
    ) {
        return new OrderService.CreateOrderCommand(
                100L,
                4,
                "Test User",
                "0912000111",
                "test@example.com",
                installationOption,
                deliveryAddress,
                "Model 3",
                "note"
        );
    }
}
