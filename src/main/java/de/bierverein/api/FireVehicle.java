package de.bierverein.api;
import jakarta.persistence.*;
@Entity @Table(name="fire_vehicles")
public class FireVehicle {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=120) private String name;
 @Column(length=40) private String callSign;
 @Column(length=1000) private String notes;
 @Column(nullable=false) private boolean active=true;
 public Long getId(){return id;} public String getName(){return name;} public void setName(String v){name=v;}
 public String getCallSign(){return callSign;} public void setCallSign(String v){callSign=v;} public String getNotes(){return notes;} public void setNotes(String v){notes=v;}
 public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
