import PDFForge.PDFWorkerPOA;
import PDFForge.Bytes;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.rendering.*;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class PDFWorkerImpl extends PDFWorkerPOA {

    // ── Helpers ──────────────────────────────────────────────────────────

    private PDDocument fromBytes(byte[] data) throws IOException {
        return PDDocument.load(new ByteArrayInputStream(data));
    }

    private byte[] docToBytes(PDDocument doc) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        doc.save(bos);
        doc.close();
        return bos.toByteArray();
    }

    private List<Integer> parsePageSpec(String spec, int total) {
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

    private PDFont loadFont(PDDocument doc, boolean bold) {
        String[] paths = bold ? new String[]{
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"
        } : new String[]{
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
        };
        for (String path : paths) {
            try { File f = new File(path); if (f.exists()) return PDType0Font.load(doc, f); }
            catch (Exception e) {}
        }
        return null;
    }

    // ── CORBA Operations ─────────────────────────────────────────────────

    @Override
    public byte[] mergePDFs(byte[] pdf1, byte[] pdf2) {
        try {
            PDFMergerUtility merger = new PDFMergerUtility();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            merger.setDestinationStream(bos);
            merger.addSource(new ByteArrayInputStream(pdf1));
            merger.addSource(new ByteArrayInputStream(pdf2));
            merger.mergeDocuments(null);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }

    @Override
    public byte[][] splitPDF(byte[] pdf, int chunkSize) {
        try {
            PDDocument doc = fromBytes(pdf);
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(chunkSize);
            List<PDDocument> parts = splitter.split(doc);
            byte[][] result = new byte[parts.size()][];
            for (int i = 0; i < parts.size(); i++) result[i] = docToBytes(parts.get(i));
            doc.close();
            return result;
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }

    @Override
    public byte[] extractPages(byte[] pdf, String pageSpec) {
        try {
            PDDocument src = fromBytes(pdf);
            List<Integer> pages = parsePageSpec(pageSpec, src.getNumberOfPages());
            PDDocument dest = new PDDocument();
            for (int idx : pages) dest.addPage(src.getPage(idx));
            byte[] result = docToBytes(dest);
            src.close();
            return result;
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }

    @Override
    public byte[] deletePages(byte[] pdf, String pageSpec) {
        try {
            PDDocument src = fromBytes(pdf);
            int total = src.getNumberOfPages();
            Set<Integer> toDelete = new HashSet<>(parsePageSpec(pageSpec, total));
            PDDocument dest = new PDDocument();
            for (int i = 0; i < total; i++)
                if (!toDelete.contains(i)) dest.addPage(src.getPage(i));
            byte[] result = docToBytes(dest);
            src.close();
            return result;
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }

    @Override
    public byte[] protectPDF(byte[] pdf, String password) {
        try {
            PDDocument doc = fromBytes(pdf);
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(false); ap.setCanModify(false); ap.setCanExtractContent(false);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, ap);
            policy.setEncryptionKeyLength(256); policy.setPreferAES(true);
            doc.protect(policy);
            return docToBytes(doc);
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }

    @Override
    public byte[][] pdfToImages(byte[] pdf, String format) {
        try {
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
            doc.close();
            return images;
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }

    @Override
    public String extractText(byte[] pdf) {
        try {
            PDDocument doc = fromBytes(pdf);
            String text = new PDFTextStripper().getText(doc);
            doc.close();
            return text;
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }

    @Override
    public byte[] createPDF(String title, String content) {
        try {
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
            cs.close();
            return docToBytes(doc);
        } catch (Exception e) {
            throw new org.omg.CORBA.INTERNAL(e.getMessage());
        }
    }
}
