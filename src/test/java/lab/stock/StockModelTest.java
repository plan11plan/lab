package lab.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StockModelTest {

    @Test
    void decrease_정상_차감() {
        StockModel s = new StockModel(100L);
        s.decrease(30L);
        assertThat(s.getStock()).isEqualTo(70L);
    }

    @Test
    void decrease_재고_부족_시_예외() {
        StockModel s = new StockModel(10L);
        assertThatThrownBy(() -> s.decrease(20L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("stock");
    }

    @Test
    void decrease_수량_0_이하_시_예외() {
        StockModel s = new StockModel(10L);
        assertThatThrownBy(() -> s.decrease(0L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
