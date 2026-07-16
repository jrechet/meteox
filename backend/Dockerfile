# meteox-laws — image JVM (Quarkus fast-jar). Build préalable : ./mvnw package (via build.sh).
FROM eclipse-temurin:26-jre

WORKDIR /app
COPY target/quarkus-app/lib/ /app/lib/
COPY target/quarkus-app/*.jar /app/
COPY target/quarkus-app/app/ /app/app/
COPY target/quarkus-app/quarkus/ /app/quarkus/

# Le fichier SQLite vit sur un volume : les redéploiements ne perdent aucune donnée.
VOLUME /data
ENV MX_DB_PATH=/data/meteox-laws.db

EXPOSE 8080
CMD ["java", "-jar", "/app/quarkus-run.jar"]
