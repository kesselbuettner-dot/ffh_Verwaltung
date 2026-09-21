package de.bierverein.api;

import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/settings/menu-icons")
public class MenuIconController {
    private final MenuIconRepository repo;
    private static final Set<String> TAGS = Set.of("svg","g","path","circle","rect","ellipse","line","polyline","polygon","title","desc");
    private static final Set<String> ATTRS = Set.of("xmlns","width","height","viewBox","fill","stroke","stroke-width","stroke-linecap",
        "stroke-linejoin","stroke-miterlimit","d","cx","cy","x","y","rx","ry","r","x1","x2","y1","y2","points",
        "transform","opacity","fill-rule","clip-rule","id");
    public MenuIconController(MenuIconRepository repo) { this.repo = repo; }

    public record IconDto(Long id, String name, String uri) {}
    private IconDto dto(MenuIcon icon) {
        return new IconDto(icon.getId(), icon.getName(), "data:" + icon.getContentType() + ";base64,"
                + Base64.getEncoder().encodeToString(icon.getImageData()));
    }

    @GetMapping
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public List<IconDto> list() {
        return repo.findAll().stream().map(this::dto).collect(Collectors.toList());
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public IconDto upload(@RequestParam("file") MultipartFile file, @RequestParam("name") String name) {
        if (file == null || file.isEmpty() || file.getSize() > 160_000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Icon muss kleiner als 160 KB sein.");
        if (repo.count() >= 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximal 50 Icons.");
        String label = name == null ? "" : name.trim();
        if (label.isBlank() || label.length() > 60) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Icon-Name ungültig.");
        String type = file.getContentType();
        try {
            byte[] data = file.getBytes();
            if ("image/png".equals(type)) {
                byte[] signature = {(byte)137,80,78,71,13,10,26,10};
                if (data.length < 24 || !Arrays.equals(Arrays.copyOf(data,8), signature))
                    throw new IllegalArgumentException("Keine gültige PNG-Datei");
            } else if ("image/svg+xml".equals(type)) {
                data = cleanSvg(data);
            } else {
                throw new IllegalArgumentException("Nur PNG und sichere SVG-Grafiken sind erlaubt.");
            }
            MenuIcon icon = new MenuIcon();
            icon.setName(label);
            icon.setContentType(type);
            icon.setImageData(data);
            return dto(repo.save(icon));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Icon konnte nicht verarbeitet werden.");
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void remove(@PathVariable Long id) {
        if (!repo.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        repo.deleteById(id);
    }

    private byte[] cleanSvg(byte[] source) throws Exception {
        String xml = new String(source, StandardCharsets.UTF_8);
        if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
            throw new IllegalArgumentException("SVG-Dokumenttyp und Entities sind nicht erlaubt.");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        var document = builder.parse(new InputSource(new StringReader(xml)));
        var root = document.getDocumentElement();
        if (!"svg".equals(root.getLocalName()) || !"http://www.w3.org/2000/svg".equals(root.getNamespaceURI()))
            throw new IllegalArgumentException("SVG-Wurzelelement ist ungültig.");
        validate(root, 0);
        var transformer = TransformerFactory.newInstance();
        transformer.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformer.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformer.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var serializer = transformer.newTransformer();
        serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        var output = new ByteArrayOutputStream();
        serializer.transform(new DOMSource(document), new StreamResult(output));
        if (output.size() > 160_000) throw new IllegalArgumentException("SVG ist zu groß.");
        return output.toByteArray();
    }

    private void validate(Element element, int depth) {
        if (depth > 16 || !TAGS.contains(element.getLocalName())
                || !"http://www.w3.org/2000/svg".equals(element.getNamespaceURI()))
            throw new IllegalArgumentException("Das SVG enthält nicht erlaubte Elemente.");
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();
            if (!ATTRS.contains(name) || value.length() > 6000
                    || value.matches("(?is).*(url\\s*\\(|javascript:|data:|[<>]).*")
                    || (name.equals("xmlns") && !value.equals("http://www.w3.org/2000/svg")))
                throw new IllegalArgumentException("Das SVG enthält nicht erlaubte Attribute.");
        }
        var children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element sub) validate(sub, depth + 1);
            else if (child.getNodeType() != Node.TEXT_NODE)
                throw new IllegalArgumentException("Das SVG enthält nicht erlaubte Inhalte.");
        }
    }
}
