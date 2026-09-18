package de.bierverein.api;
import org.springframework.http.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile; import org.springframework.web.server.ResponseStatusException;
import java.io.*; import java.nio.file.*; import java.util.*;
@RestController @RequestMapping("/api/inventory/articles")
public class ArticleImageController {
 private static final long MAX_IMAGE_SIZE=2L*1024*1024; private static final Set<String> TYPES=Set.of("image/jpeg","image/png","image/webp","image/gif");
 private final DrinkRepository drinks; private final Path storage=Paths.get("/app/data/article-images");
 public ArticleImageController(DrinkRepository d){drinks=d;}
 @PostMapping(value="/{id}/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAnyRole('ADMIN','GETRAENKEWART')")
 public Map<String,String> upload(@PathVariable Long id,@RequestPart("file") MultipartFile file)throws IOException{
  Drink d=drinks.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Artikel nicht gefunden")); if(file==null||file.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte ein Bild auswählen");
  if(file.getSize()>MAX_IMAGE_SIZE)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bild darf maximal 2 MB groß sein"); String type=Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
  if(!TYPES.contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nur JPG, PNG, WEBP oder GIF sind erlaubt"); Files.createDirectories(storage);
  String ext=".jpg"; if("image/png".equals(type)) ext=".png"; else if("image/webp".equals(type)) ext=".webp"; else if("image/gif".equals(type)) ext=".gif"; String stored=id+"-"+UUID.randomUUID()+ext; Files.copy(file.getInputStream(),storage.resolve(stored),StandardCopyOption.REPLACE_EXISTING);
  String old=d.getImageUrl(); d.setImageUrl("/uploads/articles/"+stored); drinks.save(d);
  if(old!=null&&old.startsWith("/uploads/articles/"))Files.deleteIfExists(storage.resolve(Paths.get(old.substring("/uploads/articles/".length())).getFileName()));
  return Map.of("imageUrl",d.getImageUrl());
 }
}