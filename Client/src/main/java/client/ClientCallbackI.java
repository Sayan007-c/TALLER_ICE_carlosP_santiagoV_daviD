package client;

import ChatApp.*;
import com.zeroc.Ice.Current;

/**
 * Servant de retorno (Callback Object) alojado en el cliente.
 * Permite que el servidor ZeroC Ice notifique eventos asíncronos en tiempo real:
 * - Presencia de usuarios (RF-01)
 * - Mensajes privados entrantes (RF-02)
 * - Mensajes de salas grupales (RF-03)
 * - Fragmentos de archivos multimedia (RF-04)
 * - Señalización de llamadas de voz UDP (RF-05 y RF-06)
 */
public class ClientCallbackI implements ClientCallback {

    private final String clientUsername;

    public ClientCallbackI(String clientUsername) {
        this.clientUsername = clientUsername;
    }

    // =========================================================================
    // EVENTOS DEL MODULO 1 & 2: SESIÓN, PRESENCIA Y MENSAJERÍA DIRECTA
    // =========================================================================

    @Override
    public void onUserStatusChanged(String username, boolean online, Current current) {
        String status = online ? "\u001B[32m[EN LINEA]\u001B[0m" : "\u001B[31m[DESCONECTADO]\u001B[0m";
        System.out.println("\n" + status + " El usuario @" + username + (online ? " se ha conectado." : " se ha desconectado."));
        System.out.print("> ");
    }

    @Override
    public void onPrivateMessage(String fromUser, String message, Current current) {
        System.out.println("\n\u001B[36m[MENSAJE PRIVADO]\u001B[0m @" + fromUser + ": " + message);
        System.out.print("> ");
    }

    // =========================================================================
    // EVENTOS DEL MODULO 3 & 4: SALAS GRUPALES Y ARCHIVOS (COMPAÑERO 1)
    // =========================================================================

    @Override
    public void onRoomMessage(String roomName, String fromUser, String message, Current current) {
        System.out.println("\n\u001B[33m[SALA #" + roomName + "]\u001B[0m @" + fromUser + ": " + message);
        System.out.print("> ");
    }

    @Override
    public void onPrivateFileChunkReceived(String fromUser, FileChunk chunk, Current current) {
        // TODO: Integrante 2 ensamblará y guardará el archivo en disco
        System.out.println("\n[ARCHIVO PRIVADO] @" + fromUser + " enviando bloque " + (chunk.chunkIndex + 1) + "/" + chunk.totalChunks + " de " + chunk.fileName);
        System.out.print("> ");
    }

    @Override
    public void onRoomFileChunkReceived(String roomName, String fromUser, FileChunk chunk, Current current) {
        // TODO: Integrante 2 ensamblará y guardará el archivo en disco
        System.out.println("\n[ARCHIVO SALA #" + roomName + "] @" + fromUser + " enviando bloque " + (chunk.chunkIndex + 1) + "/" + chunk.totalChunks + " de " + chunk.fileName);
        System.out.print("> ");
    }

    // =========================================================================
    // EVENTOS DEL MODULO 5 & 6: LLAMADAS DE VOZ UDP (COMPAÑERO 2)
    // =========================================================================

    @Override
    public void onIncomingCall(String caller, String callerIp, int callerUdpPort, Current current) {
        System.out.println("\n\u001B[35m[LLAMADA ENTRANTE]\u001B[0m @" + caller + " te esta llamando desde " + callerIp + ":" + callerUdpPort);
        System.out.println("-> Usa '/accept' para responder o '/reject' para colgar.");
        System.out.print("> ");
    }

    @Override
    public void onCallAccepted(String callee, String calleeIp, int calleeUdpPort, Current current) {
        System.out.println("\n\u001B[32m[LLAMADA ESTABLECIDA]\u001B[0m @" + callee + " acepto tu llamada (" + calleeIp + ":" + calleeUdpPort + ")");
        System.out.print("> ");
    }

    @Override
    public void onCallRejected(String callee, String reason, Current current) {
        System.out.println("\n\u001B[31m[LLAMADA RECHAZADA]\u001B[0m @" + callee + " rechazo la llamada. Razon: " + reason);
        System.out.print("> ");
    }

    @Override
    public void onCallEnded(String remoteUser, Current current) {
        System.out.println("\n\u001B[33m[LLAMADA FINALIZADA]\u001B[0m @" + remoteUser + " ha colgado la llamada.");
        System.out.print("> ");
    }

    @Override
    public void onUserJoinedVoiceGroup(String roomName, String username, String ip, int udpPort, Current current) {
        System.out.println("\n[VOZ SALA #" + roomName + "] @" + username + " se unio a la llamada de voz.");
        System.out.print("> ");
    }

    @Override
    public void onUserLeftVoiceGroup(String roomName, String username, Current current) {
        System.out.println("\n[VOZ SALA #" + roomName + "] @" + username + " abandono la llamada de voz.");
        System.out.print("> ");
    }
}
