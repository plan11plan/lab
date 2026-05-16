package lab.deadlock.a.scenario_d;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    public OrderProcessingService(OrderRepository orderRepository,
                                  InventoryRepository inventoryRepository) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * 안티패턴 — 주문 처리는 Order row를 먼저 잠그고 Inventory row를 그 뒤에 잠근다.
     * 환불 처리(processRefund)와 동시에 같은 (orderId, productId) 쌍에 들어오면
     * 락 획득 순서가 [Order, Inventory] vs [Inventory, Order]로 엇갈리면서 cycle이 닫힌다.
     */
    @Transactional
    public void processOrder(Long orderId, Long productId, int quantity) {
        Order order = orderRepository.findByIdForUpdate(orderId);       // 1단계 락 — Order 테이블
        Inventory inventory = inventoryRepository.findByIdForUpdate(productId); // 2단계 락 — Inventory 테이블
        inventory.decrease(quantity);
        order.markPaid();
    }

    /**
     * 안티패턴 — 환불 처리는 Inventory를 먼저 복구한 뒤 Order 상태를 바꾸는 순서로 짠다.
     * processOrder와 정확히 반대 순서이므로 두 워크플로가 동시에 돌면 데드락이 닫힌다.
     */
    @Transactional
    public void processRefund(Long orderId, Long productId, int quantity) {
        Inventory inventory = inventoryRepository.findByIdForUpdate(productId); // 1단계 락 — Inventory 테이블
        Order order = orderRepository.findByIdForUpdate(orderId);       // 2단계 락 — Order 테이블
        inventory.restore(quantity);
        order.markRefunded();
    }

    /**
     * 처방 — 어느 워크플로든 항상 Inventory → Order 순서로 잠근다.
     * 두 메서드가 같은 락 순서 컨벤션을 따르므로 cycle이 만들어질 면이 사라진다.
     * 락은 정렬된 순서로 잡아둔 뒤, 비즈니스 로직(주문/환불 방향)은 그대로 수행한다.
     */
    @Transactional
    public void processOrderSafe(Long orderId, Long productId, int quantity) {
        // 컨벤션: 항상 Inventory 테이블을 먼저 잠근다.
        Inventory inventory = inventoryRepository.findByIdForUpdate(productId);
        Order order = orderRepository.findByIdForUpdate(orderId);
        inventory.decrease(quantity);
        order.markPaid();
    }

    @Transactional
    public void processRefundSafe(Long orderId, Long productId, int quantity) {
        // 같은 컨벤션: Inventory 먼저, Order 그 뒤.
        Inventory inventory = inventoryRepository.findByIdForUpdate(productId);
        Order order = orderRepository.findByIdForUpdate(orderId);
        inventory.restore(quantity);
        order.markRefunded();
    }
}
