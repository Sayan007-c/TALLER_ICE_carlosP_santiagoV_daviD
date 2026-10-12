
package client;

import ChatApp.*;
import com.zeroc.Ice.Current;

public class ClientCallbackI implements ClientCallback {

    private final String clientUsername;
    private final VoiceController voice;
    private final FileTransferManager fileManager;

    public ClientCallbackI(
            String clientUsername,
            VoiceController voice,
            FileTransferManager fileManager) {

        this.clientUsername = clientUsername;
        this.voice = voice;
        this.fileManager = fileManager;
    }

    // RF-01: Presencia de usuarios.
    @Override
    public void onUserStatusChanged(
            String username,
            boolean online,
            Current current) {

        String status = online
                ? "[EN LINEA]"
                : "[DESCONECTADO]";

        System.out.println(
                "\n" + status + " Usuario: @"
                        + username);

        System.out.print("> ");
    }

    // RF-02: Mensajeria privada.
    @Override
    public void onPrivateMessage(
            String fromUser,
            String message,
            Current current) {

        System.out.println(
                "\n[MENSAJE PRIVADO] @"
                        + fromUser + ": " + message);

        System.out.print("> ");
    }

    // RF-03: Mensajeria de salas.
    @Override
    public void onRoomMessage(
            String roomName,
            String fromUser,
            String message,
            Current current) {

        System.out.println(
                "\n[SALA #" + roomName + "] @"
                        + fromUser + ": " + message);

        System.out.print("> ");
    }

    // RF-04: Fragmentos de archivos privados.
    @Override
    public void onPrivateFileChunkReceived(
            String fromUser,
            FileChunk chunk,
            Current current) {

        System.out.println(
                "\n[ARCHIVO PRIVADO] @"
                        + fromUser + " bloque "
                        + (chunk.chunkIndex + 1)
                        + "/" + chunk.totalChunks
                        + " de " + chunk.fileName);
        System.out.print("> ");

        fileManager.processIncomingChunk(fromUser, chunk, false);
    }

    // RF-04: Archivos en salas.
    @Override
    public void onRoomFileChunkReceived(
            String roomName,
            String fromUser,
            FileChunk chunk,
            Current current) {

        System.out.println(
                "\n[ARCHIVO SALA #" + roomName
                        + "] @" + fromUser
                        + " bloque " + (chunk.chunkIndex + 1)
                        + "/" + chunk.totalChunks
                        + " de " + chunk.fileName);
        System.out.print("> ");

        fileManager.processIncomingChunk(roomName, chunk, true);
    }

    // RF-05: Llamada entrante.
    @Override
    public void onIncomingCall(
            String caller,
            String callerIp,
            int callerUdpPort,
            Current current) {

        System.out.println(
                "\n[LLAMADA ENTRANTE] @"
                        + caller + " te esta llamando");

        voice.incoming(
                caller, callerIp, callerUdpPort);

        System.out.print("> ");
    }

    // RF-05: Llamada aceptada.
    @Override
    public void onCallAccepted(
            String callee,
            String calleeIp,
            int calleeUdpPort,
            Current current) {

        voice.accepted(
                callee, calleeIp, calleeUdpPort);

        System.out.println(
                "[LLAMADA ESTABLECIDA] @"
                        + callee + " acepto tu llamada");

        System.out.print("> ");
    }


    @Override
    public void onCallRejected(
            String callee,
            String reason,
            Current current) {

        voice.rejected(callee, reason);

        System.out.println(
                "[LLAMADA RECHAZADA] @"
                        + callee + ": " + reason);

        System.out.print("> ");
    }


    @Override
    public void onCallEnded(
            String remoteUser,
            Current current) {

        voice.ended(remoteUser);

        System.out.println(
                "[LLAMADA FINALIZADA] @"
                        + remoteUser + " colgo");

        System.out.print("> ");
    }


    @Override
    public void onUserJoinedVoiceGroup(
            String roomName,
            String username,
            String ip,
            int udpPort,
            Current current) {

        voice.joinedRoom(
                roomName, username, ip, udpPort);

        System.out.println(
                "[VOZ SALA #" + roomName + "] @"
                        + username + " se unio");
    }


    @Override
    public void onUserLeftVoiceGroup(
            String roomName,
            String username,
            Current current) {

        voice.leftRoom(roomName, username);

        System.out.println(
                "[VOZ SALA #" + roomName + "] @"
                        + username + " salio");
    }
}

