# Ayudante IA

Un asistente de IA que vive dentro de tu partida de Minecraft. Pregúntale lo que quieras y te responde en el chat **sabiendo lo que pasa en tu juego**: dónde estás, cuánta vida tienes, qué llevas en el inventario y si hay mobs cerca.

## Características
- Responde a `/ia <pregunta>` o escribiendo `ia <pregunta>` en el chat.
- Conoce tu posición, dimensión, bioma, clima, luz, vida, hambre, XP, objeto en mano, armadura, inventario y mobs hostiles a 16 bloques.
- Memoria de conversación por jugador (`/ia reiniciar` para borrarla).
- Respuestas asíncronas: el servidor no se congela mientras la IA piensa.
- Cooldown configurable para evitar spam.

## Instalación
1. Minecraft **26.3** con **Fabric Loader 0.19.5+** y **Java 25**.
2. Instala **Fabric API** para 26.3.
3. Pon el .jar en la carpeta `mods`.
4. Abre el juego una vez para crear `config/ayudante-ia.json`, pon ahí tu API key de Anthropic (o usa la variable de entorno `ANTHROPIC_API_KEY`) y escribe `/ia recargar`.

## Privacidad y avisos
- Este mod necesita **tu propia API key** y tiene costo según el uso de la API.
- Cada vez que haces una pregunta, se envía a la API de Anthropic tu pregunta junto con un resumen del estado de tu juego (posición, inventario, etc.). No se envía nada más y no se envía nada si no usas el comando.
- Tu API key se guarda solo en tu archivo de config local. Nunca la compartas.
- Mod no oficial, no afiliado a Mojang ni a Microsoft.

## Uso
| Comando | Qué hace |
|---|---|
| `/ia <mensaje>` | Pregunta a la IA |
| `ia <mensaje>` (chat) | Igual que el comando |
| `/ia reiniciar` | Borra tu memoria de conversación |
| `/ia recargar` | Recarga la config |

Licencia: MIT.
