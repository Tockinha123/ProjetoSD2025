#!/bin/bash
java --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.nio=ALL-UNNAMED \
     -jar target/ProjetoSD2025-1.0-SNAPSHOT-jar-with-dependencies.jar 2> warnings.log
