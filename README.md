# Taller Evaluativo Unidad 2: Sistemas Distribuidos, Middleware y Comunicación Multimedia
**Curso:** Computación en Internet I (09810 - TIC)  
**Semestre:** 2026-2 | Universidad Icesi  
**Modalidad:** Grupos de 3 a 4 Estudiantes  

### Integrantes:
- **Carlos Pasinga** — A00412446
- **Santiago Vinazco** — 
- **David** —  

---

## 1. Arquitectura General del Sistema

El sistema implementa una arquitectura distribuida e híbrida diseñada para desacoplar el control transaccional del flujo multimedia de alta tasa:

1. **Canal de Control, Mensajería y Transferencia de Archivos (ZeroC Ice / TCP):**
   - **Protocolo:** TCP con RPC fuertemente tipado definido en el contrato Slice (`Chat.ice`).
   - **Garantías:** Tipado estricto, entrega confiable ordenada (sin pérdida de paquetes ni corrupción) y despacho asíncrono bidireccional mediante objetos callback de retorno (`ClientCallback`).
   - **Transferencia Multimedia (RF-04):** Fragmentación en memoria (chunking de 64 KB) mediante secuencias binarias `sequence<byte>` para respetar el límite de buffer de Ice (`Ice.MessageSizeMax`), reconstruyendo los archivos byte a byte en disco en la carpeta `downloads/`.

2. **Canal de Audio en Tiempo Real (Sockets Datagrama UDP Crudos):**
   - **Protocolo:** UDP punto a punto con `java.net.DatagramSocket` y la API `javax.sound.sampled`.
   - **Garantías:** Mínima latencia acústica y fluidez de voz sin incurrir en la sobrecarga de confirmaciones (ACKs), retransmisiones ni bloqueo de cabeza de línea (Head-of-Line Blocking) característicos de TCP.
   - **Topología Grupal Seleccionada (RF-06):** **Malla Peer-to-Peer (Mesh)**, donde cada cliente replica sus paquetes UDP directamente a los demás participantes de la sala de voz, descargando por completo de tráfico de audio a la red del servidor.

### Diagrama de Arquitectura y Flujos de Red

```
                          +------------------------------------------+
                          |             ZeroC Ice Server             |
                          |        Servant: ChatServerI              |
                          |       (Puerto TCP 10000 / RPC)           |
                          +--------------------+---------------------+
                                               ^
                      TCP: Señalización /      |      TCP: Señalización /
                      Mensajería / Chunks      |      Mensajería / Chunks
                      de Archivos (64 KB)      |      de Archivos (64 KB)
                                               v
                     +-------------------------+-------------------------+
                     |                                                   |
        +------------+-------------+                        +------------+-------------+
        |     Cliente 1 (Ice)      |                        |     Cliente 2 (Ice)      |
        |  Callback: Puerto efímero|                        |  Callback: Puerto efímero|
        |  Audio: DatagramSocket   | <====================> |  Audio: DatagramSocket   |
        +--------------------------+    Flujo Audio UDP     +--------------------------+
                     \                  (DatagramPacket)                 /
                      \                 16 kHz PCM Mono                 /
                       \                                               /
                        \                                             /
                         \                                           /
                          \               +-------------------------+
                           \              |     Cliente 3 (Ice)     |
                            ============> |  Callback: Puerto efímero|
                              Audio UDP   |  Audio: DatagramSocket  |
                              (Mesh P2P)  +-------------------------+
```

---

## 2. Instrucciones de Compilación y Ejecución

El proyecto está organizado como una arquitectura multimodular en **Gradle** gestionada con el plugin oficial `com.zeroc.slice-tools`.

> **Requisito de Entorno:** Se recomienda ejecutar sobre **Java 17 o Java 21 (LTS)**.

### Compilar el Proyecto:
```bash
./gradlew compileJava
```

### Iniciar el Servidor:
```bash
./gradlew runServer
```
*(El servidor se iniciará escuchando por defecto en el puerto TCP `10000`)*

### Iniciar Clientes Interactivos:
En terminales independientes, ejecutar:
```bash
./gradlew runClient
```
*(El cliente solicitará ingresar un nickname único. Si el nickname está en uso, el servidor rechazará la conexión con la excepción Slice `NicknameInUseException` solicitando un nuevo intento)*.

---

## 3. Manual de Comandos de la Interfaz (CLI)

### Módulo 1 & 2: Gestión de Sesión, Presencia y Chat Directo
- `/users` : Consulta y lista los usuarios conectados en tiempo real.
- `/msg <usuario> <mensaje>` : Envía un mensaje de texto privado (1 a 1) al usuario especificado.
- `/logout` o `/exit` : Realiza un cierre de sesión ordenado, liberando proxies y notificando a la red.
- `/help` : Despliega el catálogo interactivo de comandos disponibles.

