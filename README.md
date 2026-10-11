# Taller Evaluativo Unidad 2: Sistemas Distribuidos, Middleware y Comunicación Multimedia
**Curso:** Computación en Internet I (09810 - TIC)  
**Semestre:** 2026-2 | Universidad Icesi  
**Integrantes:**
- Carlos Pasinga
- Santiago Vinazco
- David

---

## 1. Arquitectura General del Sistema

El sistema implementa una arquitectura híbrida cliente-servidor para mensajería y llamadas en tiempo real:
1. **Canal de Control, Mensajería y Transferencia de Archivos (ZeroC Ice / TCP):**
   - Utiliza RPC fuertemente tipado mediante el contrato `Chat.ice`.
   - Garantiza entrega confiable, callbacks asíncronos y orden estricto de paquetes.
2. **Canal de Audio en Tiempo Real (Sockets UDP crudos):**
   - Emplea `java.net.DatagramSocket` y `javax.sound.sampled` para baja latencia acústica sin sobrecarga de confirmaciones (ACKs).

```
                      +-----------------------------+
                      |      ZeroC Ice Server       |
                      |   (Puerto TCP 10000 / RPC)  |
                      |    + Audio Relay UDP SFU    |
                      +--------------+--------------+
                                     ^
           TCP (Señalización /       |       TCP (Señalización /
           Mensajería / Chunks)      |       Mensajería / Chunks) 
                                     v
                 +-------------------+-------------------+
                 |                                       |
    +------------+-----------+               +-----------+-----------+
    |   Cliente 1 (Ice)      | <--- UDP ---> |   Cliente 2 (Ice)     |
    | Callback: Puerto local |  (Voz 1 a 1)  | Callback: Puerto local|
    +------------------------+               +-----------------------+
```

## . Instrucciones de Ejecución

El proyecto está configurado con **Gradle 9** y el plugin oficial de Ice `com.zeroc.slice-tools`.

### Iniciar el Servidor:
```bash
./gradlew runServer
```
*(Escucha por defecto en el puerto `10000`)*

### Iniciar Clientes:
```bash
./gradlew runClient
```
*(Solicitará un nickname único e iniciará la consola interactiva)*

---

## 4. Manual de Comandos de la Interfaz (CLI)

### Módulo 1 & 2 (Presencia y Mensajería Directa):
- `/users` : Lista los usuarios conectados en tiempo real.
- `/msg <usuario> <mensaje>` : Envía un mensaje privado 1 a 1.
- `/logout` o `/exit` : Cierra sesión y libera los recursos en el servidor.
- `/help` : Muestra el catálogo de comandos disponibles.

### Módulo 3 & 4 (Salas de Chat y Archivos):
- `/create <sala>` : Crea una nueva sala de conversación.
- `/rooms` : Lista las salas de chat activas en el servidor.
- `/join <sala>` : Se suscribe a una sala.
- `/leave <sala>` : Abandona una sala.
- `/roommsg <sala> <mensaje>` : Envía un mensaje a todos los integrantes de la sala.
- `/sendfile <destinatario/sala> <ruta_archivo>` : Transfiere un archivo fragmentado en chunks de 32/64 KB.

### Módulo 5 & 6 (Llamadas de Voz UDP):
- `/call <usuario>` : Inicia una solicitud de llamada de voz 1 a 1.
- `/accept` : Acepta la llamada entrante y abre la captura/reproducción de audio UDP.
- `/reject` : Rechaza la llamada entrante.
- `/hangup` : Finaliza la llamada y cierra los sockets UDP.
- `/mute` / `/unmute` : Silencia o reactiva el micrófono.
- `/voicejoin <sala>` : Se une a la sala de voz grupal sobre UDP.
- `/voiceleave` : Abandona la sala de voz grupal.

---

## 5. Cuestionario de Análisis Conceptual y Decisiones de Diseño

### 1. Arquitectura Híbrida TCP/UDP
- **Razones Técnicas:** ZeroC Ice opera sobre TCP, garantizando entrega confiable, orden estricto de invocaciones RPC y detección de fallos de red. Esto es ideal para control de sesión, texto y archivos, donde la pérdida de un solo byte causaría corrupción o inconsistencia. Por el contrario, el audio en tiempo real es altamente sensible a la latencia y al retardo temporal, pero tolerante a pequeñas pérdidas aisladas de paquetes.
- **Consecuencias si la voz fuera sobre TCP:** Si se usara TCP bajo condiciones de jitter o pérdida de paquetes, los mecanismos de retransmisión (ARQ) y control de congestión de TCP congelarían el flujo (Head-of-Line Blocking), generando silencios prolongados ("buffering"), retraso acumulativo severo y un audio entrecortado e ininteligible. UDP descarta paquetes rezagados inmediatamente, manteniendo la fluidez y baja latencia.

