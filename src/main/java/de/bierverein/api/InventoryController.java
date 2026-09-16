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
        p.setPackageQuantity(d.getPackageQuantity());
        p.setPackageVolume(sizeVolume(d));
        p.setPackageUnitShortName(sizeUnit(d));
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
        BigDecimal perPiece = o.price();
        if (perPiece != null && o.quantity() != null && o.quantity().signum() > 0) {
            perPiece = perPiece.divide(o.quantity(), 4, java.math.RoundingMode.HALF_UP);
        }
        return new MarktguruOfferDto(o.offerId(), o.retailer(), o.retailerKey(), o.retailers(), o.price(), perPiece,
                o.oldPrice(), o.referencePrice(), o.volume(), o.quantity(), o.multiProduct(), o.productName(), o.description(),
                o.unitName(), o.unitShortName(), o.validityDates(), o.validFrom(), o.validTo(),
                o.loyaltyRequired(), o.externalUrl(), o.leafletFlightId(), null);
    }

    private void apply(Drink d,ArticleRequest r){
        if(r.name()==null||r.name().isBlank()||r.price()==null)throw bad("Name und Verkaufspreis sind erforderlich");
        d.setName(r.name().trim()); d.setCategory(r.category()); d.setPrice(r.price());
        d.setEan(r.ean()==null||r.ean().isBlank()?null:r.ean().replaceAll("\\D",""));
        d.setWarningThreshold(Math.max(0,r.warningThreshold())); d.setActive(r.active());
        d.setPackageQuantity(normalizePositive(r.packageQuantity()));
        d.setPackageVolume(normalizePositive(r.packageVolume()));
        d.setPackageUnitShortName(r.packageUnitShortName()==null||r.packageUnitShortName().isBlank()?null:MarktguruService.normalizeUnit(r.packageUnitShortName()));
        d.setSizeVolume(normalizePositive(r.sizeVolume()));
        d.setSizeUnitShortName(r.sizeUnitShortName()==null||r.sizeUnitShortName().isBlank()?null:MarktguruService.normalizeUnit(r.sizeUnitShortName()));
        // Backward compatibility: older articles stored the individual size in packageVolume/packageUnit.
        if(d.getSizeVolume()==null && d.getPackageVolume()!=null) d.setSizeVolume(d.getPackageVolume());
        if(d.getSizeUnitShortName()==null && d.getPackageUnitShortName()!=null) d.setSizeUnitShortName(MarktguruService.normalizeUnit(d.getPackageUnitShortName()));
        if(d.getStock()<0)d.setStock(0);
    }
    private ArticleDto article(Drink d){
        List<StockPurchase> ps=purchases.findByDrinkIdOrderByPurchaseDateDescIdDesc(d.getId());
        BigDecimal last=ps.isEmpty()?null:ps.get(0).getUnitPrice();
        BigDecimal sum=BigDecimal.ZERO; long qty=0;
        for(StockPurchase p:ps){sum=sum.add(p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())));qty+=p.getQuantity();}
        BigDecimal avg=qty==0?null:sum.divide(BigDecimal.valueOf(qty),2,java.math.RoundingMode.HALF_UP);
        return new ArticleDto(d.getId(),d.getName(),d.getCategory(),d.getPrice(),d.getEan(),d.getStock(),d.getWarningThreshold(),d.isActive(),last,avg,
                d.getPackageQuantity(),d.getPackageVolume(),d.getPackageUnitShortName(),sizeVolume(d),sizeUnit(d));
    }
    private ShoppingItemDto shopping(Drink d){
        ArticleDto a=article(d); String marktguruQuery=buildMarktguruQuery(d);
        List<MarktguruOfferDto> offers=marktguru(marktguruQuery);
        List<MarktguruOfferDto> matching=offers.stream().filter(o->sameSize(d,o)).toList();
        MarktguruOfferDto best=matching.stream().filter(o->o.price()!=null).min(Comparator.comparing(this::pricePerPiece)).orElse(null);
        List<StockPurchase> matchingPurchases=purchases.findByDrinkIdOrderByPurchaseDateDescIdDesc(d.getId()).stream()
                .filter(p->samePurchaseSize(d, p))
                .toList();
        BigDecimal matchedLast=matchingPurchases.isEmpty()?null:matchingPurchases.get(0).getUnitPrice();
        BigDecimal sum=BigDecimal.ZERO; long qty=0;
        for(StockPurchase p:matchingPurchases){sum=sum.add(p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantity())));qty+=p.getQuantity();}
        BigDecimal matchedAverage=qty==0?null:sum.divide(BigDecimal.valueOf(qty),2,java.math.RoundingMode.HALF_UP);
        boolean comparable=best!=null && !matchingPurchases.isEmpty();
        boolean belowLast=comparable && matchedLast!=null && pricePerPiece(best).compareTo(matchedLast)<0;
        boolean belowAverage=comparable && matchedAverage!=null && pricePerPiece(best).compareTo(matchedAverage)<0;
        return new ShoppingItemDto(a.id(),a.name(),a.ean(),a.stock(),a.warningThreshold(),
                Math.max(0,a.warningThreshold()-a.stock()),matchedLast,matchedAverage,best,belowLast,belowAverage,
                d.getPackageQuantity(),d.getPackageVolume(),d.getPackageUnitShortName(),comparable);
    }

    String buildMarktguruQuery(Drink d){
        String name=d.getName()==null?"":d.getName().trim();
        BigDecimal size=sizeVolume(d);
        String unit=sizeUnit(d);
        if(size==null || unit==null) return name;
        return name + " " + size.stripTrailingZeros().toPlainString() + " " + unit;
    }

    private boolean sameSize(Drink d, MarktguruOfferDto o){
        if(o==null || o.volume()==null || o.unitShortName()==null) return false;
        BigDecimal size=sizeVolume(d); String unit=sizeUnit(d);
        if(size==null || unit==null) return false;
        return MarktguruService.sameSize(size, unit, o.volume(), o.unitShortName());
    }

    private boolean samePurchaseSize(Drink d, StockPurchase p){
        if(p==null || p.getPackageVolume()==null || p.getPackageUnitShortName()==null) return false;
        BigDecimal size=sizeVolume(d); String unit=sizeUnit(d);
        if(size==null || unit==null) return false;
        return MarktguruService.sameSize(size, unit, p.getPackageVolume(), p.getPackageUnitShortName());
    }

    private BigDecimal sizeVolume(Drink d){
        if(d.getSizeVolume()!=null) return d.getSizeVolume();
        return d.getPackageVolume();
    }
    private String sizeUnit(Drink d){
        if(d.getSizeUnitShortName()!=null && !d.getSizeUnitShortName().isBlank()) return MarktguruService.normalizeUnit(d.getSizeUnitShortName());
        return d.getPackageUnitShortName()==null?null:MarktguruService.normalizeUnit(d.getPackageUnitShortName());
    }
    private BigDecimal pricePerPiece(MarktguruOfferDto o){
        if(o==null || o.price()==null) return null;
        BigDecimal q=o.quantity();
        if(q==null || q.signum()<=0) q=BigDecimal.ONE;
        return o.price().divide(q,4,java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePositive(BigDecimal v){
        return v!=null && v.signum()>0 ? v : null;
    }
    private PurchaseDto purchase(StockPurchase p){return new PurchaseDto(p.getId(),p.getDrink().getId(),p.getDrink().getName(),p.getQuantity(),p.getUnitPrice(),p.getPurchaseDate(),p.getSupplier(),p.getNote(),p.getCreatedBy());}
    private ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
    private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}

    public record ArticleRequest(String name,String category,BigDecimal price,String ean,int warningThreshold,boolean active,
                                  BigDecimal packageQuantity,BigDecimal packageVolume,String packageUnitShortName,BigDecimal sizeVolume,String sizeUnitShortName){}
    public record ArticleDto(Long id,String name,String category,BigDecimal price,String ean,int stock,int warningThreshold,boolean active,
                              BigDecimal lastPurchasePrice,BigDecimal averagePurchasePrice,BigDecimal packageQuantity,BigDecimal packageVolume,String packageUnitShortName,BigDecimal sizeVolume,String sizeUnitShortName){}
    public record PurchaseRequest(Long drinkId,int quantity,BigDecimal unitPrice,LocalDate purchaseDate,String supplier,String note){}
    public record PurchaseDto(Long id,Long drinkId,String drinkName,int quantity,BigDecimal unitPrice,LocalDate purchaseDate,String supplier,String note,String createdBy){}
    public record MarktguruOfferDto(String offerId,String retailer,String retailerKey,List<MarktguruService.Retailer> retailers,
                                     BigDecimal price,BigDecimal pricePerPiece,BigDecimal oldPrice,BigDecimal referencePrice,BigDecimal volume,
                                     BigDecimal quantity,boolean multiProduct,String productName,String description,
                                     String unitName,String unitShortName,List<MarktguruService.Validity> validityDates,
                                     String validFrom,String validTo,boolean loyaltyRequired,String externalUrl,
                                     String leafletFlightId,String error){}
    public record ShoppingItemDto(Long id,String name,String ean,int stock,int warningThreshold,int suggestedQuantity,BigDecimal lastPurchasePrice,BigDecimal averagePurchasePrice,
                                   MarktguruOfferDto marktguruOffer,boolean offerBelowLastPurchase,boolean offerBelowAveragePurchase,
                                   BigDecimal packageQuantity,BigDecimal packageVolume,String packageUnitShortName,boolean packageComparable){}
}
