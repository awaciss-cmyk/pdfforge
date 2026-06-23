import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.disk.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.util.*;

public class MultipartData {
    private Map<String, List<byte[]>> files = new HashMap<>();
    private Map<String, String> fields = new HashMap<>();

    public MultipartData(HttpExchange exchange) throws Exception {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096]; int n;
        while ((n = exchange.getRequestBody().read(tmp)) != -1) buf.write(tmp, 0, n);
        byte[] body = buf.toByteArray();

        RequestContext ctx = new RequestContext() {
            public String getCharacterEncoding() { return "UTF-8"; }
            public String getContentType() { return contentType; }
            public int getContentLength() { return body.length; }
            public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        };

        FileItemFactory factory = new DiskFileItemFactory();
        FileUpload upload = new FileUpload(factory);
        List<FileItem> items = upload.parseRequest(ctx);
        for (FileItem item : items) {
            if (item.isFormField()) {
                fields.put(item.getFieldName(), item.getString("UTF-8"));
            } else {
                files.computeIfAbsent(item.getFieldName(), k -> new ArrayList<>()).add(item.get());
            }
        }
    }

    public byte[] getFile(String name) {
        List<byte[]> list = files.get(name);
        return (list != null && !list.isEmpty()) ? list.get(0) : new byte[0];
    }

    public List<byte[]> getFiles(String name) {
        return files.getOrDefault(name, new ArrayList<>());
    }

    public String getField(String name, String def) {
        return fields.getOrDefault(name, def);
    }
}