### Módulo 3 & 4: Salas de Chat y Transferencia Multimedia (Chunking)
- `/create <sala>` : Crea dinámicamente una nueva sala grupal.
- `/rooms` : Lista las salas de chat activas en el sistema.
- `/join <sala>` : Se suscribe e ingresa a una sala.
- `/leave <sala>` : Abandona la sala de chat.
- `/roommsg <sala> <mensaje>` : Difunde un mensaje a todos los participantes inscritos en la sala (sin eco ni duplicado al remitente).
- `/sendfile <usuario|#sala> <ruta_archivo>` : Transfiere un archivo binario (imágenes, audios `.wav`, `.mp3`, `.pdf`, `.txt`) fragmentado en chunks de 64 KB vía Ice.
  - Ejemplo usuario directo: `/sendfile maria "C:\fotos\paisaje.jpg"`
  - Ejemplo sala grupal: `/sendfile #general C:\documentos\informe.pdf`
  - *Los archivos recibidos se ensamblan íntegramente y se almacenan automáticamente en la carpeta local `downloads/`.*

### Módulo 5 & 6: Llamadas de Voz UDP (Directas y Grupales)
- `/call <usuario>` : Solicita una llamada de voz 1 a 1 al usuario destino.
- `/accept` : Acepta una llamada entrante, negociando los endpoints UDP y activando el audio en tiempo real.
- `/reject` : Rechaza la llamada entrante notificando la razón al llamador.
- `/hangup` : Finaliza la llamada de voz activa y cierra los recursos de red/audio.
- `/mute` : Silencia la captura del micrófono (deja de transmitir paquetes UDP).
- `/unmute` : Reactiva la transmisión del micrófono.
- `/voicejoin <sala>` : Se une a la conferencia de voz grupal UDP de la sala (topología Mesh).
- `/voiceleave` : Abandona la conferencia de voz grupal sin interrumpir la llamada de los demás participantes.

---

## 4. Cuestionario de Análisis Conceptual y Decisiones de Diseño

### 1. Arquitectura Híbrida TCP/UDP
- **Razones Técnicas:** ZeroC Ice opera sobre TCP, garantizando confiabilidad punto a punto mediante control de flujo, retransmisión de paquetes perdidos (ARQ) y entrega estrictamente ordenada. Esto es indispensable para la señalización, autenticación, mensajería y transferencia de archivos, donde la alteración o pérdida de un único byte corrompería la lógica de negocio o la integridad de los archivos. En contraste, la voz en tiempo real es extremadamente sensible a la latencia (retardo < 150 ms) y al jitter, pero cuenta con una alta tolerancia a pérdidas aisladas de paquetes (1-3%).
- **Consecuencias si la voz se transmitiera sobre Ice/TCP:** Bajo condiciones de fluctuación de red (*jitter*) o congestión con pérdida de datagramas, el protocolo TCP detendría el consumo del búfer receptor a la espera de la retransmisión del paquete rezagado (**Head-of-Line Blocking**). Esto ocasionaría silencios prolongados (buffering), un retardo temporal acumulativo severo y un audio entrecortado e ininteligible, arruinando la interactividad natural de una llamada. UDP descarta paquetes obsoletos inmediatamente, manteniendo la conversación fluida y en tiempo real.

### 2. Manejo de Buffer y Chunking en Ice
- **Comportamiento por defecto:** Si un cliente intenta transferir un video de 80 MB en una única invocación RPC pasando toda la secuencia binaria en un `sequence<byte>`, ZeroC Ice arrojará la excepción de tiempo de ejecución:
  ```text
  com.zeroc.Ice.MemoryLimitException
  ```
  Esto se debe a que por defecto Ice impone una restricción de seguridad en el tamaño máximo del búfer de serialización por mensaje (`Ice.MessageSizeMax = 1024` KB = 1 MB) para evitar ataques de denegación de servicio (DoS) por agotamiento de heap en la JVM.
- **Solución por Fragmentación (Chunking):** La técnica de fragmentación divide el archivo en bloques o *chunks* sucesivos de 64 KB, encapsulados en la estructura `FileChunk` con sus respectivos metadatos (`fileId`, `fileName`, `totalSize`, `chunkIndex`, `totalChunks`, `data`). Cada llamada RPC se mantiene muy por debajo del umbral de 1 MB. En el cliente receptor, un descriptor de archivo (`RandomAccessFile`) reconstruye el archivo en disco indexando cada bloque según `chunkIndex * CHUNK_SIZE`, garantizando total consistencia e integridad binaria sin desbordar la memoria RAM.

### 3. Escalabilidad en Llamadas Grupales (Relay SFU vs. Mesh Peer-to-Peer)
En un escenario donde una sala cuenta con $N$ participantes activos hablando concurrentemente:

