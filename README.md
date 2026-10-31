# Ayudante IA (mod Fabric para Minecraft 26.3)

Un asistente de IA dentro de Minecraft. Ve tu posición, vida, inventario, bioma y mobs cercanos, y responde en el chat.

## Requisitos
- JDK 25 (Minecraft 26.x lo exige)
- Fabric Loader 0.19.5+ y Fabric API 0.161.0+26.3

## Compilar
1. `gradle wrapper --gradle-version latest`   (Gradle 8.x NO funciona con Java 25)
2. `./gradlew build`
3. El .jar queda en `build/libs/ayudante-ia-1.1.0.jar` (no uses el `-sources`)
4. Copialo a `.minecraft/mods/` junto con Fabric API.

## Configurar
Arranca el juego una vez: se crea `config/ayudante-ia.json`. Pon tu `apiKey` ahí
(o usa la variable de entorno `ANTHROPIC_API_KEY`). Nunca subas la key a GitHub.

## Uso
- `/ia como hago una granja de hierro`
- En el chat: `ia que hago, hay creepers cerca`
- `/ia reiniciar` borra la memoria de la conversación
- `/ia recargar` recarga la config

## Compilar sin instalar nada (GitHub Actions)
1. Sube el contenido de esta carpeta a un repositorio de GitHub (incluida la carpeta oculta `.github`).
2. Entra a la pestaña **Actions**, abre "Compilar mod" y pulsa **Run workflow** (se ejecuta solo en cada subida).
3. Al terminar, baja el artefacto **ayudante-ia-jar**, descomprímelo y pon el .jar en `.minecraft/mods`.
