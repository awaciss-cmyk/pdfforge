import com.sun.net.httpserver.*;
import org.omg.CORBA.*;
import org.omg.CosNaming.*;
import PDFForge.*;
import MultipartData;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Passerelle HTTP → CORBA
 * Reçoit les requêtes HTTP des navigateurs,
 * délègue le traitement au PDFWorker via CORBA.
 */
public class PDFHttpGateway {

    static PDFWorker worker;
    static final String OUT = "outputs/";

    // ── Init CORBA ───────────────────────────────────────────────────────

    static void initCorba() throws Exception {
        String nsHost = System.getenv().getOrDefault("CORBA_NS_HOST", "localhost");
        String nsPort = System.getenv().getOrDefault("CORBA_NS_PORT", "1050");

        String[] orbArgs = new String[]{
            "-ORBInitialHost", nsHost,
            "-ORBInitialPort", nsPort
        };

        ORB orb = ORB.init(orbArgs, null);
        org.omg.CORBA.Object nsObj = orb.resolve_initial_references("NameService");
        NamingContextExt ns = NamingContextExtHelper.narrow(nsObj);

        org.omg.CORBA.Object ref = ns.resolve_str("PDFWorker");
        worker = PDFWorkerHelper.narrow(ref);
        System.out.println("✅ Connecté au PDFWorker CORBA (" + nsHost + ":" + nsPort + ")");
    }

    // ── HTTP Utilities ───────────────────────────────────────────────────

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
        ex.getResponseBody().write(b);
        ex.getResponseBody().close();
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

    // ── Handlers ─────────────────────────────────────────────────────────

    static class MergeHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = new MultipartData(ex);
                List<byte[]> fs = mp.getFiles("files");
                if (fs.size() < 2) { err(ex, "2 fichiers requis."); return; }
                byte[] r = worker.mergePDFs(fs.get(0), fs.get(1));   // ← CORBA
                ok(ex, save(r, "_merged.pdf"), "PDF fusionné !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class SplitHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = new MultipartData(ex);
                int chunk = Integer.parseInt(mp.getField("chunk_size", "2"));
                byte[][] parts = worker.splitPDF(mp.getFile("file"), chunk);  // ← CORBA
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
                MultipartData mp = new MultipartData(ex);
                byte[] r = worker.extractPages(mp.getFile("file"), mp.getField("pages", ""));  // ← CORBA
                ok(ex, save(r, "_extracted.pdf"), "Pages extraites !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class DeletePagesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = new MultipartData(ex);
                byte[] r = worker.deletePages(mp.getFile("file"), mp.getField("pages", ""));  // ← CORBA
                ok(ex, save(r, "_deleted.pdf"), "Pages supprimées !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class ProtectHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = new MultipartData(ex);
                byte[] r = worker.protectPDF(mp.getFile("file"), mp.getField("password", ""));  // ← CORBA
                ok(ex, save(r, "_protected.pdf"), "PDF protégé !");
            } catch (Exception e) { err(ex, e.getMessage()); }
        }
    }

    static class ToImagesHandler implements HttpHandler {
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equals(ex.getRequestMethod())) { addCORS(ex); ex.sendResponseHeaders(204, -1); return; }
            try {
                MultipartData mp = new MultipartData(ex);
                String fmt = mp.getField("format", "png");
                byte[][] imgs = worker.pdfToImages(mp.getFile("file"), fmt);  // ← CORBA
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
                MultipartData mp = new MultipartData(ex);
                String text = worker.extractText(mp.getFile("file"));  // ← CORBA
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
                MultipartData mp = new MultipartData(ex);
                byte[] r = worker.createPDF(mp.getField("title", "Document"), mp.getField("content", ""));  // ← CORBA
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
            ex.getResponseBody().write(data);
            ex.getResponseBody().close();
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
            ex.getResponseBody().write(data);
            ex.getResponseBody().close();
        }
    }

    // ── Main ─────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        // Connexion CORBA
        initCorba();

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

        System.out.println("✅ PDFForge Gateway HTTP démarré sur le port " + port);
    }
}
