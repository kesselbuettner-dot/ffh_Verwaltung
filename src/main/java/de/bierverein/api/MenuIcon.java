package de.bierverein.api;

import jakarta.persistence.*;

@Entity
@Table(name = "menu_icons")
public class MenuIcon {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 60)
    private String name;
    @Column(nullable = false, length = 30)
    private String contentType;
    @Lob @Basic(fetch = FetchType.LAZY)
    private byte[] imageData;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getContentType() { return contentType; }
    public void setContentType(String value) { contentType = value; }
    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] value) { imageData = value; }
}
