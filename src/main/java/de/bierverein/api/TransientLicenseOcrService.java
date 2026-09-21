package de.bierverein.api;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Processes one camera image in RAM/stdin; does not write a photo or OCR text to storage or logs. */
@Service
public class TransientLicenseOcrService {
 public String read(String encoded) {
  if(encoded==null||encoded.length()>7_500_000)throw reject("Bild fehlt oder ist zu groß");
  String payload=encoded.replaceFirst("^data:image/(?:jpeg|png);base64,","");
  byte[] image;
  try { image=Base64.getDecoder().decode(payload); }
  catch(IllegalArgumentException ex){throw reject("Bildformat ungültig");}
  if(image.length<100||image.length>5_000_000){Arrays.fill(image,(byte)0);throw reject("Bildgröße nicht erlaubt");}
  boolean png=image[0]==(byte)0x89&&image[1]==0x50&&image[2]==0x4e&&image[3]==0x47;
  boolean jpeg=image[0]==(byte)0xff&&image[1]==(byte)0xd8&&image[2]==(byte)0xff;
  if(!png&&!jpeg){Arrays.fill(image,(byte)0);throw reject("Nur PNG oder JPEG zulässig");}
  Process process=null;
  try{
   // Tesseract can OCR from stdin to stdout; neither a temp upload nor output file is created.
   process=new ProcessBuilder("tesseract","stdin","stdout","-l","deu+eng","--psm","11")
     .redirectError(ProcessBuilder.Redirect.DISCARD).start();
   final Process proc=process;
   Thread writer=Thread.ofVirtual().start(()->{
    try(OutputStream input=proc.getOutputStream()){input.write(image);input.flush();}
    catch(IOException ignored){/* OCR process error is handled below. */}
   });
   ByteArrayOutputStream output=new ByteArrayOutputStream(16_384);
   Thread reader=Thread.ofVirtual().start(()->{
    try(InputStream stdout=proc.getInputStream()){
     byte[] chunk=new byte[4096];int n;
     while((n=stdout.read(chunk))>=0){
      if(output.size()+n>200_000){proc.destroyForcibly();return;}
      output.write(chunk,0,n);
     }
    }catch(IOException ignored){/* OCR process error is handled below. */}
   });
   boolean completed=process.waitFor(25,TimeUnit.SECONDS);
   if(!completed){process.destroyForcibly();process.waitFor(3,TimeUnit.SECONDS);}
   writer.join(3000);reader.join(3000);
   if(!completed||process.exitValue()!=0||output.size()==0)
    throw reject("Texterkennung nicht möglich. Bitte ein besseres Foto aufnehmen oder Wehrleitung kontaktieren.");
   return output.toString(StandardCharsets.UTF_8);
  }catch(ResponseStatusException ex){throw ex;}
   catch(Exception ex){
    if(ex instanceof InterruptedException)Thread.currentThread().interrupt();
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
      "Automatische Texterkennung momentan nicht verfügbar. Bitte Wehrleitung kontaktieren.");
   }finally{
    if(process!=null&&process.isAlive())process.destroyForcibly();
    Arrays.fill(image,(byte)0);
   }
 }
 private ResponseStatusException reject(String message){return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,message);}
}
