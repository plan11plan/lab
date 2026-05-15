package lab.stock;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<StockModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockModel s WHERE s.id = :id")
    Optional<StockModel> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockModel s SET s.stock = s.stock - :quantity "
         + "WHERE s.id = :id AND s.stock >= :quantity")
    int decreaseAtomic(@Param("id") Long id, @Param("quantity") long quantity);
}
