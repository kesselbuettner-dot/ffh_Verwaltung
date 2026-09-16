package de.bierverein.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','VORSTAND','KASSENWART')")
public class DashboardController {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private final OrderRepository orders;
    private final StockPurchaseRepository purchases;

    public DashboardController(OrderRepository orders, StockPurchaseRepository purchases) {
        this.orders = orders; this.purchases = purchases;
    }

    @GetMapping("/finance")
    public FinanceDto finance(@RequestParam(required=false) String from, @RequestParam(required=false) String to) {
        LocalDate end = to == null || to.isBlank() ? LocalDate.now(ZONE) : LocalDate.parse(to);
        LocalDate start = from == null || from.isBlank() ? end.withDayOfMonth(1) : LocalDate.parse(from);
        if (start.isAfter(end)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Startdatum liegt nach Enddatum");
        Instant startInstant = start.atStartOfDay(ZONE).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(ZONE).toInstant();

        BigDecimal income = orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(startInstant, endInstant).stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .map(Order::getTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expenses = purchases.findAll().stream()
                .filter(p -> p.getPurchaseDate() != null && !p.getPurchaseDate().isBefore(start) && !p.getPurchaseDate().isAfter(end))
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceDto(start, end, income, expenses, income.subtract(expenses));
    }

    public record FinanceDto(LocalDate from, LocalDate to, BigDecimal income, BigDecimal expenses, BigDecimal balance) {}
}
