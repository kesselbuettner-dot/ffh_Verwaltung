package de.bierverein.api;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

/** Local-only, bounded text extraction. No upload is sent to an external service. */
@Service
public class DocumentTextRecognitionService {
 static final int MAX_TEXT=21000, MAX_PAGES_OCR=3, MAX_IMAGE_PIXELS=12_000_000;
 public record Result(String text, String method, String warning) {}
 public Result extract(byte[] file,String mime) {
  try {
   return switch(mime){
    case "application/pdf" -> pdf(file);
    case "image/jpeg","image/png" -> new Result(limit(ocr(file)),"LOCAL_OCR",null);
    case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
     try(var doc=new XWPFDocument(new ByteArrayInputStream(file));
         var extractor=new XWPFWordExtractor(doc)){
      yield new Result(limit(extractor.getText()),"TEXT",null);
     }
    }
    case "text/plain" -> new Result(limit(new String(file,StandardCharsets.UTF_8)),"TEXT",null);
    default -> new Result("","UNSUPPORTED","Kein unterstützter Textinhalt");
   };
  }catch(Exception failure){
   // File remains available; processing errors are not logged with extracted content.
   return new Result("","FAILED","Texterkennung nicht verfügbar; Datei bleibt gespeichert.");
  }
 }
 private Result pdf(byte[] data)throws Exception {
  try(var pdf=Loader.loadPDF(data)){
   if(pdf.getNumberOfPages()>80)throw new IOException("Zu viele Seiten");
   String selectable=new PDFTextStripper().getText(pdf);
   if(selectable!=null&&selectable.trim().length()>80)
    return new Result(limit(selectable),"TEXT",null);
   StringBuilder recognized=new StringBuilder();
   PDFRenderer renderer=new PDFRenderer(pdf);
   int count=Math.min(pdf.getNumberOfPages(),MAX_PAGES_OCR);
   for(int i=0;i<count;i++){
    var bitmap=renderer.renderImageWithDPI(i,110);
    if((long)bitmap.getWidth()*bitmap.getHeight()>MAX_IMAGE_PIXELS)
     throw new IOException("Bildauflösung zu groß");
    ByteArrayOutputStream image=new ByteArrayOutputStream();
    if(!ImageIO.write(bitmap,"png",image))throw new IOException("Bildkonvertierung fehlgeschlagen");
    recognized.append(ocr(image.toByteArray())).append('\n');
    if(recognized.length()>=MAX_TEXT)break;
   }
   String text=limit((selectable==null?"":selectable+"\n")+recognized);
   return new Result(text,"LOCAL_OCR",pdf.getNumberOfPages()>count
     ?"Für die Texterkennung wurden nur die ersten "+count+" Seiten berücksichtigt.":null);
  }
 }
 private String ocr(byte[] image)throws Exception {
  // Bound compressed size and decoded resolution before spawning OCR.
  if(image.length>9_000_000)throw new IOException("Bild zu groß");
  try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(image))){
   var readers=ImageIO.getImageReaders(stream);
   if(!readers.hasNext())throw new IOException("Bild nicht lesbar");
   var reader=readers.next();
   try{reader.setInput(stream);if((long)reader.getWidth(0)*reader.getHeight(0)>MAX_IMAGE_PIXELS)
     throw new IOException("Bildauflösung zu groß");}
   finally{reader.dispose();}
  }
  Process proc=new ProcessBuilder("tesseract","stdin","stdout","-l","deu+eng","--psm","3")
   .redirectError(ProcessBuilder.Redirect.DISCARD).start();
  try{
   var output=new ByteArrayOutputStream();
   Thread writer=Thread.ofVirtual().start(()->{
    try(OutputStream input=proc.getOutputStream()){input.write(image);}
    catch(IOException ignored){/* process may have exited */ }
   });
   Thread reader=Thread.ofVirtual().start(()->{
    try(InputStream in=proc.getInputStream()){
     byte[] block=new byte[4096];int size;
     while((size=in.read(block))!=-1){
      if(output.size()+size>MAX_TEXT*5){proc.destroyForcibly();return;}
      output.write(block,0,size);
     }
    }catch(IOException ignored){/* process may have exited */}
   });
   boolean finished=proc.waitFor(20,TimeUnit.SECONDS);
   if(!finished)proc.destroyForcibly();
   writer.join(2000);reader.join(2000);
   if(!finished||proc.exitValue()!=0)throw new IOException("OCR nicht erfolgreich");
   return output.toString(StandardCharsets.UTF_8);
  }finally{if(proc.isAlive())proc.destroyForcibly();}
 }
 static String limit(String text){return text==null?"":text.substring(0,Math.min(MAX_TEXT,text.length()));}
}
