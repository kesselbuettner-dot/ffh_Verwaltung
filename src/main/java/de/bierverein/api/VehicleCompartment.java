package de.bierverein.api;
import jakarta.persistence.*;
@Entity @Table(name="vehicle_compartments")
public class VehicleCompartment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="vehicle_id") private FireVehicle vehicle;
 @Column(nullable=false,length=80) private String name;
 @Column(nullable=false,length=24) private String area="DRIVER";
 @Column(nullable=false) private int gridX=1; @Column(nullable=false) private int gridY=1;
 @Column(nullable=false) private int gridWidth=2; @Column(nullable=false) private int gridHeight=1;
 public Long getId(){return id;} public FireVehicle getVehicle(){return vehicle;} public void setVehicle(FireVehicle v){vehicle=v;}
 public String getName(){return name;} public void setName(String v){name=v;} public String getArea(){return area;} public void setArea(String v){area=v;}
 public int getGridX(){return gridX;} public void setGridX(int v){gridX=v;} public int getGridY(){return gridY;} public void setGridY(int v){gridY=v;}
 public int getGridWidth(){return gridWidth;} public void setGridWidth(int v){gridWidth=v;} public int getGridHeight(){return gridHeight;} public void setGridHeight(int v){gridHeight=v;}
}