| Métrica / Parámetro | Topología Mesh Peer-to-Peer (Implementada) | Topología Audio Relay SFU (Servidor Central) |
| :--- | :---: | :---: |
| **Paquetes emitidos por cada cliente** | $N - 1$ paquetes por instante ($O(N)$) | Exactamente **1** paquete hacia el servidor ($O(1)$) |
| **Ancho de banda de subida (Upload)** | Crece linealmente con cada nuevo miembro | Constante por cliente, ideal para conexiones domésticas |
| **Carga de paquetes en la red del Servidor** | **0 paquetes** (Carga nula en servidor) | $N \times (N - 1)$ paquetes redistribuidos ($O(N^2)$) |
| **Latencia acústica entre pares** | Mínima (transmisión directa host-a-host) | Ligeramente mayor (salto intermedio en el servidor) |
| **Justificación de la elección del equipo** | El equipo eligió **Mesh P2P** porque minimiza el costo computacional y el ancho de banda del servidor Ice, permitiendo que la máquina servidora se dedique exclusivamente a despachar eventos RPC con alta disponibilidad. |

### 4. Transparencia y Callbacks en Ice
- **Ciclo de vida y Object Adapters en el Cliente:** Para que el servidor invoque métodos de retorno (callbacks asíncronos) sobre el cliente, éste debe alojar un microservidor local. El cliente crea un `ObjectAdapter` (`communicator.createObjectAdapterWithEndpoints("ClientCallbackAdapter", "default -h 0.0.0.0 -p 0")`) en un puerto efímero asignado por el sistema operativo. Luego instancia la clase servant (`ClientCallbackI`), la registra en el adaptador asociándole una identidad única (`ClientCallback-<UUID>`) y activa el adaptador (`adapter.activate()`). Finalmente, obtiene un proxy a su servant (`ClientCallbackPrx`) y lo envía como parámetro al servidor en la invocación `server.login(nickname, callbackPrx)`.
- **Proxies Directos vs. Indirectos:**
  - **Proxy Directo:** Contiene la dirección física explícita de transporte del objeto remoto (protocolo, host y puerto, ej. `ClientCallback-123:tcp -h 192.168.1.20 -p 52341`). Es el empleado en los callbacks para que el servidor despache inmediatamente invocaciones al cliente sin consultar a un intermediario.
  - **Proxy Indirecto:** No especifica host ni puerto físicos, sino un identificador de objeto y un nombre de adaptador (o id de réplica) registrado en un servicio de localización y despliegue como **IceGrid**. IceGrid resuelve dinámicamente los endpoints reales, permitiendo balanceo de carga, activación bajo demanda y tolerancia a fallos.

---

## 5. Bitácora de Uso Ético de IAG (Nivel 3 - Colaboración Asistida)

- **Prompts Principales Empleados:**
  1. *"Diagnosticar y adaptar la configuración de Gradle multimodular con el plugin oficial de ZeroC Ice y tareas independientes `runServer` y `runClient`."*
  2. *"Diseñar el contrato Slice (`Chat.ice`) fuertemente tipado con interfaces, estructuras para fragmentación de archivos y excepciones de dominio."*
  3. *"Estructurar el gestor de fragmentación de archivos `FileTransferManager` con bloques de 64 KB y reconstrucción aleatoria en disco (`RandomAccessFile`) evitando condiciones de carrera."*
  4. *"Implementar el canal de audio en tiempo real con `DatagramSocket`, captura PCM a 16 kHz y topología Mesh UDP con filtrado de endpoints."*

- **Adaptaciones Críticas Realizadas por el Equipo:**
  - **Reconstrucción segura de archivos:** Se adaptó el receptor para utilizar `RandomAccessFile` posicionado por offsets para que la reconstrucción no dependa del orden de llegada de los chunks y se evitaran corrupciones en archivos de varios megabytes.
  - **Resiliencia en hardware de audio:** Se refactorizó la inicialización de `UploadAudioManager` para que, en caso de ausencia de dispositivo de captura de micrófono en el laboratorio, el cliente entre limpiamente en modo "solo escucha" en lugar de fallar con excepción.
  - **Resolución dinámica de red:** Se incorporó detección de interfaces de red activas en `localIp()` para descartar interfaces virtuales (como WSL o Hyper-V) y garantizar la conectividad de los datagramas UDP en la red local.

- **Lecciones Aprendidas:**
  - El impacto de las restricciones de buffer (`Ice.MessageSizeMax`) en arquitecturas middleware y la conveniencia de aplicar el patrón de fragmentación secuencial.
  - La complementariedad entre los protocolos TCP (confiable y transaccional) y UDP (no orientado a conexión, sin retransmisión, baja latencia para streaming de voz).