package client;

import ChatApp.FileChunk;
import ChatApp.ChatServerPrx;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de transferencia y reconstrucción de archivos multimedia sobre ZeroC Ice.
 * Cumple con RF-04:
 * - Fragmentación en bloques (chunks) de 64 KB con metadatos.
 * - Soporte binario para imágenes, audios, documentos y texto.
 * - Destino privado y grupal.
 * - Reconstrucción íntegra en disco en la carpeta "downloads/".
 */
public class FileTransferManager {

    public static final int CHUNK_SIZE = 64 * 1024; // 64 KB por fragmento (dentro del límite de Ice)
    private static final String DOWNLOADS_DIR = "downloads";

    // Seguimiento de archivos entrantes por su fileId
    private static class FileAssembly {
        final String fileId;
        final String fileName;
        final long totalSize;
        final int totalChunks;
        final String origin;
        final boolean isRoom;
        final File tempFile;
        final RandomAccessFile raf;
        final boolean[] receivedChunks;
        int chunksCount = 0;

        FileAssembly(String fileId, String fileName, long totalSize, int totalChunks, String origin, boolean isRoom) throws Exception {
            this.fileId = fileId;
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.totalChunks = totalChunks;
            this.origin = origin;
            this.isRoom = isRoom;
            this.receivedChunks = new boolean[totalChunks];

            File dir = new File(DOWNLOADS_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            this.tempFile = new File(dir, fileId + ".part");
            this.raf = new RandomAccessFile(this.tempFile, "rw");
            this.raf.setLength(totalSize);
        }

        synchronized boolean addChunk(FileChunk chunk) throws Exception {
            if (chunk.chunkIndex < 0 || chunk.chunkIndex >= totalChunks) {
                return false;
            }

            if (!receivedChunks[chunk.chunkIndex]) {
                long offset = (long) chunk.chunkIndex * CHUNK_SIZE;
                raf.seek(offset);
                raf.write(chunk.data);
                receivedChunks[chunk.chunkIndex] = true;
                chunksCount++;
            }

            return chunksCount == totalChunks;
        }

        synchronized void closeAndSave() throws Exception {
            raf.close();

            File dir = new File(DOWNLOADS_DIR);
            File destination = new File(dir, fileName);

            // Si ya existe un archivo con ese nombre, agregar sufijo numérico
            if (destination.exists()) {
                String baseName = fileName;
                String extension = "";
                int dotIdx = fileName.lastIndexOf('.');
                if (dotIdx > 0) {
                    baseName = fileName.substring(0, dotIdx);
                    extension = fileName.substring(dotIdx);
                }
                int counter = 1;
                while (destination.exists()) {
                    destination = new File(dir, baseName + "_" + counter + extension);
                    counter++;
                }
            }

            if (tempFile.renameTo(destination)) {
                String origenStr = isRoom ? "la sala #" + origin : "@" + origin;
                System.out.println("\n\u001B[32m[ARCHIVO RECIBIDO]\u001B[0m Archivo guardado: "
                        + destination.getName() + " (" + formatSize(totalSize) + ") desde " + origenStr);
                System.out.println("[RUTA] " + destination.getAbsolutePath());
                System.out.print("> ");
            } else {
                // Si falla renameTo, mover bytes directamente
                Files.move(tempFile.toPath(), destination.toPath());
                String origenStr = isRoom ? "la sala #" + origin : "@" + origin;
                System.out.println("\n\u001B[32m[ARCHIVO RECIBIDO]\u001B[0m Archivo guardado: "
                        + destination.getName() + " (" + formatSize(totalSize) + ") desde " + origenStr);
                System.out.println("[RUTA] " + destination.getAbsolutePath());
                System.out.print("> ");
            }
        }

        synchronized void abort() {
            try {
                raf.close();
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private final Map<String, FileAssembly> activeDownloads = new ConcurrentHashMap<>();

    /**
     * Envía un archivo fragmentado a un usuario o a una sala en un hilo asíncrono.
     */
    public void sendFileAsync(ChatServerPrx server, String sender, String target, String filePath, boolean isRoom) {
        Thread sendThread = new Thread(() -> {
            File file = new File(filePath);
            if (!file.exists() || !file.isFile()) {
                System.out.println("\u001B[31m[ERROR ARCHIVO] El archivo no existe o la ruta es invalida: " + filePath + "\u001B[0m");
                System.out.print("> ");
                return;
            }

            long totalSize = file.length();
            if (totalSize == 0) {
                System.out.println("\u001B[31m[ERROR ARCHIVO] El archivo seleccionado esta vacio (0 bytes).\u001B[0m");
                System.out.print("> ");
                return;
            }

            String fileId = UUID.randomUUID().toString();
            String fileName = file.getName();
            int totalChunks = (int) Math.ceil((double) totalSize / CHUNK_SIZE);

            System.out.println("\u001B[36m[ENVIANDO ARCHIVO]\u001B[0m '" + fileName + "' (" + formatSize(totalSize) + ") en "
                    + totalChunks + " fragmentos a " + (isRoom ? "#" + target : "@" + target) + "...");

            byte[] buffer = new byte[CHUNK_SIZE];
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead;
                int chunkIndex = 0;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] data = (bytesRead == CHUNK_SIZE) ? buffer.clone() : Arrays.copyOf(buffer, bytesRead);

                    FileChunk chunk = new FileChunk(fileId, fileName, totalSize, chunkIndex, totalChunks, data);

                    if (isRoom) {
                        server.sendRoomFileChunk(target, sender, chunk);
                    } else {
                        server.sendPrivateFileChunk(sender, target, chunk);
                    }

                    chunkIndex++;
                    int progress = (int) (((double) chunkIndex / totalChunks) * 100);
                    if (progress % 25 == 0 || chunkIndex == totalChunks) {
                        System.out.println("[SUBIDA " + fileName + "] Progreso: " + progress + "% (" + chunkIndex + "/" + totalChunks + ")");
                    }
                }

                System.out.println("\u001B[32m[EXITO ARCHIVO]\u001B[0m Archivo '" + fileName + "' transmitido completamente a "
                        + (isRoom ? "#" + target : "@" + target));
                System.out.print("> ");

            } catch (Exception e) {
                System.out.println("\n\u001B[31m[ERROR ARCHIVO] Fallo al transferir '" + fileName + "': " + e.getMessage() + "\u001B[0m");
                System.out.print("> ");
            }
        }, "hilo-envio-archivo");

        sendThread.setDaemon(true);
        sendThread.start();
    }

    /**
     * Procesa un fragmento entrante y reconstruye el archivo cuando todos los fragmentos han llegado.
     */
    public void processIncomingChunk(String origin, FileChunk chunk, boolean isRoom) {
        try {
            FileAssembly assembly = activeDownloads.computeIfAbsent(chunk.fileId, k -> {
                try {
                    return new FileAssembly(chunk.fileId, chunk.fileName, chunk.totalSize, chunk.totalChunks, origin, isRoom);
                } catch (Exception e) {
                    System.err.println("[ERROR RECONSTRUCCION] No se pudo crear archivo temporal: " + e.getMessage());
                    return null;
                }
            });

            if (assembly == null) {
                return;
            }

            boolean completed = assembly.addChunk(chunk);
            if (completed) {
                activeDownloads.remove(chunk.fileId);
                assembly.closeAndSave();
            }

        } catch (Exception e) {
            System.err.println("[ERROR RECONSTRUCCION] Error procesando fragmento de " + chunk.fileName + ": " + e.getMessage());
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