### 2. Manejo de Buffer y Chunking en Ice
- **Comportamiento por defecto:** Si se intenta transferir un archivo de 80 MB en una sola llamada pasando un `sequence<byte>`, ZeroC Ice arrojará la excepción `com.zeroc.Ice.MemoryLimitException`. Esto ocurre porque por defecto Ice impone un límite de tamaño de mensaje (`Ice.MessageSizeMax = 1024` KB = 1 MB).
- **Solución por Fragmentación (Chunking):** Fragmentar el archivo en bloques sucesivos (e.g. 32 KB a 64 KB) con metadatos asociados (`fileId`, `chunkIndex`, `totalChunks`, `totalSize`) garantiza que cada mensaje RPC esté muy por debajo del umbral de memoria. El receptor reconstruye el stream secuencialmente en disco, evitando agotar la memoria heap de la JVM.

### 3. Escalabilidad en Llamadas Grupales (Relay SFU vs. Mesh P2P)
Si una sala cuenta con $N$ participantes activos hablando simultáneamente:
- **Topología Mesh Peer-to-Peer:**
  - Cada cliente emite $N - 1$ réplicas de cada paquete UDP (uno para cada compañero).
  - Carga de ancho de banda por cliente: $O(N)$ de subida.
  - Carga en el servidor: $0$ paquetes (el servidor solo hace la señalización Ice inicial).
  - *Desventaja:* No escala en clientes con conexiones domésticas de subida limitada cuando $N > 4$.
- **Topología Audio Relay Centralizado (SFU Ligero):**
  - Cada cliente emite exactamente **1** paquete UDP hacia el servidor ($O(1)$ de subida por cliente).
  - El servidor recibe $N$ paquetes por instante y los retransmite a los otros $N - 1$ participantes, soportando una carga total de $N \times (N - 1)$ paquetes ($O(N^2)$ en el servidor).
  - *Ventaja:* Ahorra ancho de banda de subida al cliente, ideal para entornos cliente convencionales.

### 4. Transparencia y Callbacks en Ice
- **Ciclo de vida y Object Adapters en el Cliente:** Para que el servidor invoque métodos de retorno, el cliente debe comportarse temporalmente como un servidor local: crea un `ObjectAdapter` local (generalmente en un puerto efímero asignado por el SO), instancia el servant (`ClientCallbackI`), lo registra en el adaptador y lo activa (`adapter.activate()`). Luego, obtiene un proxy a su servant y se lo entrega al servidor en el método `login()`.
- **Proxies Directos vs. Indirectos:**
  - *Proxy Directo:* Contiene la dirección de red explícita del nodo (host y puerto, ej. `ClientCallback:tcp -h 192.168.1.15 -p 54321`). Es el empleado en callbacks para que el servidor despache inmediatamente sin requerir servicios de localización.
  - *Proxy Indirecto:* Contiene únicamente el identificador y el nombre de un adaptador registrado en un servicio de localización (`IceGrid`), permitiendo balanceo de carga y tolerancia a fallos a nivel de clúster.

---

## 6. Bitácora de Uso Ético de IAG (Nivel 3 - Colaboración Asistida)

- **Prompts Principales Empleados:**
  1. *"Diagnosticar y corregir la estructura inicial de Gradle y contratos Slice para que cumpla los lineamientos de la Universidad Icesi."*
  2. *"Definir una división equitativa de 3 roles técnicos para estudiantes de 5to semestre que cubra la totalidad de la rúbrica evaluativa."*
  3. *"Generar la base del servant `ChatServerI` y `ClientCallbackI` con Thread-Safety (ConcurrentHashMap) y CLI desacoplada para RF-01 y RF-02."*
- **Adaptaciones Críticas Realizadas:**
  - Sustitución de configuraciones rígidas de Gradle por la integración moderna de `com.zeroc.slice-tools` y sincronización automática de contratos `.ice`.
  - Simplificación del manejo de hilos y colecciones a estándares idiomáticos de 5to semestre (`ConcurrentHashMap` y `Scanner` en hilo desacoplado) sin recurrir a frameworks innecesariamente densos.
- **Lecciones Aprendidas:**
  - El comportamiento del compilador Slice frente a los límites de memoria (`Ice.MessageSizeMax`) y la necesidad estricta del chunking binario.
  - La diferencia operativa fundamental entre el modelo transaccional y ordenado de TCP frente a la naturaleza sin conexión y de mínima latencia de los datagramas UDP para audio.