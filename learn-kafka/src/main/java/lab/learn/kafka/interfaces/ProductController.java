package lab.learn.kafka.interfaces;

import lab.learn.kafka.facade.ProductFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductFacade productFacade;

    @PostMapping
    public ResponseEntity<String> createProduct(@RequestBody ProductCreateRequest request) {
        log.info("상품 생성 요청 - name: {}, price: {}", request.name(), request.price());
        String productId = productFacade.createProduct(request.name(), request.price());
        return ResponseEntity.ok("상품 생성 완료 - id: " + productId);
    }

    @PatchMapping("/{productId}/price")
    public ResponseEntity<String> changePrice(@PathVariable String productId,
                                              @RequestBody ProductPriceChangeRequest request) {
        log.info("가격 변경 요청 - productId: {}, newPrice: {}", productId, request.newPrice());
        productFacade.changePrice(productId, request.newPrice());
        return ResponseEntity.ok("가격 변경 완료 - productId: " + productId);
    }
}
