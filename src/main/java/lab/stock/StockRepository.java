package lab.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findById(Long id);

    @Modifying
    @Query(value = "TRUNCATE TABLE stock", nativeQuery = true)
    void truncate();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Optional<Stock> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from Stock s where s.quantity between :lo and :hi order by s.id")
    List<Stock> findInQuantityRange(@Param("lo") long lo, @Param("hi") long hi);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.quantity between :lo and :hi order by s.id")
    List<Stock> findInQuantityRangeForUpdate(@Param("lo") long lo, @Param("hi") long hi);
}
