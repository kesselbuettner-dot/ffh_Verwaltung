package de.bierverein.api;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="device_inspections")
public class DeviceInspection {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) private Device device;
    @Column(nullable=false) private LocalDate inspectionDate;
    private LocalDate nextInspectionDate;
    @Column(length=120) private String inspectionType;
    @Column(length=40) private String result;
    @Column(length=120) private String inspector;
    @Column(length=2000) private String defects;
    @Column(length=2000) private String measures;
    @Column(length=2000) private String notes;
    @Column(length=100000) private String signatureData;
    private java.time.Instant signedAt;
    private Long cycleTaskId;
    private Long sessionTaskId;
    private Long sessionReportId;
    @Column(length=150) private String deviceNameSnapshot;
    @Column(length=150) private String locationSnapshot;
    @Column(length=80) private String inventorySnapshot;

    public String getSignatureData(){return signatureData;} public void setSignatureData(String x){signatureData=x;}
    public java.time.Instant getSignedAt(){return signedAt;} public void setSignedAt(java.time.Instant x){signedAt=x;}
    public Long getCycleTaskId(){return cycleTaskId;} public void setCycleTaskId(Long x){cycleTaskId=x;}
    public Long getSessionTaskId(){return sessionTaskId;} public void setSessionTaskId(Long x){sessionTaskId=x;}
    public Long getSessionReportId(){return sessionReportId;} public void setSessionReportId(Long x){sessionReportId=x;}
    public String getDeviceNameSnapshot(){return deviceNameSnapshot;} public void setDeviceNameSnapshot(String x){deviceNameSnapshot=x;}
    public String getLocationSnapshot(){return locationSnapshot;} public void setLocationSnapshot(String x){locationSnapshot=x;}
    public String getInventorySnapshot(){return inventorySnapshot;} public void setInventorySnapshot(String x){inventorySnapshot=x;}
    public DeviceInspection(){}
    public Long getId(){return id;}
    public Device getDevice(){return device;} public void setDevice(Device v){device=v;}
    public LocalDate getInspectionDate(){return inspectionDate;} public void setInspectionDate(LocalDate v){inspectionDate=v;}
    public LocalDate getNextInspectionDate(){return nextInspectionDate;} public void setNextInspectionDate(LocalDate v){nextInspectionDate=v;}
    public String getInspectionType(){return inspectionType;} public void setInspectionType(String v){inspectionType=v;}
    public String getResult(){return result;} public void setResult(String v){result=v;}
    public String getInspector(){return inspector;} public void setInspector(String v){inspector=v;}
    public String getDefects(){return defects;} public void setDefects(String v){defects=v;}
    public String getMeasures(){return measures;} public void setMeasures(String v){measures=v;}
    public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
}
