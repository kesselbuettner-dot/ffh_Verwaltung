package de.bierverein.api;

import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.common.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class DocumentRecognitionTest {
 private final DocumentTextRecognitionService recognition=new DocumentTextRecognitionService();
 private final DocumentSuggestionsService suggestions=new DocumentSuggestionsService();
 @Test void textFileProducesSearchableLocalText(){
  var result=recognition.extract("Hersteller: Mustertechnik\nSeriennummer: FL-2026-123\n".getBytes(StandardCharsets.UTF_8),"text/plain");
  assertEquals("TEXT",result.method());
  assertTrue(result.text().contains("FL-2026-123"));
  assertEquals("FL-2026-123",suggestions.suggest(result.text(),"Prüfbericht.txt").serialNumber().value());
 }
 @Test void labelledDatesRequireActualCalendarDates(){
  String text="Prüfdatum: 29.02.2025\nNächste Prüfung: 15.09.2028";
  var data=suggestions.suggest(text,"Prüfung");
  assertNull(data.inspectionDate());
  assertEquals("2028-09-15",data.nextInspectionDate().value());
 }
 @Test void OCRReturnsAWarningForAnUnreadableFile(){
  var result=recognition.extract(new byte[]{0,1,2,3},"image/png");
  assertEquals("FAILED",result.method());
  assertNotNull(result.warning());
 }
 @Test void PDFWithRealTextDoesNotRequireTesseract()throws Exception{
  try(var pdf=new PDDocument();var output=new ByteArrayOutputStream()){
   var page=new PDPage(PDRectangle.A4);pdf.addPage(page);
   try(var stream=new PDPageContentStream(pdf,page)){
    stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
    stream.newLineAtOffset(50,750);stream.showText("Fire equipment inspection document with readable searchable text and additional reference notes for extraction and testing");
    stream.endText();
   }
   pdf.save(output);
   var result=recognition.extract(output.toByteArray(),"application/pdf");
   // Short selectable PDFs may use OCR fallback, so this fixture includes enough characters.
   assertEquals("TEXT",result.method());
   assertTrue(result.text().contains("readable searchable text"));
  }
 }
}
