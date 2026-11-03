FROM eclipse-temurin:8-jre
WORKDIR /app
COPY app.jar /app/app.jar
ENV TZ=Asia/Shanghai
CMD ["sh","-c","java -Xms512m -Xmx1024m -jar /app/app.jar --server.port=$APP_PORT"]
