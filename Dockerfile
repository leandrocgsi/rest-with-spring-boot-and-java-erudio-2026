FROM eclipse-temurin:25-jdk

# JasperReports compiles the .jrxml templates with javac at runtime, and javac cannot read the jars nested inside
# a Spring Boot fat jar ("cannot find symbol JREvaluator" and the PDF exports fail). Extracting the jar puts the
# dependencies next to it, where javac finds them through the manifest Class-Path.
# The bind mount avoids keeping a second 100 MB copy of the jar in an image layer.
RUN --mount=type=bind,source=target,target=/build \
    java -Djarmode=tools -jar /build/*.jar extract --destination /app \
    && mv /app/*.jar /app/app.jar

# The people report asks for the "Arial" font, which exists on Windows but not in this image (only DejaVu does).
# Without this property JasperReports fails with JRFontNotFoundException; with it the default font is used instead.
ENTRYPOINT ["java","-Dnet.sf.jasperreports.awt.ignore.missing.font=true","-jar","/app/app.jar"]
