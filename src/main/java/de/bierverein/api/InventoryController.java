package de.bierverein.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/inventory")
@PreAuthorize("hasRole('ADMIN')")
public class InventoryController {
    private final DrinkRepository drinks;
    private final StockPurchaseRepository purchases;
    private final MarktguruService marktguruService;

    public InventoryController(DrinkRepository drinks, StockPurchaseRepository purchases,
                               MarktguruService marktguruService) {
        this.drinks=drinks; this.purchases=purchases; this.marktguruService=marktguruService;
    }

    @GetMapping("/articles")
    public List<ArticleDto> articles(@RequestParam(defaultValue="") String q) {
        String x=q.trim().toLowerCase();
        return drinks.findAll().stream()
            .filter(d -> x.isBlank() || d.getName().toLowerCase().contains(x) ||
                    (d.getEan()!=null && d.getEan().contains(x)))
            .map(this::article).toList();
    }

    @GetMapping("/shopping-list")
    public List<ShoppingItemDto> shoppingList() {
        return drinks.findAll().stream()
            .filter(d -> d.isActive() && d.getStock() < d.getWarningThreshold())
            .map(this::shopping).toList();
    }

    @PostMapping("/articles")
    public ArticleDto createArticle(@RequestBody ArticleRequest req) {
        Drink d=new Drink(); apply(d,req); return article(drinks.save(d));
    }

    @PutMapping("/articles/{id}")
    public ArticleDto updateArticle(@PathVariable Long id,@RequestBody ArticleRequest req) {
        Drink d=drinks.findById(id).orElseThrow(()->notFound("Artikel nicht gefunden"));
        apply(d,req); return article(drinks.save(d));
    }

    @GetMapping("/articles/{id}")
    public ArticleDto article(@PathVariable Long id) {
        return article(drinks.findById(id).orElseThrow(()->notFound("Artikel nicht gefunden")));
    }

    @PostMapping("/purchases")
    @Transactional
    public PurchaseDto purchase(@RequestBody PurchaseRequest req, org.springframework.security.core.Authentication auth) {
        if(req.drinkId()==null || req.quantity()<=0 || req.unitPrice()==null || req.unitPrice().signum()<0)
            throw bad("Artikel, Menge und gültiger Einkaufspreis sind erforderlich");
        Drink d=drinks.findById(req.drinkId()).orElseThrow(()->notFound("Artikel nicht gefunden"));
        StockPurchase p=new StockPurchase(); p.setDrink(d); p.setQuantity(req.quantity());
        p.setUnitPrice(req.unitPrice()); p.setPurchaseDate(req.purchaseDate()==null?LocalDate.now():req.purchaseDate());
        p.setSupplier(req.supplier()); p.setNote(req.note()); p.setCreatedBy(auth.getName());
        d.setStock(d.getStock()+req.quantity()); drinks.save(d);
        return purchase(purchases.save(p));
    }

    @GetMapping("/purchases")
    public List<PurchaseDto> purchases(@RequestParam(required=false) Long drinkId) {
        List<StockPurchase> list=drinkId==null ? purchases.findAll() : purchases.findByDrinkIdOrderByPurchaseDateDescIdDesc(drinkId);
        return list.stream().sorted(Comparator.comparing(StockPurchase::getPurchaseDate).reversed()
                .thenComparing(StockPurchase::getId, Comparator.reverseOrder())).map(this::purchase).toList();
    }

    @GetMapping("/marktguru")
    public List<MarktguruOfferDto> marktguru(@RequestParam String q) {
        return marktguruService.search(q).stream().map(this::marktguruOffer).toList();
    }

    private MarktguruOfferDto marktguruOffer(MarktguruService.Offer o) {
        return new MarktguruOfferDto(o.offerId(), o.retailer(), o.retailerKey(), o.price(), o.oldPrice(),
                o.referencePrice(), o.productName(), o.description(), o.unitName(), o.unitShortName(),
                o.validFrom(), o.validTo(), o.loyaltyRequired(), o.externalUrl(), o.leafletFlightId(), null);
    }

