FROM eclipse-temurin:11-jdk

WORKDIR /app

# Install fonts for PDF Unicode support
RUN apt-get update && apt-get install -y \
    fonts-dejavu-core \
    fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

# Download dependencies
RUN mkdir -p libs && \
    curl -L https://archive.apache.org/dist/pdfbox/2.0.31/pdfbox-app-2.0.31.jar -o libs/pdfbox-app-2.0.31.jar && \
    curl -L https://repo1.maven.org/maven2/commons-fileupload/commons-fileupload/1.5/commons-fileupload-1.5.jar -o libs/commons-fileupload-1.5.jar && \
    curl -L https://repo1.maven.org/maven2/commons-io/commons-io/2.15.1/commons-io-2.15.1.jar -o libs/commons-io-2.15.1.jar && \
    curl -L https://repo1.maven.org/maven2/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar -o libs/javax.servlet-api-3.1.0.jar

# Copy source files
COPY src/ src/
COPY static/ static/

# Compile
RUN javac -cp "libs/*" src/*.java -d classes

# Create outputs dir
RUN mkdir -p outputs

EXPOSE 8080

CMD ["java", "-cp", "classes:libs/*", "PDFApp"]
