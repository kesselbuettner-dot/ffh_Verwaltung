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
