package de.bierverein.api;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Instant;

@Entity
@Table(name="devices", indexes={
    @Index(name="idx_devices_barcode", columnList="barcode"),
    @Index(name="idx_devices_serial", columnList="serial_number")
})
public class Device {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=150) private String name;
    @Column(length=80) private String inventoryNumber;
    @Column(length=80) private String barcode;
    @Column(name="serial_number",length=120) private String serialNumber;
    @Column(length=120) private String manufacturer;
    @Column(length=120) private String model;
    @Column(length=80) private String category;
    @Column(length=120) private String location;
    private LocalDate purchaseDate;
    private LocalDate lastInspectionDate;
    private LocalDate nextInspectionDate;
    private Integer inspectionIntervalMonths;
    @Column(length=120) private String responsibleUsername;
    @Column(length=1000) private String notes;
    @Column(nullable=false) private boolean active=true;
    // Nullable for schema migration: existing installations already contain devices.
    // Legacy null values are interpreted and backfilled as OK.
    @Column(length=24) private String operationalStatus="OK";
    private Instant operationalStatusAt;
    @Column(length=1000) private String operationalStatusNote;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="vehicle_compartment_id") private VehicleCompartment compartment;
    private Boolean inspectionRequired=false;
    private Integer placementX=5,placementY=8,placementWidth=38,placementHeight=24;
    @Column(name="placement_rotation") private Integer placementRotation=0;
    @Column(name="placement_layer") private Integer placementLayer=10;
    @Column(name="placement_group_id",length=80) private String placementGroupId;
    @Column(name="compartment_element_id") private Long compartmentElementId;

    public Device(){}
    public Long getId(){return id;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getInventoryNumber(){return inventoryNumber;} public void setInventoryNumber(String v){inventoryNumber=v;}
    public String getBarcode(){return barcode;} public void setBarcode(String v){barcode=v;}
    public String getSerialNumber(){return serialNumber;} public void setSerialNumber(String v){serialNumber=v;}
    public String getManufacturer(){return manufacturer;} public void setManufacturer(String v){manufacturer=v;}
    public String getModel(){return model;} public void setModel(String v){model=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getLocation(){return location;} public void setLocation(String v){location=v;}
    public LocalDate getPurchaseDate(){return purchaseDate;} public void setPurchaseDate(LocalDate v){purchaseDate=v;}
    public LocalDate getLastInspectionDate(){return lastInspectionDate;} public void setLastInspectionDate(LocalDate v){lastInspectionDate=v;}
    public LocalDate getNextInspectionDate(){return nextInspectionDate;} public void setNextInspectionDate(LocalDate v){nextInspectionDate=v;}
    public Integer getInspectionIntervalMonths(){return inspectionIntervalMonths;} public void setInspectionIntervalMonths(Integer v){inspectionIntervalMonths=v;}
    public String getResponsibleUsername(){return responsibleUsername;} public void setResponsibleUsername(String v){responsibleUsername=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
    public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
    public String getOperationalStatus(){return operationalStatus;} public void setOperationalStatus(String v){operationalStatus=v;}
    public Instant getOperationalStatusAt(){return operationalStatusAt;} public void setOperationalStatusAt(Instant v){operationalStatusAt=v;}
    public String getOperationalStatusNote(){return operationalStatusNote;} public void setOperationalStatusNote(String v){operationalStatusNote=v;}
    public VehicleCompartment getCompartment(){return compartment;} public void setCompartment(VehicleCompartment v){compartment=v;}
    public boolean isInspectionRequired(){return Boolean.TRUE.equals(inspectionRequired);} public void setInspectionRequired(boolean v){inspectionRequired=v;}
    public int getPlacementX(){return placementX==null?5:placementX;} public void setPlacementX(int v){placementX=v;}
    public int getPlacementY(){return placementY==null?8:placementY;} public void setPlacementY(int v){placementY=v;}
    public int getPlacementWidth(){return placementWidth==null?38:placementWidth;} public void setPlacementWidth(int v){placementWidth=v;}
    public int getPlacementHeight(){return placementHeight==null?24:placementHeight;} public void setPlacementHeight(int v){placementHeight=v;}
    public int getPlacementRotation(){return placementRotation==null?0:placementRotation;} public void setPlacementRotation(int v){placementRotation=v;}
    public int getPlacementLayer(){return placementLayer==null?10:placementLayer;} public void setPlacementLayer(int v){placementLayer=v;}
    public String getPlacementGroupId(){return placementGroupId;} public void setPlacementGroupId(String v){placementGroupId=v;}
    public Long getCompartmentElementId(){return compartmentElementId;} public void setCompartmentElementId(Long v){compartmentElementId=v;}
}
