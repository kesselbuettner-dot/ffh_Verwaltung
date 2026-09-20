package de.bierverein.api;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/vehicles")
public class FireVehicleController {
 private static final Set<String> AREAS=Set.of("DRIVER","PASSENGER","CREW","ROOF","REAR","FRONT");
 private static final Set<String> ELEMENT_TYPES=Set.of("SHELF","DRAWER","PULL_OUT","HOLDER","DIVIDER","BOX","FREE_SPACE");
 private final FireVehicleRepository vehicles;private final VehicleCompartmentRepository compartments;private final VehicleCompartmentElementRepository elements;private final DeviceRepository devices;
 public FireVehicleController(FireVehicleRepository v,VehicleCompartmentRepository c,VehicleCompartmentElementRepository e,DeviceRepository d){vehicles=v;compartments=c;elements=e;devices=d;}

 @GetMapping @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'read')") @Transactional(readOnly=true)
 public List<VehicleDto> all(){return vehicles.findByActiveTrueOrderByNameAsc().stream().map(this::dto).toList();}
 @PostMapping @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write')")
 public VehicleDto create(@RequestBody VehicleRequest r){if(r==null||r.name()==null||r.name().isBlank())throw bad("Fahrzeugname ist erforderlich.");FireVehicle v=new FireVehicle();v.setName(trim(r.name(),120));v.setCallSign(trim(r.callSign(),40));v.setNotes(trim(r.notes(),1000));v.setFireRelevant(!Boolean.FALSE.equals(r.fireRelevant()));return dto(vehicles.save(v));}
 @PutMapping("/{id}") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write')")
 public VehicleDto update(@PathVariable Long id,@RequestBody VehicleRequest r){FireVehicle v=find(id);if(r==null||r.name()==null||r.name().isBlank())throw bad("Fahrzeugname ist erforderlich.");v.setName(trim(r.name(),120));v.setCallSign(trim(r.callSign(),40));v.setNotes(trim(r.notes(),1000));if(r.fireRelevant()!=null)v.setFireRelevant(r.fireRelevant());return dto(vehicles.save(v));}

 @PostMapping("/{id}/compartments") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write')")
 public CompartmentDto compartment(@PathVariable Long id,@RequestBody CompartmentRequest r){VehicleCompartment c=new VehicleCompartment();c.setVehicle(find(id));apply(c,r);return compartmentDto(compartments.save(c));}
 @PutMapping("/compartments/{id}") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write')")
 public CompartmentDto updateCompartment(@PathVariable Long id,@RequestBody CompartmentRequest r){VehicleCompartment c=compartment(id);apply(c,r);return compartmentDto(compartments.save(c));}

 @PostMapping("/compartments/{id}/elements") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write')")
 public ElementDto createElement(@PathVariable Long id,@RequestBody ElementRequest r){VehicleCompartmentElement e=new VehicleCompartmentElement();e.setCompartment(compartment(id));apply(e,r);return elementDto(elements.save(e));}
 @PutMapping("/elements/{id}") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write')")
 public ElementDto updateElement(@PathVariable Long id,@RequestBody ElementRequest r){VehicleCompartmentElement e=element(id);apply(e,r);return elementDto(elements.save(e));}
 @DeleteMapping("/elements/{id}") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write')") @Transactional
 public ResponseEntity<Void> deleteElement(@PathVariable Long id){VehicleCompartmentElement e=element(id);devices.findByCompartmentIdAndActiveTrueOrderByNameAsc(e.getCompartment().getId()).stream().filter(d->Objects.equals(d.getCompartmentElementId(),id)).forEach(d->{d.setCompartmentElementId(null);devices.save(d);});elements.delete(e);return ResponseEntity.noContent().build();}

 @PutMapping("/compartments/{id}/device/{deviceId}") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write') and @devicePermissionGuard.allowed(authentication,'write')")
 public ResponseEntity<Void> assign(@PathVariable Long id,@PathVariable Long deviceId){VehicleCompartment c=compartment(id);Device d=device(deviceId);d.setCompartment(c);d.setLocation(c.getName());d.setCompartmentElementId(null);devices.save(d);return ResponseEntity.noContent().build();}
 @PutMapping("/compartments/{id}/device/{deviceId}/placement") @PreAuthorize("@vehiclePermissionGuard.allowed(authentication,'write') and @devicePermissionGuard.allowed(authentication,'write')")
 public ResponseEntity<Void> place(@PathVariable Long id,@PathVariable Long deviceId,@RequestBody PlacementRequest r){VehicleCompartment c=compartment(id);Device d=device(deviceId);boolean moved=d.getCompartment()==null||!Objects.equals(d.getCompartment().getId(),id);d.setCompartment(c);d.setLocation(c.getName());if(r!=null&&r.x()!=null)d.setPlacementX(bound(r.x(),0,95));if(r!=null&&r.y()!=null)d.setPlacementY(bound(r.y(),0,92));if(r!=null&&r.width()!=null)d.setPlacementWidth(bound(r.width(),6,100));if(r!=null&&r.height()!=null)d.setPlacementHeight(bound(r.height(),6,100));if(r!=null&&r.rotation()!=null)d.setPlacementRotation(bound(r.rotation(),-180,180));if(r!=null&&r.layer()!=null)d.setPlacementLayer(bound(r.layer(),1,999));if(r!=null&&r.groupId()!=null)d.setPlacementGroupId(trim(r.groupId(),80));Long elementId=r==null?null:r.elementId();if(elementId!=null){VehicleCompartmentElement e=element(elementId);if(!Objects.equals(e.getCompartment().getId(),id))throw bad("Das Ablageelement gehört nicht zu diesem Geräteraum.");d.setCompartmentElementId(elementId);}else if(moved||r!=null&&Boolean.TRUE.equals(r.clearElement()))d.setCompartmentElementId(null);devices.save(d);return ResponseEntity.noContent().build();}

 private void apply(VehicleCompartment c,CompartmentRequest r){if(r==null||r.name()==null||r.name().isBlank())throw bad("Fachbezeichnung ist erforderlich.");String area=Optional.ofNullable(r.area()).orElse("DRIVER").toUpperCase(Locale.ROOT);if(!AREAS.contains(area))throw bad("Ungültiger Fahrzeugbereich.");c.setName(trim(r.name(),80));c.setArea(area);c.setGridX(bound(r.gridX(),1,6));c.setGridY(bound(r.gridY(),1,20));c.setGridWidth(bound(r.gridWidth(),1,6));c.setGridHeight(bound(r.gridHeight(),1,5));}
 private void apply(VehicleCompartmentElement e,ElementRequest r){if(r==null)throw bad("Elementdaten fehlen.");String type=Optional.ofNullable(r.type()).orElse("SHELF").toUpperCase(Locale.ROOT);if(!ELEMENT_TYPES.contains(type))throw bad("Ungültiger Elementtyp.");e.setType(type);e.setName(Optional.ofNullable(trim(r.name(),80)).orElse(elementLabel(type)));e.setPositionX(bound(r.x(),0,95));e.setPositionY(bound(r.y(),0,95));e.setWidth(bound(r.width(),3,100));e.setHeight(bound(r.height(),3,100));e.setRotation(bound(r.rotation(),-180,180));e.setLayer(bound(r.layer(),1,500));}
 private String elementLabel(String type){return switch(type){case "DRAWER"->"Schublade";case "PULL_OUT"->"Auszug";case "HOLDER"->"Halterung";case "DIVIDER"->"Trennwand";case "BOX"->"Gerätekasten";case "FREE_SPACE"->"Ablage";default->"Regalboden";};}

 private VehicleDto dto(FireVehicle v){return new VehicleDto(v.getId(),v.getName(),v.getCallSign(),v.getNotes(),v.isFireRelevant(),compartments.findByVehicleIdOrderByGridYAscGridXAsc(v.getId()).stream().map(this::compartmentDto).toList());}
 private CompartmentDto compartmentDto(VehicleCompartment c){return new CompartmentDto(c.getId(),c.getName(),c.getArea(),c.getGridX(),c.getGridY(),c.getGridWidth(),c.getGridHeight(),elements.findByCompartmentIdOrderByLayerAscIdAsc(c.getId()).stream().map(this::elementDto).toList(),devices.findByCompartmentIdAndActiveTrueOrderByNameAsc(c.getId()).stream().map(this::deviceDto).toList());}
 private ElementDto elementDto(VehicleCompartmentElement e){return new ElementDto(e.getId(),e.getType(),e.getName(),e.getPositionX(),e.getPositionY(),e.getWidth(),e.getHeight(),e.getRotation(),e.getLayer());}
 private VehicleDeviceDto deviceDto(Device d){return new VehicleDeviceDto(d.getId(),d.getName(),d.getInventoryNumber(),d.getCategory(),d.getOperationalStatus(),d.getOperationalStatusNote(),d.isInspectionRequired(),d.getPlacementX(),d.getPlacementY(),d.getPlacementWidth(),d.getPlacementHeight(),d.getPlacementRotation(),d.getPlacementLayer(),d.getPlacementGroupId(),d.getCompartmentElementId());}
 private FireVehicle find(Long id){return vehicles.findById(id).orElseThrow(()->notFound("Fahrzeug nicht gefunden."));}private VehicleCompartment compartment(Long id){return compartments.findById(id).orElseThrow(()->notFound("Fach nicht gefunden."));}private VehicleCompartmentElement element(Long id){return elements.findById(id).orElseThrow(()->notFound("Geräteraumelement nicht gefunden."));}private Device device(Long id){return devices.findById(id).orElseThrow(()->notFound("Gerät nicht gefunden."));}
 private int bound(Integer v,int min,int max){return Math.max(min,Math.min(max,v==null?min:v));}private String trim(String s,int max){if(s==null||s.isBlank())return null;s=s.trim();return s.substring(0,Math.min(max,s.length()));}private ResponseStatusException bad(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}

 public record VehicleRequest(String name,String callSign,String notes,Boolean fireRelevant){}
 public record CompartmentRequest(String name,String area,Integer gridX,Integer gridY,Integer gridWidth,Integer gridHeight){}
 public record ElementRequest(String type,String name,Integer x,Integer y,Integer width,Integer height,Integer rotation,Integer layer){}
 public record PlacementRequest(Integer x,Integer y,Integer width,Integer height,Integer rotation,Integer layer,String groupId,Long elementId,Boolean clearElement){}
 public record ElementDto(Long id,String type,String name,int x,int y,int width,int height,int rotation,int layer){}
 public record VehicleDeviceDto(Long id,String name,String inventoryNumber,String category,String status,String statusNote,boolean inspectionRequired,int placementX,int placementY,int placementWidth,int placementHeight,int placementRotation,int placementLayer,String placementGroupId,Long compartmentElementId){}
 public record CompartmentDto(Long id,String name,String area,int gridX,int gridY,int gridWidth,int gridHeight,List<ElementDto> elements,List<VehicleDeviceDto> devices){}
 public record VehicleDto(Long id,String name,String callSign,String notes,boolean fireRelevant,List<CompartmentDto> compartments){}
}
