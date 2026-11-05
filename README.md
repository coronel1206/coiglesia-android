# Coiglesia
Sistema Nacional de Gestión Eclesiástica. Proyecto Android inicial.

## Compilación
En GitHub, abre Actions > Compilar APK Coiglesia > Run workflow. Al finalizar, descarga `Coiglesia-debug-apk` desde Artifacts.

## Estado
Versión 0.1.0: navegación de demostración sin autenticación, servidor ni persistencia. No introducir datos personales reales.

## Arquitectura futura
Android Kotlin, backend con autenticación y permisos por jurisdicción, PostgreSQL. La interfaz inicial utiliza vistas Android nativas para minimizar dependencias; se migrará progresivamente a Jetpack Compose.
