
package client;

import ChatApp.*;
import java.net.InetAddress;
import java.net.SocketException;

public final class VoiceController implements AutoCloseable {

    private final UploadAudioManager audio;
    private final ChatServerPrx server;
    private final String me;

    private String incoming;
    private String outgoing;
    private String remote;
    private String room;

    private String incomingIp;
    private int incomingPort;

    public VoiceController(
            ChatServerPrx server, String me)
            throws SocketException {

        this.server = server;
        this.me = me;
        this.audio = new UploadAudioManager();
    }

    public int udpPort() {
        return audio.getPort();
    }

    public String localIp() throws Exception {

        String configured = System.getenv("VOICE_IP");

        return configured != null && !configured.isBlank()
                ? configured
                : InetAddress.getLocalHost().getHostAddress();
    }

    private boolean busy() {
        return incoming != null
                || outgoing != null
                || remote != null
                || room != null;
    }

    // RF-05: Solicitar llamada individual.
    public synchronized void call(String user)
            throws Exception {

        if (busy()) {
            throw new IllegalStateException(
                    "Ya estas ocupado");
        }

        if (user.equalsIgnoreCase(me)) {
            throw new IllegalArgumentException(
                    "No puedes llamarte a ti mismo");
        }

        outgoing = user;

        try {
            server.requestCall(
                    me, user, localIp(), udpPort());

            System.out.println(
                    "[VOZ] Llamando a @" + user);

        } catch (Exception e) {
            outgoing = null;
            throw e;
        }
    }

    // Recibir solicitud de llamada.
    public synchronized void incoming(
            String user, String ip, int port) {

        if (busy()) {
            try {
                server.rejectCall(
                        me, user, "Ocupado");
            } catch (Exception ignored) {
            }
            return;
        }

        incoming = user;
        incomingIp = ip;
        incomingPort = port;

        System.out.println(
                "\n[VOZ] Llamada de @" + user);

        System.out.println(
                "Escribe /accept o /reject");
    }

    // RF-05: Aceptar llamada.
    public synchronized void accept()
            throws Exception {

        if (incoming == null) {
            throw new IllegalStateException(
                    "No hay llamada entrante");
        }

        String user = incoming;

        try {
            audio.addPeer(
                    user, incomingIp, incomingPort);

            audio.start();

            server.acceptCall(
                    me, user, localIp(), udpPort());

            remote = user;
            incoming = null;

            System.out.println(
                    "[VOZ] Conversacion con @" + user);

        } catch (Exception e) {
            resetCall();
            throw e;
        }
    }

    // El otro usuario acepto.
    public synchronized void accepted(
            String user, String ip, int port) {

        if (!user.equals(outgoing)) {
            return;
        }

        try {
            audio.addPeer(user, ip, port);
            audio.start();

            remote = user;
            outgoing = null;

            System.out.println(
                    "[VOZ] Llamada activa con @" + user);

        } catch (Exception e) {

            System.err.println(
                    "[VOZ] Error al iniciar audio: "
                            + e.getMessage());

            resetCall();

            try {
                server.endCall(me, user);
            } catch (Exception ignored) {
            }
        }
    }

    // RF-05: Rechazar llamada.
    public synchronized void reject() throws UserNotFoundException {

        if (incoming == null) {
            throw new IllegalStateException(
                    "No hay llamada entrante");
        }

        String user = incoming;

        server.rejectCall(
                me, user, "Rechazada por el usuario");

        incoming = null;
        incomingIp = null;
        incomingPort = 0;

        System.out.println("[VOZ] Llamada rechazada");
    }

    // Callback de rechazo.
    public synchronized void rejected(
            String user, String reason) {

        if (user.equals(outgoing)) {
            resetCall();
        }

        System.out.println(
                "[VOZ] @" + user
                        + " rechazo la llamada: " + reason);
    }

    // RF-05: Colgar llamada.
    public synchronized void hangup() throws UserNotFoundException, RoomNotFoundException {

        if (room != null) {
            leaveRoom();
            return;
        }

        String user = remote != null
                ? remote
                : outgoing != null
                ? outgoing
                : incoming;

        if (user == null) {
            throw new IllegalStateException(
                    "No hay llamada activa");
        }

        try {
            if (incoming != null) {
                server.rejectCall(
                        me, incoming, "Cancelada");
            } else {
                server.endCall(me, user);
            }

        } finally {
            resetCall();
        }
    }

    // Callback cuando el otro cuelga.
    public synchronized void ended(String user) {

        if (user.equals(remote)
                || user.equals(outgoing)
                || user.equals(incoming)) {

            resetCall();

            System.out.println(
                    "[VOZ] @" + user
                            + " termino la llamada");
        }
    }

    private void resetCall() {

        remote = null;
        outgoing = null;
        incoming = null;

        incomingIp = null;
        incomingPort = 0;

        audio.stopAudio();
    }

    // RF-06: Entrar a conferencia grupal.
    public synchronized void joinRoom(String name)
            throws Exception {

        if (busy()) {
            throw new IllegalStateException(
                    "Finaliza la llamada actual");
        }

        VoiceMember[] members = server.joinVoiceGroup(
                name, me, localIp(), udpPort());

        room = name;

        try {
            for (VoiceMember member : members) {

                if (!member.username.equals(me)) {

                    audio.addPeer(
                            member.username,
                            member.ip,
                            member.udpPort);
                }
            }

            audio.start();

            System.out.println(
                    "[VOZ] En conferencia de #" + name);

        } catch (Exception e) {
            leaveRoom();
            throw e;
        }
    }

    // Nuevo participante de la conferencia.
    public synchronized void joinedRoom(
            String name, String user,
            String ip, int port) {

        if (!name.equals(room)
                || user.equals(me)) {
            return;
        }

        try {
            audio.addPeer(user, ip, port);

            System.out.println(
                    "[VOZ] @" + user
                            + " entro a la conferencia");

        } catch (Exception e) {
            System.err.println(
                    "[VOZ] Miembro invalido: "
                            + e.getMessage());
        }
    }

    // Participante abandona la conferencia.
    public synchronized void leftRoom(
            String name, String user) {

        if (name.equals(room)) {
            audio.removePeer(user);

            System.out.println(
                    "[VOZ] @" + user
                            + " salio de la conferencia");
        }
    }

    // RF-06: Abandonar conferencia.
    public synchronized void leaveRoom() throws RoomNotFoundException {

        if (room == null) {
            throw new IllegalStateException(
                    "No estas en conferencia");
        }

        String old = room;
        room = null;

        try {
            server.leaveVoiceGroup(old, me);

        } finally {
            audio.stopAudio();
        }

        System.out.println(
                "[VOZ] Saliste de #" + old);
    }

    // RF-06: Silenciar y activar microfono.
    public void mute(boolean value) {

        audio.setMuted(value);

        System.out.println(value
                ? "[VOZ] Microfono silenciado"
                : "[VOZ] Microfono activo");
    }

    @Override
    public synchronized void close() {

        if (room != null) {
            try {
                leaveRoom();
            } catch (Exception ignored) {
            }

        } else if (busy()) {
            try {
                hangup();
            } catch (Exception ignored) {
            }
        }

        audio.close();
    }
}
