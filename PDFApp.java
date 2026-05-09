import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.rendering.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import com.sun.net.httpserver.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class PDFApp {

    static final String OUT = "outputs/";

    // ── PDF Operations ────────────────────────────────────────────────────

    static List<Integer> parsePageSpec(String spec, int total) {
        Set<Integer> pages = new TreeSet<>();
        for (String part : spec.split(",")) {
            part = part.trim();
            if (part.contains("-")) {
                String[] range = part.split("-", 2);
                int a = Integer.parseInt(range[0].trim()) - 1;
                int b = Integer.parseInt(range[1].trim()) - 1;
                for (int i = a; i <= b && i < total; i++) if (i >= 0) pages.add(i);
            } else if (!part.isEmpty()) {
                int p = Integer.parseInt(part) - 1;
                if (p >= 0 && p < total) pages.add(p);
            }
        }
        return new ArrayList<>(pages);
    }

    static byte[] docToBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.save(bos); doc.close(); return bos.toByteArray();
    }

    static PDDocument fromBytes(byte[] data) throws IOException {
        return PDDocument.load(new ByteArrayInputStream(data));
    }

    static byte[] mergePDFs(byte[] pdf1, byte[] pdf2) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        merger.setDestinationStream(bos);
        merger.addSource(new ByteArrayInputStream(pdf1));
        merger.addSource(new ByteArrayInputStream(pdf2));
        merger.mergeDocuments(null);
        return bos.toByteArray();
    }

    static byte[][] splitPDF(byte[] pdf, int chunkSize) throws Exception {
        PDDocument doc = fromBytes(pdf);
        Splitter splitter = new Splitter();
        splitter.setSplitAtPage(chunkSize);
        List<PDDocument> parts = splitter.split(doc);
        byte[][] result = new byte[parts.size()][];
        for (int i = 0; i < parts.size(); i++) result[i] = docToBytes(parts.get(i));
        doc.close(); return result;
    }

    static byte[] extractPages(byte[] pdf, String pageSpec) throws Exception {
        PDDocument src = fromBytes(pdf);
        List<Integer> pages = parsePageSpec(pageSpec, src.getNumberOfPages());
        PDDocument dest = new PDDocument();
        for (int idx : pages) dest.addPage(src.getPage(idx));
        byte[] result = docToBytes(dest); src.close(); return result;
    }

    static byte[] deletePages(byte[] pdf, String pageSpec) throws Exception {
        PDDocument src = fromBytes(pdf);
        int total = src.getNumberOfPages();
        Set<Integer> toDelete = new HashSet<>(parsePageSpec(pageSpec, total));
        PDDocument dest = new PDDocument();
        for (int i = 0; i < total; i++)
            if (!toDelete.contains(i)) dest.addPage(src.getPage(i));
        byte[] result = docToBytes(dest); src.close(); return result;
    }

    static byte[] protectPDF(byte[] pdf, String password) throws Exception {
        PDDocument doc = fromBytes(pdf);
        AccessPermission ap = new AccessPermission();
        ap.setCanPrint(false); ap.setCanModify(false); ap.setCanExtractContent(false);
        StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, ap);
        policy.setEncryptionKeyLength(256); policy.setPreferAES(true);
        doc.protect(policy);
        return docToBytes(doc);
    }

    static byte[][] pdfToImages(byte[] pdf, String format) throws Exception {
        PDDocument doc = fromBytes(pdf);
        PDFRenderer renderer = new PDFRenderer(doc);
        int n = doc.getNumberOfPages();
        byte[][] images = new byte[n][];
        String fmt = format.equalsIgnoreCase("jpeg") ? "jpeg" : "png";
        for (int i = 0; i < n; i++) {
            BufferedImage img = renderer.renderImageWithDPI(i, 144);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, fmt, bos);
            images[i] = bos.toByteArray();
        }
        doc.close(); return images;
    }

    static String extractText(byte[] pdf) throws Exception {
        PDDocument doc = fromBytes(pdf);
        String text = new PDFTextStripper().getText(doc);
        doc.close(); return text;
    }

    static PDFont loadFont(PDDocument doc, boolean bold) {
        String[] paths = bold ? new String[]{
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf"
        } : new String[]{
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSans.ttf"
        };
        for (String path : paths) {
            try { File f = new File(path); if (f.exists()) return PDType0Font.load(doc, f); }
            catch (Exception e) {}
        }
        return null;
    }

    static byte[] createPDF(String title, String content) throws Exception {
        content = content.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
        title = title.replaceAll("[\\r\\n]", "");
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float pageHeight = page.getMediaBox().getHeight();
        float pageWidth = page.getMediaBox().getWidth();
        float margin = 50, yPos = pageHeight - 60, lineH = 18;
        PDFont fontNormal = loadFont(doc, false);
        PDFont fontBold = loadFont(doc, true);
        if (fontNormal == null) fontNormal = PDType1Font.HELVETICA;
        if (fontBold == null) fontBold = PDType1Font.HELVETICA_BOLD;
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        cs.beginText(); cs.setFont(fontBold, 18);
        cs.newLineAtOffset(margin, yPos);
        cs.showText(title); cs.endText(); yPos -= 40;
        cs.setFont(fontNormal, 12);
        float maxWidth = pageWidth - 2 * margin;
        for (String para : content.split("\n")) {
            if (para.trim().isEmpty()) { yPos -= lineH; continue; }
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                if (word.isEmpty()) continue;
                String test = line.length() == 0 ? word : line + " " + word;
                float w = fontNormal.getStringWidth(test) / 1000 * 12;
                if (w < maxWidth) { line = new StringBuilder(test); }
                else {
                    if (line.length() > 0) {
                        cs.beginText(); cs.newLineAtOffset(margin, yPos);
                        cs.showText(line.toString()); cs.endText(); yPos -= lineH;
                    }
                    line = new StringBuilder(word);
                    if (yPos < 60) {
                        cs.close();
                        PDPage np = new PDPage(PDRectangle.A4); doc.addPage(np);
                        cs = new PDPageContentStream(doc, np);
                        cs.setFont(fontNormal, 12); yPos = pageHeight - 60;
                    }
                }
            }
            if (line.length() > 0) {
                cs.beginText(); cs.newLineAtOffset(margin, yPos);
                cs.showText(line.toString()); cs.endText(); yPos -= lineH;
            }
            yPos -= 6;
        }
        cs.close(); return docToBytes(doc);
    }

    // ── HTTP Utilities ────────────────────────────────────────────────────

    static void addCORS(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST,GET,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
    }

    static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        addCORS(ex);
        byte[] b = json.getBytes("UTF-8");
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b); ex.getResponseBody().close();
    }

    static void ok(HttpExchange ex, String url, String msg) throws IOException {
        sendJson(ex, 200, "{\"success\":true,\"message\":\"" + msg + "\",\"download_url\":\"" + url + "\"}");
    }

    static void okText(HttpExchange ex, String url, String msg, String text) throws IOException {
        String safe = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        sendJson(ex, 200, "{\"success\":true,\"message\":\"" + msg + "\",\"download_url\":\"" + url + "\",\"text\":\"" + safe + "\"}");
    }

    static void err(HttpExchange ex, String msg) throws IOException {
        sendJson(ex, 400, "{\"success\":false,\"error\":\"" + msg + "\"}");
    }

    static String save(byte[] data, String suffix) throws IOException {
        String name = UUID.randomUUID().toString().replace("-", "") + suffix;
        Files.write(Paths.get(OUT + name), data);
        return "/download/" + name;
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096]; int n;
        while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    static MultipartData parseMultipart(HttpExchange ex) throws Exception {
        return new MultipartData(ex);
    }

    // ── Handlers ─────────────────────────────────────────────────────────

    static class MergeHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                List<byte[]> fs = mp.getFiles("files");
                if (fs.size() < 2) { err(ex, "2 fichiers requis."); return; }
                byte[] r = mergePDFs(fs.get(0), fs.get(1));
                ok(ex, save(r, "_merged.pdf"), "PDF fusionné !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class SplitHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                int chunk = Integer.parseInt(mp.getField("chunk_size", "2"));
                byte[][] parts = splitPDF(mp.getFile("file"), chunk);
                ByteArrayOutputStream zb = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(zb)) {
                    for (int i = 0; i < parts.length; i++) {
                        zos.putNextEntry(new ZipEntry("partie_" + (i + 1) + ".pdf"));
                        zos.write(parts[i]); zos.closeEntry();
                    }
                }
                ok(ex, save(zb.toByteArray(), "_split.zip"), parts.length + " partie(s).");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class ExtractPagesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                byte[] r = extractPages(mp.getFile("file"), mp.getField("pages", ""));
                ok(ex, save(r, "_extracted.pdf"), "Pages extraites !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class DeletePagesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                byte[] r = deletePages(mp.getFile("file"), mp.getField("pages", ""));
                ok(ex, save(r, "_deleted.pdf"), "Pages supprimées !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class ProtectHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                byte[] r = protectPDF(mp.getFile("file"), mp.getField("password", ""));
                ok(ex, save(r, "_protected.pdf"), "PDF protégé !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class ToImagesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                String fmt = mp.getField("format", "png");
                byte[][] imgs = pdfToImages(mp.getFile("file"), fmt);
                String ext = fmt.equals("jpeg") ? "jpg" : "png";
                ByteArrayOutputStream zb = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(zb)) {
                    for (int i = 0; i < imgs.length; i++) {
                        zos.putNextEntry(new ZipEntry(String.format("page_%03d.%s", i + 1, ext)));
                        zos.write(imgs[i]); zos.closeEntry();
                    }
                }
                ok(ex, save(zb.toByteArray(), "_images.zip"), imgs.length + " image(s).");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class ExtractTextHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                String text = extractText(mp.getFile("file"));
                String url = save(text.getBytes("UTF-8"), "_text.txt");
                String preview = text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
                okText(ex, url, "Texte extrait !", preview);
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class CreatePdfHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = parseMultipart(ex);
                byte[] r = createPDF(mp.getField("title", "Document"), mp.getField("content", ""));
                ok(ex, save(r, "_created.pdf"), "PDF créé !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class DownloadHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCORS(ex);
            String name = ex.getRequestURI().getPath().replace("/download/", "");
            File f = new File(OUT + name);
            if (!f.exists()) { ex.sendResponseHeaders(404, -1); return; }
            byte[] data = Files.readAllBytes(f.toPath());
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
            ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data); ex.getResponseBody().close();
        }
    }

    static class StaticHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            addCORS(ex);
            String path = ex.getRequestURI().getPath();
            File f = new File("static" + (path.equals("/") ? "/index.html" : path));
            if (!f.exists()) f = new File("static/index.html");
            byte[] data = Files.readAllBytes(f.toPath());
            String ct = path.endsWith(".css") ? "text/css" : path.endsWith(".js") ? "application/javascript" : "text/html; charset=UTF-8";
            ex.getResponseHeaders().set("Content-Type", ct);
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data); ex.getResponseBody().close();
        }
    }

    // ── Main ──────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/merge",         new MergeHandler());
        server.createContext("/api/split",         new SplitHandler());
        server.createContext("/api/extract-pages", new ExtractPagesHandler());
        server.createContext("/api/delete-pages",  new DeletePagesHandler());
        server.createContext("/api/protect",       new ProtectHandler());
        server.createContext("/api/to-images",     new ToImagesHandler());
        server.createContext("/api/extract-text",  new ExtractTextHandler());
        server.createContext("/api/create-pdf",    new CreatePdfHandler());
        server.createContext("/download/",         new DownloadHandler());
        server.createContext("/",                  new StaticHandler());
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();
        System.out.println("✅ PDFForge démarré sur le port " + port);
    }
}
