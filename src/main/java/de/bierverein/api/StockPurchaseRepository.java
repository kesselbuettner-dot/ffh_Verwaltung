package de.bierverein.api;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface StockPurchaseRepository extends JpaRepository<StockPurchase,Long>{
    List<StockPurchase> findByDrinkIdOrderByPurchaseDateDescIdDesc(Long drinkId);
    @Query("select coalesce(sum(p.quantity),0) from StockPurchase p where p.drink.id=:id")
    long totalQuantity(@Param("id") Long id);
}
