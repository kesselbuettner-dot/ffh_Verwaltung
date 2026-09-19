package de.bierverein.api;

import jakarta.persistence.*;
import java.time.LocalDate;

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
}
