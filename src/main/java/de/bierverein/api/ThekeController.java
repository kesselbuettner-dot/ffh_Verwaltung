package de.bierverein.api;
import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.Authentication; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.server.ResponseStatusException; import org.springframework.http.HttpStatus; import java.math.*; import java.time.*; import java.util.*;
@RestController @RequestMapping("/api/theke") public class ThekeController {
 private static final ZoneId ZONE=ZoneId.of("Europe/Berlin");
 private final DrinkRepository drinks; private final ArticleRepository articles; private final MemberRepository members; private final OrderRepository orders; private final AppUserRepository users;
 public ThekeController(DrinkRepository d,ArticleRepository a,MemberRepository m,OrderRepository o,AppUserRepository u){drinks=d;articles=a;members=m;orders=o;users=u;}
 @GetMapping("/drinks") @PreAuthorize("hasAnyRole('ADMIN','THEKE','MEMBER','GETRAENKEWART')") public List<DrinkDto> drinks(){return drinks.findAll().stream().filter(Drink::isActive).map(d->new DrinkDto(d.getId(),d.getName(),d.getCategory(),d.getPrice(),d.getEan(),d.getStock(),d.getWarningThreshold(),sizeVolume(d),sizeUnit(d),d.getImageUrl())).toList();}
 private BigDecimal sizeVolume(Drink d){return d.getSizeVolume()!=null?d.getSizeVolume():d.getPackageVolume();}
 private String sizeUnit(Drink d){return d.getSizeUnitShortName()!=null&&!d.getSizeUnitShortName().isBlank()?MarktguruService.normalizeUnit(d.getSizeUnitShortName()):d.getPackageUnitShortName()==null?null:MarktguruService.normalizeUnit(d.getPackageUnitShortName());}
 @GetMapping("/members") @PreAuthorize("hasAnyRole('ADMIN','THEKE')") public List<MemberDto> memberSearch(@RequestParam(defaultValue="") String q){String x=q.toLowerCase().trim();return members.findAll().stream().filter(Member::isActive).filter(m->x.isBlank()||m.getName().toLowerCase().contains(x)||(m.getEmail()!=null&&m.getEmail().toLowerCase().contains(x))).limit(50).map(m->new MemberDto(m.getId(),m.getName(),m.getBalance())).toList();}
 @GetMapping("/orders") @Transactional(readOnly=true) @PreAuthorize("hasAnyRole('ADMIN','THEKE')") public List<OrderDto> orders(@RequestParam(required=false) String date){
   LocalDate day=date==null||date.isBlank()?LocalDate.now(ZONE):parseDate(date); Instant from=day.atStartOfDay(ZONE).toInstant(); Instant to=day.plusDays(1).atStartOfDay(ZONE).toInstant();
   return orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(from,to).stream().map(this::dto).toList();
 }
 @GetMapping("/orders/{id}") @Transactional(readOnly=true) @PreAuthorize("hasAnyRole('ADMIN','THEKE')") public OrderDto order(@PathVariable Long id){return dto(orders.findById(id).orElseThrow(()->notFound("Bestellung nicht gefunden")));}
 @PostMapping("/orders") @PreAuthorize("hasAnyRole('ADMIN','THEKE','MEMBER')") @Transactional public OrderDto create(@RequestBody OrderRequest req,Authentication auth){
   AppUser user=users.findByUsername(auth.getName()).orElseThrow(); Member member; boolean counter=user.getRole()==Role.ADMIN||user.getRole()==Role.THEKE;
   if(counter){if(req.memberId()==null)throw bad("Mitglied auswählen");member=members.findByIdForUpdate(req.memberId()).orElseThrow();}
   else {if(user.getMember()==null)throw bad("Benutzer ist keinem Mitglied zugeordnet");member=members.findByIdForUpdate(user.getMember().getId()).orElseThrow();}
   if(!member.isActive())throw bad("Mitglied ist inaktiv"); if(req.items()==null||req.items().isEmpty())throw bad("Warenkorb ist leer");
   Order order=new Order();order.setMember(member);order.setCreatedBy(user.getUsername());BigDecimal total=BigDecimal.ZERO;
   for(ItemRequest ir:req.items()){if(ir==null||ir.drinkId()==null||ir.quantity()<=0||ir.quantity()>99)throw bad("Ungültige Position");Drink d=drinks.findById(ir.drinkId()).orElseThrow();if(!d.isActive())throw bad("Getränk ist nicht verfügbar");if(d.getStock() < ir.quantity()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Bestand nicht ausreichend für "+d.getName()+" (Bestand: "+d.getStock()+")");
   BigDecimal line=d.getPrice().multiply(BigDecimal.valueOf(ir.quantity())); d.setStock(d.getStock()-ir.quantity()); drinks.save(d); OrderItem item=new OrderItem();item.setDrink(d);item.setQuantity(ir.quantity());item.setUnitPrice(d.getPrice());item.setTotal(line);order.addItem(item);total=total.add(line);}
   if(member.getBalance().compareTo(total)<0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Guthaben reicht nicht aus. Verfügbar: "+member.getBalance()+" €, benötigt: "+total+" €");
   member.setBalance(member.getBalance().subtract(total));order.setTotal(total);order.setStatus(OrderStatus.COMPLETED);return dto(orders.save(order));
 }
 @PutMapping("/orders/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','THEKE')") @Transactional public OrderDto status(@PathVariable Long id,@RequestBody ThekeOrderStatusRequest req,Authentication auth){
   if(req==null||req.status()==null)throw bad("Status ist erforderlich");
   Order order=orders.findByIdForUpdate(id).orElseThrow(()->notFound("Bestellung nicht gefunden"));
   OrderStatus current=order.getStatus(), next=req.status();
   boolean allowed=(current==OrderStatus.NEW && next==OrderStatus.CONFIRMED)
       ||(current==OrderStatus.CONFIRMED && next==OrderStatus.PREPARING)
       ||(current==OrderStatus.PREPARING && next==OrderStatus.READY)
       ||(current==OrderStatus.READY && next==OrderStatus.COMPLETED);
   if(!allowed)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ungültiger Statuswechsel: "+current+" -> "+next);
   order.setStatus(next);
   return dto(orders.save(order));
 }
 @PostMapping("/orders/{id}/cancel") @PreAuthorize("hasAnyRole('ADMIN','THEKE')") @Transactional public OrderDto cancel(@PathVariable Long id,@RequestBody CancellationRequest req,Authentication auth){
   String reason=req==null?null:req.reason(); if(reason==null||reason.trim().length()<3||reason.trim().length()>500)throw bad("Bitte einen Stornogrund mit 3 bis 500 Zeichen angeben");
   Order order=orders.findByIdForUpdate(id).orElseThrow(()->notFound("Bestellung nicht gefunden"));
   if(order.getStatus()!=OrderStatus.COMPLETED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Bestellung ist bereits storniert");
   Member member=members.findByIdForUpdate(order.getMember().getId()).orElseThrow();
   member.setBalance(member.getBalance().add(order.getTotal()));
   for(OrderItem item: order.getItems()){ if(item.getArticle()!=null){ Article a=articles.findById(item.getArticle().getId()).orElseThrow(); a.setStock(a.getStock().add(BigDecimal.valueOf(item.getQuantity()))); articles.save(a); } else { Drink d=drinks.findById(item.getDrink().getId()).orElseThrow(); d.setStock(d.getStock()+item.getQuantity()); drinks.save(d); } }
   order.setStatus(OrderStatus.CANCELLED);order.setCancelledAt(Instant.now());order.setCancelledBy(auth.getName());order.setCancellationReason(reason.trim());
   return dto(orders.save(order));
 }
 private LocalDate parseDate(String value){try{return LocalDate.parse(value);}catch(DateTimeException e){throw bad("Ungültiges Datum. Erwartet wird JJJJ-MM-TT");}}
 private OrderDto dto(Order o){return new OrderDto(o.getId(),o.getMember().getId(),o.getMember().getName(),o.getTotal(),o.getCreatedAt(),o.getCreatedBy(),o.getStatus(),o.getCancelledAt(),o.getCancelledBy(),o.getCancellationReason(),o.getItems().stream().map(i->{ if(i.getArticle()!=null) return new OrderItemDto(i.getArticle().getId(),i.getArticle().getName(),i.getQuantity(),i.getUnitPrice(),i.getTotal()); Drink d=i.getDrink(); return new OrderItemDto(d.getId(),d.getName(),i.getQuantity(),i.getUnitPrice(),i.getTotal()); }).toList());}
 private ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);} private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}
 public record DrinkDto(Long id,String name,String category,BigDecimal price,String ean,int stock,int warningThreshold,BigDecimal sizeVolume,String sizeUnitShortName,String imageUrl){} public record MemberDto(Long id,String name,BigDecimal balance){}
 public record OrderRequest(Long memberId,List<ItemRequest> items){} public record ItemRequest(Long drinkId,int quantity){} public record CancellationRequest(String reason){}
 public record OrderDto(Long id,Long memberId,String memberName,BigDecimal total,Instant createdAt,String createdBy,OrderStatus status,Instant cancelledAt,String cancelledBy,String cancellationReason,List<OrderItemDto> items){}
 public record OrderItemDto(Long drinkId,String drinkName,int quantity,BigDecimal unitPrice,BigDecimal total){}
}
