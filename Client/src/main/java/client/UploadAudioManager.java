
package client;

import javax.sound.sampled.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public  final class UploadAudioManager implements AutoCloseable {

    private static final AudioFormat FORMAT =
            new AudioFormat(16000f, 16, 1, true, false);

    private DatagramSocket socket;

    private final Map<String, InetSocketAddress> peers =
            new ConcurrentHashMap<>();

    private volatile boolean running;
    private volatile boolean muted;

    private volatile TargetDataLine mic;
    private volatile SourceDataLine speaker;

    private Thread sender;
    private Thread receiver;

    public UploadAudioManager() throws SocketException {
        socket = new DatagramSocket(0);
        socket.setSoTimeout(300);
    }

    public int getPort() {
        if (socket == null || socket.isClosed()) {
            throw new IllegalStateException(
                    "El socket UDP no esta disponible");
        }

        return socket.getLocalPort();
    }

    public synchronized void addPeer(
            String user, String host, int port)
            throws UnknownHostException {

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "Puerto UDP invalido");
        }

        peers.put(user, new InetSocketAddress(
                InetAddress.getByName(host), port));
    }

    public void removePeer(String user) {
        peers.remove(user);
    }

    public void clearPeers() {
        peers.clear();
    }

    public void setMuted(boolean value) {
        muted = value;
    }

    public boolean isMuted() {
        return muted;
    }

    public synchronized void start()
            throws LineUnavailableException {

        if (running) return;

        DataLine.Info in = new DataLine.Info(
                TargetDataLine.class, FORMAT);

        DataLine.Info out = new DataLine.Info(
                SourceDataLine.class, FORMAT);

        TargetDataLine newMic =
                (TargetDataLine) AudioSystem.getLine(in);

        SourceDataLine newSpeaker =
                (SourceDataLine) AudioSystem.getLine(out);

        try {
            newMic.open(FORMAT);
            newSpeaker.open(FORMAT);

            newMic.start();
            newSpeaker.start();

        } catch (LineUnavailableException | RuntimeException e) {
            newMic.close();
            newSpeaker.close();
            throw e;
        }

        mic = newMic;
        speaker = newSpeaker;
        running = true;

        sender = new Thread(
                this::sendLoop, "voz-udp-envio");

        receiver = new Thread(
                this::receiveLoop, "voz-udp-recepcion");

        sender.setDaemon(true);
        receiver.setDaemon(true);

        sender.start();
        receiver.start();

        System.out.println("[UDP] Audio iniciado");
    }

    private void sendLoop() {

        // Audio PCM de 20 milisegundos.
        byte[] buffer = new byte[640];

        while (running) {

            TargetDataLine input = mic;

            if (input == null || !input.isOpen()) {
                break;
            }

            int read = input.read(
                    buffer, 0, buffer.length);

            if (read <= 0 || muted) {
                continue;
            }

            for (InetSocketAddress peer : peers.values()) {

                if (!running) break;

                try {
                    DatagramPacket packet =
                            new DatagramPacket(
                                    buffer, read, peer);

                    socket.send(packet);

                } catch (Exception e) {
                    if (running) {
                        System.err.println(
                                "[UDP envio] " + e.getMessage());
                    }
                }
            }
        }
    }

    private void receiveLoop() {

        byte[] buffer = new byte[2048];

        while (running) {

            DatagramPacket packet =
                    new DatagramPacket(
                            buffer, buffer.length);

            try {
                socket.receive(packet);

                boolean accepted = peers.values()
                        .stream()
                        .anyMatch(peer ->
                                peer.getAddress().equals(
                                        packet.getAddress())
                                        && peer.getPort()
                                        == packet.getPort());

                SourceDataLine output = speaker;

                if (accepted && running
                        && output != null
                        && output.isOpen()) {

                    output.write(
                            packet.getData(),
                            packet.getOffset(),
                            packet.getLength());
                }

            } catch (SocketTimeoutException ignored) {

                // Permite comprobar si se detuvo el audio.

            } catch (SocketException e) {

                if (running) {
                    System.err.println(
                            "[UDP] " + e.getMessage());
                }

            } catch (Exception e) {

                if (running) {
                    System.err.println(
                            "[Audio] " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stopAudio() {

        running = false;

        if (mic != null) {
            mic.stop();
            mic.close();
            mic = null;
        }

        if (speaker != null) {
            speaker.stop();
            speaker.close();
            speaker = null;
        }

        peers.clear();

        System.out.println("[UDP] Audio detenido");
    }

    @Override
    public synchronized void close() {
        stopAudio();
        socket.close();
    }
}


