package de.bierverein.api;

import jakarta.persistence.*;

@Entity
@Table(name="vehicle_compartment_elements",indexes=@Index(name="idx_compartment_elements_compartment",columnList="compartment_id"))
public class VehicleCompartmentElement {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.EAGER) @JoinColumn(name="compartment_id") private VehicleCompartment compartment;
 @Column(nullable=false,length=24) private String type="SHELF";
 @Column(nullable=false,length=80) private String name="Regal";
 @Column(name="position_x") private Integer positionX=5;
 @Column(name="position_y") private Integer positionY=10;
 @Column(name="element_width") private Integer width=90;
 @Column(name="element_height") private Integer height=12;
 @Column(name="rotation_degrees") private Integer rotation=0;
 @Column(name="display_layer") private Integer layer=1;

 public Long getId(){return id;} public VehicleCompartment getCompartment(){return compartment;} public void setCompartment(VehicleCompartment v){compartment=v;}
 public String getType(){return type;} public void setType(String v){type=v;} public String getName(){return name;} public void setName(String v){name=v;}
 public int getPositionX(){return positionX==null?5:positionX;} public void setPositionX(int v){positionX=v;} public int getPositionY(){return positionY==null?10:positionY;} public void setPositionY(int v){positionY=v;}
 public int getWidth(){return width==null?90:width;} public void setWidth(int v){width=v;} public int getHeight(){return height==null?12:height;} public void setHeight(int v){height=v;}
 public int getRotation(){return rotation==null?0:rotation;} public void setRotation(int v){rotation=v;} public int getLayer(){return layer==null?1:layer;} public void setLayer(int v){layer=v;}
}