    private void apply(Drink d,ArticleRequest r){
        if(r.name()==null||r.name().isBlank()||r.price()==null)throw bad("Name und Verkaufspreis sind erforderlich");
        d.setName(r.name().trim()); d.setCategory(r.category()); d.setPrice(r.price());
        d.setEan(r.ean()==null||r.ean().isBlank()?null:r.ean().replaceAll("\\D",""));
        d.setWarningThreshold(Math.max(0,r.warningThreshold())); d.setActive(r.active());
        if(d.getStock()<0)d.setStock(0);
    }
    private ArticleDto article(Drink d){
        List<StockPurchase> ps=purchases.findByDrinkIdOrderByPurchaseDateDescIdDesc(d.getId());
        BigDecimal last=ps.isEmpty()?null:ps.get(0).getUnitPrice();
        BigDecimal sum=BigDecimal.ZERO; long qty=0;
        for(StockPurchase p:ps){sum=sum.add(p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())));qty+=p.getQuantity();}
        BigDecimal avg=qty==0?null:sum.divide(BigDecimal.valueOf(qty),2,java.math.RoundingMode.HALF_UP);
        return new ArticleDto(d.getId(),d.getName(),d.getCategory(),d.getPrice(),d.getEan(),d.getStock(),d.getWarningThreshold(),d.isActive(),last,avg);
    }
    private ShoppingItemDto shopping(Drink d){
        ArticleDto a=article(d); List<MarktguruOfferDto> offers=marktguru(d.getEan()!=null?d.getEan():d.getName());
        MarktguruOfferDto best=offers.stream().filter(o->o.price()!=null).min(Comparator.comparing(MarktguruOfferDto::price)).orElse(null);
        boolean belowLast=best!=null && a.lastPurchasePrice()!=null && best.price().compareTo(a.lastPurchasePrice())<0;
        boolean belowAverage=best!=null && a.averagePurchasePrice()!=null && best.price().compareTo(a.averagePurchasePrice())<0;
        return new ShoppingItemDto(a.id(),a.name(),a.ean(),a.stock(),a.warningThreshold(),
                Math.max(0,a.warningThreshold()-a.stock()),a.lastPurchasePrice(),a.averagePurchasePrice(),best,belowLast,belowAverage);
    }
    private PurchaseDto purchase(StockPurchase p){return new PurchaseDto(p.getId(),p.getDrink().getId(),p.getDrink().getName(),p.getQuantity(),p.getUnitPrice(),p.getPurchaseDate(),p.getSupplier(),p.getNote(),p.getCreatedBy());}
    private ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
    private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}

    public record ArticleRequest(String name,String category,BigDecimal price,String ean,int warningThreshold,boolean active){}
    public record ArticleDto(Long id,String name,String category,BigDecimal price,String ean,int stock,int warningThreshold,boolean active,BigDecimal lastPurchasePrice,BigDecimal averagePurchasePrice){}
    public record PurchaseRequest(Long drinkId,int quantity,BigDecimal unitPrice,LocalDate purchaseDate,String supplier,String note){}
    public record PurchaseDto(Long id,Long drinkId,String drinkName,int quantity,BigDecimal unitPrice,LocalDate purchaseDate,String supplier,String note,String createdBy){}
    public record MarktguruOfferDto(String offerId,String retailer,String retailerKey,BigDecimal price,BigDecimal oldPrice,BigDecimal referencePrice,String productName,String description,String unitName,String unitShortName,String validFrom,String validTo,boolean loyaltyRequired,String externalUrl,String leafletFlightId,String error){}
    public record ShoppingItemDto(Long id,String name,String ean,int stock,int warningThreshold,int suggestedQuantity,BigDecimal lastPurchasePrice,BigDecimal averagePurchasePrice,MarktguruOfferDto marktguruOffer,boolean offerBelowLastPurchase,boolean offerBelowAveragePurchase){}
}
